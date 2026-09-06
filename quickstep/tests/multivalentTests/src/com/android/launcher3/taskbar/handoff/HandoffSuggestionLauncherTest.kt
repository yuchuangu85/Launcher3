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

package com.android.launcher3.taskbar.handoff

import android.companion.Flags
import android.companion.datatransfer.continuity.TaskContinuityManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.LauncherMultivalentJUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(LauncherMultivalentJUnit::class)
class HandoffSuggestionLauncherTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val mockTaskContinuityManager = mock<TaskContinuityManager>()

    private val launcher =
        HandoffSuggestionLauncher(mockTaskContinuityManager, context.mainExecutor)

    @Test
    @EnableFlags(Flags.FLAG_TASK_CONTINUITY)
    fun launch_launchesSuggestion() {
        val suggestion = createSuggestion()
        launcher.launch(suggestion)
        verify(mockTaskContinuityManager)
            .requestHandoff(
                suggestion.associationId,
                suggestion.taskId,
                context.mainExecutor,
                launcher,
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_TASK_CONTINUITY)
    fun launch_flagDisabled_doesNotLaunchSuggestion() {
        val suggestion = createSuggestion()
        launcher.launch(suggestion)
        verify(mockTaskContinuityManager, never())
            .requestHandoff(
                suggestion.associationId,
                suggestion.taskId,
                context.mainExecutor,
                launcher,
            )
    }

    private fun createSuggestion(): HandoffSuggestion {
        return HandoffSuggestion(1, 1, AppInfo())
    }
}
