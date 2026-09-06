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

import android.service.personalcontext.RenderToken
import android.service.personalcontext.insight.InsightFilter
import android.service.personalcontext.insight.PublishedContextInsight
import android.service.personalcontext.renderer.InsightRendererService
import android.util.Log
import com.android.quickstep.cuebar.data.InsightListener
import java.lang.ref.WeakReference

class CueBarInsightRendererService : InsightRendererService() {

    override fun onInitializeFilter(): InsightFilter {
        Log.d(TAG, "Service registered")

        // Filter for all insight types.
        return InsightFilter.Builder().build()
    }

    override fun onRender(publishedInsight: PublishedContextInsight, renderToken: RenderToken) {
        val listener = listenerRef?.get()
        if (listener != null) {
            // Forward the data to the custom listener (the Repository)
            listener.onInsightReceived(publishedInsight, renderToken)
        } else {
            Log.w(TAG, "Insights received but no listener registered.")
        }
    }

    companion object {
        private const val TAG = "CueBarInsightRenderer"
        private var listenerRef: WeakReference<InsightListener>? = null

        @JvmStatic
        fun registerListener(listener: InsightListener) {
            listenerRef = WeakReference(listener)
        }

        @JvmStatic
        fun unregisterListener(listener: InsightListener) {
            if (listenerRef?.get() === listener) {
                listenerRef = null
            }
        }
    }
}
