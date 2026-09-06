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
import android.companion.datatransfer.continuity.RemoteTask
import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppsListData
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [TaskbarHandoffController]. */
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_TASK_CONTINUITY)
class TaskbarHandoffControllerTest {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(context)
    private val controller by taskbarUnitTestRule.delegate { it.taskbarHandoffController }

    @Before
    fun setUp() {
        taskbarUnitTestRule.activityContext.appComponent.testableModelState.appsRepo.dispatchChange(
            AppsListData(
                arrayOf(
                    AppInfo(
                        ComponentName("com.example.first", "Activity"),
                        "Second App",
                        taskbarUnitTestRule.activityContext.user,
                        Intent(),
                    )
                ),
                0,
            )
        )
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun onRemoteTasksChanged_updatesSuggestions() {
        runOnTaskbarUiThreadSync {
            HandoffSuggestionRepository.get(taskbarUnitTestRule.activityContext)
                .listener
                .onRemoteTasksChanged(
                    listOf(
                        createRemoteTask(
                            associationId = 1,
                            taskId = 1,
                            packageName = "com.example.first",
                            lastUsedTimestampMillis = 100,
                            isTaskInForeground = true,
                        )
                    )
                )
        }
        assertThat(controller.suggestions).hasSize(1)
    }

    private companion object {
        fun createRemoteTask(
            associationId: Int,
            taskId: Int,
            packageName: String,
            lastUsedTimestampMillis: Long,
            isTaskInForeground: Boolean,
        ): RemoteTask {
            return RemoteTask.Builder(associationId, taskId)
                .setPackageName(packageName)
                .setLastUsedTimestampMillis(lastUsedTimestampMillis)
                .setTaskInForeground(isTaskInForeground)
                .build()
        }
    }
}
