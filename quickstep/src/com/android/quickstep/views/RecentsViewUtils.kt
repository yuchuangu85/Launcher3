/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.graphics.PointF
import android.graphics.Rect
import android.os.UserHandle
import android.util.FloatProperty
import android.util.Log
import android.util.Property
import android.view.KeyEvent
import android.view.RemoteAnimationTarget
import android.view.View
import android.view.View.LAYOUT_DIRECTION_LTR
import android.view.View.LAYOUT_DIRECTION_RTL
import androidx.core.view.ancestors
import androidx.core.view.children
import androidx.core.view.isEmpty
import androidx.core.view.isInvisible
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.app.animation.Interpolators.LINEAR
import com.android.internal.annotations.VisibleForTesting
import com.android.launcher3.AbstractFloatingView.TYPE_TASK_MENU
import com.android.launcher3.AbstractFloatingView.TYPE_TOUCH_CONTROLLER_NO_INTERCEPT
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.Flags.hideAutomatedTasksInOverview
import com.android.launcher3.PagedView.INVALID_PAGE
import com.android.launcher3.R
import com.android.launcher3.Utilities.debugLog
import com.android.launcher3.Utilities.getPivotsForScalingRectToRect
import com.android.launcher3.automation.AutomationChange
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.DisplayId
import com.android.launcher3.display.DisplayController
import com.android.launcher3.statehandlers.DesktopVisibilityController.Companion.INACTIVE_DESK_ID
import com.android.launcher3.statehandlers.DesktopVisibilityController.DesktopVisibilityListener
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SafeCloseable
import com.android.quickstep.GestureState
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle
import com.android.quickstep.RotationTouchHelper
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.TaskGridNavHelper
import com.android.quickstep.views.RecentsView.DESKTOP_CAROUSEL_DETACH_PROGRESS
import com.android.quickstep.views.RecentsView.RECENTS_GRID_PROGRESS
import com.android.quickstep.views.RecentsView.RUNNING_TASK_ATTACH_ALPHA
import com.android.quickstep.views.RecentsView.TAG
import com.android.quickstep.views.RecentsView.TASK_THUMBNAIL_SPLASH_ALPHA
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.window.flags.Flags.betterDeskDeactivationInRecentsTransition
import com.android.wm.shell.shared.GroupedTaskInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.Executor
import java.util.function.BiConsumer
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KMutableProperty1

/**
 * Helper class for [RecentsView]. This util class contains refactored and extracted functions from
 * RecentsView to facilitate the implementation of unit tests.
 */
