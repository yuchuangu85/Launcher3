/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Rect.intersects
import android.graphics.RectF
import android.util.AttributeSet
import android.util.FloatProperty
import android.util.Log
import android.view.Display.INVALID_DISPLAY
import android.view.Gravity
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.android.internal.jank.Cuj.CUJ_LAUNCHER_APP_LAUNCH_FROM_RECENTS
import com.android.launcher3.R
import com.android.launcher3.dagger.ActivityContextComponent
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.testing.TestLogging
import com.android.launcher3.testing.shared.TestProtocol
import com.android.launcher3.util.KFloatProperty
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.launcher3.util.ViewPool
import com.android.launcher3.util.rects.lerpRect
import com.android.launcher3.util.rects.set
import com.android.quickstep.BaseContainerInterface
import com.android.quickstep.DesktopFullscreenDrawParams
import com.android.quickstep.FullscreenDrawParams
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.ViewUtils.addAccessibleChildToList
import com.android.quickstep.recents.domain.model.TaskLayoutConfig.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.TaskLayoutState.DesktopTaskLayoutState
import com.android.quickstep.recents.domain.model.TaskPosition.Hidden
import com.android.quickstep.recents.domain.model.TaskPosition.Rendered
import com.android.quickstep.recents.domain.usecase.DesktopLayoutUtils
import com.android.quickstep.recents.ui.viewmodel.TaskData
import com.android.quickstep.task.thumbnail.TaskContentView
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.RecentsOrientedState
import com.android.quickstep.util.getRemoteTargetHandle
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import kotlin.math.roundToInt

