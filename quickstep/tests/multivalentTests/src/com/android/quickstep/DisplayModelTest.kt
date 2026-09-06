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

package com.android.quickstep

import android.content.Context
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat
import java.io.PrintWriter
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class DisplayModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val systemDecorationChangeObserver =
        SystemDecorationChangeObserver.INSTANCE.get(context)
    private val displayRepositoryCompat = mock<DisplaysWithDecorationsRepositoryCompat>()
    private val dispatcher = StandardTestDispatcher()
    private val displayId = Display.DEFAULT_DISPLAY
    private val invalidDisplayId = -1

    class TestableResource : DisplayModel.DisplayResource {
        var isCleanupCalled = false

        override fun cleanup() {
            isCleanupCalled = true
        }

        override fun dump(prefix: String, writer: PrintWriter) {
            // No-Op
        }
    }

    private val testableDisplayModel =
        DisplayModel(context, systemDecorationChangeObserver, displayRepositoryCompat, dispatcher) {
            TestableResource()
        }

    @Test
    fun testCreate() {
        testableDisplayModel.storeDisplayResource(displayId)
        val resource = testableDisplayModel.getDisplayResource(displayId)
        assertNotNull(resource)
    }

    @Test
    fun testCleanAndDelete() {
        testableDisplayModel.storeDisplayResource(displayId)
        val resource = testableDisplayModel.getDisplayResource(displayId)!!
        assertNotNull(resource)
        testableDisplayModel.deleteDisplayResource(displayId)
        assert(resource.isCleanupCalled)
        assertNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_setDisplayResourceAfterClose_noop() {
        testableDisplayModel.storeDisplayResource(displayId)
        dispatcher.dispatch(EmptyCoroutineContext) {
            testableDisplayModel.onDisplayAddSystemDecorations(displayId)
        }
        testableDisplayModel.close()
        dispatcher.scheduler.runCurrent()

        assertNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_removeDisplayResourceAfterClose_noop() {
        testableDisplayModel.storeDisplayResource(displayId)
        dispatcher.dispatch(EmptyCoroutineContext) {
            testableDisplayModel.onDisplayRemoveSystemDecorations(displayId)
        }
        testableDisplayModel.close()
        dispatcher.scheduler.runCurrent()

        assertNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_removeDisplayAfterClose_noop() {
        testableDisplayModel.storeDisplayResource(displayId)
        dispatcher.dispatch(EmptyCoroutineContext) {
            testableDisplayModel.onDisplayRemoved(displayId)
        }
        testableDisplayModel.close()
        dispatcher.scheduler.runCurrent()

        assertNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_getDisplayResource_doesNotCrash() {
        assertNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_storeDisplayResource_doesNotCrash() {
        testableDisplayModel.storeDisplayResource(displayId)
        assertNotNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_onDisplayAddSystemDecorations_doesNotCrash() {
        testableDisplayModel.storeDisplayResource(displayId)
        testableDisplayModel.onDisplayAddSystemDecorations(displayId)
        assertNotNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_deleteDisplayResource_doesNotCrash() {
        testableDisplayModel.storeDisplayResource(displayId)
        testableDisplayModel.deleteDisplayResource(displayId)
        assertNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_onDisplayRemoved_doesNotCrash() {
        testableDisplayModel.storeDisplayResource(displayId)
        testableDisplayModel.onDisplayRemoved(displayId)
        assertNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_onDisplayRemoveSystemDecorations_doesNotCrash() {
        testableDisplayModel.storeDisplayResource(displayId)
        testableDisplayModel.onDisplayRemoveSystemDecorations(displayId)
        assertNull(testableDisplayModel.getDisplayResource(displayId))
    }

    @Test
    fun testDebug_storeInvalidDisplayResource_doesNotCrash() {
        testableDisplayModel.storeDisplayResource(invalidDisplayId)
        assertNull(testableDisplayModel.getDisplayResource(invalidDisplayId))
    }
}
