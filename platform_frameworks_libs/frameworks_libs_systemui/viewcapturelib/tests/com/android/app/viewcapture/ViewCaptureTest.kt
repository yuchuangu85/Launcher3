/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.app.viewcapture

import android.content.Intent
import android.media.permission.SafeCloseable
import android.testing.AndroidTestingRunner
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.app.viewcapture.ViewCapture.ViewPropertyRef
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
class ViewCaptureTest {

    private val memorySize = 100
    private val initPoolSize = 15
    private val capturedData = mutableListOf<ViewPropertyRef>()

    private val viewCapture by lazy {
        object : ViewCapture(memorySize, initPoolSize, MAIN_EXECUTOR) {
            override fun onCapturedViewPropertiesBg(
                elapsedRealtimeNanos: Long,
                windowName: String,
                startFlattenedViewTree: ViewPropertyRef,
            ) {
                capturedData.add(startFlattenedViewTree)
            }
        }
    }

    private val activityIntent =
        Intent(InstrumentationRegistry.getInstrumentation().context, TestActivity::class.java)

    @get:Rule val activityScenarioRule = ActivityScenarioRule<TestActivity>(activityIntent)

    @Test
    fun testWindowListenerDumpsOneFrameAfterInvalidate() {
        activityScenarioRule.scenario.onActivity { activity ->
            capturedData.clear()
            val closeable = startViewCaptureAndInvalidateNTimes(1, activity)
            assertEquals(1, capturedData.size)
            verifyTestActivityViewHierarchy(capturedData.last())
            closeable.close()
        }
    }

    @Test
    fun testWindowListenerDumpsCorrectlyAfterRecyclingStarted() {
        activityScenarioRule.scenario.onActivity { activity ->
            capturedData.clear()
            val closeable = startViewCaptureAndInvalidateNTimes(memorySize + 5, activity)

            // since ViewCapture MEMORY_SIZE is [viewCaptureMemorySize], only
            // [viewCaptureMemorySize] frames are stored in the ring buffer.
            assertEquals(memorySize + 5, capturedData.size)
            verifyTestActivityViewHierarchy(capturedData.last())
            closeable.close()
        }
    }

    private fun startViewCaptureAndInvalidateNTimes(n: Int, activity: TestActivity): SafeCloseable {
        val rootView: View = activity.requireViewById(android.R.id.content)
        val closeable: SafeCloseable = viewCapture.startCapture(rootView, "rootViewId")
        dispatchOnDraw(rootView, times = n)
        return closeable
    }

    private fun dispatchOnDraw(view: View, times: Int) {
        if (times > 0) {
            view.viewTreeObserver.dispatchOnDraw()
            dispatchOnDraw(view, times - 1)
        }
    }

    private fun verifyTestActivityViewHierarchy(start: ViewPropertyRef) {
        var ref: ViewPropertyRef? = start
        assertEquals(1, ref?.childCount)

        ref = ref?.next
        assertEquals(TestActivity.TEXT_VIEW_COUNT, ref?.childCount)
        assertEquals(LinearLayout::class.java, ref?.clazz)

        for (i in 0 until TestActivity.TEXT_VIEW_COUNT) {
            ref = ref?.next
            assertEquals(0, ref?.childCount)
            assertEquals(TextView::class.java, ref?.clazz)
        }
    }
}