/** TaskView that contains all tasks that are part of the desktop. */
class DesktopTaskView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    TaskView(
        context,
        attrs,
        type = TaskViewType.DESKTOP,
        thumbnailFullscreenParams = DesktopFullscreenDrawParams(context),
    ) {
    private val desktopTask: DesktopTask?
        get() = groupTask as? DesktopTask

    val deskId
        get() = desktopTask?.deskId ?: DesktopVisibilityController.INACTIVE_DESK_ID

    val selectedTaskId: Int?
        get() =
            taskContainers
                .firstOrNull { it.taskContentView.isFocused || it.taskContentView.isHovered }
                ?.task
                ?.key
                ?.id

    private val contentViewFullscreenParams = FullscreenDrawParams(context)

    private val taskContentViewPool =
        ViewPool<TaskContentView>(
            context,
            this,
            R.layout.task_content_view,
            VIEW_POOL_MAX_SIZE,
            VIEW_POOL_INITIAL_SIZE,
        )

    private val tempPointF = PointF()
    private val lastComputedTaskSize = Rect()
    private lateinit var iconView: IconAppChipView
    private lateinit var iconTouchDelegate: TransformingTouchDelegate
    private lateinit var contentView: DesktopTaskContentView
    private lateinit var backgroundView: View

    /**
     * Controls the gradual transition from the default positions to the organized non-overlapping
     * positions.
     */
    var explodeProgress = 0.0f
        set(value) {
            field = value
            positionTaskWindows()
        }

    /**
     * When a task is removed, this controls the gradual transition from the previous organized task
     * positions to the new.
     */
    private var taskRemoveProgress = 0.0f
        set(value) {
            field = value
            positionTaskWindows()
        }

    private var taskRemoveAnimator: ObjectAnimator? = null

    // The id of the task that is being reordered to the front. This is used to animate the task
    // properly when it is minimized.
    private var taskIdReorderToFront: Int? = null

    var remoteTargetHandles: Array<RemoteTargetHandle>? = null
        set(value) {
            field = value
            if (value != null) {
                positionTaskWindows()
            }
        }

    override val displayId: Int
        get() = desktopTask?.displayId ?: INVALID_DISPLAY

    override fun initialiseInjectables(component: ActivityContextComponent) {
        component.inject(this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        contentView =
            findViewById<DesktopTaskContentView>(R.id.desktop_content).apply {
                cornerRadius = contentViewFullscreenParams.currentCornerRadius
                backgroundView = findViewById(R.id.background)
                backgroundView.setBackgroundColor(
                    resources.getColor(android.R.color.system_neutral2_300, context.theme)
                )
            }
    }

    private fun positionTaskWindows(updateLayout: Boolean = false) {
        if (taskContainers.isEmpty()) {
            return
        }
        val (widthScale, heightScale) = getScreenScaleFactors()
        taskContainers.forEach { taskContainer ->
            val taskId = taskContainer.task.key.id
            val taskContentView = taskContainer.taskContentView
            val desktopTaskLayoutState =
                viewModel.getTaskLayoutState<DesktopTaskLayoutState>(taskId) ?: return@forEach

            val fullscreenPosition = desktopTaskLayoutState.fullscreenPosition
            val overviewTaskPosition = desktopTaskLayoutState.overviewPosition
            val oldOverviewTaskPosition = desktopTaskLayoutState.oldOverviewPosition

            val renderedInOverview: Boolean = overviewTaskPosition is Rendered
            val renderedInFullscreen: Boolean = fullscreenPosition is Rendered
            val isObscured = fullscreenPosition is Hidden
            val isMinimized = taskContainer.task.isMinimized
            val fullScreenTaskBound =
                if (fullscreenPosition is Rendered) {
                    fullscreenPosition.bounds
                } else {
                    taskContainer.task.appBounds
                }
            val overviewTaskBounds =
                if (overviewTaskPosition is Rendered) {
                    overviewTaskPosition.bounds
                } else {
                    DesktopLayoutUtils.createPlaceholderBounds(getDesktopLayoutConfig())
                }

            val currentTaskBounds =
                TEMP_OVERVIEW_TASK_POSITION.apply {
                    // When removing a task, interpolate between its old organized bounds and
                    // [overviewTaskBounds].
                    val prevOverviewTaskBounds = (oldOverviewTaskPosition as? Rendered)?.bounds

                    if (prevOverviewTaskBounds != null) {
                        lerpRect(prevOverviewTaskBounds, overviewTaskBounds, taskRemoveProgress)
                    } else {
                        set(overviewTaskBounds)
                    }

                    // If the task is minimized but not being launched, we can just fade it in.
                    // Otherwise, we need to translate it from its actual position on the
                    // desktop.
                    val shouldAnimateFromDesktop =
                        (!isMinimized && !isObscured) || taskId == taskIdReorderToFront
                    if (shouldAnimateFromDesktop) {
                        lerpRect(fullScreenTaskBound, this, explodeProgress)
                    }
                }

            remoteTargetHandles?.getRemoteTargetHandle(taskId)?.let { remoteTargetHandle ->
                val fromRect =
                    TEMP_FROM_RECTF.apply {
                        set(fullScreenTaskBound)
                        scale(widthScale)
                        offset(
                            lastComputedTaskSize.left.toFloat(),
                            lastComputedTaskSize.top.toFloat(),
                        )
                    }
                val toRect =
                    TEMP_TO_RECTF.apply {
                        set(currentTaskBounds)
                        scale(widthScale)
                        offset(
                            lastComputedTaskSize.left.toFloat(),
                            lastComputedTaskSize.top.toFloat(),
                        )
                    }
                val transform = Matrix()
                transform.setRectToRect(fromRect, toRect, Matrix.ScaleToFit.FILL)
                remoteTargetHandle.taskViewSimulator.setTaskRectTransform(transform)
                remoteTargetHandle.taskViewSimulator.apply(remoteTargetHandle.transformParams)

                val targetAlpha =
                    when {
                        // Animate to hide a task window that should not show in the desktop
                        // tile.
                        !renderedInOverview -> 1f - explodeProgress
                        // Obscured windows should be treated similarly to minimized windows and
                        // should fade in. Activated windows should stay visible however.
                        isObscured && taskId != taskIdReorderToFront -> explodeProgress
                        // Regular windows will stay opaque if they should be shown.
                        else -> 1f
                    }
                remoteTargetHandle.transformParams.setTargetAlpha(targetAlpha)
            }

            taskContentView.setTaskHeaderAlpha(if (renderedInOverview) explodeProgress else 0f)

            taskContentView.alpha =
                when {
                    renderedInFullscreen && !renderedInOverview -> 1f - explodeProgress
                    !renderedInFullscreen && renderedInOverview -> explodeProgress
                    renderedInFullscreen || taskId == taskIdReorderToFront -> 1f
                    else -> 0f
                }

            val overviewTaskLeft = overviewTaskBounds.left * widthScale
            val overviewTaskTop = overviewTaskBounds.top * heightScale
            val overviewTaskWidth = overviewTaskBounds.width() * widthScale
            val overviewTaskHeight = overviewTaskBounds.height() * heightScale

            if (updateLayout) {
                // Position the task to the same position as it would be on the desktop
                taskContentView.updateLayoutParams<LayoutParams> {
                    gravity = Gravity.LEFT or Gravity.TOP
                    width = overviewTaskWidth.toInt()
                    height = overviewTaskHeight.toInt()
                    leftMargin = overviewTaskLeft.toInt()
                    topMargin = overviewTaskTop.toInt()
                }

                // The taskContentView and its descendant close button should only be focusable
                // if the task is actually visible. Note that disabling the view also makes
                // it not hoverable.
                taskContentView.isHoverable = renderedInOverview
                taskContentView.isFocusable = renderedInOverview
                taskContentView.descendantFocusability =
                    if (renderedInOverview) FOCUS_BEFORE_DESCENDANTS else FOCUS_BLOCK_DESCENDANTS
            }

            // When exploded view is disabled, these scale factors will be 1.0. This secondary
            // scale factor is needed because a scale transform is applied to the thumbnail.
            val thumbnailScaleWidth =
                overviewTaskBounds.width().toFloat() / currentTaskBounds.width()
            val thumbnailScaleHeight =
                overviewTaskBounds.height().toFloat() / currentTaskBounds.height()
            val screenRect = getScreenRect()
            val contentOutlineBounds =
                if (intersects(currentTaskBounds, screenRect))
                    Rect(currentTaskBounds).apply {
                        intersectUnchecked(screenRect)
                        // Offset to 0,0 to transform into TaskThumbnailView's coordinate
                        // system.
                        offset(-currentTaskBounds.left, -currentTaskBounds.top)
                        left = (left * widthScale * thumbnailScaleWidth).roundToInt()
                        top = (top * heightScale * thumbnailScaleHeight).roundToInt()
                        right = (right * widthScale * thumbnailScaleWidth).roundToInt()
                        bottom = (bottom * heightScale * thumbnailScaleHeight).roundToInt()
                    }
                else null

            taskContentView.outlineBounds = contentOutlineBounds

            val currentTaskLeft = currentTaskBounds.left * widthScale
            val currentTaskTop = currentTaskBounds.top * heightScale
            val currentTaskWidth = currentTaskBounds.width() * widthScale
            val currentTaskHeight = currentTaskBounds.height() * heightScale
            // During the animation, apply translation and scale such that the view is transformed
            // to where we want, without triggering layout.
            taskContentView.apply {
                pivotX = 0.0f
                pivotY = 0.0f
                translationX = currentTaskLeft - overviewTaskLeft
                translationY = currentTaskTop - overviewTaskTop
                scaleX = if (overviewTaskWidth != 0f) currentTaskWidth / overviewTaskWidth else 1f
                scaleY =
                    if (overviewTaskHeight != 0f) currentTaskHeight / overviewTaskHeight else 1f
            }
        }
    }

    /** Updates this desktop task to the gives task list defined in `tasks` */
    fun bind(
        desktopTask: DesktopTask,
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory,
    ) {
        this.groupTask = desktopTask
        // Minimized tasks are shown in Overview when exploded view is enabled.
        val tasks = desktopTask.tasks
        if (DEBUG) {
            val sb = StringBuilder()
            sb.append("bind tasks=").append(tasks.size).append("\n")
            tasks.forEach { sb.append(" key=${it.key}\n") }
            Log.d(TAG, sb.toString())
        }

        iconView =
            (findViewById<IconAppChipView>(R.id.icon)).apply {
                setIcon(
                    this,
                    ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.ic_desktop_with_bg,
                        context.theme,
                    ),
                )
                setText(resources.getText(R.string.recent_task_desktop))
            }
        iconTouchDelegate = TransformingTouchDelegate(iconView)

        val backgroundViewIndex = contentView.indexOfChild(backgroundView)
        taskContainers =
            tasks.map { task ->
                val taskContentView = taskContentViewPool.view
                contentView.addView(taskContentView, backgroundViewIndex + 1)
                val snapshotView = findViewById<TaskThumbnailView>(R.id.snapshot)
                taskContentView.setOnClickListener {
                    launchTaskWithDesktopController(animated = true, task.key.id)
                }
                // Desktop tasks should have their own accessibility nodes so specific
                // actions can be performed on them.
                taskContentView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

                TaskContainer(
                    this,
                    task,
                    taskContentView,
                    snapshotView,
                    iconView,
                    iconTouchDelegate,
                    SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
                    digitalWellBeingToast = null,
                    taskOverlayFactory,
                )
            }
        onBind(orientedState, taskOverlayFactory)
    }

    override fun onBind(
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory,
    ) {
        super.onBind(orientedState, taskOverlayFactory)
    }

    override fun onRecycle() {
        super.onRecycle()
        explodeProgress = 0.0f
        taskRemoveProgress = 0.0f
        visibility = VISIBLE
        taskContainers.forEach { removeAndRecycleThumbnailView(it) }
        remoteTargetHandles = null
        taskIdReorderToFront = null
    }

    @SuppressLint("RtlHardcoded")
    override fun updateTaskSize(lastComputedTaskSize: Rect, lastComputedGridTaskSize: Rect) {
        super.updateTaskSize(lastComputedTaskSize, lastComputedGridTaskSize)
        this.lastComputedTaskSize.set(lastComputedTaskSize)

        updateTaskPositions()
    }

    override fun setIconState(container: TaskContainer, state: TaskData?) {
        container.snapshotView.contentDescription = (state as? TaskData.Data)?.titleDescription
    }

    // thumbnailView is laid out differently and is handled in onMeasure
    override fun updateThumbnailSize() {}

    override fun getThumbnailBounds(bounds: Rect, relativeToDragLayer: Boolean) {
        if (relativeToDragLayer) {
            container.dragLayer.getDescendantRectRelativeToSelf(contentView, bounds)
        } else {
            bounds.set(contentView)
        }
    }

    /**
     * Launches the desktop task and activate the task with [taskIdToReorderToFront] if it's
     * provided and already on the desktop. It will exit Overview to desktop and activate the
     * according new task afterwards if applicable.
     */
    fun launchTaskWithDesktopController(
        animated: Boolean,
        taskIdToReorderToFront: Int? = null,
    ): RunnableList? {
        val recentsView = recentsView ?: return null
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "launchDesktopFromRecents",
            taskIds.contentToString(),
        )
        val endCallback = RunnableList()
        val desktopController = recentsView.desktopRecentsController
        checkNotNull(desktopController) { "recentsController is null" }

        if (taskIdToReorderToFront != null) {
            taskIdReorderToFront = taskIdToReorderToFront
        }
        val launchDesktopFromRecents = {
            InteractionJankMonitorWrapper.begin(/* v= */ this, CUJ_LAUNCHER_APP_LAUNCH_FROM_RECENTS)
            desktopController.launchDesktopFromRecents(this, animated, taskIdToReorderToFront) {
                endCallback.add {
                    InteractionJankMonitorWrapper.end(CUJ_LAUNCHER_APP_LAUNCH_FROM_RECENTS)
                }
                endCallback.executeAllAndDestroy()
            }
        }
        if (desktopTask?.tasks?.none { !it.isMinimized } == true) {
            recentsView.switchToScreenshot {
                recentsView.finishRecentsAnimation(
                    /* toHome= */ true,
                    /* shouldPip= */ false,
                    launchDesktopFromRecents,
                )
            }
        } else {
            launchDesktopFromRecents()
        }
        Log.d(
            TAG,
            "launchTaskWithDesktopController: ${taskIds.contentToString()}, withRemoteTransition: $animated",
        )

        // Callbacks get run from recentsView for case when recents animation already running
        recentsView.addSideTaskLaunchCallback(endCallback)
        return endCallback
    }

    override fun launchAsStaticTile() = launchTaskWithDesktopController(animated = true)

    override fun launchWithoutAnimation(
        isQuickSwitch: Boolean,
        callback: (launched: Boolean) -> Unit,
    ) = launchTaskWithDesktopController(animated = false)?.add { callback(true) } ?: callback(false)

    // Return true when Task cannot be launched as fullscreen (i.e. in split select state) to skip
    // putting DesktopTaskView to split as it's not supported.
    override fun confirmSecondSplitSelectApp(): Boolean =
        recentsView?.canLaunchFullscreenTask() != true

    override fun getTaskIcons(): Sequence<Pair<IconAppChipView, TransformingTouchDelegate>> =
        sequenceOf(iconView to iconTouchDelegate)

    override fun getContainerForIconView(appChip: IconAppChipView) = null

    override fun onFullscreenProgressChanged(fullscreenProgress: Float) {
        backgroundView.alpha = 1 - fullscreenProgress
        updateSettledProgressFullscreen(fullscreenProgress)
    }

    override fun updateFullscreenParams() {
        super.updateFullscreenParams()
        updateFullscreenParams(contentViewFullscreenParams)
        contentView.cornerRadius = contentViewFullscreenParams.currentCornerRadius
    }

    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        super.addChildrenForAccessibility(outChildren)
        addAccessibleChildToList(iconView, outChildren)
        addAccessibleChildToList(backgroundView, outChildren)
    }

    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        if (!isAttachedToWindow) {
            return
        }

        // Manually control focus order.
        // 1. Add this view itself if it is focusable, visible and enabled.
        if (isFocusable && isVisible && isEnabled) {
            views.add(this)
        }

        // 2. Add the icon view.
        iconView.addFocusables(views, direction, focusableMode)

        // 3. Add the individual task thumbnails in top-to-bottom, left-to-right tabbing order.
        taskContainers
            .asSequence()
            .map { it.taskContentView }
            .sortedWith(compareBy({ it.top }, { it.left }))
            .forEach { taskContentView ->
                taskContentView.addFocusables(views, direction, focusableMode)
            }
    }

    fun removeTaskFromExplodedView(taskId: Int) {
        // Remove the task's [taskContainer] and its associated Views.
        val taskContainer = getTaskContainerById(taskId) ?: return
        removeAndRecycleThumbnailView(taskContainer)
        taskContainer.destroy()
        taskContainers = taskContainers.filterNot { it == taskContainer }

        // If this task has a live window, then hide it.
        // TODO(b/413120214) The dismissed view should fade out.
        remoteTargetHandles?.getRemoteTargetHandle(taskId)?.let {
            it.taskViewSimulator.setTaskRectTransform(Matrix().apply { postScale(0.0f, 0.0f) })
            it.taskViewSimulator.apply(it.transformParams)
        }

        // TODO(b/413130378) Nicer handling of multiple quick task dismissals.
        taskRemoveAnimator?.cancel()
        taskRemoveAnimator =
            ObjectAnimator.ofFloat(this, TASK_REMOVE_PROGRESS, 0f, 1f).apply {
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animator: Animator) {
                            taskRemoveAnimator = null
                        }
                    }
                )
                start()
            }

        updateTaskPositions(taskId)
    }

    private fun removeAndRecycleThumbnailView(taskContainer: TaskContainer) {
        contentView.removeView(taskContainer.taskContentView)
        taskContentViewPool.recycle(taskContainer.taskContentView)
    }

    private fun updateTaskPositions(dismissedTaskId: Int? = null) {
        val layoutConfig = getDesktopLayoutConfig()
        viewModel.updateTasksLayouts(taskContainers.map { it.task }, layoutConfig, dismissedTaskId)
        positionTaskWindows(updateLayout = true)
    }

    private fun getDesktopLayoutConfig(): DesktopLayoutConfig {
        val (widthScale, heightScale) = getScreenScaleFactors()
        val res = context.resources
        return DesktopLayoutConfig(
            desktopBounds = getScreenRect(),
            topBottomMarginOneRow =
                (res.getDimensionPixelSize(R.dimen.desktop_top_bottom_margin_one_row) / heightScale)
                    .toInt(),
            topMarginMultiRows =
                (res.getDimensionPixelSize(R.dimen.desktop_top_margin_multi_rows) / heightScale)
                    .toInt(),
            bottomMarginMultiRows =
                (res.getDimensionPixelSize(R.dimen.desktop_bottom_margin_multi_rows) / heightScale)
                    .toInt(),
            leftRightMarginOneRow =
                (res.getDimensionPixelSize(R.dimen.desktop_left_right_margin_one_row) / widthScale)
                    .toInt(),
            leftRightMarginMultiRows =
                (res.getDimensionPixelSize(R.dimen.desktop_left_right_margin_multi_rows) /
                        widthScale)
                    .toInt(),
            horizontalPaddingBetweenTasks =
                (res.getDimensionPixelSize(R.dimen.desktop_horizontal_padding_between_tasks) /
                        widthScale)
                    .toInt(),
            verticalPaddingBetweenTasks =
                (res.getDimensionPixelSize(R.dimen.desktop_vertical_padding_between_tasks) /
                        heightScale)
                    .toInt(),
            minTaskWidth =
                (res.getDimensionPixelSize(R.dimen.desktop_min_task_width) / widthScale).toInt(),
            maxRows = res.getInteger(R.integer.desktop_layout_max_rows),
        )
    }

    /**
     * Calculates the scale factors for the desktop task view's width and height. This is determined
     * by comparing the available task view dimensions (after accounting for margins like
     * [thumbnailTopMarginPx]) against the total screen dimensions.
     *
     * @return A [Pair] where the first value is the scale factor for width and the second is for
     *   height.
     */
    private fun getScreenScaleFactors(): Pair<Float, Float> {
        val taskViewWidth = layoutParams.width
        val taskViewHeight = layoutParams.height

        val screenRect = getScreenRect()
        val widthScale = taskViewWidth / screenRect.width().toFloat()
        val heightScale = taskViewHeight / screenRect.height().toFloat()

        return Pair(widthScale, heightScale)
    }

    /** Returns the dimensions of the screen. */
    private fun getScreenRect(): Rect {
        BaseContainerInterface.getTaskDimension(container.deviceProfile, tempPointF)
        return Rect(0, 0, tempPointF.x.toInt(), tempPointF.y.toInt())
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        contentViewFullscreenParams.updateCornerRadius(context)
    }

    companion object {
        private const val TAG = "DesktopTaskView"
        private const val DEBUG = false
        private const val VIEW_POOL_MAX_SIZE = 5

        // As DesktopTaskView is inflated in background, use initialSize=0 to avoid initPool.
        private const val VIEW_POOL_INITIAL_SIZE = 0

        // Temporaries used for various purposes to avoid allocations.
        private val TEMP_OVERVIEW_TASK_POSITION = Rect()
        private val TEMP_FROM_RECTF = RectF()
        private val TEMP_TO_RECTF = RectF()
        private val TASK_REMOVE_PROGRESS: FloatProperty<DesktopTaskView> =
            KFloatProperty(DesktopTaskView::taskRemoveProgress)
    }
}
