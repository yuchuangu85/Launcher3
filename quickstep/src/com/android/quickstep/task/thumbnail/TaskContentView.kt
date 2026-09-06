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

package com.android.quickstep.task.thumbnail

import android.animation.Animator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewStub
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.android.compose.theme.PlatformTheme
import com.android.launcher3.Flags.enableRefactorDigitalWellbeingToast
import com.android.launcher3.R
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.launcher3.util.ViewPool
import com.android.quickstep.orientation.RecentsPagedOrientationHandler
import com.android.quickstep.task.apptimer.TaskAppTimerUiState
import com.android.quickstep.task.apptimer.TaskAppTimerUiState.Uninitialized
import com.android.quickstep.task.apptimer.TaskAppTimerViewModel
import com.android.quickstep.task.apptimer.TimerTextHelper
import com.android.quickstep.task.apptimer.ui.composable.AppTimerToast
import com.android.quickstep.util.BorderAnimator
import com.android.quickstep.util.BorderAnimator.Companion.DEFAULT_BORDER_COLOR
import com.android.quickstep.util.BorderAnimator.Companion.createSimpleBorderAnimator
import com.android.quickstep.util.setActivityStarterClickListener
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskHeaderView
import com.android.wm.shell.shared.split.SplitBounds

/**
 * TaskContentView is a wrapper around the TaskHeaderView, TaskThumbnailView and Digital wellbeing
 * app timer toast. It is a sibling to AiAi (TaskOverlay).
 *
 * When enableRefactorDigitalWellbeingToast is off, it is sibling to digital wellbeing toast unlike
 * when the flag is on.
 */
class TaskContentView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs), ViewPool.Reusable {

    private var taskHeaderView: TaskHeaderView? = null

    private var taskThumbnailView: TaskThumbnailView? = null
    private val useComposeTaskAppTimer
        get() = enableRefactorDigitalWellbeingToast()

    @Deprecated("This toast is getting replaced by the compose version taskAppTimerToastCompose")
    private var taskAppTimerToast: TextView? = null

    private var taskAppTimerToastCompose: View? = null
    private val taskAppTimerViewModel by lazy { TaskAppTimerViewModel() }
    private val recentsViewContainer by
        lazy<RecentsViewContainer> { RecentsViewContainer.containerFromContext(context) }

    private var timerTextHelper: TimerTextHelper? = null
    private var timerUiState: TaskAppTimerUiState = Uninitialized
    private var timerUsageAccessibilityAction: AccessibilityAction? = null

    private var onSizeChanged: ((width: Int, height: Int) -> Unit)? = null

    private val borderWidthPx: Int by lazy {
        context.resources.getDimensionPixelSize(R.dimen.task_hover_focus_border_width)
    }

    private val borderOffsetPx: Int by lazy {
        context.resources.getDimensionPixelSize(R.dimen.task_hover_focus_offset_size)
    }

    private var activeFocusAnimator: Animator? = null
    private var activeHoverAnimator: Animator? = null

    private val focusBorderColor: Int
    private val hoverBorderColor: Int

    private var hoverBorderVisible = false
        set(value) {
            if (field == value) {
                return
            }

            field = value

            activeHoverAnimator?.cancel()
            activeHoverAnimator = animateBorder(hoverBorderAnimator, value)
        }

    var isHoverable: Boolean = false

    var cornerRadius: Float = 0f
        set(value) {
            field = value
            taskThumbnailView?.cornerRadius = value
        }

    var taskCornerRadius: Float = 0f

    var outlineBounds: Rect? = null
        set(value) {
            field = value
            taskThumbnailView?.outlineBounds = value
        }

    init {
        setWillNotDraw(false)
        context.obtainStyledAttributes(attrs, R.styleable.TaskContentView).use {
            focusBorderColor =
                it.getColor(R.styleable.TaskContentView_focusBorderColor, DEFAULT_BORDER_COLOR)
            hoverBorderColor =
                it.getColor(R.styleable.TaskContentView_hoverBorderColor, DEFAULT_BORDER_COLOR)
        }
    }

    private val focusBorderAnimator: BorderAnimator by lazy {
        createSimpleBorderAnimator(
            borderRadiusPx = taskCornerRadius.toInt() + borderOffsetPx,
            borderWidthPx = borderWidthPx,
            boundsBuilder = {
                it.set(0, 0, width, height)
                it.inset(-borderOffsetPx, -borderOffsetPx)
            },
            targetView = this,
            borderColor = focusBorderColor,
        )
    }

    private val hoverBorderAnimator: BorderAnimator by lazy {
        createSimpleBorderAnimator(
            borderRadiusPx = taskCornerRadius.toInt() + borderOffsetPx,
            borderWidthPx = borderWidthPx,
            boundsBuilder = {
                it.set(0, 0, width, height)
                it.inset(-borderOffsetPx, -borderOffsetPx)
            },
            targetView = this,
            borderColor = hoverBorderColor,
        )
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        createTaskThumbnailView()
    }

    override fun setScaleX(scaleX: Float) {
        super.setScaleX(scaleX)
        taskThumbnailView?.parentScaleX = scaleX
    }

