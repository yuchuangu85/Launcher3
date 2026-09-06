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

package com.android.quickstep.cuebar.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import com.android.internal.jank.Cuj
import com.android.quickstep.cuebar.ui.utils.AmbientCueAnimationState

@Composable
fun AmbientCueJankMonitorComposable(
    visibleTargetState: Boolean,
    enterProgress: Float,
    expanded: Boolean,
    expansionAlpha: Float,
    showAnimationInProgress: MutableState<Boolean>,
    hideAnimationInProgress: MutableState<Boolean>,
    expandAnimationInProgress: MutableState<Boolean>,
    collapseAnimationInProgress: MutableState<Boolean>,
    onAnimationStateChange: (Int, AmbientCueAnimationState) -> Unit,
) {
    LaunchedEffect(visibleTargetState, enterProgress) {
        if (visibleTargetState) {
            when (enterProgress) {
                0f -> {
                    showAnimationInProgress.value = true
                    onAnimationStateChange(Cuj.CUJ_AMBIENT_CUE_SHOW, AmbientCueAnimationState.BEGIN)
                }
                1f -> {
                    showAnimationInProgress.value = false
                    onAnimationStateChange(Cuj.CUJ_AMBIENT_CUE_SHOW, AmbientCueAnimationState.END)
                }
            }
        } else {
            when (enterProgress) {
                0f -> {
                    hideAnimationInProgress.value = false
                    onAnimationStateChange(Cuj.CUJ_AMBIENT_CUE_HIDE, AmbientCueAnimationState.END)
                }
                1f -> {
                    hideAnimationInProgress.value = true
                    onAnimationStateChange(Cuj.CUJ_AMBIENT_CUE_HIDE, AmbientCueAnimationState.BEGIN)
                }
            }
        }
    }
    LaunchedEffect(expanded, expansionAlpha) {
        if (expanded) {
            when (expansionAlpha) {
                0f -> {
                    if (expandAnimationInProgress.value) {
                        expandAnimationInProgress.value = false
                        onAnimationStateChange(
                            Cuj.CUJ_AMBIENT_CUE_EXPAND,
                            AmbientCueAnimationState.END,
                        )
                    }
                }
                1f -> {
                    expandAnimationInProgress.value = true
                    onAnimationStateChange(
                        Cuj.CUJ_AMBIENT_CUE_EXPAND,
                        AmbientCueAnimationState.BEGIN,
                    )
                }
            }
        } else {
            when (expansionAlpha) {
                0f -> {
                    collapseAnimationInProgress.value = true
                    onAnimationStateChange(
                        Cuj.CUJ_AMBIENT_CUE_COLLAPSE,
                        AmbientCueAnimationState.BEGIN,
                    )
                }
                1f -> {
                    if (collapseAnimationInProgress.value) {
                        collapseAnimationInProgress.value = false
                        onAnimationStateChange(
                            Cuj.CUJ_AMBIENT_CUE_COLLAPSE,
                            AmbientCueAnimationState.END,
                        )
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (showAnimationInProgress.value) {
                onAnimationStateChange(Cuj.CUJ_AMBIENT_CUE_SHOW, AmbientCueAnimationState.CANCEL)
            }
            if (hideAnimationInProgress.value) {
                onAnimationStateChange(Cuj.CUJ_AMBIENT_CUE_HIDE, AmbientCueAnimationState.CANCEL)
            }
            if (expandAnimationInProgress.value) {
                onAnimationStateChange(Cuj.CUJ_AMBIENT_CUE_EXPAND, AmbientCueAnimationState.CANCEL)
            }
            if (collapseAnimationInProgress.value) {
                onAnimationStateChange(
                    Cuj.CUJ_AMBIENT_CUE_COLLAPSE,
                    AmbientCueAnimationState.CANCEL,
                )
            }
        }
    }
}
