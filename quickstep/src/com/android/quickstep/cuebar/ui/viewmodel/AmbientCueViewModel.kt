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

package com.android.quickstep.cuebar.ui.viewmodel

import android.app.ActivityTaskManager
import android.os.SystemClock
import android.service.personalcontext.insight.interaction.InsightEvent
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeRect
import com.android.cuebar.ui.viewmodel.ActionType
import com.android.cuebar.ui.viewmodel.ActionViewModel
import com.android.cuebar.ui.viewmodel.IconViewModel
import com.android.launcher3.Flags.enableCueBarDesktopFormFactor
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.util.OnboardingPrefs.AMBIENT_CUE_FIRST_TIME_SHOWN_AT
import com.android.launcher3.util.OnboardingPrefs.AMBIENT_CUE_LONG_PRESS_SEEN
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.widgetpicker.ui.ViewModel
import com.android.quickstep.cuebar.data.ActionModel
import com.android.quickstep.cuebar.domain.interactor.AmbientCueInteractor
import com.android.quickstep.cuebar.logger.AmbientCueAceLogger
import com.android.quickstep.cuebar.logger.AmbientCueLogger
import com.android.systemui.shared.Flags.cueBarAceMigration
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.PrintWriter
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AmbientCueViewModel
@AssistedInject
constructor(
    private val ambientCueInteractor: AmbientCueInteractor,
    private val launcherPrefs: LauncherPrefs,
    private val ambientCueLogger: AmbientCueLogger,
    private val ambientCueAceLogger: AmbientCueAceLogger,
    private val isDesktopFormFactor: Boolean,
    private val scope: CoroutineScope,
    @Ui private val uiExecutor: Executor,
) : ViewModel {
    var isVisible: Boolean by mutableStateOf(false)
        private set

    var isExpanded: Boolean by mutableStateOf(false)
        private set

    var showFirstTimeEducation: Boolean by mutableStateOf(false)
        private set

    var showLongPressEducation: Boolean by mutableStateOf(false)
        private set

    var pillStyle: PillStyleViewModel by mutableStateOf(PillStyleViewModel.Uninitialized)
        private set

    // Hold the IME-filtered actions for the UI
    var actions: List<ActionViewModel> by mutableStateOf(emptyList())
        private set

    // Internally hold the latest incoming actions
    private var currentUnfilteredActions: List<ActionModel> = emptyList()

    var targetTaskId: Int by mutableIntStateOf(INVALID_TASK_ID)
        private set

    var onVisibilityChanged: (Boolean) -> Unit = {}
    private var isSessionStarted = false

    private val prefListener = LauncherPrefChangeListener { key ->
        if (
            key == AMBIENT_CUE_FIRST_TIME_SHOWN_AT.sharedPrefKey ||
                key == AMBIENT_CUE_LONG_PRESS_SEEN.sharedPrefKey
        ) {
            recalculateStates()
        }
    }

    override suspend fun onInit() {
        recalculateStates()
    }

    fun expand() {
        if (!isExpanded) {
            isExpanded = true
            disableFirstTimeHint()
        }
    }

    fun collapse() {
        if (isExpanded) {
            isExpanded = false
            disableLongPressHint()
        }
    }

    fun hide() {
        ambientCueInteractor.setDeactivated(true)
        isExpanded = false
        disableFirstTimeHint()
        ambientCueLogger.setClickedCloseButtonStatus()
        ambientCueInteractor.reportCloseEvent()
    }

    private val listeners = mutableListOf<SafeCloseable>()
    private var deactivateCueBarJob: Job? = null
    private var actionUpdateJob: Job? = null

    init {
        if (cueBarAceMigration()) {
            registerListeners()
        }
    }

    private fun registerListeners() {
        // Recalculate everything whenever any of the source states change.
        listeners.add(ambientCueInteractor.isImeVisible.forEach(uiExecutor) { recalculateStates() })
        listeners.add(
            ambientCueInteractor.isOccludedBySystemUi.forEach(uiExecutor) { recalculateStates() }
        )
        listeners.add(
            ambientCueInteractor.isDeactivated.forEach(uiExecutor) { recalculateStates() }
        )
        listeners.add(
            ambientCueInteractor.isAmbientCueEnabled.forEach(uiExecutor) { recalculateStates() }
        )
        listeners.add(ambientCueInteractor.isGestureNav.forEach(uiExecutor) { recalculateStates() })
        listeners.add(
            ambientCueInteractor.isTaskBarVisible.forEach(uiExecutor) { recalculateStates() }
        )
        listeners.add(
            ambientCueInteractor.recentsButtonPosition.forEach(uiExecutor) { recalculateStates() }
        )
        listeners.add(
            ambientCueInteractor.globallyFocusedTaskId.forEach(uiExecutor) { recalculateStates() }
        )
        // Handles actions separately for debouncing empty lists and updating the internal state.
        listeners.add(ambientCueInteractor.actions.forEach(uiExecutor, ::onUnfilteredActionsChange))
        launcherPrefs.addListener(prefListener, AMBIENT_CUE_FIRST_TIME_SHOWN_AT)
        launcherPrefs.addListener(prefListener, AMBIENT_CUE_LONG_PRESS_SEEN)
    }

    private fun recalculateStates() {
        if (!cueBarAceMigration()) {
            return
        }
        val oldVisibility = isVisible
        val globallyFocusedTaskId = ambientCueInteractor.globallyFocusedTaskId.value
        val isRootAttached =
            ambientCueInteractor.isTestMode.value ||
                currentUnfilteredActions.isNotEmpty() &&
                    ambientCueInteractor.isAmbientCueEnabled.value &&
                    !ambientCueInteractor.isDeactivated.value &&
                    globallyFocusedTaskId == targetTaskId
        if (isRootAttached && !isSessionStarted) {
            isSessionStarted = true
            var maCount = 0
            var mrCount = 0
            val packageName = ambientCueInteractor.frontTaskPackageName.value
            currentUnfilteredActions.forEach { action ->
                when (action.actionType) {
                    "ma" -> maCount++
                    "mr" -> mrCount++
                    else -> {}
                }
            }
            ambientCueLogger.setPackageName(packageName)
            ambientCueLogger.setAmbientCueDisplayStatus(maCount, mrCount)
            ambientCueAceLogger.reportInsightEvent(InsightEvent.EVENT_SHOW)
        } else if (!isRootAttached && isSessionStarted) {
            if (globallyFocusedTaskId != targetTaskId) {
                ambientCueLogger.setLoseFocusMillis()
            }
            ambientCueLogger.flushAmbientCueEventReported()
            ambientCueLogger.clear()
            isSessionStarted = false
            ambientCueAceLogger.reportInsightEvent(InsightEvent.EVENT_HIDE)
        }
        val isGestureNav = ambientCueInteractor.isGestureNav.value
        val isTaskBarVisible = ambientCueInteractor.isTaskBarVisible.value
        pillStyle =
            when {
                enableCueBarDesktopFormFactor() && isDesktopFormFactor ->
                    PillStyleViewModel.DesktopPillStyle
                isGestureNav && !isTaskBarVisible -> PillStyleViewModel.NavBarPillStyle
                else -> {
                    val position =
                        if (isGestureNav) null else ambientCueInteractor.recentsButtonPosition.value
                    PillStyleViewModel.ShortPillStyle(position?.toComposeRect())
                }
            }
        // Handle timeout activation
        if (isRootAttached) {
            if (isExpanded) {
                cancelDeactivation()
            } else {
                scope.launch { delayAndDeactivateCueBar() }
            }
        } else {
            cancelDeactivation()
        }

        val firstTimeShown = launcherPrefs.get(AMBIENT_CUE_FIRST_TIME_SHOWN_AT)
        showFirstTimeEducation = firstTimeShown == -1L

        val shouldShowLongPress = launcherPrefs.get(AMBIENT_CUE_LONG_PRESS_SEEN)
        val firstTimeSeenAtMs =
            (if (firstTimeShown == -1L) SystemClock.elapsedRealtime() else firstTimeShown)
                .milliseconds
        showLongPressEducation =
            firstTimeSeenAtMs + ONBOARDING_DELAY < System.currentTimeMillis().milliseconds &&
                shouldShowLongPress

        updateActionViewModelList()

        isVisible =
            isRootAttached &&
                !ambientCueInteractor.isOccludedBySystemUi.value &&
                actions.isNotEmpty()

        if (oldVisibility != isVisible) {
            onVisibilityChanged(isVisible)
        }
    }

    /**
     * Callback function invoked when the list of unfiltered [ActionModel] changes in the
     * [AmbientCueInteractor].
     *
     * This function updates the internal [currentUnfilteredActions] state. To prevent UI
     * flickering, it debounces updates if the [newActions] list is empty. Non-empty lists trigger
     * an immediate state update and recalculation.
     *
     * @param newActions The new list of unfiltered [ActionModel]s.
     */
    fun onUnfilteredActionsChange(newActions: List<ActionModel>) {
        Log.d(TAG, "onUnfilteredActionsChange: $newActions")

        val updateState = {
            currentUnfilteredActions = newActions
            targetTaskId =
                if (currentUnfilteredActions.isNotEmpty()) {
                    currentUnfilteredActions[0].taskId
                } else {
                    INVALID_TASK_ID
                }
            recalculateStates()
        }

        // Cancel any pending debounced (empty) action job
        actionUpdateJob?.cancel()
        if (newActions.isEmpty()) {
            // If the list is empty, DEBOUNCE it.
            // We only launch a coroutine if we need to delay.
            actionUpdateJob =
                scope.launch { // Use BG scope for delay
                    delay(ACTIONS_DEBOUNCE_MS)
                    withContext(uiExecutor.asCoroutineDispatcher()) {
                        Log.d(TAG, "Debounced empty action: updating state")
                        updateState()
                    }
                }
        } else {
            // If the list is NOT empty, update state IMMEDIATELY.
            // No coroutine needed, we are already on the main thread.
            updateState()
        }
    }

    /**
     * Updates the [actions] list, which is used by the UI.
     *
     * This method filters the [currentUnfilteredActions] based on IME visibility (Input Method
     * Editor, i.e., the on-screen keyboard). Actions not enabled with IME visible are removed when
     * the IME is shown.
     */
    private fun updateActionViewModelList() {
        val isImeVisible = ambientCueInteractor.isImeVisible.value
        val filteredActions =
            if (isImeVisible) {
                currentUnfilteredActions.filter { it.isEnabledWithImeVisible }
            } else {
                currentUnfilteredActions
            }

        actions =
            filteredActions
                .map { action ->
                    ActionViewModel(
                        icon =
                            IconViewModel(
                                large = action.icon.large,
                                small = action.icon.small,
                                iconId = action.icon.iconId,
                                repeatCount = 0,
                            ),
                        label = action.label,
                        attribution = action.attribution,
                        onClick = {
                            action.onPerformAction()
                            collapse()
                        },
                        onLongClick = {
                            action.onPerformLongClick()
                            launcherPrefs.put(AMBIENT_CUE_LONG_PRESS_SEEN, false)
                        },
                        actionType =
                            when (action.actionType) {
                                "ma" -> ActionType.MA
                                "mr" -> ActionType.MR
                                else -> ActionType.Unknown
                            },
                        oneTapEnabled = action.oneTapEnabled,
                        oneTapDelayMs = action.oneTapDelayMs,
                    )
                }
                .toList()
    }

    fun cancelDeactivation() {
        deactivateCueBarJob?.cancel()
    }

    suspend fun delayAndDeactivateCueBar() {
        deactivateCueBarJob?.cancel()
        deactivateCueBarJob =
            scope.launch {
                delay(ambientCueInteractor.ambientCueTimeoutMs.value.milliseconds)
                ambientCueInteractor.setDeactivated(true)
                Log.i(TAG, "Cuebar deactivated due to timeout")
                ambientCueLogger.setReachedTimeoutStatus()
            }
    }

    fun activate() {
        // Initial calculation when the viewmodel becomes active
        recalculateStates()
    }

    fun deactivate() {
        listeners.forEach { it.close() }
        listeners.clear()
        cancelDeactivation()
        actionUpdateJob?.cancel()
        onVisibilityChanged = {}
    }

    fun disableFirstTimeHint() {
        if (showFirstTimeEducation) {
            Log.i(TAG, "Suppressing first time tooltip")
            launcherPrefs.put(AMBIENT_CUE_FIRST_TIME_SHOWN_AT, SystemClock.elapsedRealtime())
            // Trigger a recalculation to update the education state immediately
            recalculateStates()
        }
    }

    private fun disableLongPressHint() {
        if (showLongPressEducation) {
            Log.i(TAG, "Suppressing long press tooltip")
            launcherPrefs.put(AMBIENT_CUE_LONG_PRESS_SEEN, false)
            // Trigger a recalculation to update the education state immediately
            recalculateStates()
        }
    }

    fun dump(pw: PrintWriter, prefix: String) {
        pw.println("$prefix AmbientCueViewModel:")
        pw.println("$prefix   isVisible: $isVisible")
        pw.println("$prefix   isExpanded: $isExpanded")
        pw.println("$prefix   pillStyle: ${pillStyle::class.simpleName}")
        pw.println("$prefix   actions: ${actions.size} actions")
        pw.println("$prefix   deactivateCueBarJob: $deactivateCueBarJob")
        pw.println("$prefix  targetTaskId: $targetTaskId")
        pw.println(
            "$prefix  frontTaskPackageName(source): ${ambientCueInteractor.frontTaskPackageName.value}"
        )
        pw.println("$prefix  isSessionStarted: $isSessionStarted")
        pw.println("$prefix   isImeVisible(source): ${ambientCueInteractor.isImeVisible.value}")
        pw.println(
            "$prefix   isOccluded(source): ${ambientCueInteractor.isOccludedBySystemUi.value}"
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): AmbientCueViewModel
    }

    companion object {
        private const val TAG = "LauncherAmbientCueVM"
        private val ONBOARDING_DELAY = 7.days
        @VisibleForTesting const val ACTIONS_DEBOUNCE_MS = 300L
        private const val INVALID_TASK_ID = ActivityTaskManager.INVALID_TASK_ID
    }
}
