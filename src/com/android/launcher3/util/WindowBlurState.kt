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

import android.content.Context
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent

/** Class to keep track of cross window blur enabled state */
object WindowBlurState {

    /** Name for dagger injection */
    const val WINDOW_BLUR_STATE = "window_blur_state"

    @JvmStatic
    fun getInstance(ctx: Context): ListenableRef<Boolean> = ctx.appComponent.windowBlurState
}