class RecentsViewUtils
@AssistedInject
constructor(
    @Assisted private val recentsView: RecentsView<*, *>,
    private val displayController: DisplayController,
    @DisplayId private val displayId: Int,
    private val taskAnimationManager: TaskAnimationManager,
    private val rotationTouchHelper: RotationTouchHelper,
    private val systemUiProxy: SystemUiProxy,
    automationRepository: AutomationRepository,
    @Ui uiExecutor: Executor,
    private val abstractFloatingViewHelper: AbstractFloatingViewHelper,
) : DesktopVisibilityListener {
    val taskViews = TaskViewsIterable(recentsView)

    var keyboardFocusTask: KeyboardFocusTask = KeyboardFocusTask.Unfocused

    private var isInOverview: Boolean = false

    private var automationChangesClosable: SafeCloseable? = null

    init {
        if (hideAutomatedTasksInOverview()) {
            automationChangesClosable =
                automationRepository.automatedPackages.changes.forEach(uiExecutor) {
                    dismissAutomatedTasks(it)
                }
        }
    }

    private fun dismissAutomatedTasks(change: AutomationChange) {
        val addedPackages = change.addedPackages
        if (addedPackages.isEmpty()) {
            return
        }
        getTaskIdsByPackageNamesAndUserHandle(taskViews, addedPackages, change.userHandle).forEach {
            recentsView.dismissTask(it, /* removeTask= */ false)
        }
    }

    @VisibleForTesting
    fun getTaskIdsByPackageNamesAndUserHandle(
        taskViews: Iterable<TaskView>,
        packages: Set<String>,
        userHandle: UserHandle,
    ) =
        taskViews
            .flatMap { it.taskContainers }
            .map { it.task.key }
            .filter { it.userId == userHandle.identifier && it.packageName in packages }
            .map { it.id }

    fun destroy() {
        automationChangesClosable?.close()
        automationChangesClosable = null
    }

    /** Takes a screenshot of all [taskView] and return map of taskId to the screenshot */
    fun screenshotTasks(taskView: TaskView): Map<Int, ThumbnailData> {
        val recentsAnimationController = recentsView.recentsAnimationController ?: return emptyMap()
        return taskView.taskContainers
            .associate {
                it.task.key.id to recentsAnimationController.screenshotTask(it.task.key.id)
            }
            .filter { it.value.thumbnail != null }
    }

    /**
     * Sorts task groups to move desktop tasks to the end of the list.
     *
     * @param tasks List of group tasks to be sorted.
     * @return Sorted list of GroupTasks to be used in the RecentsView.
     */
    fun sortDesktopTasksToFront(tasks: List<GroupTask>): List<GroupTask> {
        val (desktopTasks, otherTasks) = tasks.partition { it.taskViewType == TaskViewType.DESKTOP }
        // Desk IDs of newer desks are larger than those of older desks, hence we can use them
        // to sort desks from old to new.
        return otherTasks + desktopTasks.sortedBy { (it as DesktopTask).deskId }
    }

    open class TaskViewsIterable(val recentsView: RecentsView<*, *>) : Iterable<TaskView> {
        /** Iterates TaskViews when its index inside the RecentsView is needed. */
        fun forEachWithIndexInParent(consumer: BiConsumer<Int, TaskView>) {
            recentsView.children.forEachIndexed { index, child ->
                (child as? TaskView)?.let { consumer.accept(index, it) }
            }
        }

        override fun iterator(): Iterator<TaskView> =
            recentsView.children.mapNotNull { it as? TaskView }.iterator()
    }

    /** Counts [TaskView]s that are [DesktopTaskView] instances. */
    fun getDesktopTaskViewCount(): Int = taskViews.count { it is DesktopTaskView }

    /** Counts [TaskView]s that are not [DesktopTaskView] instances. */
    fun getNonDesktopTaskViewCount(): Int = taskViews.count { it !is DesktopTaskView }

    /** Returns a list of all large TaskView Ids from [TaskView]s */
    fun getLargeTaskViewIds(): List<Int> = taskViews.filter { it.isLargeTile }.map { it.taskViewId }

    /** Returns a list of all large TaskViews [TaskView]s */
    fun getLargeTaskViews(): List<TaskView> = taskViews.filter { it.isLargeTile }

    /** Returns a list of all non-large TaskViews [TaskView]s */
    fun getSmallTaskViews(): List<TaskView> = taskViews.filter { !it.isLargeTile }

    fun getVisibleTaskIds(): List<Int> {
        val visibleTaskViews: Iterable<TaskView> =
            if (recentsView.showAsGrid()) {
                val pagedOrientationHandler = recentsView.pagedOrientationHandler
                val screenStart = pagedOrientationHandler.getPrimaryScroll(recentsView)
                val pageOrientedSize = pagedOrientationHandler.getMeasuredSize(recentsView)
                // Use +/- 1 task column as visible area for preloading.
                val extraWidth =
                    recentsView.lastComputedGridTaskSize.width() + recentsView.pageSpacing
                val visibleStart = screenStart - extraWidth
                val visibleEnd = screenStart + pageOrientedSize + extraWidth

                taskViews.filter { taskView ->
                    recentsView.isTaskViewWithinBounds(
                        taskView,
                        visibleStart,
                        visibleEnd,
                        recentsView.mTaskViewsDismissPrimaryTranslations.getOrDefault(taskView, 0),
                    )
                }
            } else {
                val centerPageIndex: Int = recentsView.pageNearestToCenterOfScreen
                // 2 tasks either side of the central task
                val lowerIndex = max(0, centerPageIndex - 2)
                val upperIndex = centerPageIndex + 2

                recentsView.children
                    .drop(lowerIndex)
                    .take(upperIndex - lowerIndex + 1)
                    .filterIsInstance<TaskView>()
                    .toList()
            }

        return visibleTaskViews
            .flatMap { taskView -> taskView.taskContainers }
            .map { taskContainer -> taskContainer.task.key.id }
            .toList()
    }

    /** Returns all the TaskViews in the top row, without the focused task */
    fun getTopRowTaskViews(): List<TaskView> =
        taskViews.filter { recentsView.mTopRowIdSet.contains(it.taskViewId) }

    /** Returns all the task Ids in the top row, without the focused task */
    fun getTopRowIdArray(): IntArray =
        getTopRowTaskViews().map { it.taskViewId }.toLauncher3IntArray()

    /** Returns all the TaskViews in the bottom row, without the focused task */
    fun getBottomRowTaskViews(): List<TaskView> =
        taskViews.filter { !recentsView.mTopRowIdSet.contains(it.taskViewId) && !it.isLargeTile }

    /** Returns all the task Ids in the bottom row, without the focused task */
    fun getBottomRowIdArray(): IntArray =
        getBottomRowTaskViews().map { it.taskViewId }.toLauncher3IntArray()

    private fun List<Int>.toLauncher3IntArray() =
        IntArray(size).apply { this@toLauncher3IntArray.forEach(::add) }

    /** Counts [TaskView]s that are large tiles. */
    fun getLargeTileCount(): Int = taskViews.count { it.isLargeTile }

    /** Counts [TaskView]s that are grid tasks. */
    fun getGridTaskCount(): Int = taskViews.count { it.isGridTask }

    /** Returns the first TaskView that should be displayed as a large tile. */
    fun getFirstLargeTaskView(): TaskView? =
        taskViews.firstOrNull {
            it.isLargeTile && !(recentsView.isSplitSelectionActive && it is DesktopTaskView)
        }

    /**
     * Returns the [DesktopTaskView] that matches the given [deskId], or null if it doesn't exist.
     */
    fun getDesktopTaskViewForDeskId(deskId: Int): DesktopTaskView? {
        if (deskId == INACTIVE_DESK_ID) {
            return null
        }
        return taskViews.firstOrNull { it is DesktopTaskView && it.deskId == deskId }
            as? DesktopTaskView
    }

    /** Returns the expected focus task. */
    fun getFirstNonDesktopTaskView(): TaskView? = taskViews.firstOrNull { it !is DesktopTaskView }

    fun getLastDesktopTaskView(): TaskView? = taskViews.lastOrNull { it is DesktopTaskView }

    /** Returns true if it is in desktop-first mode. Otherwise, returns false. */
    fun isInDesktopFirstMode() =
        displayController.getInfoForDisplay(displayId)?.isInDesktopFirstMode == true

    /** Returns false if it is the last desktop on desktop-first. Otherwise,returns true. */
    fun canRemoveTaskView(taskView: TaskView) =
        !isInDesktopFirstMode() || taskView !is DesktopTaskView || getDesktopTaskViewCount() > 1

    /**
     * Returns the [TaskView] that should be the current page during task binding, in the following
     * priorities:
     * 1. Running task
     * 2. Last desktop task if it's desktop-first mode.
     * 3. First non-desktop task
     * 4. Last desktop task
     * 5. null otherwise
     */
    fun getExpectedCurrentTask(runningTaskView: TaskView?): TaskView? =
        runningTaskView
            ?: (if (isInDesktopFirstMode()) getLastDesktopTaskView() else null)
            ?: getFirstNonDesktopTaskView()
            ?: getLastDesktopTaskView()

    fun handleTabKeyEvent(event: KeyEvent, superCall: (KeyEvent) -> Boolean): Boolean {
        val isShiftPressed = event.isShiftPressed
        val cycleTaskViews = {
            recentsView.snapToPageRelative(
                if (isShiftPressed) -1 else 1,
                /* cycle= */ true,
                TaskGridNavHelper.TaskNavDirection.TAB,
            )
        }

        // When alt + tabbing on phones (KQS handles on large screens) go to the next task.
        if (event.isAltPressed) {
            return cycleTaskViews()
        }

        // If not alt + tabbing, cycle through the available views in a single task (e.g. chip menu)
        val currentFocus: View = recentsView.findFocus() ?: return superCall(event)

        // If already at the last focusable element within the TaskView (or if cycling in reverse
        // order and on first element), snap to the next page.
        val direction = if (isShiftPressed) View.FOCUS_BACKWARD else View.FOCUS_FORWARD
        findParentTaskView(currentFocus)?.getVisibleFocusables(direction)?.let { focusables ->
            if (
                focusables.isNotEmpty() &&
                    ((!isShiftPressed && currentFocus == focusables.last()) ||
                        (isShiftPressed && currentFocus == focusables.first()))
            ) {
                return cycleTaskViews()
            }
        }

        // Snap to next page if a single item is focusable, like the clear all button. Skip any
        // invisible views for focusing.
        val nextFocus: View? = recentsView.focusSearch(currentFocus, direction)
        if (nextFocus == null || !nextFocus.isVisibleToUser) {
            return cycleTaskViews()
        }

        return nextFocus.requestFocus()
    }

    /** Finds the first parent of this View that is an instance of TaskView, including itself. */
    private fun findParentTaskView(view: View): TaskView? {
        if (view is TaskView) {
            return view
        }
        return view.ancestors.filterIsInstance<TaskView>().firstOrNull()
    }

    fun View.getVisibleFocusables(direction: Int): List<View> =
        getFocusables(direction)?.filter { it.isVisibleToUser } ?: emptyList()

    private fun getDeviceProfile() = recentsView.container.deviceProfile

    fun getRunningTaskExpectedIndex(runningTaskView: TaskView): Int {
        if (runningTaskView is DesktopTaskView) {
            // Use the [deskId] to keep desks in the order of their creation, as a newer desk
            // always has a larger [deskId] than the older desks.
            val desktopTaskView =
                taskViews.firstOrNull {
                    it is DesktopTaskView &&
                        it.deskId != INACTIVE_DESK_ID &&
                        it.deskId <= runningTaskView.deskId
                }
            if (desktopTaskView != null) return recentsView.indexOfChild(desktopTaskView)
        }
        val firstTaskViewIndex = recentsView.indexOfChild(getFirstTaskView())
        return if (getDeviceProfile().deviceProperties.isLargeScreen) {
            var index = firstTaskViewIndex
            if (runningTaskView !is DesktopTaskView) {
                // For fullsreen tasks, skip over Desktop tasks in its section
                index +=
                    if (runningTaskView.isExternalDisplay) {
                        taskViews.count { it is DesktopTaskView && it.isExternalDisplay }
                    } else {
                        taskViews.count { it is DesktopTaskView && !it.isExternalDisplay }
                    }
            }
            if (!runningTaskView.isExternalDisplay) {
                // For main display section, skip over external display tasks
                index += taskViews.count { it.isExternalDisplay }
            }
            index
        } else {
            val currentIndex: Int = recentsView.indexOfChild(runningTaskView)
            return if (currentIndex != -1) {
                currentIndex // Keep the position if running task already in layout.
            } else {
                // New running task are added to the front to begin with.
                firstTaskViewIndex
            }
        }
    }

    /** Returns the first TaskView if it exists, or null otherwise. */
    fun getFirstTaskView(): TaskView? = taskViews.firstOrNull()

    /** Returns the last TaskView if it exists, or null otherwise. */
    fun getLastTaskView(): TaskView? = taskViews.lastOrNull()

    /** Returns the first TaskView that is not large */
    fun getFirstSmallTaskView(): TaskView? = taskViews.firstOrNull { !it.isLargeTile }

    /** Returns the last TaskView that should be displayed as a large tile. */
    fun getLastLargeTaskView(): TaskView? = taskViews.lastOrNull { it.isLargeTile }

    fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
        recentsView.addDeskButton?.isInvisible = !canCreateDesks
    }

    /** Animates the alpha of the AddDesktopButton when a gesture ends. */
    fun startAddDesktopButtonFadeInOnGestureComplete() {
        val addDesktopButton = recentsView.addDeskButton ?: return
        ObjectAnimator.ofFloat(addDesktopButton, AddDesktopButton.GESTURE_ALPHA, 1f)
            .apply {
                duration = TaskView.FADE_IN_ICON_DURATION
                interpolator = LINEAR
            }
            .start()
    }

    private fun animateDesktopTaskViewSpringIn(
        desktopTaskView: DesktopTaskView,
        fadeInAddDesktopButton: Boolean,
    ) {
        val taskDismissFloatProperty =
            FloatPropertyCompat.createFloatPropertyCompat(
                desktopTaskView.primaryDismissTranslationProperty
            )

        with(recentsView) {
            // Calculate initial translation to bring it offscreen.
            val desktopTaskViewIndex = indexOfChild(desktopTaskView)
            val midpointIndex =
                if (getTaskViewAt(desktopTaskViewIndex + 1) != null) desktopTaskViewIndex + 1
                else INVALID_PAGE
            var offscreenTranslationX =
                getHorizontalOffsetSize(desktopTaskViewIndex, midpointIndex, 1f)

            // Add 40dp to the offscreen translation.
            val additionalOffsetPx =
                context.resources.getDimensionPixelSize(
                    R.dimen.newly_created_desktop_offscreen_position
                )
            offscreenTranslationX += if (isRtl) additionalOffsetPx else -additionalOffsetPx
            desktopTaskView.primaryDismissTranslationProperty.set(
                desktopTaskView,
                offscreenTranslationX,
            )
            desktopTaskView.isInvisible = false

            val dampingRatio =
                context.resources.getFloat(R.dimen.newly_created_desktop_spring_damping_ratio)
            val stiffness =
                context.resources.getFloat(R.dimen.newly_created_desktop_spring_stiffness)

            SpringAnimation(desktopTaskView, taskDismissFloatProperty)
                .setSpring(SpringForce(0f).setDampingRatio(dampingRatio).setStiffness(stiffness))
                .addEndListener { _, _, _, _ ->
                    if (fadeInAddDesktopButton) {
                        addDeskButton?.setContentVisibility(toVisible = true, animate = true)
                    }
                }
                .start()
        }
    }

    override fun onDeskAdded(displayId: Int, deskId: Int) {
        if (displayId != this.displayId) return

        with(recentsView) {
            if (getDesktopTaskViewForDeskId(deskId) != null) {
                Log.e(TAG, "A task view for this desk has already been added.")
                return
            }
            val currentPageChild = getChildAt(currentPage)

            val wasEmpty = isEmpty()
            if (wasEmpty) {
                // Add ClearAllButton and AddDesktopButton if they are not present.
                addDeskButton?.let {
                    it.setContentVisibility(toVisible = false, animate = false)
                    addView(it)
                }
                addView(clearAllButton)
            }

            // Compute [mCurrentPageScrollDiff] to be used for adjusting the scroll to guarantee
            // the existing tasks remain in their previous position after creating the desktop.
            val primaryScroll = pagedOrientationHandler.getPrimaryScroll(this)
            val currentPageScroll = getScrollForPage(currentPage)
            currentPageScrollDiff = primaryScroll - currentPageScroll

            // We assume that a newly added desk is always empty and gets added to the left of the
            // `AddNewDesktopButton`.
            val desktopTaskView = getTaskViewFromPool(TaskViewType.DESKTOP) as DesktopTaskView
            desktopTaskView.bind(
                DesktopTask(deskId, displayId, emptyList()),
                pagedViewOrientedState,
                taskOverlayFactory,
            )
            desktopTaskView.isInvisible = true

            val insertionIndex = 1 + indexOfChild(addDeskButton!!)
            addView(desktopTaskView, insertionIndex)
            updateTaskSize()
            updateChildTaskOrientations()
            updateScrollSynchronously()
            animateDesktopTaskViewSpringIn(desktopTaskView, fadeInAddDesktopButton = wasEmpty)

            // Set Current Page based on the stored View.
            currentPageChild?.let { setCurrentPage(indexOfChild(it)) }
            // Reset visuals for the newly created desk.
            resetTaskVisuals(desktopTaskView)
        }
    }

    override fun onDeskRemoved(displayId: Int, deskId: Int) {
        if (displayId != this.displayId) return

        // We need to distinguish between desk removals that are triggered from outside of
        // overview vs. the ones that were initiated from overview by dismissing the
        // corresponding desktop task view.
        getDesktopTaskViewForDeskId(deskId)?.let {
            recentsView.dismissTaskView(it, /* removeTask= */ true)
        }
    }

    override fun onActiveDeskChanged(displayId: Int, newActiveDesk: Int, oldActiveDesk: Int) {
        if (betterDeskDeactivationInRecentsTransition()) return
        if (!isInDesktopFirstMode()) return
        if (displayId != this.displayId) return
        if (oldActiveDesk != INACTIVE_DESK_ID || newActiveDesk == INACTIVE_DESK_ID) return
        // TaskAnimationManager.onTasksAppeared already handles desktop task launching.
        if (taskAnimationManager.isRecentsAnimationRunning) return
        // Desktop launch will close Recents when transition is finished.
        if (recentsView.desktopRecentsController?.isDesktopLaunchOngoing() == true) return

        Log.d(
            TAG,
            "onActiveDeskChanged - closing RecentsView because desk $newActiveDesk is activated",
        )
        recentsView.stateManager.moveToRestState()
    }

    override fun onTaskAppearingInDeskWithOverviewShowing(
        taskId: Int,
        displayId: Int,
        deskId: Int,
    ) {
        if (!betterDeskDeactivationInRecentsTransition()) return
        if (!isInDesktopFirstMode()) return
        if (displayId != this.displayId) return
        // TaskAnimationManager.onTasksAppeared already handles desktop task launching.
        if (taskAnimationManager.isRecentsAnimationRunning) return
        // Desktop launch will close Recents when tra]nsition is finished.
        if (recentsView.desktopRecentsController?.isDesktopLaunchOngoing() == true) return

        Log.d(
            TAG,
            "onTaskAppearingInDeskWithOverviewShowing - " +
                "closing RecentsView because taskId $taskId appeared in deskId $deskId",
        )
        recentsView.stateManager.moveToRestState()
    }

    /**
     * Gets the list of accessibility children. Currently all the children of RecentsViews are
     * added, and in the reverse order to the list.
     */
    fun getAccessibilityChildren(): List<View> = recentsView.children.toList().reversed()

    @JvmOverloads
    /** Returns the first [TaskView], with some tasks possibly hidden in the carousel. */
    fun getFirstTaskViewInCarousel(
        nonRunningTaskCarouselHidden: Boolean,
        runningTaskView: TaskView? = recentsView.runningTaskView,
    ): TaskView? =
        taskViews.firstOrNull {
            it.isVisibleInCarousel(runningTaskView, nonRunningTaskCarouselHidden)
        }

    /** Returns the last [TaskView], with some tasks possibly hidden in the carousel. */
    fun getLastTaskViewInCarousel(nonRunningTaskCarouselHidden: Boolean): TaskView? =
        taskViews.lastOrNull {
            it.isVisibleInCarousel(recentsView.runningTaskView, nonRunningTaskCarouselHidden)
        }

    /** Returns if any small tasks are fully visible */
    fun isAnySmallTaskFullyVisible(): Boolean =
        taskViews.any { !it.isLargeTile && recentsView.isTaskViewFullyVisible(it) }

    /** Apply attachAlpha to all [TaskView] accordingly to different conditions. */
    fun applyAttachAlpha(nonRunningTaskCarouselHidden: Boolean) {
        taskViews.forEach { taskView ->
            taskView.attachAlpha =
                if (taskView == recentsView.runningTaskView) {
                    RUNNING_TASK_ATTACH_ALPHA.get(recentsView)
                } else {
                    if (
                        taskView.isVisibleInCarousel(
                            recentsView.runningTaskView,
                            nonRunningTaskCarouselHidden,
                        )
                    )
                        1f
                    else 0f
                }
        }
    }

    fun TaskView.isVisibleInCarousel(
        runningTaskView: TaskView?,
        nonRunningTaskCarouselHidden: Boolean,
    ): Boolean =
        if (!nonRunningTaskCarouselHidden) true
        else getCarouselType() == runningTaskView.getCarouselType()

    /** Returns the carousel type of the TaskView, and default to fullscreen if it's null. */
    private fun TaskView?.getCarouselType(): TaskViewCarousel =
        if (this is DesktopTaskView) TaskViewCarousel.DESKTOP else TaskViewCarousel.FULL_SCREEN

    private enum class TaskViewCarousel {
        FULL_SCREEN,
        DESKTOP,
    }

    /** Returns true if there are at least one TaskView has been added to the RecentsView. */
    fun hasTaskViews() = taskViews.any()

    fun getTaskContainerById(taskId: Int) =
        taskViews.firstNotNullOfOrNull { it.getTaskContainerById(taskId) }

    private fun getRowRect(firstView: View?, lastView: View?, outRowRect: Rect) {
        outRowRect.setEmpty()
        firstView?.let {
            it.getHitRect(TEMP_RECT)
            outRowRect.union(TEMP_RECT)
        }
        lastView?.let {
            it.getHitRect(TEMP_RECT)
            outRowRect.union(TEMP_RECT)
        }
    }

    private fun getRowRect(rowTaskViewIds: IntArray, outRowRect: Rect) {
        if (rowTaskViewIds.isEmpty) {
            outRowRect.setEmpty()
            return
        }
        getRowRect(
            recentsView.getTaskViewFromTaskViewId(rowTaskViewIds.get(0)),
            recentsView.getTaskViewFromTaskViewId(rowTaskViewIds.get(rowTaskViewIds.size() - 1)),
            outRowRect,
        )
    }

    fun updateTaskViewDeadZoneRect(
        outTaskViewRowRect: Rect,
        outTopRowRect: Rect,
        outBottomRowRect: Rect,
    ) {
        if (!getDeviceProfile().deviceProperties.isLargeScreen) {
            getRowRect(getFirstTaskView(), getLastTaskView(), outTaskViewRowRect)
            return
        }
        getRowRect(getFirstLargeTaskView(), getLastLargeTaskView(), outTaskViewRowRect)
        getRowRect(getTopRowIdArray(), outTopRowRect)
        getRowRect(getBottomRowIdArray(), outBottomRowRect)

        // Expand large tile Rect to include space between top/bottom row.
        val nonEmptyRowRect =
            when {
                !outTopRowRect.isEmpty -> outTopRowRect
                !outBottomRowRect.isEmpty -> outBottomRowRect
                else -> return
            }
        if (recentsView.isRtl) {
            if (outTaskViewRowRect.left > nonEmptyRowRect.right) {
                outTaskViewRowRect.left = nonEmptyRowRect.right
            }
        } else {
            if (outTaskViewRowRect.right < nonEmptyRowRect.left) {
                outTaskViewRowRect.right = nonEmptyRowRect.left
            }
        }

        // Expand the shorter row Rect to include the space between the 2 rows.
        if (outTopRowRect.isEmpty || outBottomRowRect.isEmpty) return
        if (outTopRowRect.width() <= outBottomRowRect.width()) {
            if (outTopRowRect.bottom < outBottomRowRect.top) {
                outTopRowRect.bottom = outBottomRowRect.top
            }
        } else {
            if (outBottomRowRect.top > outTopRowRect.bottom) {
                outBottomRowRect.top = outTopRowRect.bottom
            }
        }
    }

    private fun getTaskMenu(): TaskMenuView? =
        abstractFloatingViewHelper.getTopOpenViewWithType(recentsView.container, TYPE_TASK_MENU)
            as? TaskMenuView

    fun taskMenuIsOpen() = getTaskMenu()?.isOpen == true

    fun updateChildTaskOrientations() {
        with(recentsView) {
            taskViews.forEach { it.setOrientationState(mOrientationState) }
            children.forEach {
                it.layoutDirection = if (isRtl) LAYOUT_DIRECTION_LTR else LAYOUT_DIRECTION_RTL
            }

            // Return when it's not fake landscape
            if (mOrientationState.isRecentsActivityRotationAllowed) return@with

            // Rotation is supported on phone (details at b/254198019#comment4)
            getTaskMenu()?.onRotationChanged()
        }
    }

    fun updateCentralTask() {
        val currentPageTaskView = recentsView.currentPageTaskView

        fun isInExpectedScrollPosition(taskView: TaskView?) =
            taskView?.let { recentsView.isTaskInExpectedScrollPosition(it) } ?: false

        val centralTaskIds: Set<Int> =
            when {
                getDeviceProfile().deviceProperties.isLargeScreen -> emptySet()
                isInExpectedScrollPosition(currentPageTaskView) -> currentPageTaskView!!.taskIdSet
                else -> emptySet()
            }

        recentsView.mRecentsViewModel.updateCentralTaskIds(centralTaskIds)
    }

    var deskExplodeProgress: Float = 0f
        set(value) {
            field = value
            taskViews.filterIsInstance<DesktopTaskView>().forEach { it.explodeProgress = field }
        }

    var selectedTaskView: TaskView? = null
        set(newValue) {
            val oldValue = field
            field = newValue
            if (oldValue != newValue) {
                onSelectedTaskViewUpdated(oldValue, newValue)
            }
        }

    private fun onSelectedTaskViewUpdated(
        oldSelectedTaskView: TaskView?,
        newSelectedTaskView: TaskView?,
    ) {
        with(recentsView) {
            oldSelectedTaskView?.modalScale = 1f
            oldSelectedTaskView?.modalPivot = null

            if (newSelectedTaskView == null) return

            val modalTaskBounds = mTempRect
            getModalTaskSize(modalTaskBounds)
            val selectedTaskBounds = getTaskBounds(newSelectedTaskView)

            // Map bounds to selectedTaskView's coordinate system.
            modalTaskBounds.offset(-selectedTaskBounds.left, -selectedTaskBounds.top)
            selectedTaskBounds.offset(-selectedTaskBounds.left, -selectedTaskBounds.top)

            val modalScale =
                min(
                    (modalTaskBounds.height().toFloat() / selectedTaskBounds.height()),
                    (modalTaskBounds.width().toFloat() / selectedTaskBounds.width()),
                )
            val modalPivot = PointF()
            getPivotsForScalingRectToRect(modalTaskBounds, selectedTaskBounds, modalPivot)

            newSelectedTaskView.modalScale = modalScale
            newSelectedTaskView.modalPivot = modalPivot
        }
    }

    /**
     * Creates a [DesktopTaskView] for the currently active desk on this display, which contains the
     * tasks with the given [groupedTaskInfo].
     */
    fun createDesktopTaskViewForActiveDesk(groupedTaskInfo: GroupedTaskInfo): DesktopTaskView {
        val desktopTaskView =
            recentsView.getTaskViewFromPool(TaskViewType.DESKTOP) as DesktopTaskView
        val tasks: List<Task> = groupedTaskInfo.taskInfoList.map { taskInfo -> Task.from(taskInfo) }
        desktopTaskView.bind(
            DesktopTask(groupedTaskInfo.deskId, groupedTaskInfo.deskDisplayId, tasks),
            recentsView.mOrientationState,
            recentsView.mTaskOverlayFactory,
        )
        return desktopTaskView
    }

    fun getRunningTaskViewFromGroupTaskInfo(groupedTaskInfo: GroupedTaskInfo) =
        if (groupedTaskInfo.isBaseType(GroupedTaskInfo.TYPE_DESK)) {
            getDesktopTaskViewForDeskId(groupedTaskInfo.deskId)
        } else {
            val runningTaskIds = groupedTaskInfo.taskInfoList.map { it.taskId }.toIntArray()
            val taskView = recentsView.getTaskViewByTaskIds(runningTaskIds)
            if (taskView?.type == groupedTaskInfo.getTaskViewType()) taskView else null
        }

    private fun GroupedTaskInfo.getTaskViewType() =
        when {
            isBaseType(GroupedTaskInfo.TYPE_FULLSCREEN) -> TaskViewType.SINGLE
            isBaseType(GroupedTaskInfo.TYPE_SPLIT) -> TaskViewType.GROUPED
            isBaseType(GroupedTaskInfo.TYPE_DESK) -> TaskViewType.DESKTOP
            else -> null
        }

    fun onPrepareGestureEndAnimation(
        animatorSet: AnimatorSet,
        endTarget: GestureState.GestureEndTarget,
        remoteTargetHandles: Array<RemoteTargetHandle>,
        isHandlingAtomicEvent: Boolean,
    ) {
        // Create ObjectAnimator that immediately settles on [endStateValue] when
        // [isHandlingAtomicEvent] is true.
        fun <T> immediateObjectAnimator(
            target: T,
            property: Property<T, Float>,
            endStateValue: Float,
        ) =
            if (isHandlingAtomicEvent)
                ObjectAnimator.ofFloat(target, property, endStateValue, endStateValue)
            else ObjectAnimator.ofFloat(target, property, endStateValue)

        with(recentsView) {
            Log.d(TAG, "onPrepareGestureEndAnimation - endTarget: $endTarget")
            mCurrentGestureEndTarget = endTarget
            val endState: BaseState<*> = mContainerInterface.stateFromGestureEndTarget(endTarget)

            // Starting the desk exploded animation when the gesture from an app is released.
            animatorSet.play(
                ObjectAnimator.ofFloat(
                    this,
                    DESK_EXPLODE_PROGRESS,
                    if (endState.showExplodedDesktopView()) 1f else 0f,
                )
            )
            taskViews.filterIsInstance<DesktopTaskView>().forEach {
                it.remoteTargetHandles = remoteTargetHandles
            }

            if (endState.displayOverviewTasksAsGrid(getDeviceProfile())) {
                updateGridProperties()
                animatorSet.play(immediateObjectAnimator(this, RECENTS_GRID_PROGRESS, 1f))

                val runningTaskView = runningTaskView
                var runningTaskGridTranslationX = 0f
                var runningTaskGridTranslationY = 0f
                if (runningTaskView != null) {
                    // Apply the grid translation to running task unless it's being snapped to
                    // and removes the current translation applied to the running task.
                    runningTaskGridTranslationX =
                        (runningTaskView.gridTranslationX - runningTaskView.nonGridTranslationX)
                    runningTaskGridTranslationY = runningTaskView.gridTranslationY
                }
                remoteTargetHandles.forEach { remoteTargetHandle ->
                    val taskViewSimulator = remoteTargetHandle.taskViewSimulator
                    animatorSet.play(taskViewSimulator.carouselScale.animateToValue(1f))
                    animatorSet.play(
                        taskViewSimulator.taskGridTranslationX.animateToValue(
                            runningTaskGridTranslationX
                        )
                    )
                    animatorSet.play(
                        taskViewSimulator.taskGridTranslationY.animateToValue(
                            runningTaskGridTranslationY
                        )
                    )
                }
            }
            animatorSet.play(
                immediateObjectAnimator(
                    this,
                    TASK_THUMBNAIL_SPLASH_ALPHA,
                    if (endState.showTaskThumbnailSplash()) 1f else 0f,
                )
            )
            animatorSet.play(ObjectAnimator.ofFloat(this, DESKTOP_CAROUSEL_DETACH_PROGRESS, 0f))

            // Reload visible tasks according to new [mCurrentGestureEndTarget] value.
            loadVisibleTaskData()
        }
    }

    fun resetShareUIState() {
        taskViews.flatMap { it.taskContainers }.forEach { it.overlay.resetShareUI() }
    }

    fun getKeyboardFocusTaskView(): TaskView? =
        when (val keyboardFocusTask = keyboardFocusTask) {
            is KeyboardFocusTask.Unfocused -> null
            is KeyboardFocusTask.CurrentPageTaskView -> recentsView.currentPageTaskView
            is KeyboardFocusTask.ExpectedCurrentTask ->
                getExpectedCurrentTask(recentsView.runningTaskView)
            is KeyboardFocusTask.TaskViewWithIds ->
                recentsView.getTaskViewByTaskIds(keyboardFocusTask.taskIds.toIntArray())
        }

    /**
     * Handle when the Delete key is presses. It can be one of the three following cases:
     * 1. If a [TaskView] is in focus, dismiss it;
     * 2. If a [DesktopTaskView] is in the exploded view, dismiss the selected window;
     * 3. otherwise, do nothing.
     */
    fun onDeleteKeyPressed() {
        taskViews.forEach { taskView ->
            if (taskView.isFocused) {
                recentsView.dismissTaskView(taskView, /* removeTask= */ true)
                return
            } else if (taskView is DesktopTaskView) {
                val focusedTaskId =
                    taskView.taskContainers
                        .firstOrNull { it.taskContentView.isFocused }
                        ?.task
                        ?.key
                        ?.id
                if (focusedTaskId != null) {
                    recentsView.dismissTask(focusedTaskId, /* removeTask= */ true)
                    return
                }
            }
        }
    }

    fun isKeyboardTaskFocusPending() = keyboardFocusTask !is KeyboardFocusTask.Unfocused

    fun getAlternatePageWithSameScroll(page: Int): Int {
        val pageScroll = recentsView.getScrollForPage(page)
        recentsView.children.forEachIndexed { index, _ ->
            if (index != page && recentsView.getScrollForPage(index) == pageScroll) {
                return index
            }
        }
        return INVALID_PAGE
    }

    /**
     * Return to desktop by launching any available [DesktopTaskView].
     *
     * Prioritize launching live tile desktop, then current page desktop, and otherwise any desktop
     * that is available.
     *
     * @return provides runnable list to attach runnable at end of Desktop Mode launch
     */
    fun returnToDesktop(): RunnableList? =
        with(recentsView) {
            val desktopTaskView =
                (runningTaskView as? DesktopTaskView)
                    ?: currentPageTaskView as? DesktopTaskView
                    ?: lastDesktopTaskView
                    ?: return null
            if (!isTaskViewVisible(desktopTaskView)) {
                snapToPageImmediately(indexOfChild(desktopTaskView))
            }
            return desktopTaskView.launchWithAnimation()
        }

    @AssistedFactory
    interface Factory {
        fun create(recentsView: RecentsView<*, *>): RecentsViewUtils
    }

    fun isTaskLaunchingInFreeFromWindow(taskId: Int, apps: Array<RemoteAnimationTarget>): Boolean {
        return apps.firstOrNull { it.taskId == taskId }?.windowConfiguration?.windowingMode ==
            WINDOWING_MODE_FREEFORM
    }

    fun reapplyActiveRotation() {
        recentsView.setLayoutRotation(
            rotationTouchHelper.currentActiveRotation,
            rotationTouchHelper.displayRotation,
        )
    }

    /** Called when a transition to a new state finishes. */
    fun onStateTransitionComplete(finalState: BaseState<*>) {
        if (!betterDeskDeactivationInRecentsTransition()) return
        if (!isInOverview && finalState.isInOverview) {
            systemUiProxy.onOverviewShown(displayId)
        } else if (isInOverview && !finalState.isInOverview) {
            systemUiProxy.onOverviewHidden(displayId)
        }
        isInOverview = finalState.isInOverview
    }

    fun shouldSwipeDownLaunchTaskView(taskView: TaskView?) =
        with(recentsView) {
            when {
                // Floating views that a TouchController should not try to intercept touches from.
                abstractFloatingViewHelper.getTopOpenViewWithType(
                    container,
                    TYPE_TOUCH_CONTROLLER_NO_INTERCEPT,
                ) != null -> {
                    debugLog(TAG, "Not swiping down, open floating view blocking touch.")
                    false
                }

                // Do not allow launch while recents is scrolling.
                !recentsView.scroller.isFinished -> {
                    debugLog(TAG, "Not swiping down, Recents scrolling.")
                    false
                }

                !stateManager.state.isTaskViewInteractive -> {
                    debugLog(TAG, "Not swiping down, Recents not interactive.")
                    false
                }

                taskView == null -> {
                    debugLog(TAG, "Not swiping down, taskView not found.")
                    false
                }

                taskView !== currentPageTaskView -> {
                    debugLog(TAG, "Not swiping down, not current page.")
                    false
                }

                taskView.isGridTask -> {
                    debugLog(TAG, "Not swiping down on grid taskView.")
                    false
                }

                !isTaskInExpectedScrollPosition(taskView) -> {
                    debugLog(TAG, "Not swiping down, taskView not in middle")
                    false
                }

                else -> true
            }
        }

    companion object {
        class RecentsViewFloatProperty(
            private val utilsProperty: KMutableProperty1<RecentsViewUtils, Float>
        ) : FloatProperty<RecentsView<*, *>>(utilsProperty.name) {
            override fun get(recentsView: RecentsView<*, *>): Float =
                utilsProperty.get(recentsView.mUtils)

            override fun setValue(recentsView: RecentsView<*, *>, value: Float) {
                utilsProperty.set(recentsView.mUtils, value)
            }
        }

        @JvmField
        val DESK_EXPLODE_PROGRESS = RecentsViewFloatProperty(RecentsViewUtils::deskExplodeProgress)

        val TEMP_RECT = Rect()
    }
}
