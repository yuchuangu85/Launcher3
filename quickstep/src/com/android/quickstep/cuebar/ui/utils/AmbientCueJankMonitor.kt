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

package com.android.quickstep.cuebar.ui.utils

import android.view.View
import com.android.internal.jank.Cuj.CujType
import com.android.internal.jank.InteractionJankMonitor

class AmbientCueJankMonitor(
    private val interactionJankMonitor: InteractionJankMonitor,
    private val composeView: View,
) {
    fun onAnimationStateChange(@CujType cujType: Int, animationState: AmbientCueAnimationState) {
        when (animationState) {
            AmbientCueAnimationState.BEGIN -> interactionJankMonitor.begin(composeView, cujType)
            AmbientCueAnimationState.END -> interactionJankMonitor.end(cujType)
            AmbientCueAnimationState.CANCEL -> interactionJankMonitor.cancel(cujType)
        }
    }
}

enum class AmbientCueAnimationState {
    BEGIN,
    END,
    CANCEL,
}
