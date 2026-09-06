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

package com.android.launcher3.taskbar

import android.app.ActivityTaskManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.OnboardingPrefs
import com.android.quickstep.cuebar.data.ActionModel
import com.android.quickstep.cuebar.data.IconModel
import com.android.quickstep.cuebar.domain.interactor.AmbientCueInteractor
import com.android.quickstep.cuebar.logger.AmbientCueAceLogger
import com.android.quickstep.cuebar.logger.AmbientCueLogger
import com.android.quickstep.cuebar.ui.viewmodel.AmbientCueViewModel
import com.android.quickstep.cuebar.ui.viewmodel.PillStyleViewModel
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AmbientCueViewModelTest {

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Mock private lateinit var mockInteractor: AmbientCueInteractor
    @Mock private lateinit var mockLauncherPrefs: LauncherPrefs
    @Mock private lateinit var mockAmbientCueLogger: AmbientCueLogger
    @Mock private lateinit var mockAmbientCueAceLogger: AmbientCueAceLogger
    @Mock private lateinit var mockIconModel: IconModel
    @Mock private lateinit var mockDrawable: Drawable

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    // Mock Executors
    private val uiExecutor: Executor = Executor { it.run() }

    // Real ListenableRefs to be returned by the mock Interactor
    private val actionsRef = MutableListenableRef<List<ActionModel>>(emptyList())
    private val isOccludedRef = MutableListenableRef(false)
    private val isDeactivatedRef = MutableListenableRef(false)
    private val isAmbientCueEnabledRef = MutableListenableRef(true)
    private val isGestureNavRef = MutableListenableRef(true)
    private val isTaskBarVisibleRef = MutableListenableRef(true)
    private val recentsButtonPositionRef = MutableListenableRef<Rect?>(null)
    private val globallyFocusedTaskIdRef = MutableListenableRef(TASK_ID)
    private val isImeVisibleRef = MutableListenableRef(false)
    private val frontTaskPackageNameRef = MutableListenableRef("com.example")
    private val isTestModeRef = MutableListenableRef(false)
    private val ambientCueTimeoutMsRef = MutableListenableRef(30000)

    private lateinit var viewModel: AmbientCueViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        `when`(mockIconModel.small).thenReturn(mockDrawable)
        `when`(mockIconModel.large).thenReturn(mockDrawable)
        `when`(mockIconModel.iconId).thenReturn("iconId")

        // Stub the property getters on mockInteractor to return real MutableListenableRefs
        `when`(mockInteractor.actions).thenReturn(actionsRef)
        `when`(mockInteractor.isOccludedBySystemUi).thenReturn(isOccludedRef)
        `when`(mockInteractor.isDeactivated).thenReturn(isDeactivatedRef)
        `when`(mockInteractor.isAmbientCueEnabled).thenReturn(isAmbientCueEnabledRef)
        `when`(mockInteractor.isGestureNav).thenReturn(isGestureNavRef)
        `when`(mockInteractor.isTaskBarVisible).thenReturn(isTaskBarVisibleRef)
        `when`(mockInteractor.recentsButtonPosition).thenReturn(recentsButtonPositionRef)
        `when`(mockInteractor.globallyFocusedTaskId).thenReturn(globallyFocusedTaskIdRef)
        `when`(mockInteractor.isImeVisible).thenReturn(isImeVisibleRef)
        `when`(mockInteractor.frontTaskPackageName).thenReturn(frontTaskPackageNameRef)
        `when`(mockInteractor.isTestMode).thenReturn(isTestModeRef)
        `when`(mockInteractor.ambientCueTimeoutMs).thenReturn(ambientCueTimeoutMsRef)

        // Mock LauncherPrefs for onboarding
        `when`(mockLauncherPrefs.get(OnboardingPrefs.AMBIENT_CUE_FIRST_TIME_SHOWN_AT))
            .thenReturn(-1L)
        `when`(mockLauncherPrefs.get(OnboardingPrefs.AMBIENT_CUE_LONG_PRESS_SEEN)).thenReturn(true)
    }

    private fun setupViewModel(isDesktopFormFactor: Boolean = false) {
        viewModel =
            AmbientCueViewModel(
                mockInteractor,
                mockLauncherPrefs,
                mockAmbientCueLogger,
                mockAmbientCueAceLogger,
                isDesktopFormFactor,
                testScope,
                uiExecutor,
            )
        // Activate View Model
        viewModel.activate()
    }

    // Helper functions to update the refs and manually trigger recalculation
    private fun updateActions(actions: List<ActionModel>) {
        actionsRef.dispatchValue(actions)
    }

    private fun updateImeVisible(isVisible: Boolean) {
        isImeVisibleRef.dispatchValue(isVisible)
    }

    private fun updateOccluded(isOccluded: Boolean) {
        isOccludedRef.dispatchValue(isOccluded)
    }

    private fun updateDeactivated(isDeactivated: Boolean) {
        isDeactivatedRef.dispatchValue(isDeactivated)
    }

    private fun updateGloballyFocusedTaskId(taskId: Int) {
        globallyFocusedTaskIdRef.dispatchValue(taskId)
    }

    private fun createMockAction(
        label: String,
        taskId: Int = TASK_ID,
        isEnabledWithIme: Boolean = false,
        attribution: String? = null,
    ): ActionModel {
        return ActionModel(
            icon = mockIconModel,
            label = label,
            onPerformAction = {},
            onPerformLongClick = {},
            taskId = taskId,
            actionType = "ma",
            isEnabledWithImeVisible = isEnabledWithIme,
            attribution = attribution,
        )
    }

    @Test
    fun init_defaultState_isVisibleFalse() = runTest {
        setupViewModel()
        assertThat(viewModel.isVisible).isFalse()
        assertThat(viewModel.actions).isEmpty()
        assertThat(viewModel.targetTaskId).isEqualTo(ActivityTaskManager.INVALID_TASK_ID)
    }

    @Test
    fun onUnfilteredActionsChange_nonEmpty_updatesStateAndBecomesVisible() = runTest {
        setupViewModel()
        val testActions = listOf(createMockAction("Action 1"))

        updateActions(testActions)

        assertThat(viewModel.targetTaskId).isEqualTo(TASK_ID)
        assertThat(viewModel.actions).hasSize(1)
        assertThat(viewModel.actions[0].label).isEqualTo("Action 1")
        assertThat(viewModel.isVisible).isTrue()
        verify(mockAmbientCueLogger).setAmbientCueDisplayStatus(1, 0)
    }

    @Test
    fun onUnfilteredActionsChange_empty_debouncesAndHides() = runTest {
        setupViewModel()
        updateActions(listOf(createMockAction("Action 1")))
        assertThat(viewModel.isVisible).isTrue()

        updateActions(emptyList())
        assertThat(viewModel.isVisible).isTrue() // Should not hide immediately

        testScheduler.advanceTimeBy(AmbientCueViewModel.ACTIONS_DEBOUNCE_MS + 50)

        assertThat(viewModel.actions).isEmpty()
        assertThat(viewModel.isVisible).isFalse()
        assertThat(viewModel.targetTaskId).isEqualTo(ActivityTaskManager.INVALID_TASK_ID)
    }

    @Test
    fun onUnfilteredActionsChange_nonEmptyAfterDebounce_cancelsDebounce() = runTest {
        setupViewModel()
        updateActions(emptyList())
        testScheduler.advanceTimeBy(AmbientCueViewModel.ACTIONS_DEBOUNCE_MS / 2)
        updateActions(listOf(createMockAction("Action 2")))

        testScheduler.advanceTimeBy(AmbientCueViewModel.ACTIONS_DEBOUNCE_MS)

        assertThat(viewModel.actions).hasSize(1)
        assertThat(viewModel.actions[0].label).isEqualTo("Action 2")
        assertThat(viewModel.isVisible).isTrue()
    }

    @Test
    fun imeVisible_filtersActions_visibilityCorrect() = runTest {
        setupViewModel()
        val actionIme = createMockAction("IME OK", isEnabledWithIme = true)
        val actionNoIme = createMockAction("IME Blocked", isEnabledWithIme = false)
        updateActions(listOf(actionIme, actionNoIme))

        updateImeVisible(false)
        assertThat(viewModel.isVisible).isTrue()
        assertThat(viewModel.actions).hasSize(2)

        updateImeVisible(true)
        assertThat(viewModel.isVisible).isTrue()
        assertThat(viewModel.actions).hasSize(1)
        assertThat(viewModel.actions[0].label).isEqualTo("IME OK")

        updateImeVisible(false)
        assertThat(viewModel.isVisible).isTrue()
        assertThat(viewModel.actions).hasSize(2)
    }

    @Test
    fun imeVisible_filtersAllActions_becomesInvisible() = runTest {
        setupViewModel()
        val actionNoIme1 = createMockAction("IME Blocked 1", isEnabledWithIme = false)
        val actionNoIme2 = createMockAction("IME Blocked 2", isEnabledWithIme = false)
        updateActions(listOf(actionNoIme1, actionNoIme2))

        updateImeVisible(false)
        assertThat(viewModel.isVisible).isTrue()
        assertThat(viewModel.actions).hasSize(2)

        updateImeVisible(true)
        assertThat(viewModel.actions).isEmpty()
        assertThat(viewModel.isVisible).isFalse()
    }

    @Test
    fun isOccluded_becomesInvisible() = runTest {
        setupViewModel()
        updateActions(listOf(createMockAction("Action")))
        assertThat(viewModel.isVisible).isTrue()

        updateOccluded(true)
        assertThat(viewModel.isVisible).isFalse()

        updateOccluded(false)
        assertThat(viewModel.isVisible).isTrue()
    }

    @Test
    fun isDeactivated_becomesInvisible() = runTest {
        setupViewModel()
        updateActions(listOf(createMockAction("Action")))
        assertThat(viewModel.isVisible).isTrue()

        updateDeactivated(true)

        assertThat(viewModel.isVisible).isFalse()
    }

    @Test
    fun hide_setsDeactivated_becomesInvisible() = runTest {
        setupViewModel()
        updateActions(listOf(createMockAction("Action")))
        viewModel.hide()

        verify(mockInteractor).setDeactivated(true)
        verify(mockInteractor).reportCloseEvent()
        updateDeactivated(true)

        assertThat(viewModel.isVisible).isFalse()
        verify(mockAmbientCueLogger).setClickedCloseButtonStatus()
    }

    @Test
    fun timeout_setsDeactivated() = runTest {
        setupViewModel()
        updateActions(listOf(createMockAction("Action")))
        assertThat(viewModel.isVisible).isTrue()

        val timeout = ambientCueTimeoutMsRef.value.toLong()
        testScheduler.advanceTimeBy(timeout + 100)

        verify(mockInteractor).setDeactivated(true)
    }

    @Test
    fun taskChange_updatesVisibility() = runTest {
        setupViewModel()
        updateActions(listOf(createMockAction("Action", taskId = TASK_ID)))
        updateGloballyFocusedTaskId(TASK_ID)
        assertThat(viewModel.isVisible).isTrue()

        updateGloballyFocusedTaskId(OTHER_TASK_ID)
        assertThat(viewModel.isVisible).isFalse()
        verify(mockAmbientCueLogger).setLoseFocusMillis()

        updateGloballyFocusedTaskId(TASK_ID)
        assertThat(viewModel.isVisible).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CUE_BAR_DESKTOP_FORM_FACTOR)
    fun desktopFormFactor_usesDesktopLayout() = runTest {
        setupViewModel(isDesktopFormFactor = true)

        updateActions(listOf(createMockAction("Action", taskId = TASK_ID)))
        updateGloballyFocusedTaskId(TASK_ID)

        assertThat(viewModel.pillStyle).isEqualTo(PillStyleViewModel.DesktopPillStyle)
    }

    companion object {
        private const val TASK_ID = 123
        private const val OTHER_TASK_ID = 456
    }
}
