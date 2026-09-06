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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.content.Context
import android.util.Log
import android.view.SurfaceControl
import android.window.IRemoteTransitionFinishedCallback
import android.window.WindowContainerTransaction
import androidx.annotation.UiThread
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.util.window.RefreshRateTracker.Companion.getSingleFrameMs
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.math.min

/**
 * Extends [IRemoteTransitionFinishedCallback] and used by [RemoteTransitionDelegate]
 * implementations to run actual animations and its lifecycle callbacks.
 */
class AnimationResult
@Inject
constructor(
    private val syncFinishRunnable: Runnable,
    private val asyncFinishRunnable: Runnable,
    @LightweightBackground(LightweightBackgroundPriority.UI) private val bgExecutor: Executor,
    @Ui private val mainExecutor: Executor,
) : IRemoteTransitionFinishedCallback.Stub() {

    private var onCompleteCallback: Runnable? = null
    private var finished: Boolean = false
    private var initialized: Boolean = false

    @UiThread
    fun finish() {
        if (!finished) {
            syncFinishRunnable.run()
            bgExecutor.execute {
                asyncFinishRunnable.run()
                onCompleteCallback?.let { mainExecutor.execute(it) }
            }
            finished = true
        }
    }

    @UiThread
    fun setAnimation(animation: AnimatorSet?, context: Context?) {
        setAnimation(
            animation = animation,
            context = context,
            onCompleteCallback = null,
            skipFirstFrame = false,
        )
    }

    /**
     * Sets the animation to play for this app launch
     *
     * @param skipFirstFrame Iff true, we skip the first frame of the animation. We set to false
     *   when skipping first frame causes jank.
     */
    @UiThread
    fun setAnimation(
        animation: AnimatorSet?,
        context: Context?,
        onCompleteCallback: Runnable?,
        skipFirstFrame: Boolean,
    ) {
        if (initialized) {
            throw IllegalStateException("Animation already initialized")
        }
        initialized = true
        this.onCompleteCallback = onCompleteCallback

        if (animation == null) {
            finish()
        } else if (finished) {
            // Animation callback was already finished, skip the animation.
            animation.start()
            animation.end()
            onCompleteCallback?.run()
        } else {
            // Start the animation
            animation.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        finish()
                    }
                }
            )
            if (skipFirstFrame) {
                // Because t=0 has the app icon in its original spot, we can skip the
                // first frame and have the same movement one frame earlier.
                Log.d("b/311077782", "AnimationResult.setAnimation")
                animation.currentPlayTime =
                    min(context?.getSingleFrameMs()?.toLong() ?: 0L, animation.totalDuration)
            }
            animation.start()
        }
    }

    /**
     * When used as a simple [IRemoteTransitionFinishedCallback], this method is used to run the
     * animation finished runnable.
     */
    override fun onTransitionFinished(
        wct: WindowContainerTransaction?,
        transaction: SurfaceControl.Transaction?,
    ) {
        asyncFinishRunnable.run()
    }
}
