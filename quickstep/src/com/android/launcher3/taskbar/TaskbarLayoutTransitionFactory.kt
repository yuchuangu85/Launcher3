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

package com.android.launcher3.taskbar

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.LayoutTransition.APPEARING
import android.animation.LayoutTransition.CHANGE_APPEARING
import android.animation.LayoutTransition.CHANGE_DISAPPEARING
import android.animation.LayoutTransition.DISAPPEARING
import android.animation.LayoutTransition.TransitionListener
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.util.FloatProperty
import android.util.Property
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.android.app.animation.Interpolators
import com.android.app.animation.Interpolators.EMPHASIZED
import com.android.app.animation.Interpolators.LINEAR
import com.android.internal.jank.Cuj
import com.android.launcher3.LauncherAnimUtils.getScaleProperty
import com.android.launcher3.Reorderable
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.customization.containers.TaskbarPinnedAppIconContainer
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_PINNING_ANIM
import com.android.systemui.shared.system.InteractionJankMonitorWrapper

/**
 * Creates a [LayoutTransition] for [TaskbarView] or its icon containers.
 *
 * Both Taskbar and [ViewGroup] containers within it need to support a common set of transitions,
 * but each need their own [LayoutTransition] instance. [TaskbarView] has extra change transition
 * behavior to propagate [INDEX_TASKBAR_PINNING_ANIM] `translateX` to icons in containers.
 *
 * [transitionListeners] will be added to any [LayoutTransition] instance.
 */
