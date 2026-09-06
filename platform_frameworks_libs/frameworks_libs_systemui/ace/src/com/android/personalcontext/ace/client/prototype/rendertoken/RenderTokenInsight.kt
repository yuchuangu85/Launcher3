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
package com.android.personalcontext.ace.client.prototype.rendertoken

import android.os.Bundle
import android.service.personalcontext.RenderToken
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ContextInsight
import androidx.core.os.BundleCompat
import com.android.personalcontext.ace.client.prototype.PrototypeInsight
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.RenderTokenInsightId

/**
 * A [PrototypeInsight] that wraps a [RenderToken].
 *
 * @param renderToken the render token to wrap.
 */
data class RenderTokenInsight(val renderToken: RenderToken) :
    PrototypeInsight(RenderTokenInsightId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putParcelable(KEY_RENDER_TOKEN, renderToken)
    }

    companion object : Creator {
        private const val KEY_RENDER_TOKEN = "render_token"

        override fun create(
            bundle: Bundle,
            insights: List<ContextInsight?>,
            originHints: Set<PublishedContextHint>,
        ): PrototypeInsight =
            RenderTokenInsight(
                BundleCompat.getParcelable(bundle, KEY_RENDER_TOKEN, RenderToken::class.java)
                    ?: throw IllegalArgumentException("RenderToken is null")
            )
    }
}
