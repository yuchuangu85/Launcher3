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

package com.android.launcher3

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class FakeWorkspaceSelectionManagerTest {

    @Test
    fun startBoxSelection_invokesIsAppendingConsumer() {
        var capturedIsAppending: Boolean? = null
        val selectionManager =
            FakeWorkspaceSelectionManager(
                isAppendingConsumer = { capturedIsAppending = it },
                capturedBoxBoundsConsumer = {},
            )

        selectionManager.startBoxSelection(true)
        assertThat(capturedIsAppending).isTrue()

        selectionManager.startBoxSelection(false)
        assertThat(capturedIsAppending).isFalse()
    }

    @Test
    fun updateBoxSelection_invokesCapturedBoxBoundsConsumer() {
        var capturedBoxBounds: Rect? = null
        val selectionManager =
            FakeWorkspaceSelectionManager(
                isAppendingConsumer = {},
                capturedBoxBoundsConsumer = { capturedBoxBounds = it },
            )
        val rect = Rect(1, 2, 3, 4)

        selectionManager.updateBoxSelection(rect)
        assertThat(capturedBoxBounds).isEqualTo(rect)
    }
}
