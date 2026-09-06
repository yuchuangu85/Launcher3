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

package com.android.launcher3.popup

import android.app.PendingIntent
import android.util.Log
import android.view.View
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DISABLE_APP_LOCK_TAP
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_ENABLE_APP_LOCK_TAP
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.AppLockShortcut.Companion.newInstance
import com.android.launcher3.util.Executors
import com.android.launcher3.views.ActivityContext
import java.util.concurrent.CompletableFuture

/**
 * Represents the App Lock system shortcut for a given app.
 *
 * Use the [newInstance] factory method to create instances of this shortcut.
 *
 * The label, the icon, and the action performed after click depend on the current App Lock enabled
 * state for the given app. If the app has App Lock enabled, then all items will be related to
 * disabling App Lock, and vice versa.
 */
sealed class AppLockShortcut<T : ActivityContext>(
    iconId: Int,
    titleId: Int,
    target: T,
    itemInfo: ItemInfo,
    originalView: View,
    private val newAppLockEnabled: Boolean,
) : SystemShortcut<T>(iconId, titleId, target, itemInfo, originalView) {

    private val packageName: String? = mItemInfo.targetComponent?.packageName
    private val appLockPendingIntentFuture: CompletableFuture<PendingIntent?> =
        getAppLockPendingIntentFuture()

    private fun getAppLockPendingIntentFuture(): CompletableFuture<PendingIntent?> =
        if (packageName == null) {
            Log.w(TAG, "Package name is null")
            CompletableFuture.completedFuture(null)
        } else {
            CompletableFuture.supplyAsync(
                {
                    try {
                        mTarget
                            .asContext()
                            .packageManager
                            .getEnableAppLockIntentForPackage(packageName, newAppLockEnabled)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Failed to get App Lock intent for $packageName", e)
                        null
                    }
                },
                Executors.ORDERED_BG_EXECUTOR,
            )
        }

    /**
     * Handles the click event for the App Lock shortcut.
     *
     * This method logs the tap event and asynchronously retrieves a [PendingIntent] to toggle the
     * App Lock state for the target package. It then sends the intent and dismisses the menu.
     */
    override fun onClick(view: View) {
        mTarget.statsLogManager.logger().withItemInfo(mItemInfo).log(tapEvent)

        // getAppLockPendingIntentFuture already logs a warning about the null package name.
        packageName ?: return
        appLockPendingIntentFuture
            .thenAcceptAsync(
                { pendingIntent ->
                    handleAppLockPendingIntentOnUi(pendingIntent, packageName, view)
                },
                mTarget.uiExecutor,
            )
            .exceptionally { ex ->
                Log.e(TAG, "Failed to get App Lock intent future for $packageName", ex)
                null
            }
    }

    private fun handleAppLockPendingIntentOnUi(
        pendingIntent: PendingIntent?,
        packageName: String,
        view: View,
    ) {
        if (pendingIntent == null) {
            Log.w(TAG, "Unable to get App Lock intent for $packageName")
            dismissTaskMenuView()
            return
        }

        val onEndCallback = mTarget.sendPendingIntentWithAnimation(view, pendingIntent, mItemInfo)
        if (onEndCallback == null) {
            dismissTaskMenuView()
        } else {
            onEndCallback.add(::dismissTaskMenuView)
        }
    }

    protected abstract val tapEvent: LauncherEvent

    /** Shortcut to enable App Lock for an app. */
    private class EnableAppLockShortcut<T : ActivityContext>(
        target: T,
        itemInfo: ItemInfo,
        originalView: View,
    ) :
        AppLockShortcut<T>(
            R.drawable.ic_enable_app_lock_button,
            R.string.enable_app_lock,
            target,
            itemInfo,
            originalView,
            newAppLockEnabled = true,
        ) {
        override val tapEvent: LauncherEvent = LAUNCHER_SYSTEM_SHORTCUT_ENABLE_APP_LOCK_TAP
    }

    /** Shortcut to disable App Lock for an app. */
    private class DisableAppLockShortcut<T : ActivityContext>(
        target: T,
        itemInfo: ItemInfo,
        originalView: View,
    ) :
        AppLockShortcut<T>(
            R.drawable.ic_disable_app_lock_button,
            R.string.disable_app_lock,
            target,
            itemInfo,
            originalView,
            newAppLockEnabled = false,
        ) {
        override val tapEvent: LauncherEvent = LAUNCHER_SYSTEM_SHORTCUT_DISABLE_APP_LOCK_TAP
    }

    companion object {
        private const val TAG = "AppLockShortcut"

        /**
         * Creates an appropriate [AppLockShortcut] for the given item.
         *
         * Before creating this shortcut, callers should confirm the given app supports App Lock by
         * querying [android.content.pm.ApplicationInfo.isAppLockSupported].
         *
         * @param isAppLockEnabled the current App Lock state of the app.
         * @return a shortcut to disable App Lock if it's currently enabled, or a shortcut to enable
         *   it if it's disabled.
         */
        @JvmStatic
        fun <T : ActivityContext> newInstance(
            target: T,
            itemInfo: ItemInfo,
            originalView: View,
            isAppLockEnabled: Boolean,
        ): AppLockShortcut<T> {
            return if (isAppLockEnabled) {
                DisableAppLockShortcut(target, itemInfo, originalView)
            } else {
                EnableAppLockShortcut(target, itemInfo, originalView)
            }
        }
    }
}
