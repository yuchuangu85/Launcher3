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

import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import android.view.InsetsState
import android.view.WindowInsets
import androidx.annotation.AnyThread
import com.android.launcher3.Utilities
import com.android.launcher3.util.Executors
import com.android.wm.shell.shared.IHomeTransitionListener.Stub
import com.android.wm.shell.shared.IShellTransitions
import java.util.concurrent.CopyOnWriteArrayList
import javax.annotation.concurrent.ThreadSafe

/** Class to track visibility state of Launcher */
@ThreadSafe
class HomeVisibilityState {

    @Volatile
    var isHomeVisible = true
        private set

    @Volatile
    var isHomeBehindDesktop = false
        private set

    @Volatile var navbarInsetPosition = 0

    private var listeners = CopyOnWriteArrayList<VisibilityChangeListener>()

    @AnyThread fun addListener(l: VisibilityChangeListener) = listeners.add(l)

    @AnyThread fun removeListener(l: VisibilityChangeListener) = listeners.remove(l)

    fun init(transitions: IShellTransitions?) {
        try {
            transitions?.setHomeTransitionListener(
                object : Stub() {
                    override fun onHomeVisibilityChanged(
                        isVisible: Boolean,
                        keyguardGoingAwayOrWaking: Boolean,
                        behindDesktop: Boolean,
                    ) {
                        Utilities.postAsyncCallback(Executors.MAIN_EXECUTOR.handler) {
                            val homeVisibilityChanged = isHomeVisible != isVisible
                            isHomeVisible = isVisible
                            isHomeBehindDesktop = behindDesktop
                            listeners.forEach {
                                if (
                                    homeVisibilityChanged || it.handleDesktopVisibilityOnlyChanges()
                                ) {
                                    it.onHomeVisibilityChanged(
                                        isVisible,
                                        keyguardGoingAwayOrWaking,
                                        behindDesktop,
                                    )
                                }
                            }
                        }
                    }

                    override fun onDisplayInsetsChanged(insetsState: InsetsState) {
                        val displayFrame = insetsState.displayFrame
                        val bottomInset =
                            insetsState
                                .calculateInsets(
                                    displayFrame,
                                    displayFrame,
                                    WindowInsets.Type.navigationBars(),
                                    false,
                                )
                                .bottom
                        navbarInsetPosition = displayFrame.bottom - bottomInset
                    }
                },
                UserHandle.myUserId(),
            )
        } catch (e: RemoteException) {
            Log.w(TAG, "Failed call setHomeTransitionListener", e)
        }
    }

    interface VisibilityChangeListener {
        fun handleDesktopVisibilityOnlyChanges(): Boolean

        fun onHomeVisibilityChanged(
            isVisible: Boolean,
            keyguardGoingAwayOrWaking: Boolean,
            behindDesktop: Boolean,
        )
    }

    override fun toString() = "{HomeVisibilityState isHomeVisible=$isHomeVisible}"

    companion object {

        private const val TAG = "HomeVisibilityState"
    }
}
