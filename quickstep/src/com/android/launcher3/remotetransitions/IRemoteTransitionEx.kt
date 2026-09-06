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

package com.android.launcher3.remotetransitions

import android.os.IBinder
import android.view.SurfaceControl.Transaction
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import android.window.WindowAnimationState
import java.lang.ref.WeakReference

object IRemoteTransitionEx {

    @JvmStatic
    fun IRemoteTransition.toWeakRef(): IRemoteTransition =
        WeakIRemoteTransition(WeakReference(this))

    class WeakIRemoteTransition(private val delegate: WeakReference<IRemoteTransition>) :
        IRemoteTransition.Stub() {

        override fun mergeAnimation(
            token: IBinder?,
            info: TransitionInfo?,
            t: Transaction?,
            mergeTarget: IBinder?,
            finishCallback: IRemoteTransitionFinishedCallback?,
        ) {
            delegate.get()?.mergeAnimation(token, info, t, mergeTarget, finishCallback)
                ?: finishCallback?.onTransitionFinished(null, null)
        }

        override fun onTransitionConsumed(token: IBinder?, aborted: Boolean) {
            delegate.get()?.onTransitionConsumed(token, aborted)
        }

        override fun startAnimation(
            token: IBinder?,
            info: TransitionInfo?,
            t: Transaction?,
            finishCallback: IRemoteTransitionFinishedCallback?,
        ) {
            delegate.get()?.startAnimation(token, info, t, finishCallback)
                ?: finishCallback?.onTransitionFinished(null, null)
        }

        override fun takeOverAnimation(
            token: IBinder?,
            info: TransitionInfo?,
            t: Transaction?,
            finishCallback: IRemoteTransitionFinishedCallback?,
            states: Array<out WindowAnimationState>?,
        ) {
            delegate.get()?.takeOverAnimation(token, info, t, finishCallback, states)
                ?: finishCallback?.onTransitionFinished(null, null)
        }
    }
}
