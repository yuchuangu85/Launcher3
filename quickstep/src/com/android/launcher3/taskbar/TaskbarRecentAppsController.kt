/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.content.Context
import android.os.UserHandle
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.launcher3.BubbleTextView.RunningAppState
import com.android.launcher3.Flags
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.graphics.ThemeManager.ThemeChangeListener
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.model.data.TaskItemInfo.Companion.isSameItem
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.launcher3.taskbar.TaskbarPopupController.canPinAppWithContextMenu
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.SafeCloseable
import com.android.quickstep.RecentsFilterState
import com.android.quickstep.RecentsModel
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SplitTask
import com.android.quickstep.util.TaskVisualsChangeListener
import com.android.systemui.shared.Flags.enableRecentsInTaskbar
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import java.io.PrintWriter

/**
 * Provides recent apps functionality, when the Taskbar Recent Apps section is enabled. Behavior:
 * - When in Fullscreen mode: show the N most recent Tasks
 * - When in Desktop mode: show the currently running (open) Tasks
 */
class TaskbarRecentAppsController(
    private val context: Context,
    private val recentsModel: RecentsModel,
    private val themeManager: ThemeManager,
    private val desktopModeCompatPolicy: DesktopModeCompatPolicy,
) : LoggableTaskbarController {

    var canShowRunningApps = DesktopModeStatus.canEnterDesktopMode(context)
        @VisibleForTesting
        set(isEnabledFromTest) {
            field = isEnabledFromTest
            if (!field && !canShowRecentApps) {
                if (enableTaskbarUiThread()) {
                    recentTasksChangedListenerClosable?.close()
                    recentTasksChangedListenerClosable = null
                } else {
                    recentsModel.unregisterRecentTasksChangedListener(recentTasksChangedListener)
                }
            }
        }

    // TODO(b/343532825): Add a setting to disable Recents even when the flag is on.
    var canShowRecentApps = enableRecentsInTaskbar()
        @VisibleForTesting
        set(isEnabledFromTest) {
            field = isEnabledFromTest
            if (!field && !canShowRunningApps) {
                if (enableTaskbarUiThread()) {
                    recentTasksChangedListenerClosable?.close()
                    recentTasksChangedListenerClosable = null
                } else {
                    recentsModel.unregisterRecentTasksChangedListener(recentTasksChangedListener)
                }
            }
        }

    /** `true` if recent icons are replacing predictions. */
    val isReplacingPredictions: Boolean
        get() {
            val showDesktopTasks =
                controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()
            return (showDesktopTasks && canShowRunningApps) ||
                (!showDesktopTasks && canShowRecentApps)
        }

    // Initialized in init.
    private lateinit var controllers: TaskbarControllers

    var shownHotseatItems: List<ItemInfo> = emptyList()
        private set

    private var allRecentTasks: List<GroupTask> = emptyList()
    private var taskbarRunningTasks: List<GroupTask> = emptyList()
    // Keeps track of the order in which running tasks appear.
    private var orderedRunningTaskIds = emptyList<Int>()
    var shownTasks: List<GroupTask> = emptyList()
        private set

    val shownTaskIds: List<Int>
        get() = shownTasks.flatMap { shownTask -> shownTask.tasks }.map { it.key.id }

    private var itemMarkedForDeletion: ItemInfo? = null

    fun setItemMarkedForDeletion(item: ItemInfo, deleted: Boolean): Boolean {
        var changed = false
        if (deleted && itemMarkedForDeletion?.isSameItem(item) != true) {
            itemMarkedForDeletion = item
            changed = true
        } else if (!deleted && itemMarkedForDeletion?.isSameItem(item) == true) {
            itemMarkedForDeletion = null
            changed = true
        }

        return changed
    }

    /**
     * The task-state of an app, i.e. whether the app has a task and what state that task is in.
     *
     * @property taskId The ID of the task if one exists (i.e. if the state is RUNNING or
     *   MINIMIZED), null otherwise (NOT_RUNNING).
     */
    data class TaskState(val runningAppState: RunningAppState, val taskId: Int? = null)

    /**
     * Returns the state of the most active Running task represented by the given [ItemInfo].
     *
     * If there are several tasks represented by the same [ItemInfo] we return the most active one,
     * i.e. we return [RunningAppState.RUNNING] over [RunningAppState.MINIMIZED], and
     * [RunningAppState.MINIMIZED] over [RunningAppState.NOT_RUNNING].
     */
    fun getTaskbarItemState(itemInfo: ItemInfo?): TaskState {
        val packageName =
            itemInfo?.getTargetPackage() ?: return TaskState(RunningAppState.NOT_RUNNING)
        return getTaskbarTaskState(packageName, itemInfo.user.identifier)
    }

    private fun getTaskbarTaskState(packageName: String, userId: Int): TaskState {
        if (taskbarRunningTasks.isEmpty()) {
            return TaskState(RunningAppState.NOT_RUNNING)
        }
        val appTasks =
            taskbarRunningTasks
                .flatMap { it.tasks }
                .filter { task -> packageName == task.key.packageName && task.key.userId == userId }
        val runningTask = appTasks.find { getRunningAppState(it.key.id) == RunningAppState.RUNNING }
        if (runningTask != null) {
            return TaskState(RunningAppState.RUNNING, runningTask.key.id)
        }
        val minimizedTask =
            appTasks.find { getRunningAppState(it.key.id) == RunningAppState.MINIMIZED }
        if (minimizedTask != null) {
            return TaskState(RunningAppState.MINIMIZED, taskId = minimizedTask.key.id)
        }
        return TaskState(RunningAppState.NOT_RUNNING)
    }

    /** Get the [RunningAppState] for the given task. */
    fun getRunningAppState(taskId: Int): RunningAppState {
        return when (taskId) {
            in minimizedTaskIds -> RunningAppState.MINIMIZED
            in runningTaskIds -> RunningAppState.RUNNING
            else -> RunningAppState.NOT_RUNNING
        }
    }

    /** Returns the single task (i.e., fullscreen) represented by the given [itemInfo]. */
    fun getSingleTask(itemInfo: ItemInfo?): SingleTask? {
        val packageName = itemInfo?.targetPackage ?: return null
        return allRecentTasks.find { task ->
            task is SingleTask &&
                packageName == task.task.key.packageName &&
                task.task.key.userId == itemInfo.user.identifier
        } as? SingleTask
    }

    /** Returns the non-desktop task represented by the given [itemInfo]. */
    fun getNonDesktopTask(itemInfo: ItemInfo?): Task? {
        val packageName = itemInfo?.targetPackage ?: return null
        val userId = itemInfo.user.identifier
        return allRecentTasks
            .filterNot { it is DesktopTask }
            .flatMap { it.tasks }
            .find { task -> packageName == task.key.packageName && userId == task.key.userId }
    }

    @VisibleForTesting
    val runningTaskIds: Set<Int>
        /**
         * Returns the task IDs of apps that should be indicated as "running" to the user.
         * Specifically, we return all the open tasks currently tracked by the Taskbar, else
         * emptySet().
         */
        get() {
            if (
                !canShowRunningApps ||
                    !controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()
            ) {
                return emptySet()
            }
            return taskbarRunningTasks.flatMap { it.tasks }.map { it.key.id }.toSet()
        }

    @VisibleForTesting
    val minimizedTaskIds: Set<Int>
        /**
         * Returns the task IDs for the tasks that should be indicated as "minimized" to the user.
         */
        get() {
            if (
                !canShowRunningApps ||
                    !controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()
            ) {
                return emptySet()
            }
            // The indicator only indicates whether the window is minimized or not. This means an
            // opened window inside an inactive desk will still have long app indicator inside the
            // taskbar.
            return taskbarRunningTasks
                .flatMap { it.tasks }
                .filter { task -> task.isMinimized }
                .map { task -> task.key.id }
                .toSet()
        }

    private val recentTasksChangedListener =
        RecentsModel.RecentTasksChangedListener { reloadRecentTasksIfNeeded() }

    private val taskVisualsChangeListener =
        object : TaskVisualsChangeListener {
            override fun onTaskIconChanged(pkg: String, user: UserHandle) {
                getTaskbarUiThread().execute {
                    for (groupTask in shownTasks) {
                        for ((i, task) in groupTask.tasks.withIndex()) {
                            if (task.key.packageName == pkg && task.key.userId == user.identifier) {
                                fetchIconForTask(groupTask, i, forceUpdate = true)
                            }
                        }
                    }
                }
            }
        }

    private val iconLoadRequests: MutableSet<CancellableTask<*>> = HashSet()

    private var recentTasksChangedListenerClosable: SafeCloseable? = null

    // TODO(b/343291428): add TaskVisualsChangListener as well (for calendar/clock?)

    // Used to keep track of the last requested task list ID, so that we do not request to load the
    // tasks again if we have already requested it and the task list has not changed
    private var taskListChangeId = -1

    // Whether we're currently loading recents tasks
    private var loadingRecentsTasks = false
    // Whether we need to reload recents tasks when the current loading operation is finished
    private var needsRecentsTasksReload = false
    // Whether we've loaded recents tasks at least once
    private var recentTasksLoaded = false

    private var iconShapeDataCloseable: SafeCloseable? = null
    private var themeChangeListener: ThemeChangeListener? = null

    fun init(taskbarControllers: TaskbarControllers, previousShownTasks: List<GroupTask>) {
        controllers = taskbarControllers
        if (
            !controllers.taskbarActivityContext.deviceProfile.deviceProperties.taskbarConfiguration
                .isTaskbarPresent
        )
            return

        if (previousShownTasks.isNotEmpty()) {
            shownTasks = previousShownTasks
            fetchIcons()
        }
        orderedRunningTaskIds =
            controllers.sharedState?.recentOrderedRunningTaskIds?.filterNotNull() ?: emptyList()
        if (canShowRunningApps || canShowRecentApps) {
            if (enableTaskbarUiThread()) {
                recentTasksChangedListenerClosable?.close()
                recentTasksChangedListenerClosable =
                    recentsModel.tasksChanges.forEach(getTaskbarUiThread()) {
                        reloadRecentTasksIfNeeded()
                    }
            } else {
                recentsModel.registerRecentTasksChangedListener(recentTasksChangedListener)
            }
            recentsModel.addThumbnailChangeListener(taskVisualsChangeListener)

            controllers.runAfterInit { reloadRecentTasksIfNeeded() }
            // Both callbacks force an icon fetch, because these changes may affect how icons
            // are generated from BitmapInfo.
            iconShapeDataCloseable =
                themeManager.iconShapeData.forEach(getTaskbarUiThread()) {
                    fetchIcons(forceUpdate = true)
                }
            themeChangeListener =
                ThemeChangeListener {
                        getTaskbarUiThread().execute { fetchIcons(forceUpdate = true) }
                    }
                    .also { themeManager.addChangeListener(it) }
        }
    }

    fun onDestroy() {
        controllers.sharedState?.recentTasksBeforeTaskbarRecreate?.clear()
        if (shownTasks.isNotEmpty()) {
            controllers.sharedState?.recentTasksBeforeTaskbarRecreate?.addAll(shownTasks)
        }
        controllers.sharedState?.recentOrderedRunningTaskIds?.clear()
        if (orderedRunningTaskIds.isNotEmpty()) {
            controllers.sharedState?.recentOrderedRunningTaskIds?.addAll(orderedRunningTaskIds)
        }
        recentsModel.removeThumbnailChangeListener(taskVisualsChangeListener)
        if (enableTaskbarUiThread()) {
            recentTasksChangedListenerClosable?.close()
            recentTasksChangedListenerClosable = null
        } else {
            recentsModel.unregisterRecentTasksChangedListener(recentTasksChangedListener)
        }
        cancelIconLoadRequests()
        iconShapeDataCloseable?.close()
        themeChangeListener?.let { themeManager.removeChangeListener(it) }
    }

    private fun cancelIconLoadRequests() {
        for (it in iconLoadRequests) it.cancel()
        iconLoadRequests.clear()
    }

    /** Called to update hotseatItems, in order to de-dupe them from Recent/Running tasks later. */
    fun updateHotseatItemInfos(hotseatItems: Array<ItemInfo?>): Array<ItemInfo?> {
        // Ignore predicted apps - we show running or recent apps instead.
        if (!isReplacingPredictions) {
            shownHotseatItems = hotseatItems.filterNotNull()
            onRecentsOrHotseatChanged()
            return hotseatItems
        }
        if (hotseatItems.none { itemInfo -> itemInfo?.isSameItem(itemMarkedForDeletion) == true }) {
            itemMarkedForDeletion = null
        }

        shownHotseatItems =
            hotseatItems
                .filterNotNull()
                .filter { itemInfo -> !itemInfo.isPredictedItem }
                .filter { itemInfo -> itemMarkedForDeletion?.isSameItem(itemInfo) != true }
                .toMutableList()

        val showDesktopTasks =
            controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()
        if (showDesktopTasks && canShowRunningApps) {
            shownHotseatItems =
                updateHotseatItemsFromRunningTasks(
                    getOrderedAndWrappedRunningTasks(),
                    shownHotseatItems,
                )
        }

        if (recentTasksLoaded) {
            onRecentsOrHotseatChanged()
        }

        return shownHotseatItems.toTypedArray()
    }

    fun getRunningTaskWithId(id: Int): Task? {
        return taskbarRunningTasks.flatMap { it.tasks }.find { it.key.id == id }
    }

    private fun getOrderedAndWrappedRunningTasks(): List<SingleTask> {
        // We wrap each task in the Taskbar as a `SingleTask`.
        val orderFromId = orderedRunningTaskIds.withIndex().associate { (index, id) -> id to index }
        return taskbarRunningTasks
            .filterIsInstance<SingleTask>()
            .sortedWith(compareBy(nullsLast()) { orderFromId[it.task.key.id] })
    }

    private fun reloadRecentTasksIfNeeded() {
        if (recentsModel.isTaskListValid(taskListChangeId)) return
        if (loadingRecentsTasks) {
            Log.v(TAG, "reloadRecentTasksIfNeeded: tried to reload while loading recents tasks")
            needsRecentsTasksReload = true
            return
        }
        Log.v(TAG, "reloadRecentTasksIfNeeded: load recents tasks")
        loadingRecentsTasks = true
        taskListChangeId =
            recentsModel.getTasks(RecentsFilterState.EMPTY_FILTER) { tasks ->
                getTaskbarUiThread().execute {
                    loadingRecentsTasks = false
                    recentTasksLoaded = true
                    allRecentTasks = tasks
                    val oldRunningTaskdIds = runningTaskIds
                    val oldMinimizedTaskIds = minimizedTaskIds
                    taskbarRunningTasks =
                        allRecentTasks.flatMap { group ->
                            when (group) {
                                is DesktopTask -> {
                                    // Apply current filters: remove transparent overlays and map to
                                    // individual icons
                                    group.tasks
                                        .filterNot { task ->
                                            desktopModeCompatPolicy.isTransparentOverlay(
                                                task.key.isActivityStackTransparent,
                                                task.key.numActivities,
                                                task.key.windowingMode,
                                            )
                                        }
                                        .map { task -> SingleTask(task) }
                                }

                                // CURRENTLY IGNORED: Preserve current behavior by returning empty
                                // lists
                                is SplitTask -> emptyList()
                                is SingleTask -> emptyList()
                                else -> emptyList<GroupTask>()
                            }
                        }
                    val runningTasksChanged = oldRunningTaskdIds != runningTaskIds
                    val minimizedTasksChanged = oldMinimizedTaskIds != minimizedTaskIds

                    if (
                        (onRecentsOrHotseatChanged() ||
                            runningTasksChanged ||
                            minimizedTasksChanged) &&
                            !controllers.taskbarDesktopModeController.isLauncherAnimationRunning
                    ) {
                        controllers.taskbarViewController.commitRunningAppsToUI()
                    }
                    if (needsRecentsTasksReload) {
                        Log.v(TAG, "reloadRecentTasksIfNeeded: reload recents tasks")
                        needsRecentsTasksReload = false
                        reloadRecentTasksIfNeeded()
                    }
                }
            }
    }

    /**
     * Updates [shownTasks] when Recents or Hotseat changes.
     *
     * @return Whether [shownTasks] changed.
     */
    private fun onRecentsOrHotseatChanged(): Boolean {
        val oldShownTasks = shownTasks
        orderedRunningTaskIds = updateOrderedRunningTaskIds()
        shownTasks =
            if (controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()) {
                computeShownRunningTasks()
            } else {
                computeShownRecentTasks()
            }
        if (oldShownTasks == shownTasks) return false
        getTaskbarUiThread().execute { fetchIcons() }
        return true
    }

    /**
     * Fetches the icons for shown tasks.
     *
     * Only updates the task views if the bitmap info has changed or [forceUpdate] is `true`.
     */
    private fun fetchIcons(forceUpdate: Boolean = false) {
        Preconditions.assertTaskbarUiThread()
        if (enableRecentsInTaskbar()) {
            cancelIconLoadRequests() // Cancel any previous requests.
        }

        for (groupTask in shownTasks) {
            for (i in groupTask.tasks.indices) {
                fetchIconForTask(groupTask, i, forceUpdate)
            }
        }
    }

    private fun fetchIconForTask(groupTask: GroupTask, index: Int, forceUpdate: Boolean = false) {
        val task = groupTask.tasks[index]
        val cancellableTask =
            recentsModel.iconCache.getBitmapInfoInBackground(task, getTaskbarUiThread()) { bi, d, t
                ->
                if (
                    !forceUpdate &&
                        bi === groupTask.bitmapInfos[index] &&
                        d == task.titleDescription &&
                        t == task.title
                ) {
                    return@getBitmapInfoInBackground
                }
                groupTask.bitmapInfos[index] = bi
                task.titleDescription = d
                task.title = t
                controllers.taskbarViewController.onTaskUpdated(task, groupTask)
            }
        if (cancellableTask != null) {
            iconLoadRequests.add(cancellableTask)
        }
    }

    private fun updateOrderedRunningTaskIds(): MutableList<Int> {
        val runningTasksAsList = getOrderedAndWrappedRunningTasks().map { it.task }
        val runningTaskIds = runningTasksAsList.map { it.key.id }
        var newOrder =
            orderedRunningTaskIds
                .filter { it in runningTaskIds } // Only keep the tasks that are still running
                .toMutableList()
        // Add new tasks not already listed
        newOrder.addAll(runningTaskIds.filter { it !in newOrder })
        return newOrder
    }

    /**
     * Computes the list of running tasks to be shown in the recent apps section of the taskbar,
     * taking into account deduplication against hotseat items and existing tasks.
     */
    private fun computeShownRunningTasks(): List<GroupTask> {
        if (!canShowRunningApps) {
            return emptyList()
        }

        val runningTasks = getOrderedAndWrappedRunningTasks()

        val newShownTasks =
            if (Flags.enableMultiInstanceMenuTaskbar()) {
                val deduplicatedRunningTasks =
                    runningTasks.distinctBy { Pair(it.task.key.packageName, it.task.key.userId) }
                val activityContext = controllers.taskbarActivityContext

                shownTasks
                    .filter {
                        it is SingleTask &&
                            it.task.key.id in deduplicatedRunningTasks.map { it.task.key.id } &&
                            (!canPinAppWithContextMenu(activityContext) ||
                                shownHotseatItems.none { hotseatItem ->
                                    it.containsPackage(
                                        hotseatItem.targetPackage,
                                        hotseatItem.user.identifier,
                                    )
                                })
                    }
                    .toMutableList()
                    .apply {
                        addAll(
                            deduplicatedRunningTasks.filter { currentTask ->
                                val currentTaskKey = currentTask.task.key
                                currentTaskKey.id !in shownTaskIds &&
                                    shownHotseatItems.none { hotseatItem ->
                                        currentTask.containsPackage(
                                            hotseatItem.targetPackage,
                                            hotseatItem.user.identifier,
                                        )
                                    }
                            }
                        )
                    }
            } else {
                val taskIds = runningTasks.map { it.task.key.id }
                val shownHotseatItemTaskIds =
                    shownHotseatItems.mapNotNull { it as? TaskItemInfo }.map { it.taskId }

                shownTasks
                    .filter { it is SingleTask && it.task.key.id in taskIds }
                    .toMutableList()
                    .apply {
                        addAll(
                            runningTasks.filter { runningTask ->
                                runningTask.task.key.id !in shownTaskIds
                            }
                        )
                        removeAll { it is SingleTask && it.task.key.id in shownHotseatItemTaskIds }
                    }
            }

        return newShownTasks
    }

    private fun computeShownRecentTasks(): List<GroupTask> {
        if (!canShowRecentApps || allRecentTasks.isEmpty()) {
            return emptyList()
        }
        // Remove the current task.
        val allRecentTasks = allRecentTasks.subList(0, allRecentTasks.size - 1)
        var nextShownTasks = dedupeHotseatTasks(allRecentTasks, shownHotseatItems)
        if (nextShownTasks.size > MAX_RECENT_TASKS) {
            // Remove any tasks older than MAX_RECENT_TASKS.
            nextShownTasks =
                nextShownTasks.subList(nextShownTasks.size - MAX_RECENT_TASKS, nextShownTasks.size)
        }

        // Reuse matching previous GroupTasks, which may already tag a View and/or have BitmapInfo.
        val prevTasksSet = shownTasks.toSet()
        return nextShownTasks.map { n -> prevTasksSet.find { p -> p == n } ?: n }
    }

    private fun dedupeHotseatTasks(
        groupTasks: List<GroupTask>,
        shownHotseatItems: List<ItemInfo>,
    ): List<GroupTask> {
        // TODO: b/393476333 - Check the behavior of the Taskbar recents section when empty desks
        // become supported.
        return if (Flags.enableMultiInstanceMenuTaskbar()) {
            groupTasks.filter { groupTask ->
                // Keep tasks that are group tasks or unique package name/user combinations
                when (groupTask) {
                    is SingleTask ->
                        shownHotseatItems.none {
                            groupTask.containsPackage(it.targetPackage, it.user.identifier)
                        }
                    is SplitTask ->
                        shownHotseatItems.filterIsInstance<AppPairInfo>().none {
                            val firstPackage = it.getFirstApp().targetPackage
                            val secondPackage = it.getSecondApp().targetPackage
                            val userId = it.user.identifier
                            // Dedupe even if the app order is swapped.
                            groupTask.containsPackage(firstPackage, userId) &&
                                groupTask.containsPackage(secondPackage, userId)
                        }
                    else -> true
                }
            }
        } else {
            val hotseatPackages = shownHotseatItems.map { it.targetPackage }
            groupTasks.filter { groupTask ->
                when (groupTask) {
                    is SingleTask -> hotseatPackages.none { groupTask.containsPackage(it) }

                    else -> true
                }
            }
        }
    }

    /**
     * Returns the hotseat items updated so that any item that points to a package+user with a
     * running task also references that task.
     */
    private fun updateHotseatItemsFromRunningTasks(
        groupTasks: List<GroupTask>,
        shownHotseatItems: List<ItemInfo>,
    ): List<ItemInfo> =
        shownHotseatItems.map { itemInfo ->
            if (itemInfo is TaskItemInfo) {
                itemInfo
            } else {
                val foundTask =
                    groupTasks
                        .flatMap { it.tasks }
                        .find { task ->
                            task.key.packageName == itemInfo.targetPackage &&
                                task.key.userId == itemInfo.user.identifier
                        } ?: return@map itemInfo
                TaskItemInfo(foundTask.key.id, itemInfo as WorkspaceItemInfo)
            }
        }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println("$prefix TaskbarRecentAppsController:")
        pw.println("$prefix\tcanShowRunningApps=$canShowRunningApps")
        pw.println("$prefix\tcanShowRecentApps=$canShowRecentApps")
        pw.println("$prefix\tshownHotseatItems=${shownHotseatItems.map{item->item.targetPackage}}")
        pw.println("$prefix\tallRecentTasks=${allRecentTasks.map { it.packageNames }}")
        pw.println("$prefix\ttaskbarRunningTasks=$taskbarRunningTasks")
        pw.println("$prefix\tshownTasks=${shownTasks.map { it.packageNames }}")
        pw.println("$prefix\trunningTaskIds=$runningTaskIds")
        pw.println("$prefix\tminimizedTaskIds=$minimizedTaskIds")
    }

    private val GroupTask.packageNames: List<String>
        get() = tasks.map { task -> task.key.packageName }

    private companion object {
        private val TAG = "TaskbarRecentAppsController"

        const val MAX_RECENT_TASKS = 2
    }
}
