/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Context
import android.graphics.drawable.Icon
import android.provider.Settings
import android.provider.Settings.Secure.USER_SETUP_COMPLETE
import android.view.accessibility.AccessibilityManager
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.taskbar.TaskbarManagerImpl
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SettingsCache
import com.android.quickstep.dagger.SysUIConnectionSingleton
import com.android.quickstep.input.QuickstepKeyGestureEventsManager
import java.io.PrintWriter
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Provider

private val USER_SETUP_COMPLETE_URI = Settings.Secure.getUriFor(USER_SETUP_COMPLETE)

/**
 * Registers a [RemoteAction] for toggling All Apps if needed.
 *
 * We need this action when either [isHomeAndOverviewSame] or [isTaskbarPresent] is `true`. When
 * home and overview are the same, we can control Launcher's or Taskbar's All Apps tray. If they are
 * not the same, but Taskbar is present, we can only control Taskbar's tray.
 */
@SysUIConnectionSingleton
class AllAppsActionManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @LightweightBackground private val bgExecutor: Executor,
    private val quickstepKeyGestureEventsManager: QuickstepKeyGestureEventsManager,
    private val allAppsIntentSenderProvider: Provider<TaskbarManagerImpl.AllAppsIntentSender>,
) {

    private var onSettingsChangeSafeCloseable: SafeCloseable? = null

    init {
        onSettingsChangeSafeCloseable =
            SettingsCache.INSTANCE[context].getListenableRef(USER_SETUP_COMPLETE_URI).forEach(
                bgExecutor
            ) { v ->
                // Setting will call updateSystemAction() which is synchronized, thus this callback
                // is safe to be called on bgExecutor
                isUserSetupComplete = v
            }
    }

    /** `true` if home and overview are the same Activity. */
    @Volatile
    var isHomeAndOverviewSame = false
        set(value) {
            field = value
            updateSystemAction()
        }

    /** `true` if Taskbar is enabled. */
    @Volatile
    var isTaskbarPresent = false
        set(value) {
            field = value
            updateSystemAction()
        }

    /** `true` if the setup UI is visible. */
    @Volatile
    var isSetupUiVisible = false
        set(value) {
            field = value
            updateSystemAction()
        }

    @Volatile
    private var isUserSetupComplete: Boolean = false
        set(value) {
            field = value
            updateSystemAction()
        }

    /** `true` if the action should be registered. */
    @Volatile
    var isActionRegistered = false
        private set

    @Volatile private var isUserUnlocked = false

    fun onUserUnlocked() {
        isUserUnlocked = true
        updateSystemAction()
    }

    private fun updateSystemAction() {
        synchronized(this) {
            val isInSetupFlow = isSetupUiVisible || !isUserSetupComplete
            val shouldRegisterAction =
                (isHomeAndOverviewSame || isTaskbarPresent) && !isInSetupFlow && isUserUnlocked
            if (isActionRegistered == shouldRegisterAction) return
            isActionRegistered = shouldRegisterAction

            bgExecutor.execute {
                val accessibilityManager =
                    context.getSystemService(AccessibilityManager::class.java) ?: return@execute
                if (shouldRegisterAction) {
                    val allAppsPendingIntent = PendingIntent(allAppsIntentSenderProvider.get())
                    accessibilityManager.registerSystemAction(
                        RemoteAction(
                            Icon.createWithResource(context, R.drawable.ic_apps),
                            context.getString(R.string.all_apps_label),
                            context.getString(R.string.all_apps_label),
                            allAppsPendingIntent,
                        ),
                        GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS,
                    )
                    quickstepKeyGestureEventsManager.registerAllAppsKeyGestureEvent(
                        allAppsPendingIntent
                    )
                } else {
                    accessibilityManager.unregisterSystemAction(
                        GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS
                    )
                    quickstepKeyGestureEventsManager.unregisterAllAppsKeyGestureEvent()
                }
            }
        }
    }

    fun onDestroy() {
        synchronized(this) {
            isActionRegistered = false
            context
                .getSystemService(AccessibilityManager::class.java)
                ?.unregisterSystemAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
            quickstepKeyGestureEventsManager.unregisterAllAppsKeyGestureEvent()
            onSettingsChangeSafeCloseable?.close()
            onSettingsChangeSafeCloseable = null
        }
    }

    fun dump(pw: PrintWriter) {
        pw.println("AllAppsActionManager:")
        pw.println("\tisHomeAndOverviewSame=$isHomeAndOverviewSame")
        pw.println("\tisTaskbarPresent=$isTaskbarPresent")
        pw.println("\tisSetupUiVisible=$isSetupUiVisible")
        pw.println("\tisUserSetupComplete=$isUserSetupComplete")
        pw.println("\tisActionRegistered=$isActionRegistered")
        pw.println("\tisUserUnlocked=$isUserUnlocked")
    }
}
