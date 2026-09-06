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
package com.android.quickstep

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.util.Log
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER
import com.android.launcher3.QuickstepTransitionManager.SPLIT_DIVIDER_ANIM_DURATION
import com.android.launcher3.Utilities
import com.android.wm.shell.shared.split.SplitScreenConstants.DEFAULT_OFFSCREEN_DIM
import com.android.wm.shell.shared.TransitionUtil.TYPE_SPLIT_SCREEN_DIM_LAYER

/**
 * Stateless utils class to help create simple fade [Animator]s for split divider and split
 * dim layer surfaces.
 *
 * I know it may be tempting, but do not try to combine the two different surface animators any more
 * than they already are. If you do, we will find you and we will end you...r animations.
 * This class is a result of them being too coupled to begin with.
 *
 * Divider anim alpha values are bound between 0f and 1f.
 * Dim layer alpha values are bound between [DEFAULT_OFFSCREEN_DIM] and 1f.
 *
 * Only animates targets of [TYPE_DOCK_DIVIDER] and [TYPE_SPLIT_SCREEN_DIM_LAYER]
 */
class SplitRecentsAnimUtils(nonApps: Array<RemoteAnimationTarget>) {

    private val mDividerSurfaces = mutableListOf<SurfaceControl>()
    private val mDimLayerSurfaces = mutableListOf<SurfaceControl>()

    init {
        for (target in nonApps) {
            val leash = target.leash
            if (leash != null && leash.isValid) {
                if (target.windowType == TYPE_DOCK_DIVIDER) {
                    mDividerSurfaces.add(leash)
                } else if (target.windowType == TYPE_SPLIT_SCREEN_DIM_LAYER) {
                    mDimLayerSurfaces.add(leash)
                }
            }
        }
    }

    /**
     * Creates a fade [ValueAnimator] for a list of [SurfaceControl]s or immediately applies the
     * end alpha if [immediate] is true.
     *
     * @param surfaces The list of [SurfaceControl]s to animate.
     * @param startAlpha The starting alpha value for the animation.
     * @param endAlpha The ending alpha value for the animation.
     * @param immediate If true, the animation's end state is applied instantly; otherwise, a
     *                  [ValueAnimator] is returned.
     * @return A [ValueAnimator] if [immediate] is false, or null if [immediate] is true or
     *         the surfaces list is empty.
     */
    private fun createFadeAnimator(surfaces: List<SurfaceControl>,
                                   startAlpha: Float, endAlpha: Float,
                                   immediate: Boolean): ValueAnimator? {
        if (surfaces.isEmpty()) {
            return null
        }

        if (immediate) {
            val t = SurfaceControl.Transaction()
            for (leash in surfaces) {
                if (!leash.isValid) {
                    continue
                }
                if (endAlpha == 0f) {
                    t.hide(leash)
                } else {
                    if (startAlpha == 0f) {
                        t.setLayer(leash, Integer.MAX_VALUE)
                        t.show(leash)
                    }
                    t.setAlpha(leash, endAlpha)
                }
            }
            t.apply()
            t.close()
            return null
        }

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = SPLIT_DIVIDER_ANIM_DURATION.toLong()
        val t = SurfaceControl.Transaction()
        animator.addUpdateListener { valueAnimator ->
            val progress = valueAnimator.animatedFraction
            for (leash in surfaces) {
                if (leash.isValid) {
                    t.setAlpha(leash, Utilities.mapRange(progress, startAlpha, endAlpha))
                }
            }
            t.apply()
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                if (startAlpha == 0f) {
                    for (leash in surfaces) {
                        t.setLayer(leash, Integer.MAX_VALUE)
                        t.setAlpha(leash, 0f)
                        t.show(leash)
                    }
                    t.apply()
                }
            }

            override fun onAnimationEnd(animation: Animator) {
                if (endAlpha == 0f) {
                    for (leash in surfaces) {
                        if (leash.isValid) {
                            t.hide(leash)
                        }
                    }
                    t.apply()
                }
                t.close()
            }
        })
        return animator
    }

    fun fadeOutDivider(immediate: Boolean = true): ValueAnimator? {
        return createFadeAnimator(mDividerSurfaces, 1f, 0f, immediate)
    }

    fun fadeInDivider(immediate: Boolean = true): ValueAnimator? {
        return createFadeAnimator(mDividerSurfaces, 0f, 1f, immediate)
    }

    fun fadeOutDimLayer(immediate: Boolean = true): ValueAnimator? {
        return createFadeAnimator(mDimLayerSurfaces, DEFAULT_OFFSCREEN_DIM, 0f, immediate)
    }

    fun fadeInDimLayer(immediate: Boolean = true): ValueAnimator? {
        return createFadeAnimator(mDimLayerSurfaces, 0f, DEFAULT_OFFSCREEN_DIM, immediate)
    }
}