/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.test.tracing.coroutines.util

import android.os.Trace
import android.util.Log
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@OptIn(ExperimentalStdlibApi::class)
@Suppress("unused_parameter")
@Implements(Trace::class)
object ShadowTrace {

    @Implementation
    @JvmStatic
    fun isEnabled(): Boolean {
        return isTagEnabled(Trace.TRACE_TAG_APP)
    }

    @Implementation
    @JvmStatic
    fun isTagEnabled(traceTag: Long): Boolean {
        return FakeTraceState.isTracingEnabled && traceTag == Trace.TRACE_TAG_APP
    }

    @Implementation
    @JvmStatic
    fun traceBegin(traceTag: Long, methodName: String) {
        if (traceTag == Trace.TRACE_TAG_APP && isTagEnabled(traceTag)) {
            debug("traceBegin: $methodName")
            FakeTraceState.begin(methodName)
        } else {
            debug("IGNORE traceBegin: $methodName")
        }
    }

    @Implementation
    @JvmStatic
    fun traceEnd(traceTag: Long) {
        if (traceTag == Trace.TRACE_TAG_APP && isTagEnabled(traceTag)) {
            debug("traceEnd")
            FakeTraceState.end()
        } else {
            debug("IGNORE traceEnd")
        }
    }

    @Implementation
    @JvmStatic
    fun beginSection(sectionName: String) {
        debug("IGNORE beginSection")
    }

    @Implementation
    @JvmStatic
    fun endSection() {
        debug("IGNORE endSection()")
    }

    @Implementation
    @JvmStatic
    fun asyncTraceBegin(traceTag: Long, methodName: String, cookie: Int) {
        debug("IGNORE asyncTraceBegin")
    }

    @Implementation
    @JvmStatic
    fun asyncTraceEnd(traceTag: Long, methodName: String, cookie: Int) {
        debug("IGNORE asyncTraceEnd")
    }

    @Implementation
    @JvmStatic
    fun asyncTraceForTrackBegin(
        traceTag: Long,
        trackName: String,
        methodName: String,
        cookie: Int,
    ) {
        debug("IGNORE asyncTraceForTrackBegin")
    }

    @Implementation
    @JvmStatic
    fun asyncTraceForTrackEnd(traceTag: Long, trackName: String, methodName: String, cookie: Int) {
        debug("IGNORE asyncTraceForTrackEnd")
    }

    @Implementation
    @JvmStatic
    fun instant(traceTag: Long, eventName: String) {
        debug("IGNORE instant")
    }

    @Implementation
    @JvmStatic
    fun instantForTrack(traceTag: Long, trackName: String, eventName: String) {
        debug("IGNORE instantForTrack")
    }
}

private const val DEBUG = false

/** Log a message with a tag indicating the current thread ID */
private fun debug(message: String, e: Throwable? = null) {
    if (DEBUG) {
        if (e != null) {
            Log.d("ShadowTrace", "Thread #${Thread.currentThread().threadId()}: $message", e)
        } else {
            Log.d("ShadowTrace", "Thread #${Thread.currentThread().threadId()}: $message")
        }
    }
}
