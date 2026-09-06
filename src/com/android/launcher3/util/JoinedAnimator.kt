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
import android.animation.Animator.AnimatorListener

/** Similar to AnimationSet but only supports start(), end() and addListener() API. */
class JoinedAnimator(vararg animators: ThreadedAnimator) : ThreadedAnimator {

    private inline fun BooleanArray.updateAndCheck(
        index: Int,
        callback: AnimatorListener.() -> Unit,
    ) {
        this[index] = true
        if (all { it }) {
            listeners.forEach(callback)
        }
    }

    private val started: BooleanArray = BooleanArray(animators.size) { false }
    private val ended: BooleanArray = BooleanArray(animators.size) { false }
    private val children: List<ThreadedAnimator> = animators.toList()

    val listeners = mutableListOf<AnimatorListener>()

    init {
        children.forEachIndexed { index, child ->
            child.addListener(
                object : AnimatorListener {
                    override fun onAnimationStart(animator: Animator) {
                        started.updateAndCheck(index) { onAnimationStart(animator) }
                    }

                    override fun onAnimationEnd(animator: Animator) {
                        ended.updateAndCheck(index) { onAnimationEnd(animator) }
                    }

                    override fun onAnimationCancel(animator: Animator) {
                        ended.updateAndCheck(index) { onAnimationCancel(animator) }
                    }

                    override fun onAnimationRepeat(animator: Animator) {}
                }
            )
        }
    }

    override fun start() {
        children.forEach { it.start() }
    }

    override fun end() {
        children.forEach { it.end() }
    }

    override fun addListener(listener: AnimatorListener) {
        listeners.add(listener)
    }
}
