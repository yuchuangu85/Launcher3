/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.dragndrop

import android.view.DragEvent
import androidx.test.filters.SmallTest
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/** Tests for {@link DragDriver}. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class DragDriverTest {

    @Test
    fun testDragEnterExitSupportWithInternalDriver() {
        testDragEnterExitSupport(useSystemDriver = false)
    }

    @Test
    fun testDragEnterExitSupportWithSystemDriver() {
        testDragEnterExitSupport(useSystemDriver = true)
    }

    private fun testDragEnterExitSupport(useSystemDriver: Boolean) {
        val controller = mock<DragController>()
        val options = DragOptions().apply { if (useSystemDriver) simulatedDndStartPoint = mock() }
        val driver = DragDriver.create(controller, options, mock())
        val useInternalDriver = !useSystemDriver
        val event = mock<DragEvent>()
        var eventX = 1.0f
        var eventY = 2.0f

        // Step 0: Initial state.
        assertThat(driver is DragDriver.InternalDragDriver).isEqualTo(useInternalDriver)
        assertThat(driver is DragDriver.SystemDragDriver).isEqualTo(useSystemDriver)
        assertThat(driver.isDragWithinWindow).isEqualTo(useInternalDriver)

        // Step 1: Drag start.
        whenever(event.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(event.x).thenReturn(++eventX)
        whenever(event.y).thenReturn(++eventY)
        driver.onDragEvent(event)
        assertThat(driver.isDragWithinWindow).isEqualTo(useInternalDriver)
        verifyNoMoreInteractions(controller)
        clearInvocations(controller)

        // Step 2: Drag enter.
        whenever(event.action).thenReturn(DragEvent.ACTION_DRAG_ENTERED)
        whenever(event.x).thenReturn(++eventX)
        whenever(event.y).thenReturn(++eventY)
        driver.onDragEvent(event)
        assertThat(driver.isDragWithinWindow).isEqualTo(true)
        if (useSystemDriver) {
            verify(controller).onDriverDragEnterWindow()
        }
        verifyNoMoreInteractions(controller)
        clearInvocations(controller)

        // Step 3: Drag move.
        whenever(event.action).thenReturn(DragEvent.ACTION_DRAG_LOCATION)
        whenever(event.x).thenReturn(++eventX)
        whenever(event.y).thenReturn(++eventY)
        driver.onDragEvent(event)
        assertThat(driver.isDragWithinWindow).isEqualTo(true)
        if (useSystemDriver) {
            verify(controller).onDriverDragMove(eventX, eventY)
        }
        verifyNoMoreInteractions(controller)
        clearInvocations(controller)

        // Step 4: Drag exit.
        whenever(event.action).thenReturn(DragEvent.ACTION_DRAG_EXITED)
        whenever(event.x).thenReturn(++eventX)
        whenever(event.y).thenReturn(++eventY)
        driver.onDragEvent(event)
        assertThat(driver.isDragWithinWindow).isEqualTo(useInternalDriver)
        if (useSystemDriver) {
            verify(controller).onDriverDragExitWindow()
        }
        verifyNoMoreInteractions(controller)
        clearInvocations(controller)

        // Step 5: Drop.
        whenever(event.action).thenReturn(DragEvent.ACTION_DROP)
        whenever(event.x).thenReturn(++eventX)
        whenever(event.y).thenReturn(++eventY)
        driver.onDragEvent(event)
        assertThat(driver.isDragWithinWindow).isEqualTo(useInternalDriver)
        if (useSystemDriver) {
            verify(controller).onDriverDragMove(eventX, eventY)
            verify(controller).onDriverDragEnd(eventX, eventY)
        }
        verifyNoMoreInteractions(controller)
        clearInvocations(controller)

        // Step 6: Drag end.
        whenever(event.action).thenReturn(DragEvent.ACTION_DRAG_ENDED)
        whenever(event.x).thenReturn(++eventX)
        whenever(event.y).thenReturn(++eventY)
        driver.onDragEvent(event)
        assertThat(driver.isDragWithinWindow).isEqualTo(useInternalDriver)
        if (useSystemDriver) {
            verify(controller).onDriverDragCancel()
        }
        verifyNoMoreInteractions(controller)
        clearInvocations(controller)
    }
}
