/*
 * Copyright (C) 2026 The Android Open Source Project
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

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PACKAGE_PRIVATE

/** Interface for intercepting surface release method */
open class SurfaceReleaseCheck {

    @VisibleForTesting(otherwise = PACKAGE_PRIVATE)
    @get:JvmName("canRelease")
    var canRelease = false
        private set

    private var afterApplyCallback: Runnable? = null

    open fun setCanRelease(release: Boolean) {
        canRelease = release
        if (canRelease && afterApplyCallback != null) {
            val afterApplyRunnable = afterApplyCallback
            afterApplyCallback = null
            afterApplyRunnable?.run()
        }
    }

    fun addOnSafeToReleaseCallback(callback: Runnable) {
        if (canRelease) {
            callback.run()
        } else {
            if (afterApplyCallback == null) {
                afterApplyCallback = callback
            } else {
                val oldCallback = afterApplyCallback
                afterApplyCallback = Runnable {
                    callback.run()
                    oldCallback?.run()
                }
            }
        }
    }
}
