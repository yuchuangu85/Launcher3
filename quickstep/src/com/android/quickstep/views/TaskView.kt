/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.quickstep.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.IdRes
import android.app.ActivityOptions
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.util.FloatProperty
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.animation.doOnCancel
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.updateLayoutParams
import com.android.app.animation.Interpolators
import com.android.app.tracing.traceSection
import com.android.internal.jank.Cuj.CUJ_LAUNCHER_APP_LAUNCH_FROM_RECENTS
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Flags.enableRefactorDigitalWellbeingToast
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.getTrimmedStackTrace
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ActivityContextComponent
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.TaskViewItemInfo
import com.android.launcher3.testing.TestLogging
import com.android.launcher3.testing.shared.TestProtocol
import com.android.launcher3.util.Executors
import com.android.launcher3.util.KFloatProperty
import com.android.launcher3.util.MultiPropertyDelegate
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.MultiValueAlpha
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.launcher3.util.TraceHelper
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.launcher3.util.ViewPool
import com.android.launcher3.util.rects.set
import com.android.quickstep.FullscreenDrawParams
import com.android.quickstep.RemoteAnimationTargets
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.TaskViewUtils
import com.android.quickstep.orientation.RecentsPagedOrientationHandler
import com.android.quickstep.recents.domain.usecase.ThumbnailPosition
import com.android.quickstep.recents.ui.mapper.TaskUiStateMapper
import com.android.quickstep.recents.ui.viewmodel.TaskData
import com.android.quickstep.recents.ui.viewmodel.TaskTileUiState
import com.android.quickstep.recents.ui.viewmodel.TaskViewModel
import com.android.quickstep.task.TaskDismissButtonState
import com.android.quickstep.task.thumbnail.TaskContentView
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.util.ActiveGestureErrorDetector
import com.android.quickstep.util.ActiveGestureLog
import com.android.quickstep.util.BorderAnimator
import com.android.quickstep.util.BorderAnimator.Companion.DEFAULT_APPEARANCE_ANIMATION_DURATION_MS
import com.android.quickstep.util.BorderAnimator.Companion.DEFAULT_DISAPPEARANCE_ANIMATION_DURATION_MS
import com.android.quickstep.util.BorderAnimator.Companion.DEFAULT_INTERPOLATOR
import com.android.quickstep.util.BorderAnimator.Companion.createSimpleBorderAnimator
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.RecentsOrientedState
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.TaskCornerRadius
import com.android.quickstep.util.TaskRemovedDuringLaunchListener
import com.android.quickstep.util.isExternalDisplay
import com.android.quickstep.util.safeDisplayId
import com.android.quickstep.views.IconAppChipView.AppChipStatus
import com.android.quickstep.views.OverviewActionsView.DISABLED_NO_THUMBNAIL
import com.android.quickstep.views.OverviewActionsView.DISABLED_ROTATED
import com.android.quickstep.views.RecentsView.UNBOUND_TASK_VIEW_ID
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import com.android.wm.shell.shared.split.SplitScreenConstants
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** A task in the Recents view. */
open class TaskView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    focusBorderAnimator: BorderAnimator? = null,
    hoverBorderAnimator: BorderAnimator? = null,
    val type: TaskViewType = TaskViewType.SINGLE,
    protected val thumbnailFullscreenParams: FullscreenDrawParams = FullscreenDrawParams(context),
) : FrameLayout(context, attrs), ViewPool.Reusable {
    var groupTask: GroupTask? = null

    val taskIds: IntArray
        /** Returns a copy of integer array containing taskIds of all tasks in the TaskView. */
        get() = taskContainers.map { it.task.key.id }.toIntArray()

    val taskIdSet: Set<Int>
        /** Returns a copy of integer array containing taskIds of all tasks in the TaskView. */
        get() = taskContainers.map { it.task.key.id }.toSet()

    val snapshotViews: Array<View>
        get() = taskContainers.map { it.snapshotView }.toTypedArray()

    val taskContentViews: Array<TaskContentView>
        get() = taskContainers.map { it.taskContentView }.toTypedArray()

    val isGridTask: Boolean
        /** Returns whether the task is part of overview grid and not being focused. */
        get() = container.deviceProfile.deviceProperties.isLargeScreen && !isLargeTile

    val isRunningTask: Boolean
        get() = this === recentsView?.runningTaskView

    private val isSelectedTask: Boolean
        get() = this === recentsView?.selectedTaskView

    open val displayId: Int
        get() = taskContainers.firstOrNull()?.task.safeDisplayId

    val isExternalDisplay: Boolean
        get() = displayId.isExternalDisplay

    val isLargeTile: Boolean
        get() = type == TaskViewType.DESKTOP

    val recentsView: RecentsView<*, *>?
        get() = parent as? RecentsView<*, *>

    val pagedOrientationHandler: RecentsPagedOrientationHandler
        get() = orientedState.orientationHandler

    val firstTaskContainer: TaskContainer?
        get() = taskContainers.firstOrNull()

    val firstTask: Task?
        /** Returns the first task bound to this TaskView. */
        get() = firstTaskContainer?.task

    val isOnGridBottomRow: Boolean
        get() = recentsView?.isOnGridBottomRow(this) == true

    /**
     * A [TaskViewItemInfo] of this TaskView. The [firstTaskContainer] will be used to get some
     * specific information like user, title etc of the Task. However, these task specific
     * information will be skipped if the TaskView has no [taskContainers]. Note, please use
     * [TaskContainer.itemInfo] for [TaskViewItemInfo] on a specific [TaskContainer].
     */
    val itemInfo: TaskViewItemInfo
        get() = TaskViewItemInfo(this, firstTaskContainer)

    protected val container: RecentsViewContainer by lazy {
        RecentsViewContainer.containerFromContext(context)
    }
    protected val lastTouchDownPosition = PointF()

    // Derived view properties
    protected val persistentScale: Float
        /**
         * Returns multiplication of scale that is persistent (e.g. fullscreen and grid), and does
         * not change according to a temporary state.
         */
        get() = Utilities.mapRange(gridProgress, nonGridScale, 1f)

    protected val persistentTranslationX: Float
        /**
         * Returns addition of translationX that is persistent (e.g. fullscreen and grid), and does
         * not change according to a temporary state (e.g. task offset).
         */
        get() = (getNonGridTrans(nonGridTranslationX) + getGridTrans(this.gridTranslationX))

    val persistentTranslationY: Float
        /**
         * Returns addition of translationY that is persistent (e.g. fullscreen and grid), and does
         * not change according to a temporary state (e.g. task offset).
         */
        get() = boxTranslationY + getGridTrans(gridTranslationY)

    protected val primarySplitTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getPrimaryValue(
                SPLIT_SELECT_TRANSLATION_X,
                SPLIT_SELECT_TRANSLATION_Y,
            )

    protected val secondarySplitTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(
                SPLIT_SELECT_TRANSLATION_X,
                SPLIT_SELECT_TRANSLATION_Y,
            )

    val primaryDismissTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getPrimaryValue(DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y)

    val secondaryDismissTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y)

    protected val primaryTaskOffsetTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getPrimaryValue(
                TASK_OFFSET_TRANSLATION_X,
                TASK_OFFSET_TRANSLATION_Y,
            )

    protected val secondaryTaskOffsetTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(
                TASK_OFFSET_TRANSLATION_X,
                TASK_OFFSET_TRANSLATION_Y,
            )

    protected val taskResistanceTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(
                TASK_RESISTANCE_TRANSLATION_X,
                TASK_RESISTANCE_TRANSLATION_Y,
            )

    private val tempCoordinates = FloatArray(2)
    private val borderWidthPx: Int by lazy {
        context.resources.getDimensionPixelSize(R.dimen.task_hover_focus_border_width)
    }
    private val borderOffsetPx: Int by lazy {
        context.resources.getDimensionPixelSize(R.dimen.task_hover_focus_offset_size)
    }
    private val focusBorderAnimator: BorderAnimator =
        focusBorderAnimator
            ?: createSimpleBorderAnimator(
                TaskCornerRadius.get(context).toInt() + borderOffsetPx,
                borderWidthPx,
                {
                    getThumbnailBounds(it)
                    it.inset(-borderOffsetPx, -borderOffsetPx)
                },
                this,
                context
                    .obtainStyledAttributes(attrs, R.styleable.TaskView, defStyleAttr, defStyleRes)
                    .getColor(
                        R.styleable.TaskView_focusBorderColor,
                        BorderAnimator.DEFAULT_BORDER_COLOR,
                    ),
            )

    private val hoverBorderAnimator: BorderAnimator =
        hoverBorderAnimator
            ?: createSimpleBorderAnimator(
                TaskCornerRadius.get(context).toInt() + borderOffsetPx,
                borderWidthPx,
                {
                    getThumbnailBounds(it)
                    it.inset(-borderOffsetPx, -borderOffsetPx)
                },
                this,
                context
                    .obtainStyledAttributes(attrs, R.styleable.TaskView, defStyleAttr, defStyleRes)
                    .getColor(
                        R.styleable.TaskView_hoverBorderColor,
                        BorderAnimator.DEFAULT_BORDER_COLOR,
                    ),
            )

    private val rootViewDisplayId: Int
        get() = rootView.display?.displayId ?: Display.DEFAULT_DISPLAY

    /** Returns a list of all TaskContainers in the TaskView. */
    lateinit var taskContainers: List<TaskContainer>
        protected set

    lateinit var orientedState: RecentsOrientedState
    lateinit var taskOverlayFactory: TaskOverlayFactory

    var taskViewId = UNBOUND_TASK_VIEW_ID
    var isEndQuickSwitchCuj = false
    var isBeingDraggedForDismissal = false
    var isBeingDismissed: Boolean = false

    private val systemGestureExclusionRectList = listOf(Rect()) // We only need 1 exclusion Rect

    var sysUiStatusNavFlags: Int = 0
        private set

    // Various animation progress variables.
    // progress: 0 = show icon and no insets; 1 = don't show icon and show full insets.
    protected var fullscreenProgress = 0f
        set(value) {
            if (value == field) return
            field = Utilities.boundToRange(value, 0f, 1f)
            onFullscreenProgressChanged(field)
        }

    // gridProgress 0 = carousel; 1 = 2 row grid.
    protected var gridProgress = 0f
        set(value) {
            field = value
            onGridProgressChanged()
        }

    /**
     * The modalness of this view is how it should be displayed when it is shown on its own in the
     * modal state of overview. 0 being in context with other tasks, 1 being shown on its own.
     */
    protected var modalness = 0f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            onModalnessUpdated(field)
        }

    var modalPivot: PointF? = null
        set(value) {
            field = value
            updatePivots()
        }

    var splitSplashAlpha = 0f
        set(value) {
            field = value
            applyThumbnailSplashAlpha()
        }

    protected var taskThumbnailSplashAlpha = 0f
        set(value) {
            field = value
            applyThumbnailSplashAlpha()
        }

    protected var nonGridScale = 1f
        set(value) {
            field = value
            applyScale()
        }

    private var dismissScale = 1f
        set(value) {
            field = value
            applyScale()
        }

    var modalScale = 1f
        set(value) {
            field = value
            applyScale()
        }

    var animateToIconScale = 1f
        set(value) {
            field = value
            applyScale()
        }

    private var dismissTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private var dismissTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private var taskOffsetTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private var taskOffsetTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private var taskResistanceTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private var taskResistanceTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    // The following translation variables should only be used in the same orientation as Launcher.
    private var boxTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    // The following grid translations scales with mGridProgress.
    var gridTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    var gridTranslationY = 0f
        protected set(value) {
            field = value
            applyTranslationY()
        }

    // The following grid translation is used to animate closing the gap between grid and clear all.
    private var gridEndTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    // Applied as a complement to gridTranslation, for adjusting the carousel overview and quick
    // switch.
    var nonGridTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    // Used when in SplitScreenSelectState
    private var splitSelectTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private var splitSelectTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    var animateToIconTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    var animateToIconTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private val taskViewAlpha = MultiValueAlpha(this, Alpha.entries.size)
    protected var stableAlpha by MultiPropertyDelegate(taskViewAlpha, Alpha.Stable)
    var attachAlpha by MultiPropertyDelegate(taskViewAlpha, Alpha.Attach)
    var splitAlpha by MultiPropertyDelegate(taskViewAlpha, Alpha.Split)
    private var modalAlpha by MultiPropertyDelegate(taskViewAlpha, Alpha.Modal)
    var animateToIconAlpha by MultiPropertyDelegate(taskViewAlpha, Alpha.AnimateToIcon)

    var shouldShowScreenshot = false
        get() = !isRunningTask || field
        set(value) {
            if (field == value) return
            field = value
        }

    /** Enable or disable showing border on hover and focus change */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    var borderEnabled = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            // Set the animation correctly in case it misses the hover/focus event during state
            // transition
            hoverBorderAnimator.setBorderVisibility(visible = field && isHovered, animated = true)
            focusBorderAnimator.setBorderVisibility(visible = field && isFocused, animated = true)
        }

    /**
     * Used to cache the hover border state so we don't repeatedly call the border animator with
     * every hover event when the user hasn't crossed the threshold of the [thumbnailBounds].
     */
    private var hoverBorderVisible = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            Log.d(
                TAG,
                "${taskIds.contentToString()} - setting border animator visibility to: $field",
            )
            hoverBorderAnimator.setBorderVisibility(visible = field, animated = true)
        }

    // Used to cache thumbnail bounds to avoid recalculating on every hover move.
    private var thumbnailBounds = Rect()

    // Progress variable indicating if the TaskView is in a settled state:
    // 0 = The TaskView is in a transitioning state e.g. during gesture, in quickswitch carousel,
    // becoming focus task etc.
    // 1 = The TaskView is settled and no longer transitioning
    private var settledProgress = 1f
        set(value) {
            if (value == field) return
            field = value
            onSettledProgressUpdated(field)
        }

    private val settledProgressPropertyFactory =
        MultiPropertyFactory(
            this,
            SETTLED_PROGRESS,
            SettledProgress.entries.size,
            { x: Float, y: Float -> x * y },
            1f,
        )
    private var settledProgressFullscreen by
        MultiPropertyDelegate(settledProgressPropertyFactory, SettledProgress.Fullscreen)
    private var settledProgressGesture by
        MultiPropertyDelegate(settledProgressPropertyFactory, SettledProgress.Gesture)
    private var settledProgressDismiss by
        MultiPropertyDelegate(settledProgressPropertyFactory, SettledProgress.Dismiss)

    @Inject lateinit var viewModel: TaskViewModel
    @Inject
    @LightweightBackground(LightweightBackgroundPriority.UI)
    lateinit var lightweightBackgroundDispatcher: CoroutineDispatcher
    @Inject @Ui lateinit var mainDispatcher: CoroutineDispatcher
    @Inject lateinit var coroutineScope: CoroutineScope
    private val coroutineJobs = mutableListOf<Job>()
    private var taskDismissButton: FrameLayout? = null
    private var taskDismissButtonAnimator: ObjectAnimator? = null
    private var taskDismissButtonEnabled: Boolean = false

    /**
     * Returns a sequence of [Pair]s, where each pair contains a [IconAppChipView] and its
     * corresponding [TransformingTouchDelegate].
     */
    open fun getTaskIcons(): Sequence<Pair<IconAppChipView, TransformingTouchDelegate>> =
        taskContainers.asSequence().map { it.iconView to it.iconTouchDelegate }

    private var iconFadeInOnGestureCompleteAnimator: ObjectAnimator? = null
    private var isClickableAsLiveTile = true

    init {
        setOnClickListener { _ -> onClick() }

        setWillNotDraw(false)
    }

    override fun onConfigurationChanged(p0: Configuration?) {
        super.onConfigurationChanged(p0)
        thumbnailFullscreenParams.updateCornerRadius(context)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (borderEnabled) {
            focusBorderAnimator.setBorderVisibility(gainFocus, /* animated= */ true)
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (borderEnabled) {
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    getThumbnailBounds(thumbnailBounds)
                    hoverBorderVisible = event.isWithinThumbnailBounds()
                    showTaskDismissButton()
                }
                MotionEvent.ACTION_HOVER_MOVE ->
                    hoverBorderVisible = event.isWithinThumbnailBounds()
                MotionEvent.ACTION_HOVER_EXIT -> {
                    hoverBorderVisible = false
                    if (taskDismissButton?.isHovered == false) {
                        hideTaskDismissButton()
                    }
                }
                else -> {}
            }
        }
        return super.onHoverEvent(event)
    }

    open fun initialiseInjectables(component: ActivityContextComponent) {
        component.inject(this)
    }

    private fun showTaskDismissButton() {
        if (!taskDismissButtonEnabled) return

        val taskDismissButton = taskDismissButton ?: return

        if (taskDismissButtonAnimator?.isRunning == true) {
            taskDismissButtonAnimator?.cancel()
        }

        if (taskDismissButton.alpha == 1f) {
            return
        }

        taskDismissButtonAnimator =
            ObjectAnimator.ofFloat(taskDismissButton, View.ALPHA, 1f).apply {
                duration = DEFAULT_APPEARANCE_ANIMATION_DURATION_MS
                interpolator = DEFAULT_INTERPOLATOR
                doOnCancel { taskDismissButton.alpha = 0f }
                start()
            }
    }

    private fun hideTaskDismissButton() {
        if (!taskDismissButtonEnabled) return

        val taskDismissButton = taskDismissButton ?: return

        if (taskDismissButtonAnimator?.isRunning == true) {
            taskDismissButtonAnimator?.cancel()
        }

        if (taskDismissButton.alpha == 0f) {
            return
        }

        taskDismissButtonAnimator =
            ObjectAnimator.ofFloat(taskDismissButton, View.ALPHA, 0f).apply {
                duration = DEFAULT_DISAPPEARANCE_ANIMATION_DURATION_MS
                interpolator = DEFAULT_INTERPOLATOR
                doOnCancel { taskDismissButton.alpha = 0f }
                start()
            }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val recentsView = recentsView ?: return false
        val splitSelectStateController = recentsView.splitSelectController
        // Disable taps for split selection animation unless we have a task not being selected
        if (
            splitSelectStateController.isSplitSelectActive &&
                taskContainers.none { it.task.key.id != splitSelectStateController.initialTaskId }
        ) {
            return false
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            with(lastTouchDownPosition) {
                x = ev.x
                y = ev.y
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun draw(canvas: Canvas) {
        // Draw border first so any child views outside of the thumbnail bounds are drawn above it.
        focusBorderAnimator.drawBorder(canvas)
        hoverBorderAnimator.drawBorder(canvas)
        super.draw(canvas)
    }

    override fun setLayoutDirection(layoutDirection: Int) {
        super.setLayoutDirection(layoutDirection)
        val deviceLayoutDirection = resources.configuration.layoutDirection
        getTaskIcons().forEach { (icon, _) -> icon.layoutDirection = deviceLayoutDirection }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updatePivots()
        systemGestureExclusionRectList[0].apply {
            this.right = width
            this.bottom = height
        }
        getThumbnailBounds(thumbnailBounds)
    }

    private fun updatePivots() {
        val modalPivot = modalPivot
        if (modalPivot != null) {
            pivotX = modalPivot.x
            pivotY = modalPivot.y
        } else {
            if (container.deviceProfile.deviceProperties.isLargeScreen) {
                pivotX =
                    (if (layoutDirection == LAYOUT_DIRECTION_RTL) 0 else right - left).toFloat()
                pivotY = 0f
            } else {
                pivotX = (right - left) * 0.5f
                pivotY = height * 0.5f
            }
        }
    }

    override fun onRecycle() {
        isBeingDraggedForDismissal = false
        isBeingDismissed = false
        resetPersistentViewTransforms()

        groupTask = null
        viewModel.unbind()
        attachAlpha = 1f
        splitAlpha = 1f
        splitSplashAlpha = 0f
        modalAlpha = 1f
        modalScale = 1f
        modalPivot = null
        taskThumbnailSplashAlpha = 0f
        borderEnabled = false
        hoverBorderVisible = false
        taskViewId = UNBOUND_TASK_VIEW_ID
        // TODO(b/390583187): Clean the components UI State when TaskView is recycled.
        taskContainers.forEach { it.destroy() }

        getTaskIcons().forEach { (icon, _) -> icon.reset() }
        recycleTaskDismissButton()
    }

    private fun recycleTaskDismissButton() {
        if (taskDismissButtonAnimator?.isRunning == true) {
            taskDismissButtonAnimator?.cancel()
        }
        taskDismissButtonEnabled = false
        taskDismissButton?.setOnClickListener(null)
        taskDismissButton?.alpha = 0f
    }

    // TODO: Clip-out the icon region from the thumbnail, since they are overlapping.
    override fun hasOverlappingRendering() = false

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        with(info) {
            // Only make actions available if the app icon menu is visible to the user.
            // When modalness is >0, the user is in select mode and the icon menu is hidden.
            // When split selection is active, they should only be able to select the app and not
            // take any other action.
            val shouldPopulateAccessibilityMenu =
                modalness == 0f && recentsView?.isSplitSelectionActive == false
            if (shouldPopulateAccessibilityMenu) {
                taskContainers.forEach {
                    TraceHelper.allowIpcs("TV.a11yInfo") {
                        taskOverlayFactory.getEnabledShortcuts(this@TaskView, it).forEach { shortcut
                            ->
                            addAction(shortcut.createAccessibilityAction(context))
                        }
                    }
                }

                // Add DWB accessibility action at the end of the list
                taskContainers.forEach {
                    if (enableRefactorDigitalWellbeingToast()) {
                        it.taskContentView.getSupportedAccessibilityActions().forEach(::addAction)
                    } else {
                        it.digitalWellBeingToast?.getDWBAccessibilityAction()?.let(::addAction)
                    }
                }
            }

            recentsView?.let {
                collectionItemInfo =
                    AccessibilityNodeInfo.CollectionItemInfo(
                        0,
                        1,
                        // We only care about TaskView's for the `CollectionInfo` that Talkback uses
                        // to read out.
                        it.taskViews.reversed().indexOf(this@TaskView),
                        1,
                        false,
                    )
            }
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        // TODO(b/343708271): Add support for multiple tasks per action.
        taskContainers.forEach {
            if (enableRefactorDigitalWellbeingToast()) {
                if (it.taskContentView.handleAccessibilityAction(action)) {
                    return true
                }
            } else {
                if (it.digitalWellBeingToast?.handleAccessibilityAction(action) == true) {
                    return true
                }
            }

            taskOverlayFactory.getEnabledShortcuts(this, it).forEach { shortcut ->
                if (shortcut.hasHandlerForAction(action)) {
                    shortcut.onClick(this)
                    return true
                }
            }
        }

        return super.performAccessibilityAction(action, arguments)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        inflateViewStubs()
        taskDismissButton = findViewById(R.id.task_dismiss_button)
    }

    fun onIntersectScreenEdgeChanged(intersectsScreenEdge: Boolean) {
        systemGestureExclusionRects =
            if (intersectsScreenEdge) systemGestureExclusionRectList else emptyList()
    }

    protected open fun inflateViewStubs() {
        findViewById<ViewStub>(R.id.task_content_view)
            ?.apply {
                inflatedId = R.id.task_content_view
                layoutResource = R.layout.task_content_view
            }
            ?.inflate()

        if (!enableRefactorDigitalWellbeingToast()) {
            findViewById<ViewStub>(R.id.digital_wellbeing_toast)
                ?.apply { layoutResource = R.layout.digital_wellbeing_toast }
                ?.inflate()
        }
    }

    override fun onAttachedToWindow() =
        traceSection("TaskView.onAttachedToWindow") {
            super.onAttachedToWindow()
            // TaskView binds the ViewModel during onBind, and unbinds it in onRecycle. So it
            // should start listening here.
            // TV Lifecycle: onBind -> onAttachedToWindow -> onDetachFromWindow -> onRecycle
            coroutineJobs +=
                coroutineScope.launch(mainDispatcher) {
                    viewModel.state.collectLatest(::updateTaskViewState)
                }
        }

    private fun updateTaskViewState(state: TaskTileUiState) =
        traceSection("TaskView.updateTaskViewState") {
            sysUiStatusNavFlags = state.sysUiStatusNavFlags

            // Updating containers
            val mapOfTasks = state.tasks.associateBy { it.taskId }
            taskContainers.forEach { container ->
                val taskId = container.task.key.id
                val containerState = mapOfTasks[taskId]
                val shouldHaveHeader = (type == TaskViewType.DESKTOP)
                val shouldShowAppTimer =
                    (type == TaskViewType.SINGLE || type == TaskViewType.GROUPED)
                container.setState(
                    state = containerState,
                    hasHeader = shouldHaveHeader,
                    canShowAppTimer = shouldShowAppTimer,
                    clickCloseListener =
                        if (shouldHaveHeader) {
                            {
                                // Update the layout UI to remove this task from the layout grid,
                                // and remove the task from ActivityManager afterwards.
                                recentsView?.dismissTask(taskId, /* removeTask= */ true)
                            }
                        } else {
                            null
                        },
                )
                updateThumbnailValidity(container)
                val thumbnailPosition =
                    updateThumbnailMatrix(
                        container = container,
                        width = container.snapshotView.width,
                        height = container.snapshotView.height,
                    )
                container.setOverlayEnabled(state.taskOverlayEnabled, thumbnailPosition)
                if (state.isCentralTask) {
                    this.container.actionsView.let {
                        it.updateDisabledFlags(DISABLED_ROTATED, thumbnailPosition.isRotated)
                        it.updateDisabledFlags(
                            DISABLED_NO_THUMBNAIL,
                            state.tasks.any { taskData ->
                                (taskData as? TaskData.Data)?.thumbnailData?.thumbnail == null
                            },
                        )
                    }
                }

                setIconState(container, containerState)
                if (
                    containerState is TaskData &&
                        container.digitalWellBeingToast?.isDestroyed == false &&
                        container.task.titleDescription != null
                ) {
                    container.digitalWellBeingToast.initialize()
                }

                val dismissTaskViewOnClick: (View) -> Unit = {
                    recentsView?.dismissTaskView(container.taskView, /* removeTask= */ true)
                }
                setTaskDismissButtonState(
                    TaskUiStateMapper.toTaskDismissButtonState(
                        (type == TaskViewType.DESKTOP),
                        dismissTaskViewOnClick,
                    )
                )
            }
        }

    private fun setTaskDismissButtonState(state: TaskDismissButtonState) {
        when (state) {
            is TaskDismissButtonState.Enabled -> {
                taskDismissButtonEnabled = true
                taskDismissButton?.setOnClickListener(state.clickCloseListener)
            }
            is TaskDismissButtonState.Disabled -> {
                taskDismissButtonEnabled = false
                taskDismissButton?.setOnClickListener(null)
            }
        }
    }

    private fun updateThumbnailValidity(container: TaskContainer) {
        container.isThumbnailValid =
            viewModel.isThumbnailValid(
                thumbnail = container.thumbnailData,
                width = container.snapshotView.width,
                height = container.snapshotView.height,
                splitBounds = (this as? GroupedTaskView)?.splitBoundsConfig,
                stagePosition = container.stagePosition,
            )
        applyThumbnailSplashAlpha()
    }

    /**
     * Updates the thumbnail's transformation matrix and rotation state within a TaskContainer.
     *
     * This function is called to reposition the thumbnail in the following scenarios:
     * - When the TTV's size changes (onSizeChanged), and it's displaying a SnapshotSplash.
     * - When drawing a snapshot (drawSnapshot).
     *
     * @param container The TaskContainer holding the thumbnail to be updated.
     * @param width The desired width of the thumbnail's container.
     * @param height The desired height of the thumbnail's container.
     */
    private fun updateThumbnailMatrix(
        container: TaskContainer,
        width: Int,
        height: Int,
    ): ThumbnailPosition =
        traceSection("TaskView.updateThumbnailMatrix") {
            // TODO: b/428764855: Refactor `TaskViewModel` to include Split data.
            val splitPosition =
                when (container.stagePosition) {
                    SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT ->
                        SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
                    SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT ->
                        SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
                    else -> SplitScreenConstants.SPLIT_POSITION_UNDEFINED
                }
            val thumbnailPosition =
                viewModel.getThumbnailPosition(
                    container.thumbnailData,
                    width,
                    height,
                    isLayoutRtl,
                    (this as? GroupedTaskView)?.splitBoundsConfig,
                    splitPosition,
                    context.resources.displayMetrics.densityDpi,
                )
            container.updateThumbnailMatrix(thumbnailPosition.matrix)
            return thumbnailPosition
        }

    override fun onDetachedFromWindow() =
        traceSection("TaskView.onDetachedFromWindow") {
            super.onDetachedFromWindow()
            cancelJobs()
        }

    fun cancelJobs() {
        // The jobs are being cancelled in the background thread. So we make a copy of the
        // list to prevent cleaning a new job that might be added to this list during
        // onAttach or another moment in the lifecycle.
        val coroutineJobsToCancel = coroutineJobs.toList()
        coroutineJobs.clear()
        if (coroutineJobsToCancel.isEmpty()) return

        // TODO(b/391842220): This should ideally be handled in the completion block of the
        //  jobs above to be cancelled.
        taskContainers.forEach {
            it.setState(
                state = null,
                hasHeader = false,
                canShowAppTimer = false,
                clickCloseListener = null,
            )
            // Do not set icon to null if we are actively in split selection. The task
            // appears to have been offloaded as we remove it during split, but we still
            // need the icon to show over the split task.
            if (recentsView?.isSplitSelectionActive == false) {
                setIconState(it, null)
            }
        }

        coroutineScope.launch(lightweightBackgroundDispatcher) {
            traceSection("TaskView.onDetachedFromWindow.cancellingJobs") {
                coroutineJobsToCancel.forEach { it.cancel("TaskView detaching from window") }
            }
        }
    }

    /** Updates this task view to the given {@param task}. */
    open fun bind(
        singleTask: SingleTask,
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory,
    ) {
        this.groupTask = singleTask
        taskContainers =
            listOf(
                createTaskContainer(
                    singleTask.task,
                    R.id.task_content_view,
                    R.id.snapshot,
                    R.id.icon,
                    R.id.digital_wellbeing_toast,
                    STAGE_POSITION_UNDEFINED,
                    taskOverlayFactory,
                )
            )
        onBind(orientedState, taskOverlayFactory)
    }

    protected open fun onBind(
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory,
    ) =
        traceSection("TaskView.onBind") {
            this.orientedState = orientedState // Needed for dependencies
            this.taskOverlayFactory = taskOverlayFactory

            traceSection("TaskView.onBind.bindViewModel") {
                Log.d(TAG, "onBind $context ${orientedState.containerInterface}")
                viewModel.bind(type, *taskIds)
            }

            taskContainers.forEach { container ->
                container.bind()
                container.taskContentView.apply {
                    cornerRadius = thumbnailFullscreenParams.currentCornerRadius
                    taskCornerRadius = thumbnailFullscreenParams.taskCornerRadius
                    doOnSizeChange { width, height ->
                        updateThumbnailValidity(container)
                        val thumbnailPosition = updateThumbnailMatrix(container, width, height)
                        container.refreshOverlay(thumbnailPosition)
                    }
                }
            }
            setOrientationState(orientedState)
        }

    private fun applyThumbnailSplashAlpha() {
        val alpha = getSplashAlphaProgress()
        taskContainers.forEach { it.updateThumbnailSplashProgress(alpha) }
    }

    private fun getSplashAlphaProgress(): Float =
        when {
            splitSplashAlpha > 0f -> splitSplashAlpha
            shouldShowSplash() -> taskThumbnailSplashAlpha
            else -> 0f
        }

    internal fun shouldShowSplash(): Boolean = taskContainers.any { !it.isThumbnailValid }

    protected fun createTaskContainer(
        task: Task,
        @IdRes taskContentViewId: Int,
        @IdRes thumbnailViewId: Int,
        @IdRes iconViewId: Int,
        @IdRes digitalWellbeingBannerId: Int,
        @StagePosition stagePosition: Int,
        taskOverlayFactory: TaskOverlayFactory,
    ): TaskContainer =
        traceSection("TaskView.createTaskContainer") {
            val iconView = findViewById<IconAppChipView>(iconViewId)
            val taskContentView = findViewById<TaskContentView>(taskContentViewId)
            val snapshotView = taskContentView.findViewById<TaskThumbnailView>(thumbnailViewId)!!

            val digitalWellBeingToast: DigitalWellBeingToast? =
                if (enableRefactorDigitalWellbeingToast()) {
                    null
                } else {
                    findViewById(digitalWellbeingBannerId)!!
                }
            return TaskContainer(
                this,
                task,
                taskContentView,
                snapshotView,
                iconView,
                TransformingTouchDelegate(iconView),
                stagePosition,
                digitalWellBeingToast,
                taskOverlayFactory,
            )
        }

    fun containsMultipleTasks() = taskContainers.size > 1

    /**
     * Returns the TaskContainer corresponding to a given taskId, or null if the TaskView does not
     * contain a Task with that ID.
     */
    fun getTaskContainerById(taskId: Int) = taskContainers.firstOrNull { it.task.key.id == taskId }

    /** Check if given `taskId` is tracked in this view */
    fun containsTaskId(taskId: Int) = getTaskContainerById(taskId) != null

    open fun setOrientationState(orientationState: RecentsOrientedState) =
        traceSection("TaskView.setOrientationState") {
            this.orientedState = orientationState
            getTaskIcons().forEach { (icon, _) -> icon.setIconOrientation(orientationState) }
            setThumbnailOrientation(orientationState)
        }

    protected open fun setThumbnailOrientation(orientationState: RecentsOrientedState) {
        taskContainers.forEach {
            it.overlay.updateOrientationState(orientationState)
            it.digitalWellBeingToast?.initialize()
            it.onTaskViewDisplayConfigChanged()
        }
    }

    /**
     * Updates TaskView scaling and translation required to support variable width if enabled, while
     * ensuring TaskView fits into screen in fullscreen.
     */
    open fun updateTaskSize(lastComputedTaskSize: Rect, lastComputedGridTaskSize: Rect) {
        val taskWidth = lastComputedTaskSize.width()
        val taskHeight = lastComputedTaskSize.height()
        val nonGridScale: Float
        val boxTranslationY: Float
        val expectedWidth: Int
        val expectedHeight: Int
        if (container.deviceProfile.deviceProperties.isLargeScreen) {
            val boxWidth: Int
            val boxHeight: Int

            // Focused task and Desktop tasks should use focusTaskRatio that is associated
            // with the original orientation of the focused task.
            if (isLargeTile) {
                boxWidth = taskWidth
                boxHeight = taskHeight
            } else {
                // Otherwise task is in grid, and should use lastComputedGridTaskSize.
                boxWidth = lastComputedGridTaskSize.width()
                boxHeight = lastComputedGridTaskSize.height()
            }

            // Bound width/height to the box size.
            expectedWidth = boxWidth
            expectedHeight = boxHeight

            // Scale to to fit task Rect.
            nonGridScale = taskWidth / boxWidth.toFloat()

            // Align to top of task Rect.
            boxTranslationY = (expectedHeight - taskHeight) / 2.0f
        } else {
            nonGridScale = 1f
            boxTranslationY = 0f
            expectedWidth = taskWidth
            expectedHeight = taskHeight
        }
        this.nonGridScale = nonGridScale
        this.boxTranslationY = boxTranslationY
        updateLayoutParams<ViewGroup.LayoutParams> {
            width = expectedWidth
            height = expectedHeight
        }
        updateThumbnailSize()
    }

    protected open fun updateThumbnailSize() {
        taskContainers.forEach { it.digitalWellBeingToast?.setupLayout() }
    }

    /** Returns the thumbnail's bounds, optionally relative to the screen. */
    @JvmOverloads
    open fun getThumbnailBounds(bounds: Rect, relativeToDragLayer: Boolean = false) {
        bounds.setEmpty()
        taskContainers.forEach {
            val thumbnailBounds = Rect()
            if (relativeToDragLayer) {
                container.dragLayer.getDescendantRectRelativeToSelf(
                    it.taskContentView,
                    thumbnailBounds,
                )
            } else {
                thumbnailBounds.set(it.taskContentView)
            }
            bounds.union(thumbnailBounds)
        }
    }

    protected open fun setIconState(container: TaskContainer, state: TaskData?) =
        traceSection("TaskView.setIconState") {
            if (state is TaskData.Data) {
                setIcon(container.iconView, state.icon)
                container.iconView.setText(state.title)
            } else {
                setIcon(container.iconView, null)
                container.iconView.setText(null)
            }
        }

    protected fun setIcon(iconView: IconAppChipView, icon: Drawable?) {
        with(iconView) {
            if (icon != null) {
                setDrawable(icon)
                setOnClickListener {
                    if (!confirmSecondSplitSelectApp()) {
                        showTaskMenu(this)
                    }
                }
                setOnLongClickListener {
                    requestDisallowInterceptTouchEvent(true)
                    showTaskMenu(this)
                }
            } else {
                setDrawable(null)
                setOnClickListener(null)
                setOnLongClickListener(null)
            }
        }
    }

    private fun onClick() {
        if (confirmSecondSplitSelectApp()) {
            Log.d(
                TAG,
                "${taskIds.contentToString()} - onClick - ignoring click: " +
                    "this task click selected the second app in split selection",
            )
            return
        }
        if (recentsView?.stateManager?.state?.isTaskViewInteractive != true) {
            Log.d(
                TAG,
                "${taskIds.contentToString()} - onClick - ignoring click: the state manager is " +
                    "not in an interactive state (state=${recentsView?.stateManager?.state})",
            )
            return
        }
        launchWithAnimation()
        container.statsLogManager
            .logger()
            .withItemInfo(itemInfo)
            .log(LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP)
    }

    /** Launch of the current task (both live and inactive tasks) with an animation. */
    fun launchWithAnimation(): RunnableList? {
        Log.d(
            TAG,
            "${taskIds.contentToString()} - launchWithAnimation - initiating launch, " +
                "partial trace: ${getTrimmedStackTrace("TaskView.launchWithAnimation")}",
        )
        return if (isRunningTask && recentsView?.remoteTargetHandles != null) {
                launchAsLiveTile(recentsView?.remoteTargetHandles!!)
            } else {
                launchAsStaticTile()
            }
            ?.also {
                InteractionJankMonitorWrapper.begin(
                    /* v = */ this,
                    CUJ_LAUNCHER_APP_LAUNCH_FROM_RECENTS,
                )
                it.add {
                    InteractionJankMonitorWrapper.end(CUJ_LAUNCHER_APP_LAUNCH_FROM_RECENTS)
                    Log.d(
                        TAG,
                        "${taskIds.contentToString()} - launchWithAnimation - launchCompleted",
                    )
                }
            }
    }

    private fun launchAsLiveTile(remoteTargetHandles: Array<RemoteTargetHandle>): RunnableList? {
        val recentsView = recentsView ?: return null
        if (!isClickableAsLiveTile) {
            Log.e(
                TAG,
                "launchAsLiveTile - TaskView is not clickable as a live tile; returning to home: ${taskIds.contentToString()}",
            )
            return null
        }
        isClickableAsLiveTile = false
        val targets =
            if (remoteTargetHandles.isNotEmpty()) {
                if (remoteTargetHandles.size == 1) {
                    remoteTargetHandles[0].transformParams.targetSet
                } else {
                    val apps =
                        remoteTargetHandles.flatMap {
                            it.transformParams.targetSet.apps.asIterable()
                        }
                    val wallpapers =
                        remoteTargetHandles.flatMap {
                            it.transformParams.targetSet.wallpapers.asIterable()
                        }
                    RemoteAnimationTargets(
                        apps.toTypedArray(),
                        wallpapers.toTypedArray(),
                        remoteTargetHandles[0].transformParams.targetSet.nonApps,
                        remoteTargetHandles[0].transformParams.targetSet.targetMode,
                    )
                }
            } else {
                null
            }
        if (targets == null) {
            // If the recents animation is cancelled somehow between the parent if block and
            // here, try to launch the task as a non live tile task.
            val runnableList = launchAsStaticTile()
            if (runnableList == null) {
                Log.e(
                    TAG,
                    "launchAsLiveTile - Recents animation cancelled and cannot launch task as non-live tile; returning to home: ${taskIds.contentToString()}",
                )
            }
            isClickableAsLiveTile = true
            return runnableList
        }
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "composeRecentsLaunchAnimator",
            taskIds.contentToString(),
        )
        val runnableList = RunnableList()
        with(AnimatorSet()) {
            TaskViewUtils.composeRecentsLaunchAnimator(
                this,
                this@TaskView,
                targets.apps,
                targets.wallpapers,
                targets.nonApps,
                true, /* launcherClosing */
                recentsView.stateManager,
                recentsView,
                recentsView.depthController,
                /* transitionInfo= */ null,
                /* appearedTaskId= */ INVALID_TASK_ID,
            )
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animator: Animator) {
                        if (taskContainers.any { it.task.key.displayId != rootViewDisplayId }) {
                            launchAsStaticTile()
                        }
                        isClickableAsLiveTile = true
                        runEndCallback()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        runEndCallback()
                    }

                    private fun runEndCallback() {
                        runnableList.executeAllAndDestroy()
                    }
                }
            )
            start()
        }
        Log.d(TAG, "launchAsLiveTile - composeRecentsLaunchAnimator: ${taskIds.contentToString()}")
        recentsView.onTaskLaunchedInLiveTileMode()
        return runnableList
    }

    /**
     * Starts the task associated with this view and animates the startup.
     *
     * @return CompletionStage to indicate the animation completion or null if the launch failed.
     */
    open fun launchAsStaticTile(): RunnableList? {
        val firstTaskContainer = firstTaskContainer ?: return null
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "startActivityFromRecentsAsync",
            taskIds.contentToString(),
        )
        val opts =
            container.getActivityLaunchOptions(this, null).apply {
                options.launchDisplayId = displayId
                // On external displays the default windowing mode is not fullscreen. To make
                // sure fullscreen apps remain fullscreen we set the windowing mode explicitly.
                options.launchWindowingMode = WINDOWING_MODE_FULLSCREEN
            }
        if (
            ActivityManagerWrapper.getInstance()
                .startActivityFromRecents(firstTaskContainer.task.key, opts.options)
        ) {
            Log.d(
                TAG,
                "launchAsStaticTile - startActivityFromRecents: ${taskIds.contentToString()}",
            )
            ActiveGestureLog.INSTANCE.trackEvent(
                ActiveGestureErrorDetector.GestureEvent.EXPECTING_TASK_APPEARED
            )
            val recentsView = recentsView ?: return null
            if (
                recentsView.runningTaskViewId != -1 &&
                    recentsView.mRecentsAnimationController != null
            ) {
                recentsView.onTaskLaunchedInLiveTileMode()

                // Return a fresh callback in the live tile case, so that it's not accidentally
                // triggered by QuickstepTransitionManager.AppLaunchAnimationRunner.
                return RunnableList().also { recentsView.addSideTaskLaunchCallback(it) }
            }
            // If the recents transition is running (ie. in live tile mode), then the start
            // of a new task will merge into the existing transition and it currently will
            // not be run independently, so we need to rely on the onTaskAppeared() call
            // for the new task to trigger the side launch callback to flush this runnable
            // list (which is usually flushed when the app launch animation finishes)
            recentsView.addSideTaskLaunchCallback(opts.onEndCallback)
            return opts.onEndCallback
        } else {
            notifyTaskLaunchFailed("launchAsStaticTile")
            return null
        }
    }

    /** Starts the task associated with this view without any animation */
    @JvmOverloads
    open fun launchWithoutAnimation(
        isQuickSwitch: Boolean = false,
        callback: (launched: Boolean) -> Unit,
    ) {
        val callbackWithLogging = { launchSuccess: Boolean ->
            Log.d(TAG, "launchWithoutAnimation - callback: launchSuccess: $launchSuccess")
            callback(launchSuccess)
        }
        val firstTaskContainer = firstTaskContainer ?: return
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "startActivityFromRecentsAsync",
            taskIds.contentToString(),
        )
        val failureListener = TaskRemovedDuringLaunchListener(context.applicationContext)
        if (isQuickSwitch) {
            // We only listen for failures to launch in quickswitch because the during this
            // gesture launcher is in the background state, vs other launches which are in
            // the actual overview state
            failureListener.register(container, firstTaskContainer.task.key.id) {
                notifyTaskLaunchFailed("launchWithoutAnimation")
                recentsView?.let {
                    // Disable animations for now, as it is an edge case and the app usually
                    // covers launcher and also any state transition animation also gets
                    // clobbered by QuickstepTransitionManager.createWallpaperOpenAnimations
                    // when launcher shows again
                    it.startHome(false /* animated */, null /* onHomeAnimationComplete */)
                    // LauncherTaskbarUIController depends on the launcher state when
                    // checking whether to handle resume, but that can come in before
                    // startHome() changes the state, so force-refresh here to ensure the
                    // taskbar is updated
                    it.mContainerInterface.getTaskbarInteractor()?.refreshResumedState()
                }
            }
        }
        // Indicate success once the system has indicated that the transition has started
        val opts =
            ActivityOptions.makeCustomTaskAnimation(
                    context,
                    0,
                    0,
                    Executors.MAIN_EXECUTOR.handler,
                    { callbackWithLogging(true) },
                ) {
                    Log.d(TAG, "launchWithoutAnimation: launch animation finished")
                    failureListener.onTransitionFinished()
                }
                .apply {
                    launchDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
                    if (isQuickSwitch) {
                        setFreezeRecentTasksReordering()
                    }
                    // TODO(b/331754864): Update this to use TV.shouldShowSplash
                    disableStartingWindow = firstTaskContainer.shouldShowSplashView
                }
        Executors.UI_HELPER_EXECUTOR.execute {
            Log.d(
                TAG,
                "launchWithoutAnimation(isQuickSwitch: $isQuickSwitch) - " +
                    "startActivityFromRecents: ${taskIds.contentToString()}",
            )
            if (
                !ActivityManagerWrapper.getInstance()
                    .startActivityFromRecents(firstTaskContainer.task.key, opts)
            ) {
                Log.d(TAG, "launchWithoutAnimation - task launch failed")
                // If the call to start activity failed, then post the result immediately,
                // otherwise, wait for the animation start callback from the activity options
                // above
                Executors.MAIN_EXECUTOR.post {
                    notifyTaskLaunchFailed("launchTask")
                    callbackWithLogging(false)
                }
            }
        }
    }

    private fun notifyTaskLaunchFailed(launchMethod: String) {
        val sb =
            StringBuilder("$launchMethod - Failed to launch task: ${taskIds.contentToString()}\n")
        taskContainers.forEach {
            sb.append("(task=${it.task.key.baseIntent} userId=${it.task.key.userId})\n")
        }
        Log.w(TAG, sb.toString())
        Toast.makeText(context, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
    }

    /**
     * Returns `true` if user is already in split select mode and this tap was to choose the second
     * app. `false` otherwise
     */
    protected open fun confirmSecondSplitSelectApp(): Boolean {
        val index = getLastSelectedChildTaskIndex()
        if (index >= taskContainers.size) {
            return false
        }
        val container = taskContainers[index]
        val recentsView = recentsView ?: return false
        return recentsView.confirmSplitSelect(
            this,
            container.task,
            container.iconView.getDrawable(),
            container.snapshotView,
            container.thumbnail,
            /* intent */ null,
            /* user */ null,
            container.itemInfo,
        )
    }

    /**
     * Returns the task index of the last selected child task (0 or 1). If we contain multiple tasks
     * and this TaskView is used as part of split selection, the selected child task index will be
     * that of the remaining task.
     */
    protected open fun getLastSelectedChildTaskIndex() = 0

    private fun showTaskMenu(iconView: IconAppChipView): Boolean {
        val recentsView = recentsView ?: return false
        if (!recentsView.canLaunchFullscreenTask()) {
            // Don't show menu when selecting second split screen app
            return true
        }
        if (
            !container.deviceProfile.deviceProperties.isLargeScreen && !recentsView.isClearAllHidden
        ) {
            recentsView.snapToPage(recentsView.indexOfChild(this))
            return false
        }
        val menuContainer = getContainerForIconView(iconView)

        container.statsLogManager
            .logger()
            .withItemInfo(menuContainer?.itemInfo ?: itemInfo)
            .log(LauncherEvent.LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS)
        return showTaskMenuWithContainer(menuContainer)
    }

    private fun closeTaskMenu(): Boolean {
        val floatingView: AbstractFloatingView? =
            AbstractFloatingView.getTopOpenViewWithType(
                container,
                AbstractFloatingView.TYPE_TASK_MENU,
            )
        if (floatingView?.isOpen == true) {
            floatingView.close(true)
            return true
        } else {
            return false
        }
    }

    private fun showTaskMenuWithContainer(menuContainer: TaskContainer?): Boolean {
        val recentsView = recentsView ?: return false
        // Disable hover on all TaskView's whilst menu is showing.
        recentsView.setTaskBorderEnabled(false)
        val iconView = menuContainer?.iconView ?: getTaskIcons().elementAt(0).first

        return if (iconView.status == AppChipStatus.Expanded) {
            closeTaskMenu()
        } else {
            val onShowAction = { recentsView.setTaskBorderEnabled(true) }
            val taskTarget =
                if (menuContainer != null) {
                    TaskMenuView.TaskTarget.FromTaskContainer(menuContainer)
                } else {
                    TaskMenuView.TaskTarget.FromTaskView(this)
                }
            TaskMenuView.showForTask(taskTarget, onShowAction)
        }
    }

    /**
     * Whether the taskview should take the touch event from parent. Events passed to children that
     * might require special handling.
     */
    open fun offerTouchToChildren(event: MotionEvent): Boolean {
        getTaskIcons().forEach { (icon, iconTouchDelegate) ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                computeAndSetIconTouchDelegate(icon, tempCoordinates, iconTouchDelegate)
                if (iconTouchDelegate.onTouchEvent(event)) {
                    return true
                }
            }
        }
        return false
    }

    private fun computeAndSetIconTouchDelegate(
        view: IconAppChipView,
        tempCenterCoordinates: FloatArray,
        transformingTouchDelegate: TransformingTouchDelegate,
    ) {
        val viewHalfWidth = view.width / 2f
        val viewHalfHeight = view.height / 2f
        Utilities.getDescendantCoordRelativeToAncestor(
            view,
            container.dragLayer,
            tempCenterCoordinates.apply {
                this[0] = viewHalfWidth
                this[1] = viewHalfHeight
            },
            false,
        )
        transformingTouchDelegate.setBounds(
            (tempCenterCoordinates[0] - viewHalfWidth).toInt(),
            (tempCenterCoordinates[1] - viewHalfHeight).toInt(),
            (tempCenterCoordinates[0] + viewHalfWidth).toInt(),
            (tempCenterCoordinates[1] + viewHalfHeight).toInt(),
        )
    }

    /**
     * Called to animate a smooth transition when going directly from an app into Overview (and vice
     * versa). Icons fade in, and DWB banners slide in with a "shift up" animation.
     */
    private fun onSettledProgressUpdated(settledProgress: Float) {
        getTaskIcons().forEach { (icon, _) -> icon.settledProgressAlpha = settledProgress }
        taskContainers.forEach {
            if (enableRefactorDigitalWellbeingToast()) {
                it.taskContentView.onParentAnimationProgress(settledProgress)
            } else {
                it.digitalWellBeingToast?.bannerOffsetPercentage = 1f - settledProgress
            }
        }
    }

    fun startIconFadeInOnGestureComplete() {
        iconFadeInOnGestureCompleteAnimator?.cancel()
        iconFadeInOnGestureCompleteAnimator =
            ObjectAnimator.ofFloat(this, SETTLED_PROGRESS_GESTURE, 1f).apply {
                duration = FADE_IN_ICON_DURATION
                interpolator = Interpolators.LINEAR
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            iconFadeInOnGestureCompleteAnimator = null
                        }
                    }
                )
                start()
            }
    }

    fun setIconVisibleForGesture(isVisible: Boolean) {
        iconFadeInOnGestureCompleteAnimator?.cancel()
        settledProgressGesture = if (isVisible) 1f else 0f
    }

    /** Set a color tint on the snapshot and supporting views. */
    open fun setColorTint(amount: Float, tintColor: Int) {
        // RecentsView's COLOR_TINT animates between 0 and 0.5f, we want to hide the app chip menu.
        val colorTintAlpha = Utilities.mapToRange(amount, 0f, 0.5f, 1f, 0f, Interpolators.LINEAR)
        getTaskIcons().forEach { (icon, _) -> icon.colorTintAlpha = colorTintAlpha }
        taskContainers.forEach {
            it.updateTintAmount(amount)
            it.digitalWellBeingToast?.setColorTint(tintColor, amount)
        }
    }

    /**
     * Sets visibility for the thumbnail and associated elements (DWB banners and action chips).
     * IconView is unaffected.
     *
     * @param taskId is only used when setting visibility to a non-[View.VISIBLE] value
     */
    open fun setThumbnailVisibility(isVisible: Boolean, taskId: Int) {
        taskContainers
            .filter { isVisible || it.task.key.id == taskId }
            .forEach { taskContainer ->
                sequenceOf(
                        taskContainer.taskContentView,
                        taskContainer.digitalWellBeingToast,
                        taskContainer.overlay.suggestView,
                    )
                    .filterNotNull()
                    .forEach {
                        // Prevent setting to INVISIBLE when already GONE to avoid onLayout pass.
                        // Use isInvisible to set visible/invisible. Using isVisible sets GONE if
                        // false.
                        if (it.isInvisible != !isVisible && !it.isGone) {
                            it.isInvisible = !isVisible
                        }
                    }
            }
    }

    protected fun getScrollAdjustment(gridEnabled: Boolean) =
        if (gridEnabled) gridTranslationX else nonGridTranslationX

    fun getOffsetAdjustment(gridEnabled: Boolean) = getScrollAdjustment(gridEnabled)

    fun getSizeAdjustment(fullscreenEnabled: Boolean) = if (fullscreenEnabled) nonGridScale else 1f

    private fun applyScale() {
        val scale =
            persistentScale *
                dismissScale *
                animateToIconScale *
                Utilities.mapRange(modalness, 1f, modalScale)
        scaleX = scale
        scaleY = scale
        updateFullscreenParams()
    }

    private fun applyTranslationX() {
        translationX =
            dismissTranslationX +
                taskOffsetTranslationX +
                taskResistanceTranslationX +
                splitSelectTranslationX +
                gridEndTranslationX +
                persistentTranslationX +
                animateToIconTranslationX
    }

    private fun applyTranslationY() {
        translationY =
            dismissTranslationY +
                taskOffsetTranslationY +
                taskResistanceTranslationY +
                splitSelectTranslationY +
                persistentTranslationY +
                animateToIconTranslationY
    }

    private fun onGridProgressChanged() {
        applyTranslationX()
        applyTranslationY()
        applyScale()
    }

    protected open fun onFullscreenProgressChanged(fullscreenProgress: Float) {
        taskContainers.forEach { it.overlay.setFullscreenProgress(fullscreenProgress) }
        updateSettledProgressFullscreen(fullscreenProgress)
        updateFullscreenParams()
    }

    protected fun updateSettledProgressFullscreen(fullscreenProgress: Float) {
        settledProgressFullscreen =
            SETTLED_PROGRESS_FAST_OUT_INTERPOLATOR.getInterpolation(1 - fullscreenProgress)
    }

    protected open fun updateFullscreenParams() {
        updateFullscreenParams(thumbnailFullscreenParams)
        taskContainers.forEach {
            it.taskContentView.cornerRadius = thumbnailFullscreenParams.currentCornerRadius
            it.overlay.setFullscreenParams(thumbnailFullscreenParams)
        }
    }

    protected fun updateFullscreenParams(fullscreenParams: FullscreenDrawParams) {
        recentsView?.let { fullscreenParams.setProgress(fullscreenProgress, it.scaleX, scaleX) }
    }

    private fun onModalnessUpdated(modalness: Float) {
        getTaskIcons().forEach { (icon, _) -> icon.modalAlpha = 1f - modalness }
        taskContainers.forEach {
            if (enableRefactorDigitalWellbeingToast()) {
                it.taskContentView.onParentAnimationProgress(1f - modalness)
            } else {
                it.digitalWellBeingToast?.bannerOffsetPercentage = modalness
            }
        }
        modalAlpha = if (isSelectedTask) 1f else (1f - modalness)
        applyScale()
    }

    fun resetPersistentViewTransforms() {
        nonGridTranslationX = 0f
        gridTranslationX = 0f
        gridTranslationY = 0f
        boxTranslationY = 0f
        taskContainers.forEach {
            it.snapshotView.translationX = 0f
            it.snapshotView.translationY = 0f
        }
        resetViewTransforms()
    }

    fun resetViewTransforms() {
        // Dismiss translation shouldn't reset if actively being dragged
        if (!isBeingDraggedForDismissal) {
            secondaryDismissTranslationProperty.setValue(this, 0f)
        }
        primaryDismissTranslationProperty.setValue(this, 0f)

        // fullscreenTranslation and accumulatedTranslation should not be reset, as
        // resetViewTransforms is called during QuickSwitch scrolling.
        taskOffsetTranslationX = 0f
        taskResistanceTranslationX = 0f
        splitSelectTranslationX = 0f
        gridEndTranslationX = 0f
        taskOffsetTranslationY = 0f
        taskResistanceTranslationY = 0f
        if (recentsView?.isSplitSelectionActive != true) {
            splitSelectTranslationY = 0f
        }
        dismissScale = 1f
        translationZ = 0f
        setIconVisibleForGesture(true)
        settledProgressDismiss = 1f
        setColorTint(0f, 0)
        animateToIconAlpha = 1f
        animateToIconScale = 1f
        animateToIconTranslationX = 0f
        animateToIconTranslationY = 0f
    }

    private fun getGridTrans(endTranslation: Float) =
        Utilities.mapRange(gridProgress, 0f, endTranslation)

    private fun getNonGridTrans(endTranslation: Float) =
        endTranslation - getGridTrans(endTranslation)

    private fun MotionEvent.isWithinThumbnailBounds(): Boolean {
        return thumbnailBounds.contains(x.toInt(), y.toInt())
    }

    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        (if (isLayoutRtl) taskContainers.reversed() else taskContainers).forEach {
            it.addChildForAccessibility(outChildren)
        }
    }

    protected open fun getContainerForIconView(appChip: IconAppChipView) =
        taskContainers.firstOrNull { it.iconView === appChip }

    override fun setAlpha(alpha: Float) {
        super.setAlpha(alpha)
        getTaskIcons().forEach { (icon, _) -> icon.contentAlpha = alpha }
    }

    companion object {
        private const val TAG = "TaskView"

        private enum class Alpha {
            Stable,
            Attach,
            Split,
            Modal,
            AnimateToIcon,
        }

        private enum class SettledProgress {
            Fullscreen,
            Gesture,
            Dismiss,
        }

        const val FADE_IN_ICON_DURATION: Long = 120
        private const val DIM_ANIM_DURATION: Long = 700
        private const val SETTLE_TRANSITION_THRESHOLD =
            FADE_IN_ICON_DURATION.toFloat() / DIM_ANIM_DURATION
        val SETTLED_PROGRESS_FAST_OUT_INTERPOLATOR =
            Interpolators.clampToProgress(
                Interpolators.FAST_OUT_SLOW_IN,
                1f - SETTLE_TRANSITION_THRESHOLD,
                1f,
            )!!

        private val SETTLED_PROGRESS: FloatProperty<TaskView> =
            KFloatProperty(TaskView::settledProgress)

        private val SETTLED_PROGRESS_GESTURE: FloatProperty<TaskView> =
            KFloatProperty(TaskView::settledProgressGesture)

        private val SPLIT_SELECT_TRANSLATION_X: FloatProperty<TaskView> =
            KFloatProperty(TaskView::splitSelectTranslationX)

        private val SPLIT_SELECT_TRANSLATION_Y: FloatProperty<TaskView> =
            KFloatProperty(TaskView::splitSelectTranslationY)

        private val DISMISS_TRANSLATION_X: FloatProperty<TaskView> =
            KFloatProperty(TaskView::dismissTranslationX)

        private val DISMISS_TRANSLATION_Y: FloatProperty<TaskView> =
            KFloatProperty(TaskView::dismissTranslationY)

        private val TASK_OFFSET_TRANSLATION_X: FloatProperty<TaskView> =
            KFloatProperty(TaskView::taskOffsetTranslationX)

        private val TASK_OFFSET_TRANSLATION_Y: FloatProperty<TaskView> =
            KFloatProperty(TaskView::taskOffsetTranslationY)

        private val TASK_RESISTANCE_TRANSLATION_X: FloatProperty<TaskView> =
            KFloatProperty(TaskView::taskResistanceTranslationX)

        private val TASK_RESISTANCE_TRANSLATION_Y: FloatProperty<TaskView> =
            KFloatProperty(TaskView::taskResistanceTranslationY)

        @JvmField
        val GRID_END_TRANSLATION_X: FloatProperty<TaskView> =
            KFloatProperty(TaskView::gridEndTranslationX)

        @JvmField
        val DISMISS_SCALE: FloatProperty<TaskView> = KFloatProperty(TaskView::dismissScale)

        @JvmField val SPLIT_ALPHA: FloatProperty<TaskView> = KFloatProperty(TaskView::splitAlpha)
    }
}
