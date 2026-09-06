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

package com.android.launcher3.taskbar

import android.app.PendingIntent
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Configuration.configurationDiffToString
import android.os.Trace
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import com.android.app.tracing.TraceUtils
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.display.LauncherDisplayInfo
import com.android.launcher3.logging.RecreateTaskbarLatencyLogger
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.actionsFilter
import com.android.quickstep.DisplayModel
import com.android.quickstep.util.SystemActionConstants
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAVIGATION_BAR_DISABLED
import java.io.PrintWriter
import java.util.function.IntConsumer

/**
 * Stores taskbar states per display for TaskbarManagerImpl
 *
 * @property configChangeCallback called when the display configuration changes with configDiff set
 *   to the difference from previous configuration
 */
class PerDisplayTaskbarResource(
    val windowContext: Context,
    val displayId: Int,
    val navButtonController: TaskbarNavButtonController,
    val isExternalDisplay: Boolean,
    private val configChangeCallback: (PerDisplayTaskbarResource, configDiff: Int) -> Unit,
) : DisplayModel.DisplayResource {

    var taskbar: TaskbarActivityContext? = null
        private set

    private var isDestroyed = false

    private var oldConfig = windowContext.resources.configuration
    private var displayChangeSafeClosable: SafeCloseable? = null

    val rootLayout: FrameLayout =
        object : FrameLayout(windowContext) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                // The motion events can be outside the view bounds of task bar, and hence
                // manually dispatching them to the drag layer here.
                val dragLayer = taskbar?.dragLayer
                if (dragLayer != null && dragLayer.isAttachedToWindow) {
                    return dragLayer.dispatchTouchEvent(ev)
                }
                return super.dispatchTouchEvent(ev)
            }
        }

    private val viewManager =
        SafeViewManager(
            windowManager =
                requireNotNull(windowContext.getSystemService(WindowManager::class.java)) {
                    "WindowManager not found for $displayId"
                },
            rootLayout = rootLayout,
        )

    private val showTaskbarReceiver =
        SimpleBroadcastReceiver(windowContext, UI_HELPER_EXECUTOR, getTaskbarUiThread()) {
                Preconditions.assertTaskbarUiThread()
                debugMsg("showTaskbarFromBroadcast")
                taskbar?.showTaskbarFromBroadcast()
            }
            .apply {
                register(
                    actionsFilter(SystemActionConstants.ACTION_SHOW_TASKBAR),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            }

    val sharedState =
        TaskbarSharedState().apply {
            taskbarSystemActionPendingIntent =
                PendingIntent.getBroadcast(
                    windowContext,
                    SystemActionConstants.SYSTEM_ACTION_ID_TASKBAR,
                    Intent(SystemActionConstants.ACTION_SHOW_TASKBAR)
                        .setPackage(windowContext.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
        }

    val isTaskbarEnabled: Boolean
        get() =
            ((sharedState.sysuiStateFlags and SYSUI_STATE_NAVIGATION_BAR_DISABLED) == 0L).also {
                if (!it) debugMsg("No taskbar due to SYSUI_STATE_NAVIGATION_BAR_DISABLED")
            }

    val createTaskbarLatencyLogger = RecreateTaskbarLatencyLogger()

    private val componentCallbacks =
        object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    getTaskbarUiThread().execute { onConfigurationChangedInternal(newConfig) }
                }

                private fun onConfigurationChangedInternal(newConfig: Configuration) {
                    if (isDestroyed) return
                    Trace.instantForTrack(
                        Trace.TRACE_TAG_APP,
                        "TaskbarManager",
                        "onConfigurationChanged: $newConfig",
                    )
                    debugMsg("onConfigurationChanged: $newConfig")

                    var configDiff =
                        oldConfig.diff(newConfig) and SKIP_RECREATE_CONFIG_CHANGES.inv()

                    if ((configDiff and ActivityInfo.CONFIG_UI_MODE) != 0) {
                        debugMsg("onConfigurationChanged: theme changed")
                        // Only recreate for theme changes, not other UI mode changes such as
                        // docking.
                        val oldUiNightMode = oldConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        val newUiNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        if (oldUiNightMode == newUiNightMode) {
                            configDiff = configDiff and ActivityInfo.CONFIG_UI_MODE.inv()
                        }
                    }

                    debugMsg(
                        "onConfigurationChanged: | configDiff=" +
                            configurationDiffToString(configDiff)
                    )
                    configChangeCallback(this@PerDisplayTaskbarResource, configDiff)

                    oldConfig = Configuration(newConfig)
                }

                override fun onLowMemory() {}
            }
            .apply { windowContext.registerComponentCallbacks(this) }

    /** Calls [callback] when the display properties changes with the corresponding change flags */
    fun setDisplayChangeListener(callback: IntConsumer) {
        displayChangeSafeClosable?.close()
        displayChangeSafeClosable =
            windowContext.appComponent.displayController.getListenable(displayId)?.forEachChange(
                getTaskbarUiThread()
            ) { _, flags ->
                if ((flags and LauncherDisplayInfo.CHANGE_DENSITY) != 0) {
                    debugMsg("onDisplayInfoChanged: Display density changed")
                }
                if ((flags and LauncherDisplayInfo.CHANGE_NAVIGATION_MODE) != 0) {
                    debugMsg("onDisplayInfoChanged: Navigation mode changed")
                }
                if ((flags and LauncherDisplayInfo.CHANGE_ROTATION) != 0) {
                    debugMsg("onDisplayInfoChanged: Rotation changed")
                }
                val change = flags and RELEVANT_DISPLAY_CHANGES
                if (change != 0) {
                    if ((flags and LauncherDisplayInfo.CHANGE_SHOW_DESKTOP_FIRST_TASKBAR) != 0) {
                        debugMsg("onDisplayInfoChanged: show desktop-first taskbar changed")
                    }
                    callback.accept(change)
                }
            }
    }

    private fun removeExistingTaskbar() {
        taskbar?.onDestroy()
        taskbar = null
    }

    fun setCurrentTaskbar(activity: TaskbarActivityContext) {
        removeExistingTaskbar()
        taskbar = activity
        if (!isDestroyed) {
            viewManager.addView(activity.windowLayoutParams)
        }
    }

    fun destroyTaskbarForDisplay() {
        val taskbar = taskbar
        if (taskbar == null) {
            debugMsg("destroyTaskbarForDisplay: taskbar is NULL!")
            return
        }
        TraceUtils.trace("destroyTaskbarForDisplay") {
            debugMsg("destroyTaskbarForDisplay")
            removeExistingTaskbar()
            if (!isTaskbarEnabled) {
                removeTaskbarRootViewFromWindow()
            }
        }
    }

    fun removeTaskbarRootViewFromWindow() {
        removeExistingTaskbar()
        debugMsg("removeTaskbarRootViewFromWindow")
        viewManager.removeView()
    }

    override fun cleanup() {
        isDestroyed = true
        debugMsg("destroy removeTaskbarRootViewFromWindow")
        removeTaskbarRootViewFromWindow()

        debugMsg("destroy removeAndUnregisterComponentCallbacks")
        windowContext.unregisterComponentCallbacks(componentCallbacks)

        debugMsg("destroy displayChangeSafeClosable")
        displayChangeSafeClosable?.close()

        debugMsg("destroy taskbarReceiver")
        showTaskbarReceiver.close()
    }

    override fun dump(prefix: String, writer: PrintWriter) {
        writer.println("$prefix\tTaskbar at display $displayId:")
        taskbar?.dumpLogs(prefix + "\t\t", writer)
            ?: run { writer.println("$prefix\t\tTaskbarActivityContext: null") }
    }

    /**
     * Logs debug information about the TaskbarManager for primary display.
     *
     * @param debugReason A string describing the reason for the debug log.
     */
    fun debugMsg(debugReason: String) = Log.d(TAG, "$debugReason displayId=$displayId")

    companion object {
        private const val TAG = "PerDisplayTaskbarResource"

        /**
         * All the configurations which do not initiate taskbar recreation. This includes all the
         * configurations defined in Launcher's manifest entry and
         * ActivityController#filterConfigChanges
         */
        private const val SKIP_RECREATE_CONFIG_CHANGES =
            ActivityInfo.CONFIG_WINDOW_CONFIGURATION or
                ActivityInfo.CONFIG_KEYBOARD or
                ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
                ActivityInfo.CONFIG_MCC or
                ActivityInfo.CONFIG_MNC or
                ActivityInfo.CONFIG_NAVIGATION or
                ActivityInfo.CONFIG_ORIENTATION or
                ActivityInfo.CONFIG_SCREEN_SIZE or
                ActivityInfo.CONFIG_SCREEN_LAYOUT or
                ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE

        private const val RELEVANT_DISPLAY_CHANGES =
            LauncherDisplayInfo.CHANGE_DENSITY or
                LauncherDisplayInfo.CHANGE_NAVIGATION_MODE or
                LauncherDisplayInfo.CHANGE_SHOW_DESKTOP_FIRST_TASKBAR or
                LauncherDisplayInfo.CHANGE_ROTATION
    }
}
