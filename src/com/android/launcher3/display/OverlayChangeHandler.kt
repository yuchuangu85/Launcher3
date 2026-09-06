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

package com.android.launcher3.display

import android.content.Context
import android.util.Log
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.packageFilter
import javax.inject.Inject

/** Class to track overlay change events */
@LauncherAppSingleton
class OverlayChangeHandler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val uiExecutor: LooperExecutor,
    lifecycle: DaggerSingletonTracker,
) {

    private val callbacks = mutableListOf<Runnable>()

    /**
     * Initialize navigation mode change listener. We will register broadcast receiver on main
     * thread to ensure not missing changes on TARGET_OVERLAY_PACKAGE and ACTION_OVERLAY_CHANGED
     */
    init {
        var destroyed = false
        SimpleBroadcastReceiver(context, uiExecutor) {
                Log.d(TAG, "Overlay changed, destroyed=$destroyed")
                if (!destroyed) callbacks.forEach { it.run() }
            }
            .let {
                it.register(packageFilter(TARGET_OVERLAY_PACKAGE, ACTION_OVERLAY_CHANGED))
                lifecycle.addCloseable(it)
                lifecycle.addCloseable { destroyed = true }
            }
    }

    /**
     * Adds the [callback] to be called on the main thread when overlay changes. Returns a
     * [SafeCloseable] which can be used to remove this callback. So singleton objects can safely
     * ignore the cleanup as this class is also application scoped
     */
    fun addCallback(callback: Runnable): SafeCloseable {
        callbacks.add(callback)
        return SafeCloseable { callbacks.remove(callback) }
    }

    companion object {
        private const val TAG = "OverlayChangeHandler"
        private const val ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED"
        private const val TARGET_OVERLAY_PACKAGE = "android"
    }
}
