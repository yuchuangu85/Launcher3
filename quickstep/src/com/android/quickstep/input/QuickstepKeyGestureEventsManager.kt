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

package com.android.quickstep.input

import android.Manifest.permission.MANAGE_KEY_GESTURES
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.hardware.input.InputManager
import android.hardware.input.InputManager.KeyGestureEventHandler
import android.hardware.input.KeyGestureEvent
import android.hardware.input.KeyGestureEvent.ACTION_GESTURE_COMPLETE
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.provider.Settings.Secure.USER_SETUP_COMPLETE
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.RetryingExecutor
import com.android.launcher3.util.SettingsCache
import com.android.quickstep.OverviewCommandHelper
import com.android.quickstep.sysuiconnection.SysUIConnectionTracker
import com.android.window.flags.Flags
import javax.inject.Inject

/**
 * Manages subscription and unsubscription to launcher's key gesture events, e.g. all apps and
 * recents (incl. alt + tab).
 */
@LauncherAppSingleton
class QuickstepKeyGestureEventsManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    sysUIConnectionTracker: SysUIConnectionTracker,
    @Ui private val uiExecutor: LooperExecutor,
    lifecycle: DaggerSingletonTracker,
    private val settingsCache: SettingsCache,
) {

    private val sysUIComponent = sysUIConnectionTracker.activeComponent
    private val hasPermission =
        context.checkSelfPermission(MANAGE_KEY_GESTURES) == PERMISSION_GRANTED

    private val isUserSetupCompleted: Boolean
        get() = settingsCache.getValue(USER_SETUP_COMPLETE_URI)

    private val inputManager =
        if (Flags.grantManageKeyGesturesToRecents() && hasPermission)
            context.getSystemService(InputManager::class.java)
        else null

    @VisibleForTesting
    val overviewKeyGestureHelper =
        KeyGestureHelper(
            listOf(
                KEY_GESTURE_TYPE_RECENT_APPS,
                KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER,
                KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY,
            )
        ) { event: KeyGestureEvent ->
            if (!isUserSetupCompleted) return@KeyGestureHelper
            val component = sysUIComponent.value ?: return@KeyGestureHelper
            if (!hasPermission) return@KeyGestureHelper

            when (event.keyGestureType) {
                KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY ->
                    component.overviewCommandHelper
                        .getIfReady()
                        ?.addCommand(OverviewCommandHelper.CommandType.HOME, event.displayId)
                KEY_GESTURE_TYPE_RECENT_APPS -> {
                    if (event.action == ACTION_GESTURE_COMPLETE && !event.isCancelled) {
                        component.binder.onOverviewShown(triggeredFromAltTab = false)
                    }
                }
                KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER -> {
                    if (event.action == KeyGestureEvent.ACTION_GESTURE_START) {
                        component.binder.onOverviewShown(triggeredFromAltTab = true)
                    } else {
                        component.binder.onOverviewHidden(
                            triggeredFromAltTab = true,
                            triggeredFromHomeKey = false,
                        )
                    }
                }
                else ->
                    Log.e(
                        TAG,
                        "Ignore unsupported overview key gesture event type: ${event.keyGestureType}",
                    )
            }
        }

    private var allAppsPendingIntent: PendingIntent? = null

    @VisibleForTesting
    val allAppsKeyGestureHelper =
        KeyGestureHelper(listOf(KEY_GESTURE_TYPE_ALL_APPS)) { event ->
            if (!isUserSetupCompleted) return@KeyGestureHelper
            if (!hasPermission) return@KeyGestureHelper

            if (event.keyGestureType != KEY_GESTURE_TYPE_ALL_APPS) {
                Log.e(TAG, "Ignore unsupported key gesture event type: ${event.keyGestureType}")
                return@KeyGestureHelper
            }

            // Ignore the display ID from the KeyGestureEvent as we will use the focus display
            // from the SysUi proxy as the source of truth.
            allAppsPendingIntent?.send()
        }

    init {
        // Listen on UI executor as TISBinder calls destroy on UIExecutor as well
        lifecycle.addCloseable(
            sysUIComponent.forEach(uiExecutor) { component ->
                if (component == null) return@forEach
                registerOverviewKeyGestureEventHandler()
            }
        )
    }

    /**
     * Registers the all apps key gesture events.
     *
     * Subsequent registrations are ignored until [unregisterAllAppsKeyGestureEvent] is called.
     */
    fun registerAllAppsKeyGestureEvent(allAppsPendingIntent: PendingIntent) {
        this.allAppsPendingIntent = allAppsPendingIntent
        allAppsKeyGestureHelper.register()
    }

    /** Unregisters the all apps key gesture events. */
    fun unregisterAllAppsKeyGestureEvent() {
        allAppsPendingIntent = null
        allAppsKeyGestureHelper.unregister()
    }

    private fun registerOverviewKeyGestureEventHandler() {
        overviewKeyGestureHelper.register()
    }

    fun onDestroy() {
        overviewKeyGestureHelper.unregister()
        unregisterAllAppsKeyGestureEvent()
    }

    inner class KeyGestureHelper(
        private val keyGesturesToHandle: List<Int>,
        private val callback: (event: KeyGestureEvent) -> Unit,
    ) : KeyGestureEventHandler {

        private val retryingExecutor = RetryingExecutor(uiExecutor.handler)
        private val syncToken = Any()

        private var keyHandlerRegistered = false

        fun register() {
            retryingExecutor.execute {
                try {
                    synchronized(syncToken) {
                        if (!keyHandlerRegistered) {
                            inputManager?.registerKeyGestureEventHandler(keyGesturesToHandle, this)
                            keyHandlerRegistered = true
                        }
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering key event for: $keyGesturesToHandle", e)
                    false
                }
            }
        }

        fun unregister() {
            retryingExecutor.cancel()
            synchronized(syncToken) {
                inputManager?.unregisterKeyGestureEventHandler(this)
                keyHandlerRegistered = false
            }
        }

        override fun handleKeyGestureEvent(event: KeyGestureEvent, focusedToken: IBinder?) {
            callback.invoke(event)
        }
    }

    private companion object {
        const val TAG = "KeyGestureEventsHandler"
        val USER_SETUP_COMPLETE_URI: Uri = Settings.Secure.getUriFor(USER_SETUP_COMPLETE)
    }
}
