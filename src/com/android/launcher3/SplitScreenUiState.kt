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

/** Expose Split Screen UI State to Taskbar. */
class SplitScreenUiState {

    @Volatile var isSplitSelectActive = false

    // Split select state
    @Volatile private var _initialTask = SplitSelectTask()
    @Volatile private var _secondTask = SplitSelectTask()

    fun setSplitSelectInitialTask(initialTask: SplitSelectTask) {
        _initialTask = initialTask
        updateIsSplitSelectActiveRef()
    }

    fun setSplitSelectSecondTask(secondTask: SplitSelectTask) {
        _secondTask = secondTask
        updateIsSplitSelectActiveRef()
    }

    private fun updateIsSplitSelectActiveRef() {
        isSplitSelectActive = _initialTask.isIntentSet && !_secondTask.isIntentSet
    }
}