class TaskbarLayoutTransitionFactory(private vararg val transitionListeners: TransitionListener) {
    private val appearingAnimator =
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(null, "alpha", 0f, 1f).apply {
                    interpolator = APPEARING_ALPHA_INTERPOLATOR
                },
                ObjectAnimator.ofFloat(null, getScaleProperty(), 0f, 1f).apply {
                    interpolator = EMPHASIZED
                },
            )
        }

    private val disappearingAnimator =
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(null, "alpha", 1f, 0f).apply {
                    interpolator = DISAPPEARING_ALPHA_INTERPOLATOR
                },
                ObjectAnimator.ofFloat(null, getScaleProperty(), 1f, 0f).apply {
                    interpolator = EMPHASIZED
                },
            )
        }

    private val leftRightAnimator =
        ObjectAnimator.ofPropertyValuesHolder(
            null as View?,
            PropertyValuesHolder.ofInt("left", 0, 1),
            PropertyValuesHolder.ofInt("right", 0, 1),
        )

    private val translateXPinningAnimator =
        ObjectAnimator.ofFloat(
            null,
            object : FloatProperty<View>("translateXPinning") {
                override fun setValue(view: View, value: Float) {
                    view.pinningTranslationX.value = value
                }

                override fun get(view: View): Float = view.pinningTranslationX.value
            },
            0f,
            1f,
        )

    /**
     * Propagates [INDEX_TASKBAR_PINNING_ANIM] `translateX` to icons in [TaskbarView] containers.
     *
     * [CHANGE_APPEARING] and [CHANGE_DISAPPEARING] on the root view occur due to a child being
     * added or removed. If the child is an icon (e.g. app icon, divider, etc.), the total number of
     * icons across Taskbar and its containers may have changed. In this case, `translateX` is
     * recalculated per icon. This animator ensures the resulting `translateX` changes within
     * containers, where the transition did not happen, smoothly animate.
     */
    private val containerIconsTranslateXPinningAnimator =
        ObjectAnimator.ofObject(
            null as View?, // Needed to call correct overload; a few do not take in a target.
            object :
                Property<View, FloatArray>(
                    FloatArray::class.java,
                    "containerIconsTranslateXPinning",
                ) {
                override fun get(view: View): FloatArray {
                    return if (view is TaskbarPinnedAppIconContainer) {
                        view.children.map { it.pinningTranslationX.value }.toList().toFloatArray()
                    } else {
                        FloatArray(0)
                    }
                }

                override fun set(view: View, values: FloatArray) {
                    if (view is TaskbarPinnedAppIconContainer) {
                        for (i in values.indices) {
                            view.getChildAt(i)?.pinningTranslationX?.value = values[i]
                        }
                    }
                }
            },
            { fraction, start, end ->
                if (start.size != end.size) {
                    FloatArray(0)
                } else {
                    FloatArray(start.size) { Utilities.mapRange(fraction, start[it], end[it]) }
                }
            },
            FloatArray(0),
        )

    /** Creates a [LayoutTransition] for the root [TaskbarView]. */
    fun createForTaskbarView(): LayoutTransition = create(isRootView = true)

    /** Creates a [LayoutTransition] for an icon container in Taskbar. */
    fun createForTaskbarContainer(): LayoutTransition = create(isRootView = false)

    private val setUpViewOnAppearingStart =
        object : TransitionListener {
            override fun startTransition(
                transition: LayoutTransition,
                container: ViewGroup,
                view: View,
                type: Int,
            ) {
                if (type == APPEARING) {
                    view.alpha = 0f
                    view.scaleX = 0f
                    view.scaleY = 0f
                }
            }

            override fun endTransition(
                transition: LayoutTransition,
                container: ViewGroup,
                view: View,
                type: Int,
            ) = Unit
        }

    private val iconAppearDisappearJankCujListener =
        object : TransitionListener {
            override fun startTransition(
                transition: LayoutTransition,
                container: ViewGroup,
                view: View,
                type: Int,
            ) {
                if (type == APPEARING || type == DISAPPEARING) {
                    InteractionJankMonitorWrapper.begin(container, Cuj.CUJ_TASKBAR_ICON_APPEAR)
                }
            }

            override fun endTransition(
                transition: LayoutTransition,
                container: ViewGroup,
                view: View,
                type: Int,
            ) {
                if (type == APPEARING || type == DISAPPEARING) {
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_TASKBAR_ICON_APPEAR)
                }
            }
        }

    private fun create(isRootView: Boolean): LayoutTransition {
        return LayoutTransition().apply {
            setDuration(TRANSITION_DEFAULT_DURATION)
            addTransitionListener(setUpViewOnAppearingStart)
            addTransitionListener(iconAppearDisappearJankCujListener)
            for (l in this@TaskbarLayoutTransitionFactory.transitionListeners) {
                addTransitionListener(l)
            }

            setAnimator(APPEARING, appearingAnimator)
            setStartDelay(APPEARING, TRANSITION_DELAY)

            setAnimator(DISAPPEARING, disappearingAnimator)

            // Change transitions.
            val changeAnimator =
                AnimatorSet().apply {
                    playTogether(leftRightAnimator, translateXPinningAnimator)
                    if (isRootView) play(containerIconsTranslateXPinningAnimator)
                }

            // Change appearing.
            setAnimator(CHANGE_APPEARING, changeAnimator)
            setInterpolator(CHANGE_APPEARING, EMPHASIZED)

            // Change disappearing.
            setAnimator(CHANGE_DISAPPEARING, changeAnimator)
            setInterpolator(CHANGE_DISAPPEARING, EMPHASIZED)
            setStartDelay(CHANGE_DISAPPEARING, TRANSITION_DELAY)
        }
    }

    companion object {
        private const val TRANSITION_DELAY = 50L
        const val TRANSITION_DEFAULT_DURATION = 500L
        private const val TRANSITION_FADE_IN_DURATION = 167L
        private const val TRANSITION_FADE_OUT_DURATION = 83L

        private val APPEARING_ALPHA_INTERPOLATOR =
            Interpolators.clampToProgress(
                LINEAR,
                0f,
                TRANSITION_FADE_IN_DURATION.toFloat() / TRANSITION_DEFAULT_DURATION,
            )
        private val DISAPPEARING_ALPHA_INTERPOLATOR =
            Interpolators.clampToProgress(
                LINEAR,
                TRANSITION_DELAY.toFloat() / TRANSITION_DEFAULT_DURATION,
                (TRANSITION_DELAY + TRANSITION_FADE_OUT_DURATION).toFloat() /
                    TRANSITION_DEFAULT_DURATION,
            )

        private val View.pinningTranslationX: MultiPropertyFactory<*>.MultiProperty
            get() {
                return (this as Reorderable)
                    .translateDelegate
                    .getTranslationX(INDEX_TASKBAR_PINNING_ANIM)
            }
    }
}
