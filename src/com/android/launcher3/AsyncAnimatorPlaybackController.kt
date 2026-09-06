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

package com.android.launcher3

import com.android.launcher3.anim.AnimatorPlaybackController
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.annotation.concurrent.ThreadSafe

/**
 * Wraps a [CompletableFuture] to create [AnimatorPlaybackController] and invoke APIs on target
 * executor.
 */
@ThreadSafe
class AsyncAnimatorPlaybackController(
    private val executor: Executor,
    private val animatorPlaybackControllerProvider: () -> AnimatorPlaybackController?,
) {

    private val completableFuture: CompletableFuture<AnimatorPlaybackController?> =
        CompletableFuture.supplyAsync({ animatorPlaybackControllerProvider() }, executor)

    fun dispatchOnStart() {
        completableFuture.thenApplyAsync(
            { animatorPlaybackController -> animatorPlaybackController?.dispatchOnStart() },
            executor,
        )
    }

    fun dispatchOnEnd() {
        completableFuture.thenApplyAsync(
            { animatorPlaybackController -> animatorPlaybackController?.dispatchOnEnd() },
            executor,
        )
    }

    fun setPlayFraction(fraction: Float) {
        completableFuture.thenApplyAsync(
            { animatorPlaybackController -> animatorPlaybackController?.setPlayFraction(fraction) },
            executor,
        )
    }
}
