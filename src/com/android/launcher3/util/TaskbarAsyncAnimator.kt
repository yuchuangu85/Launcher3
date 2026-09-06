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

package com.android.launcher3.util

import android.animation.Animator
import android.animation.ObjectAnimator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Wraps a [CompletableFuture] to obtain a Animator from [executor]. It supports taskbar animation
 * during swipe up gesture and currently only supports [Animator.start], [Animator.end] and
 * [Animator.addListener].
 */
class TaskbarAsyncAnimator(
    private val executor: Executor,
    private val callbackExecutor: Executor,
    animatorSupplier: () -> Animator?,
) : ThreadedAnimator {

    private val animatorFuture: CompletableFuture<Animator?> =
        CompletableFuture.supplyAsync(animatorSupplier, executor)

    override fun start() {
        animatorFuture.thenApplyAsync({ animator -> animator?.start() }, executor)
    }

    override fun end() {
        animatorFuture.thenApplyAsync({ animator -> animator?.end() }, executor)
    }

    override fun addListener(listener: Animator.AnimatorListener) {
        val listenerProxy =
            object : Animator.AnimatorListener {
                override fun onAnimationCancel(animation: Animator) =
                    callbackExecutor.execute { listener.onAnimationCancel(animation) }

                override fun onAnimationEnd(animation: Animator) =
                    callbackExecutor.execute { listener.onAnimationEnd(animation) }

                override fun onAnimationRepeat(animation: Animator) =
                    callbackExecutor.execute { listener.onAnimationRepeat(animation) }

                override fun onAnimationStart(animation: Animator) =
                    callbackExecutor.execute { listener.onAnimationStart(animation) }
            }
        animatorFuture.thenApplyAsync(
            { animator ->
                if (animator != null) {
                    animator.addListener(listenerProxy)
                } else {
                    // Trigger onAnimationEnd to allow caller to clean up listener.
                    listenerProxy.onAnimationEnd(ObjectAnimator())
                }
            },
            executor,
        )
    }
}
