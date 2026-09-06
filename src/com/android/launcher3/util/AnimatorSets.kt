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
package com.android.launcher3.util

import android.animation.AnimatorSet

/**
 * Sets the duration of the [AnimatorSet] and all its child animations.
 *
 * This is a workaround for a framework bug where [AnimatorSet.setDuration] can cause an
 * [IllegalStateException] if one of the child animations is a RenderNodeAnimator (e.g. from
 * [android.view.ViewAnimationUtils.createCircularReveal]) because its duration cannot be changed
 * mid-animation.
 */
fun AnimatorSet.safeSetDuration(duration: Long): AnimatorSet {
    childAnimations.forEach {
        if (it is AnimatorSet) {
            it.safeSetDuration(duration)
        } else {
            it.duration = duration
        }
    }
    return this
}