    override fun setScaleY(scaleY: Float) {
        super.setScaleY(scaleY)
        taskThumbnailView?.parentScaleY = scaleY
    }

    override fun onRecycle() {
        taskHeaderView?.isInvisible = true
        taskHeaderView?.alpha = 1.0f
        onSizeChanged = null
        alpha = 1.0f
        taskThumbnailView?.onRecycle()
        taskAppTimerToast?.isInvisible = true
        timerUiState = Uninitialized
        if (taskAppTimerToastCompose != null) taskAppTimerViewModel.setState(Uninitialized)
        timerTextHelper = null
        timerUsageAccessibilityAction = null
        hoverBorderVisible = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun doOnSizeChange(action: (width: Int, height: Int) -> Unit) {
        onSizeChanged = action
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        onSizeChanged?.invoke(width, height)
        updateTimerText(w)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (isFocusable) {
            focusBorderAnimator.drawBorder(canvas)
        }
        if (isHoverable) {
            hoverBorderAnimator.drawBorder(canvas)
        }
    }

    public override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)

        activeFocusAnimator?.cancel()
        activeFocusAnimator = animateBorder(focusBorderAnimator, gainFocus)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!isHoverable) return false
        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                hoverBorderVisible = true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverBorderVisible = false
            }
        }
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        with(info) { taskHeaderView?.getSupportedAccessibilityActions()?.forEach(::addAction) }
    }

    fun onParentAnimationProgress(progress: Float) {
        taskAppTimerToast?.apply { alpha = progress }
        taskAppTimerToastCompose?.apply { alpha = progress }
    }

    /** Returns accessibility actions supported by items in the task content view. */
    fun getSupportedAccessibilityActions(): List<AccessibilityAction> {
        return listOfNotNull(timerUsageAccessibilityAction)
    }

    fun handleAccessibilityAction(action: Int): Boolean {
        timerUsageAccessibilityAction?.let {
            if (action == it.id) {
                return taskAppTimerToast?.callOnClick() ?: false
            }
        }
        return false
    }

    fun getTaskAppTimerToastHeight() =
        (if (useComposeTaskAppTimer) taskAppTimerToastCompose else taskAppTimerToast)?.height ?: 0

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        taskHeaderView?.let {
            if (it.handleAccessibilityAction(action)) {
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun createHeaderView(taskHeaderState: TaskHeaderUiState) {
        if (taskHeaderView == null && taskHeaderState is TaskHeaderUiState.ShowHeader) {
            taskHeaderView =
                findViewById<ViewStub>(R.id.task_header_view)
                    .apply { layoutResource = R.layout.task_header_view }
                    .inflate() as TaskHeaderView
        }
    }

    private fun createTaskThumbnailView() {
        if (taskThumbnailView == null) {
            taskThumbnailView =
                findViewById<ViewStub>(R.id.snapshot)
                    .apply { layoutResource = R.layout.task_thumbnail }
                    .inflate() as TaskThumbnailView
        }
    }

    private fun createAppTimerToastView(taskAppTimerUiState: TaskAppTimerUiState) {
        if (taskAppTimerUiState is TaskAppTimerUiState.Timer) {
            when {
                useComposeTaskAppTimer && taskAppTimerToastCompose == null -> {
                    taskAppTimerToastCompose =
                        ComposeView(context).apply {
                            setContent {
                                val timerUiState by taskAppTimerViewModel.uiState
                                PlatformTheme { AppTimerToast(timerUiState, taskAppTimerViewModel) }
                            }
                        }
                    addAppTimerToastToLayout()
                }

                !useComposeTaskAppTimer && taskAppTimerToast == null -> {
                    taskAppTimerToast =
                        findViewById<ViewStub>(R.id.task_app_timer_toast)
                            .apply { layoutResource = R.layout.task_app_timer_toast }
                            .inflate() as TextView
                }
            }
        }
    }

    private fun addAppTimerToastToLayout() {
        val params =
            LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                startToStart = PARENT_ID
                endToEnd = PARENT_ID
                bottomToBottom = R.id.snapshot
            }
        addView(taskAppTimerToastCompose, params)
    }

    fun setState(
        taskHeaderState: TaskHeaderUiState,
        taskThumbnailUiState: TaskThumbnailUiState,
        taskAppTimerUiState: TaskAppTimerUiState,
        taskId: Int?,
    ) {
        createHeaderView(taskHeaderState)
        taskHeaderView?.setState(taskHeaderState)
        taskThumbnailView?.setState(taskThumbnailUiState, taskId)
        createAppTimerToastView(taskAppTimerUiState)

        if (enableRefactorDigitalWellbeingToast() && timerUiState != taskAppTimerUiState) {
            setAppTimerToastState(taskAppTimerUiState)
            updateContentDescriptionWithTimer(taskAppTimerUiState)
        }
    }

    fun setTaskHeaderAlpha(alpha: Float) {
        taskHeaderView?.alpha = alpha
    }

    fun onTaskViewDisplayConfigChanged(
        taskViewWidth: Int,
        taskViewHeight: Int,
        isGroupedTaskView: Boolean,
        splitBounds: SplitBounds?,
        orientationHandler: RecentsPagedOrientationHandler,
        @StagePosition stagePosition: Int,
    ) {
        if (enableRefactorDigitalWellbeingToast()) {
            updateTaskAppTimerOrientation(
                taskViewWidth,
                taskViewHeight,
                isGroupedTaskView,
                splitBounds,
                orientationHandler,
                stagePosition,
            )
        }
    }

    /**
     * This method updates the orientation of the task app timer toast. Practically This is only
     * needed while Recents(overview) relies on Fake Landscape/Seascape. This code may be removed
     * when Recents in a window project is completed b/292269949
     */
    private fun updateTaskAppTimerOrientation(
        taskViewWidth: Int,
        taskViewHeight: Int,
        isGroupedTaskView: Boolean,
        splitBounds: SplitBounds?,
        orientationHandler: RecentsPagedOrientationHandler,
        @StagePosition stagePosition: Int,
    ) {
        val appTimer = if (useComposeTaskAppTimer) taskAppTimerToastCompose else taskAppTimerToast
        if (appTimer == null) return

        val (snapshotWidth, snapshotHeight) =
            computeSnapshotDimensions(
                splitBounds,
                taskViewWidth,
                taskViewHeight,
                orientationHandler,
                stagePosition,
            )

        orientationHandler.updateAppTimerLayout(
            taskViewWidth,
            taskViewHeight,
            isGroupedTaskView,
            recentsViewContainer.deviceProfile,
            snapshotWidth,
            snapshotHeight,
            appTimer,
        )
    }

    private fun computeSnapshotDimensions(
        splitBounds: SplitBounds?,
        taskViewWidth: Int,
        taskViewHeight: Int,
        pagedOrientationHandler: RecentsPagedOrientationHandler,
        @StagePosition stagePosition: Int,
    ): Pair<Int, Int> {
        val snapshotWidth: Int
        val snapshotHeight: Int
        if (splitBounds == null) {
            snapshotWidth = taskViewWidth
            snapshotHeight = taskViewHeight
        } else {
            val groupedTaskSize =
                pagedOrientationHandler.getGroupedTaskViewSizes(
                    recentsViewContainer.deviceProfile,
                    splitBounds,
                    taskViewWidth,
                    taskViewHeight,
                )
            if (stagePosition == STAGE_POSITION_TOP_OR_LEFT) {
                snapshotWidth = groupedTaskSize.first.x
                snapshotHeight = groupedTaskSize.first.y
            } else {
                snapshotWidth = groupedTaskSize.second.x
                snapshotHeight = groupedTaskSize.second.y
            }
        }

        return Pair(snapshotWidth, snapshotHeight)
    }

    private fun updateContentDescriptionWithTimer(state: TaskAppTimerUiState) {
        taskThumbnailView?.contentDescription =
            when (state) {
                is Uninitialized -> return
                is TaskAppTimerUiState.NoTimer -> state.taskDescription
                is TaskAppTimerUiState.Timer ->
                    context.getString(
                        R.string.task_contents_description_with_remaining_time,
                        state.taskDescription,
                        context.getString(
                            R.string.time_left_for_app,
                            taskAppTimerViewModel.getFormattedDuration(state.timeRemaining, context),
                        ),
                    )
            }
    }

    private fun setAppTimerToastState(state: TaskAppTimerUiState) {
        timerUiState = state
        if (useComposeTaskAppTimer) {
            if (taskAppTimerToastCompose != null) taskAppTimerViewModel.setState(state)
            return
        }

        taskAppTimerToast?.apply {
            when (state) {
                is Uninitialized -> isInvisible = true
                is TaskAppTimerUiState.NoTimer -> isInvisible = true
                is TaskAppTimerUiState.Timer -> {
                    timerTextHelper = TimerTextHelper(context, state.timeRemaining)
                    isInvisible = false
                    updateTimerText(width)

                    // TODO: add WW logging on the app usage settings click.
                    setActivityStarterClickListener(
                        appUsageSettingsIntent(state.taskPackageName),
                        "app usage settings for task ${state.taskDescription}",
                    )

                    timerUsageAccessibilityAction =
                        createAppUsageSettingsAccessibilityAction(
                            context,
                            state.accessibilityActionId,
                            state.taskDescription,
                        )
                }
            }
        }
    }

    private fun updateTimerText(width: Int) {
        taskAppTimerToast?.apply {
            val helper = timerTextHelper

            if (isVisible && helper != null) {
                text = helper.getTextThatFits(width, paint)
            }
        }
    }

    private fun animateBorder(borderAnimator: BorderAnimator, show: Boolean) =
        borderAnimator.buildAnimator(show).apply { start() }

    companion object {
        const val TAG = "TaskContentView"

        private fun appUsageSettingsIntent(packageName: String) =
            Intent(Intent(Settings.ACTION_APP_USAGE_SETTINGS))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        private fun createAppUsageSettingsAccessibilityAction(
            context: Context,
            @IdRes actionId: Int,
            taskDescription: String?,
        ) =
            AccessibilityAction(
                actionId,
                context.getString(R.string.split_app_usage_settings, taskDescription),
            )
    }
}
