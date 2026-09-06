/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.model

import android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ArchiveCompatibilityParams
import android.util.Log
import androidx.annotation.WorkerThread
import com.android.launcher3.BuildConfig
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.Utilities
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.graphics.ThemeManager.ThemeChangeListener
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUpdateTask
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.IconChangeTracker
import com.android.launcher3.icons.LauncherIcons.IconPool
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.tasks.AutomationChangedTask
import com.android.launcher3.model.tasks.PackageUpdatedTask
import com.android.launcher3.model.tasks.ShortcutsChangedTask
import com.android.launcher3.model.tasks.UserAvailabilityChangedTask
import com.android.launcher3.model.tasks.UserLockStateChangedTask
import com.android.launcher3.model.tasks.WidgetFeaturesUpdateTask
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.pm.UserCache.UserChangeEvent
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI
import com.android.launcher3.util.SettingsCache.PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.actionsFilter
import com.android.launcher3.widget.ProvidersUpdateDispatcher
import com.android.launcher3.widget.custom.CustomWidgetManager
import javax.inject.Inject
import javax.inject.Provider

/** Utility class for initializing all model callbacks */
class ModelInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val iconPool: IconPool,
    private val iconCache: IconCache,
    private val idp: InvariantDeviceProfile,
    private val themeManager: ThemeManager,
    private val userCache: UserCache,
    private val settingsCache: SettingsCache,
    private val customWidgetManager: CustomWidgetManager,
    private val installSessionHelper: InstallSessionHelper,
    private val homeScreenFilesProvider: HomeScreenFilesProvider,
    private val lifeCycle: DaggerSingletonTracker,
    private val homeScreenFilesUpdateTask: HomeScreenFilesUpdateTask.Factory,
    private val iconChangeTracker: IconChangeTracker,
    private val prefs: LauncherPrefs,
    private val automationRepo: AutomationRepository,
    private val updateDispatcher: Provider<ProvidersUpdateDispatcher>,
) {

    // only allow this once per reboot to reload work apps
    private var mShouldReloadWorkProfile = true

    fun initialize(model: LauncherModel) {
        initializeDisplayEvents(model)

        // System changes
        val modelCallbacks = model.newModelCallbacks()
        val launcherApps = context.getSystemService(LauncherApps::class.java)!!
        // TODO: remove logging after b/425319508
        FileLog.d(TAG, "registering modelCallbacks for LauncherApps")
        launcherApps.registerCallback(modelCallbacks, MODEL_EXECUTOR.handler)
        lifeCycle.addCloseable {
            // TODO: remove logging after b/425319508
            FileLog.d(TAG, "Unregistering modelCallbacks from LauncherApps")
            launcherApps.unregisterCallback(modelCallbacks)
        }

        if (Utilities.ATLEAST_V && Flags.enableSupportForArchiving()) {
            launcherApps.setArchiveCompatibility(
                ArchiveCompatibilityParams().apply {
                    setEnableUnarchivalConfirmation(false)
                    setEnableIconOverlay(false)
                }
            )
        }

        // Device profile policy changes
        val dpUpdateReceiver =
            SimpleBroadcastReceiver(
                context = context,
                executor = UI_HELPER_EXECUTOR,
                callbackExecutor = MODEL_EXECUTOR,
            ) {
                model.enqueueModelUpdateTask(ReloadStringCacheTask())
            }
        dpUpdateReceiver.register(actionsFilter(ACTION_DEVICE_POLICY_RESOURCE_UPDATED))
        lifeCycle.addCloseable(dpUpdateReceiver)

        // Development helper
        if (BuildConfig.IS_STUDIO_BUILD) {
            val reloadReceiver =
                SimpleBroadcastReceiver(context, UI_HELPER_EXECUTOR) {
                    model.reloadIfActive("force-reload-broadcast")
                }
            reloadReceiver.register(actionsFilter(ACTION_FORCE_RELOAD), Context.RECEIVER_EXPORTED)
            lifeCycle.addCloseable(reloadReceiver)
        }

        // User changes
        lifeCycle.addCloseable(
            userCache.userChanges.forEach(MODEL_EXECUTOR) { handleUserEvent(model, it) }
        )

        // Private space settings changes
        lifeCycle.addCloseable(
            settingsCache.getListenableRef(PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI).forEach(
                MAIN_EXECUTOR
            ) {
                model.rebindCallbacks("private-space-state-changed")
            }
        )

        // Notification dots changes
        lifeCycle.addCloseable(
            settingsCache.getListenableRef(NOTIFICATION_BADGING_URI).forEach(MAIN_EXECUTOR) {
                dotsEnabled ->
                if (dotsEnabled)
                    NotificationListener.requestRebind(
                        ComponentName(context, NotificationListener::class.java)
                    )
            }
        )

        // Custom widgets
        lifeCycle.addCloseable(
            customWidgetManager.addWidgetRefreshCallback {
                model.reloadIfActive("custom-widgets-refresh")
            }
        )

        // Install session changes
        lifeCycle.addCloseable(installSessionHelper.registerInstallTracker(modelCallbacks))

        // Monitor changes to files shown on home screen.
        lifeCycle.addCloseable(
            homeScreenFilesProvider.updates.forEach(MODEL_EXECUTOR) {
                model.enqueueModelUpdateTask(homeScreenFilesUpdateTask.create(it))
            }
        )

        // Monitor widget features
        lifeCycle.addCloseable(
            updateDispatcher.get().updates.forEach(MODEL_EXECUTOR) { update ->
                model.enqueueModelUpdateTask(
                    WidgetFeaturesUpdateTask(update.appWidgetId, update.info)
                )
            }
        )
    }

    fun initializeDisplayEvents(model: LauncherModel) {
        fun refreshAndReloadLauncher(eventName: String) {
            iconPool.clear()
            iconCache.updateIconParams(idp.fillResIconDpi, idp.iconBitmapSize)
            model.reloadIfActive(eventName)
        }

        // IDP changes
        val idpChangeListener = OnIDPChangeListener { modelChanged ->
            if (modelChanged) refreshAndReloadLauncher("idb-changed")
        }
        idp.addOnChangeListener(idpChangeListener)
        lifeCycle.addCloseable { idp.removeOnChangeListener(idpChangeListener) }

        // Shape changes
        lifeCycle.addCloseable(
            themeManager.iconShapeData.forEach(MAIN_EXECUTOR) {
                model.rebindCallbacks("icon-shape-changed")
            }
        )

        if (Flags.enableAppAutomationIndicator()) {
            lifeCycle.addCloseable(
                automationRepo.automatedPackages.changes.forEach(MODEL_EXECUTOR) { change ->
                    model.enqueueModelUpdateTask(AutomationChangedTask(change))
                }
            )
        }

        // Theme changes
        val themeChangeListener = ThemeChangeListener { refreshAndReloadLauncher("theme-changed") }
        themeManager.addChangeListener(themeChangeListener)
        lifeCycle.addCloseable { themeManager.removeChangeListener(themeChangeListener) }

        // Icon changes
        lifeCycle.addCloseable(
            iconChangeTracker.changes.forEach(MODEL_EXECUTOR) { onAppIconChanged(model, it) }
        )
    }

    /** Called when the icon for an app changes, outside of package event */
    @WorkerThread
    private fun onAppIconChanged(model: LauncherModel, event: PackageUserKey) {
        // Update the icon for the calendar package
        Log.d(TAG, "onAppIconChanged: ${event.mPackageName}")
        model.enqueueModelUpdateTask(
            PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, event.mUser, event.mPackageName)
        )
        ShortcutRequest(context, event.mUser)
            .forPackage(event.mPackageName)
            .query(ShortcutRequest.PINNED)
            .let {
                if (it.isNotEmpty()) {
                    model.enqueueModelUpdateTask(
                        ShortcutsChangedTask(event.mPackageName, it, event.mUser, false)
                    )
                }
            }
    }

    private fun handleUserEvent(model: LauncherModel, event: UserChangeEvent) {
        val oldUser = event.oldUser
        val newUser = event.newUser

        when {
            // User removed
            oldUser != null && newUser == null -> {
                if (oldUser.iconInfo.isWork) prefs.put(LauncherPrefs.WORK_EDU_STEP, 0)
                model.reloadIfActive("user-removed")
            }

            // User added
            oldUser == null && newUser != null -> model.reloadIfActive("user-added")

            // User changed
            oldUser != null && newUser != null -> {
                var handled = false
                if (newUser.isQuietModeEnabled != oldUser.isQuietModeEnabled) {
                    val isWork = newUser.iconInfo.isWork
                    if (isWork && mShouldReloadWorkProfile && !newUser.isQuietModeEnabled) {
                        // Force reload the first time work profile's quiet mode is disabled
                        model.reloadIfActive("first-time-work-profile")
                    } else {
                        if (isWork) mShouldReloadWorkProfile = false
                        model.enqueueModelUpdateTask(
                            UserAvailabilityChangedTask(newUser.iconInfo.user)
                        )
                    }
                    handled = true
                }
                if (newUser.isUnlocked != oldUser.isUnlocked) {
                    model.enqueueModelUpdateTask(
                        UserLockStateChangedTask(newUser.iconInfo.user, newUser.isUnlocked)
                    )
                    handled = true
                }

                // We don't know what changed, just reload to be safe
                if (!handled) model.reloadIfActive("unknown-user-event")
            }
        }
    }

    companion object {
        private const val TAG = "ModelInitializer"
        private const val ACTION_FORCE_RELOAD = "force-reload-launcher"
    }
}
