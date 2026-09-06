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

package com.android.quickstep.recents.domain.usecase

import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.quickstep.recents.domain.model.TaskLayoutConfig
import com.android.quickstep.recents.domain.model.TaskLayoutState.DesktopTaskLayoutState
import com.android.quickstep.recents.domain.model.TaskPosition.Hidden
import com.android.quickstep.recents.domain.model.TaskPosition.Rendered
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class GetDesktopTaskLayoutStateUseCaseTest {

    private val organizeDesktopTasksUseCase = mock<OrganizeDesktopTasksUseCase>()
    private val getDesktopTaskFullscreenPositionUseCase =
        mock<GetDesktopTaskFullscreenPositionUseCase>()
    private val desktopModeCompatPolicy = mock<DesktopModeCompatPolicy>()

    private lateinit var systemUnderTest: GetDesktopTaskLayoutStateUseCase

    @Before
    fun setUp() {
        systemUnderTest =
            GetDesktopTaskLayoutStateUseCase(
                organizeDesktopTasksUseCase,
                getDesktopTaskFullscreenPositionUseCase,
                desktopModeCompatPolicy,
            )
    }

    @Test
    fun invoke_emptyTaskPositions() {
        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any())).thenReturn(emptyList())
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(emptyList())

        val result =
            systemUnderTest(
                tasks = emptyList(),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = emptyMap(),
            )

        assertThat(result).isEmpty()

        verify(getDesktopTaskFullscreenPositionUseCase).invoke(emptyList())
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(emptyList()), any(), eq(emptyList()), isNull())
    }

    @Test
    fun invoke_taskWithNullBounds() {
        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any())).thenReturn(emptyList())
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(emptyList())

        val task = createTask(NEW_TASK_ID_1, appBounds = null)
        val result =
            systemUnderTest(
                tasks = listOf(task),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = emptyMap(),
            )

        assertThat(result).isEmpty()

        verify(getDesktopTaskFullscreenPositionUseCase).invoke(emptyList())
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(emptyList()), any(), eq(emptyList()), isNull())
    }

    @Test
    fun invoke_singleRenderedAndObscuredTaskPosition() {
        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any()))
            .thenReturn(listOf(Hidden(NEW_TASK_ID_1)))
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))

        val task = createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)
        val result =
            systemUnderTest(
                tasks = listOf(task),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = emptyMap(),
            )

        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                DesktopTaskLayoutState(
                    fullscreenPosition = Hidden(NEW_TASK_ID_1),
                    overviewPosition = ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                    oldOverviewPosition = null,
                ),
            )

        verify(getDesktopTaskFullscreenPositionUseCase).invoke(listOf(task))

        val allCurrentOriginalTaskBounds =
            listOf(Rendered(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1))
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), isNull())
    }

    @Test
    fun invoke_oneRenderedTaskOneHiddenTask() {
        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any()))
            .thenReturn(
                listOf(
                    Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    Rendered(NEW_TASK_ID_2, NEW_TASK_BOUNDS_2),
                )
            )
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(
                listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA, ORGANIZED_HIDDEN_TASK_BOUNDS_DATA)
            )

        val task1 = createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)
        val task2 = createTask(NEW_TASK_ID_2, NEW_TASK_BOUNDS_2)

        val result =
            systemUnderTest(
                tasks = listOf(task1, task2),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = emptyMap(),
            )

        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                DesktopTaskLayoutState(
                    fullscreenPosition = Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    overviewPosition = ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                    oldOverviewPosition = null,
                ),
                NEW_TASK_ID_2,
                DesktopTaskLayoutState(
                    fullscreenPosition = Rendered(NEW_TASK_ID_2, NEW_TASK_BOUNDS_2),
                    overviewPosition = ORGANIZED_HIDDEN_TASK_BOUNDS_DATA,
                    oldOverviewPosition = null,
                ),
            )

        verify(getDesktopTaskFullscreenPositionUseCase).invoke(listOf(task1, task2))

        val allCurrentOriginalTaskBounds =
            listOf(
                Rendered(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1),
                Rendered(taskId = NEW_TASK_ID_2, bounds = NEW_TASK_BOUNDS_2),
            )
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), isNull())
    }

    @Test
    fun invoke_oldOrganizedVisibilityData() {
        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any()))
            .thenReturn(listOf(Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)))
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))

        val taskLayoutStateMap =
            mapOf(
                OLD_RENDERED_TASK_ID to
                    Rendered(OLD_RENDERED_TASK_ID, bounds = OLD_RENDERED_TASK_BOUNDS),
                OLD_HIDDEN_TASK_ID to Hidden(OLD_HIDDEN_TASK_ID),
            )

        val task1 = createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)
        val result =
            systemUnderTest(
                tasks = listOf(task1),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = taskLayoutStateMap,
            )

        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                DesktopTaskLayoutState(
                    fullscreenPosition = Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    overviewPosition = ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                    oldOverviewPosition = null,
                ),
            )

        verify(getDesktopTaskFullscreenPositionUseCase).invoke(listOf(task1))

        val allCurrentOriginalTaskBounds =
            listOf(Rendered(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1))
        val taskPositionsHint =
            listOf(
                Rendered(taskId = OLD_RENDERED_TASK_ID, bounds = OLD_RENDERED_TASK_BOUNDS),
                Hidden(taskId = OLD_HIDDEN_TASK_ID),
            )
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(taskPositionsHint), isNull())
    }

    @Test
    fun invoke_validDismissTaskId() {
        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any()))
            .thenReturn(listOf(Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)))
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), any()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))

        val task1 = createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)

        val result =
            systemUnderTest(
                tasks = listOf(task1),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = emptyMap(),
                dismissedTaskId = NEW_TASK_ID_2,
            )

        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                DesktopTaskLayoutState(
                    fullscreenPosition = Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    overviewPosition = ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                    oldOverviewPosition = null,
                ),
            )

        verify(getDesktopTaskFullscreenPositionUseCase).invoke(listOf(task1))

        val allCurrentOriginalTaskBounds =
            listOf(Rendered(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1))
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), eq(NEW_TASK_ID_2))
    }

    @Test
    fun invoke_minimizedTask_taskPositionMinimized() {
        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any()))
            .thenReturn(listOf(Hidden(NEW_TASK_ID_1)))
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(emptyList())

        val minimizedTask = createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1, isMinimized = true)
        val result =
            systemUnderTest(
                tasks = listOf(minimizedTask),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = emptyMap(),
            )

        verify(getDesktopTaskFullscreenPositionUseCase).invoke(listOf(minimizedTask))

        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                DesktopTaskLayoutState(
                    fullscreenPosition = Hidden(NEW_TASK_ID_1),
                    overviewPosition = Hidden(NEW_TASK_ID_1),
                ),
            )
    }

    @Test
    fun invoke_transparentActivityTransparentActivityStackTaskInDesktop_isHidden() {
        val transparentTask =
            createTask(
                TRANSPARENT_TASK_ID,
                TRANSPARENT_TASK_BOUNDS,
                isActivityStackTransparent = true,
                windowingMode = WINDOWING_MODE_FULLSCREEN,
            )
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))
        whenever(
                desktopModeCompatPolicy.isTransparentOverlay(
                    transparentTask.key.isActivityStackTransparent,
                    transparentTask.key.numActivities,
                    transparentTask.key.windowingMode,
                )
            )
            .thenReturn(true)
        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any())).thenAnswer { invocation ->
            val tasks = invocation.getArgument<List<Task>>(0)
            tasks.map { Rendered(it.key.id, it.appBounds!!) }
        }
        val task1 = createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)

        val result =
            systemUnderTest(
                tasks = listOf(task1, transparentTask),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = emptyMap(),
            )

        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                DesktopTaskLayoutState(
                    fullscreenPosition = Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    overviewPosition = ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                ),
                TRANSPARENT_TASK_ID,
                DesktopTaskLayoutState(
                    fullscreenPosition = Rendered(TRANSPARENT_TASK_ID, TRANSPARENT_TASK_BOUNDS),
                    overviewPosition = Hidden(TRANSPARENT_TASK_ID),
                ),
            )

        val allCurrentOriginalTaskBounds =
            listOf(Rendered(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1))
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), isNull())
    }

    @Test
    fun invoke_transparentActivityNonTransparentActivityStackTaskInDesktop_isShown() {
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(
                listOf(
                    ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                    ORGANIZED_RENDERED_TASK_BOUNDS_DATA_FOR_SEMITRANSPARENT_TASK,
                )
            )
        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any())).thenAnswer { invocation ->
            val tasks = invocation.getArgument<List<Task>>(0)
            tasks.map { Rendered(it.key.id, it.appBounds!!) }
        }

        val task1 = createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)
        val transparentTask =
            createTask(
                SEMI_TRANSPARENT_TASK_ID,
                SEMI_TRANSPARENT_TASK_BOUNDS,
                windowingMode = 1, // WINDOWING_MODE_FULLSCREEN
            )
        val result =
            systemUnderTest(
                tasks = listOf(task1, transparentTask),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = emptyMap(),
            )

        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                DesktopTaskLayoutState(
                    fullscreenPosition = Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    overviewPosition = ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                ),
                SEMI_TRANSPARENT_TASK_ID,
                DesktopTaskLayoutState(
                    fullscreenPosition =
                        Rendered(SEMI_TRANSPARENT_TASK_ID, SEMI_TRANSPARENT_TASK_BOUNDS),
                    overviewPosition = ORGANIZED_RENDERED_TASK_BOUNDS_DATA_FOR_SEMITRANSPARENT_TASK,
                ),
            )

        verify(getDesktopTaskFullscreenPositionUseCase).invoke(any())

        val allCurrentOriginalTaskBounds =
            listOf(
                Rendered(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1),
                Rendered(taskId = SEMI_TRANSPARENT_TASK_ID, bounds = SEMI_TRANSPARENT_TASK_BOUNDS),
            )

        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), isNull())
    }

    @Test
    fun invoke_populatesOldOverviewPositionFromOldMap() {
        val oldOverviewPosition =
            Rendered(taskId = NEW_TASK_ID_1, bounds = OLD_RENDERED_TASK_BOUNDS)
        val oldTaskLayoutStateMap = mapOf(NEW_TASK_ID_1 to oldOverviewPosition)

        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any()))
            .thenReturn(listOf(Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)))
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), any()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))

        val dismissedTaskId = 10
        val result =
            systemUnderTest(
                tasks = listOf(createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = oldTaskLayoutStateMap,
                dismissedTaskId = dismissedTaskId,
            )

        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                DesktopTaskLayoutState(
                    fullscreenPosition = Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    overviewPosition = ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                    oldOverviewPosition = oldOverviewPosition,
                ),
            )
    }

    @Test
    fun invoke_nullDismissedTask_doesNotPopulatesOldOverviewPositionFromOldMap() {
        val oldOverviewPosition =
            Rendered(taskId = NEW_TASK_ID_1, bounds = OLD_RENDERED_TASK_BOUNDS)
        val oldTaskLayoutStateMap = mapOf(NEW_TASK_ID_1 to oldOverviewPosition)

        whenever(getDesktopTaskFullscreenPositionUseCase.invoke(any()))
            .thenReturn(listOf(Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)))
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))

        val result =
            systemUnderTest(
                tasks = listOf(createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)),
                layoutConfig = TEST_LAYOUT_CONFIG,
                oldTaskOverviewPositionsMap = oldTaskLayoutStateMap,
                dismissedTaskId = null,
            )

        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                DesktopTaskLayoutState(
                    fullscreenPosition = Rendered(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    overviewPosition = ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                    oldOverviewPosition = null,
                ),
            )
    }

    private fun createTask(
        id: Int,
        appBounds: Rect?,
        isMinimized: Boolean = false,
        isActivityStackTransparent: Boolean = false,
        windowingMode: Int = 0,
    ) =
        Task().apply {
            key =
                TaskKey(
                    id,
                    windowingMode,
                    Intent(),
                    ComponentName("", ""),
                    /* userId */ 0,
                    /* lastActiveTime */ 0,
                )
            this.appBounds = appBounds
            this.key.numActivities = 1
            this.key.isActivityStackTransparent = isActivityStackTransparent
            this.isMinimized = isMinimized
        }

    private companion object {
        private val TEST_LAYOUT_CONFIG =
            TaskLayoutConfig.DesktopLayoutConfig(
                desktopBounds = Rect(0, 0, 1000, 2000),
                topBottomMarginOneRow = 20,
                topMarginMultiRows = 20,
                bottomMarginMultiRows = 20,
                leftRightMarginOneRow = 20,
                leftRightMarginMultiRows = 20,
                horizontalPaddingBetweenTasks = 10,
                verticalPaddingBetweenTasks = 10,
                minTaskWidth = 100,
                maxRows = 4,
            )

        const val NEW_TASK_ID_1 = 1
        val NEW_TASK_BOUNDS_1 = Rect(0, 0, 1, 1)

        const val NEW_TASK_ID_2 = 2
        val NEW_TASK_BOUNDS_2 = Rect(0, 0, 2, 2)

        const val OLD_RENDERED_TASK_ID = -1
        val OLD_RENDERED_TASK_BOUNDS = Rect(-1, -1, 0, 0)

        const val OLD_HIDDEN_TASK_ID = -2

        val ORGANIZED_RENDERED_TASK_BOUNDS = Rect(0, 0, 10, 10)
        val ORGANIZED_RENDERED_TASK_BOUNDS_DATA =
            Rendered(taskId = NEW_TASK_ID_1, bounds = ORGANIZED_RENDERED_TASK_BOUNDS)
        val ORGANIZED_HIDDEN_TASK_BOUNDS_DATA = Hidden(taskId = NEW_TASK_ID_2)

        const val TRANSPARENT_TASK_ID = 3
        val TRANSPARENT_TASK_BOUNDS = Rect(0, 0, 3, 3)

        const val SEMI_TRANSPARENT_TASK_ID = 4
        val SEMI_TRANSPARENT_TASK_BOUNDS = Rect(0, 0, 4, 4)
        val ORGANIZED_RENDERED_TASK_BOUNDS_DATA_FOR_SEMITRANSPARENT_TASK =
            Rendered(taskId = SEMI_TRANSPARENT_TASK_ID, bounds = ORGANIZED_RENDERED_TASK_BOUNDS)
    }
}
