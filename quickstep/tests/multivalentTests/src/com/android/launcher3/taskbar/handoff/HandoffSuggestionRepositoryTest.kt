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

import android.companion.datatransfer.continuity.RemoteTask
import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppsListData
import com.android.launcher3.util.SandboxApplication
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [HandoffSuggestionRepository]. */
@RunWith(AndroidJUnit4::class)
@EnableFlags(android.companion.Flags.FLAG_TASK_CONTINUITY)
class HandoffSuggestionRepositoryTest {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = SandboxApplication()

    private lateinit var repository: HandoffSuggestionRepository

    @Before
    fun setUp() {
        context.appComponent.testableModelState.appsRepo.dispatchChange(
            AppsListData(
                arrayOf(
                    AppInfo(
                        ComponentName(ALTERNATIVE_INSTALLED_APP_PACKAGE_NAME, "Activity"),
                        "First App",
                        context.user,
                        Intent(),
                    ),
                    AppInfo(
                        ComponentName(INSTALLED_APP_PACKAGE_NAME, "Activity"),
                        "Second App",
                        context.user,
                        Intent(),
                    ),
                ),
                0,
            )
        )

        repository = HandoffSuggestionRepository.get(context)
    }

    @Test
    fun onRemoteTasksChanged_noTasks_setsSuggestionToNull() {
        repository.listener.onRemoteTasksChanged(emptyList())
        assertThat(repository.suggestion.value).isNull()
    }

    @Test
    fun onRemoteTaskChanged_noTaskInForeground_setsSuggestionToNull() {
        val remoteTask =
            createRemoteTask(
                associationId = 1,
                taskId = 1,
                packageName = INSTALLED_APP_PACKAGE_NAME,
                lastUsedTimestampMillis = 100,
                isTaskInForeground = false,
            )
        repository.listener.onRemoteTasksChanged(listOf(remoteTask))
        assertThat(repository.suggestion.value).isNull()
    }

    @Test
    fun onRemoteTaskChanged_taskDoesNotHavePackageName_setsSuggestionToNull() {
        val remoteTask =
            createRemoteTask(
                associationId = 1,
                taskId = 1,
                packageName = null,
                lastUsedTimestampMillis = 100,
                isTaskInForeground = true,
            )
        repository.listener.onRemoteTasksChanged(listOf(remoteTask))
        assertThat(repository.suggestion.value).isNull()
    }

    @Test
    fun onRemoteTaskChanged_takesMostRecentTaskInForeground() {
        val remoteTask1 =
            createRemoteTask(
                associationId = 1,
                taskId = 1,
                packageName = INSTALLED_APP_PACKAGE_NAME,
                lastUsedTimestampMillis = 100,
                isTaskInForeground = true,
            )
        val remoteTask2 =
            createRemoteTask(
                associationId = 2,
                taskId = 2,
                packageName = ALTERNATIVE_INSTALLED_APP_PACKAGE_NAME,
                lastUsedTimestampMillis = 200,
                isTaskInForeground = true,
            )
        repository.listener.onRemoteTasksChanged(listOf(remoteTask1, remoteTask2))
        assertSuggestionMatchesTask(repository.suggestion.value, remoteTask2)
        assertSuggestionHasBadge(repository.suggestion.value)
    }

    @Test
    fun onRemoteTaskChanged_appNotInLauncher_setsSuggestionToNull() {
        val remoteTask =
            createRemoteTask(
                associationId = 1,
                taskId = 1,
                packageName = "com.example.third",
                lastUsedTimestampMillis = 100,
                isTaskInForeground = true,
            )
        repository.listener.onRemoteTasksChanged(listOf(remoteTask))
        assertThat(repository.suggestion.value).isNull()
    }

    private companion object {
        const val ALTERNATIVE_INSTALLED_APP_PACKAGE_NAME = "com.example.first"
        const val INSTALLED_APP_PACKAGE_NAME = "com.example.second"

        fun createRemoteTask(
            associationId: Int,
            taskId: Int,
            packageName: String?,
            lastUsedTimestampMillis: Long,
            isTaskInForeground: Boolean,
        ): RemoteTask {
            return RemoteTask.Builder(associationId, taskId)
                .setPackageName(packageName)
                .setLastUsedTimestampMillis(lastUsedTimestampMillis)
                .setTaskInForeground(isTaskInForeground)
                .build()
        }

        fun assertSuggestionHasBadge(suggestion: HandoffSuggestion?) {
            assertThat(suggestion).isNotNull()
            assertThat(suggestion?.itemInfoWithIcon).isNotNull()
            assertThat(suggestion?.itemInfoWithIcon?.bitmap?.badgeInfo).isNotNull()
        }

        fun assertSuggestionMatchesTask(
            suggestion: HandoffSuggestion?,
            expectedRemoteTask: RemoteTask,
        ) {
            assertThat(suggestion).isNotNull()
            assertThat(suggestion?.associationId)
                .isEqualTo(expectedRemoteTask.companionDeviceAssociationId)
            assertThat(suggestion?.taskId).isEqualTo(expectedRemoteTask.taskId)
            assertThat(suggestion?.itemInfoWithIcon).isNotNull()
            assertThat(suggestion?.itemInfoWithIcon?.targetPackage)
                .isEqualTo(expectedRemoteTask.packageName)
        }
    }
}
