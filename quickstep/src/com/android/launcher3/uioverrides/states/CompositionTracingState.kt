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

package com.android.launcher3.uioverrides.states

import android.os.Trace
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.COMPOSITION_TRACING_PREF_KEY
import com.android.launcher3.LauncherPrefs.Companion.ENABLE_COMPOSITION_TRACING

class CompositionTracingState(private val launcherPrefs: LauncherPrefs) :
    LauncherPrefChangeListener {

    private var isCompositionTracingEnabled = false

    fun initialize() {
        isCompositionTracingEnabled = getLatestState()

        launcherPrefs.addListener(this, ENABLE_COMPOSITION_TRACING)
    }

    fun destroy() {
        launcherPrefs.removeListener(this, ENABLE_COMPOSITION_TRACING)
    }

    @OptIn(InternalComposeTracingApi::class)
    override fun onPrefChanged(key: String?) {
        if (key == COMPOSITION_TRACING_PREF_KEY) {
            val wasCompositionTracingEnabled = isCompositionTracingEnabled
            isCompositionTracingEnabled = getLatestState()

            when {
                isCompositionTracingEnabled && !wasCompositionTracingEnabled ->
                    Composer.setTracer(
                        object : CompositionTracer {
                            override fun traceEventStart(
                                key: Int,
                                dirty1: Int,
                                dirty2: Int,
                                info: String,
                            ) {
                                Trace.traceBegin(Trace.TRACE_TAG_APP, info)
                            }

                            override fun traceEventEnd() = Trace.traceEnd(Trace.TRACE_TAG_APP)

                            override fun isTraceInProgress(): Boolean = Trace.isEnabled()
                        }
                    )

                !isCompositionTracingEnabled && wasCompositionTracingEnabled ->
                    Composer.setTracer(null)
            }
        }
    }

    private fun getLatestState() = launcherPrefs.get(ENABLE_COMPOSITION_TRACING)
}
