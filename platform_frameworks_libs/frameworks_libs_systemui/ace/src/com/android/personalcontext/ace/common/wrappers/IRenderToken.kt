/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("FlaggedApi", "NewApi")

package com.android.personalcontext.ace.common.wrappers

import android.service.personalcontext.RenderToken
import androidx.annotation.VisibleForTesting

/** Wrapper interface for [RenderToken]. */
sealed interface IRenderToken {

    /**
     * Returns the unwrapped [RenderToken]. May return null if originally wrapped from a unit test,
     * where constructing an instance of [RenderToken] is not possible.
     */
    fun unwrap(): RenderToken?
}

/** Creates an [IRenderToken] from a [RenderToken]. */
fun RenderToken.wrap(): IRenderToken = RenderTokenWrapper(this)

private class RenderTokenWrapper(private val original: RenderToken) : IRenderToken {
    override fun unwrap() = original
}

@VisibleForTesting
class RenderTokenForTesting : IRenderToken {
    override fun unwrap() = null
}
