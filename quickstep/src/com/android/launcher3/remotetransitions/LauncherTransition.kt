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

import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.SurfaceControl
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransitionStub
import android.window.TransitionInfo
import androidx.annotation.BinderThread
import com.android.launcher3.Utilities.postAsyncCallback
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.concurrent.annotations.Ui
import com.android.systemui.animation.DefaultTransitionHelper
import com.android.systemui.animation.DefaultTransitionHelper.Companion.invoke
import com.android.systemui.animation.RemoteTransitionDelegate
import com.android.systemui.animation.RemoteTransitionHelper
import com.android.systemui.shared.recents.utilities.Utilities.postAtFrontOfQueueAsynchronously
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import javax.inject.Inject

class LauncherTransition
@Inject
constructor(
    private val handler: Handler,
    delegate: RemoteTransitionDelegate<AnimationResult>?,
    private val startAtFrontOfQueue: Boolean,
    private val transitionHelper: RemoteTransitionHelper = DefaultTransitionHelper(),
    @LightweightBackground(LightweightBackgroundPriority.UI) private val bgExecutor: Executor,
    @Ui private val mainExecutor: Executor,
) : RemoteTransitionStub() {

    private val TAG = "LauncherTransition"

    private var animationResult: AnimationResult? = null

    private val _delegate = WeakReference(delegate)

    private val _defaultDelegate =
        object : RemoteTransitionDelegate<AnimationResult> {
            override fun startAnimation(
                transition: IBinder?,
                info: TransitionInfo?,
                transaction: SurfaceControl.Transaction?,
                finishedCallback: AnimationResult?,
            ) {
                finishedCallback?.setAnimation(null, null)
            }
        }

    private val delegate
        get() = _delegate.get() ?: _defaultDelegate

    override fun startAnimation(
        transition: IBinder?,
        info: TransitionInfo?,
        transaction: SurfaceControl.Transaction?,
        finishCallback: IRemoteTransitionFinishedCallback?,
    ) {
        if (info == null || transaction == null || transition == null) {
            Log.e(
                TAG,
                "data required for transition missing, aborting transition! " +
                    "info=$info, transaction=$transaction, transition=$transition",
            )
            return
        }

        transitionHelper.setUpAnimation(
            token = transition,
            info = info,
            transaction = transaction,
            finishCallback = finishCallback,
        )

        val finishRunnable = Runnable {
            finishExistingAnimation()
            animationResult =
                AnimationResult(
                    syncFinishRunnable = { animationResult = null },
                    asyncFinishRunnable = {
                        transitionHelper.cleanUpAnimation(transition, transaction)
                    },
                    bgExecutor = bgExecutor,
                    mainExecutor = mainExecutor,
                )
            delegate.startAnimation(transition, info, transaction, animationResult)
        }

        if (startAtFrontOfQueue) {
            postAtFrontOfQueueAsynchronously(handler, finishRunnable)
        } else {
            postAsyncCallback(handler, finishRunnable)
        }
    }

    override fun mergeAnimation(
        transition: IBinder?,
        info: TransitionInfo?,
        transaction: SurfaceControl.Transaction?,
        mergeTarget: IBinder?,
        finishCallback: IRemoteTransitionFinishedCallback?,
    ) {
        postAsyncCallback(handler) {
            finishExistingAnimation()
            delegate.onTransitionConsumed(transition, true)
        }
        transitionHelper.mergeAnimation(info, transaction, mergeTarget)
    }

    override fun onTransitionConsumed(transition: IBinder?, aborted: Boolean) {
        transition?.let { transitionHelper.onTransitionConsumed(transition) }
        postAsyncCallback(handler) {
            finishExistingAnimation()
            delegate.onTransitionConsumed(transition, aborted)
        }
    }

    @BinderThread
    private fun finishExistingAnimation() {
        // finish existing animation
        animationResult?.let {
            it.finish()
            animationResult = null
        }
    }
}
