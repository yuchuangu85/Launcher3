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
import android.view.View
import java.util.function.Consumer

/**
 * Fake implementation of [WorkspaceSelectionManager] for testing purposes.
 *
 * @param isAppendingConsumer A consumer to capture the `isAppending` value passed to
 *   [startBoxSelection].
 * @param capturedBoxBoundsConsumer A consumer to capture the [boxBounds] value passed to
 *   [updateBoxSelection].
 */
class FakeWorkspaceSelectionManager(
    private val isAppendingConsumer: Consumer<Boolean>,
    private val capturedBoxBoundsConsumer: Consumer<Rect>,
) : WorkspaceSelectionManager {
    override fun startBoxSelection(isAppending: Boolean) {
        isAppendingConsumer.accept(isAppending)
    }

    override fun updateBoxSelection(boxBounds: Rect) {
        capturedBoxBoundsConsumer.accept(boxBounds)
    }

    override fun endBoxSelection() {
        // No-op
    }

    override fun updateItemSelection(
        view: View,
        itemSelectionType: WorkspaceSelectionManager.ItemSelectionType,
    ) {
        // No-op
    }
}
