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

package com.android.app.tracing.coroutines

import android.os.Trace
import com.android.app.tracing.TraceUtils
import com.android.app.tracing.TrackGroupUtils.trackGroup
import java.io.Closeable
import java.util.concurrent.ThreadLocalRandom
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Wrapper to trace to a single perfetto track elegantly, without duplicating trace tag and track
 * name all the times.
 *
 * The intended use is the following:
 * ```kotlin
 * class SomeClass {
 *    privat val t = TrackTracer("SomeTrackName")
 *
 *    ...
 *    t.instant { "some instant" }
 *    t.traceAsync("Some slice name") { ... }
 * }
 * ```
 */
@OptIn(ExperimentalContracts::class)
public class TrackTracer(
    trackName: String,
    public val traceTag: Long = Trace.TRACE_TAG_APP,
    public val trackGroup: String? = null,
) {
    public val trackName: String =
        if (trackGroup != null) trackGroup(trackGroup, trackName) else trackName

    /** See [Trace.instantForTrack]. */
    public inline fun instant(s: () -> String) {
        if (!Trace.isEnabled()) return
        Trace.instantForTrack(traceTag, trackName, s())
    }

    /** See [Trace.asyncTraceForTrackBegin]. */
    public inline fun <T> traceAsync(crossinline sliceName: () -> String, block: () -> T): T {
        contract {
            callsInPlace(sliceName, InvocationKind.AT_MOST_ONCE)
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        return TraceUtils.traceAsync(traceTag, trackName, sliceName, block)
    }

    /** See [Trace.asyncTraceForTrackBegin]. */
    public inline fun <T> traceAsync(sliceName: String, block: () -> T): T {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return TraceUtils.traceAsync(traceTag, trackName, sliceName, block)
    }

    /** See [Trace.asyncTraceForTrackBegin]. */
    public fun traceAsyncBegin(sliceName: String): Closeable {
        val cookie = ThreadLocalRandom.current().nextInt()
        Trace.asyncTraceForTrackBegin(traceTag, trackName, sliceName, cookie)
        return Closeable { Trace.asyncTraceForTrackEnd(traceTag, trackName, cookie) }
    }

    /** Traces [block] both sync and async. */
    public fun <T> traceSyncAndAsync(sliceName: () -> String, block: () -> T): T {
        contract {
            callsInPlace(sliceName, InvocationKind.AT_MOST_ONCE)
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        return if (Trace.isEnabled()) {
            val name = sliceName()
            TraceUtils.trace(name) { traceAsync(name, block) }
        } else {
            block()
        }
    }

    public companion object {
        /**
         * Creates an instant event for a track called [trackName] inside [groupName]. See
         * [trackGroup] for details on how the rendering in groups works.
         */
        @JvmStatic
        public fun instantForGroup(groupName: String, trackName: String, i: Int) {
            Trace.traceCounter(Trace.TRACE_TAG_APP, trackGroup(groupName, trackName), i)
        }

        /**
         * Creates an instant event for a track called [trackName] inside [groupName]. See
         * [trackGroup] for details on how the rendering in groups works.
         */
        @JvmStatic
        public fun instantForGroup(groupName: String, trackName: String, event: () -> String) {
            if (!Trace.isEnabled()) return
            Trace.instantForTrack(Trace.TRACE_TAG_APP, trackGroup(groupName, trackName), event())
        }

        /** Creates an instant event for [groupName] grgorp, see [instantForGroup]. */
        @JvmStatic
        public inline fun instantForGroup(groupName: String, trackName: () -> String, i: Int) {
            if (!Trace.isEnabled()) return
            instantForGroup(groupName, trackName(), i)
        }

        /**
         * Creates an instant event, see [instantForGroup], converting [i] to an int by multiplying
         * it by 100.
         */
        @JvmStatic
        public fun instantForGroup(groupName: String, trackName: String, i: Float) {
            instantForGroup(groupName, trackName, (i * 100).toInt())
        }
    }
}
