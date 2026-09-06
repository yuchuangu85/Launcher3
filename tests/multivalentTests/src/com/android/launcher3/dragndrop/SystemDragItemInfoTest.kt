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

import androidx.test.filters.SmallTest
import com.android.launcher3.util.LauncherMultivalentJUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Tests for {@link SystemDragItemInfo}. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class SystemDragItemInfoTest {

    @Test
    fun testClone() {
        val original = SystemDragItemInfo()
        original.payload = mock<SystemDragItemInfo.UriListPayload>()
        original.title = "Title"

        // Verify [original] can be cloned.
        val clone = original.clone() as SystemDragItemInfo
        assertEquals(original.itemType, clone.itemType)
        assertEquals(original.payload, clone.payload)
        assertEquals(original.title, clone.title)

        // Verify modifying [original] payload does not modify [clone] payload.
        original.payload = mock<SystemDragItemInfo.UriListPayload>()
        assertNotEquals(original.payload, clone.payload)

        // Verify modifying [clone] payload does not modify [original] payload.
        clone.payload = mock<SystemDragItemInfo.UriListPayload>()
        assertNotEquals(clone.payload, original.payload)
    }

    @Test
    fun testCopyFrom() {
        val original = SystemDragItemInfo()
        original.payload = mock<SystemDragItemInfo.UriListPayload>()
        original.title = "Title"

        // Verify [original] can be copied.
        val copy = SystemDragItemInfo().apply { copyFrom(original) }
        assertEquals(original.itemType, copy.itemType)
        assertEquals(original.payload, copy.payload)
        assertEquals(original.title, copy.title)

        // Verify modifying [original] payload does not modify [copy] payload.
        original.payload = mock<SystemDragItemInfo.UriListPayload>()
        assertNotEquals(original.payload, copy.payload)

        // Verify modifying [copy] payload does not modify [original] payload.
        copy.payload = mock<SystemDragItemInfo.UriListPayload>()
        assertNotEquals(copy.payload, original.payload)
    }

    @Test
    fun testEmptyPayloadIsNotAcceptable() {
        assertFalse(SystemDragItemInfo.EmptyPayload.isAcceptable())
    }

    @Test
    fun testMakeShallowCopy() {
        val original = SystemDragItemInfo()
        original.payload = mock<SystemDragItemInfo.UriListPayload>()
        original.title = "Title"

        // Verify [original] can be shallow copied.
        val shallowCopy = original.makeShallowCopy() as SystemDragItemInfo
        assertEquals(original.itemType, shallowCopy.itemType)
        assertEquals(original.payload, shallowCopy.payload)
        assertEquals(original.title, shallowCopy.title)

        // Verify modifying [original] payload modifies [shallowCopy] payload.
        original.payload = mock<SystemDragItemInfo.UriListPayload>()
        assertEquals(original.payload, shallowCopy.payload)

        // Verify modifying [shallowCopy] payload modifies [original] payload.
        shallowCopy.payload = mock<SystemDragItemInfo.UriListPayload>()
        assertEquals(shallowCopy.payload, original.payload)
    }

    @Test
    fun testUriListPayloadIsAcceptableWhenFullyPopulated() {
        val payload = SystemDragItemInfo.UriListPayload(mock(), listOf(mock()))
        assertTrue(payload.isAcceptable())
    }

    @Test
    fun testUriListPayloadIsNotAcceptableWhenPermissionsAreNull() {
        val payload = SystemDragItemInfo.UriListPayload(null, listOf(mock()))
        assertFalse(payload.isAcceptable())
    }

    @Test
    fun testUriListPayloadIsNotAcceptableWhenUriListIsEmpty() {
        val payload = SystemDragItemInfo.UriListPayload(mock(), emptyList())
        assertFalse(payload.isAcceptable())
    }

    @Test
    fun testUriListPayloadIsNotAcceptableDropWhenUriListIsNull() {
        val payload = SystemDragItemInfo.UriListPayload(mock(), null)
        assertFalse(payload.isAcceptable())
    }
}
