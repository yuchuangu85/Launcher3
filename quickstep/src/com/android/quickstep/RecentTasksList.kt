/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep

import android.app.ActivityManager.RunningTaskInfo
import android.app.KeyguardManager
import android.app.TaskInfo
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
import android.os.Process
import android.util.Log
import android.util.SparseBooleanArray
import android.view.Display
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.content.getSystemService
import com.android.launcher3.Flags.hideAutomatedTasksInOverview
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.OverviewReleaseFlags.enableLaterIsLockedCheck
import com.android.quickstep.RecentsModel.RecentTasksChangedListener
import com.android.quickstep.SystemUiProxy.GetRecentTasksException
import com.android.quickstep.recents.data.RecentTasksKeysDataSource
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SplitTask
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.wm.shell.Flags.enableShellTopTaskTracking
import com.android.wm.shell.recents.IRecentTasksListener
import com.android.wm.shell.shared.GroupedTaskInfo
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_DESK
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_FULLSCREEN
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_SPLIT
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.split.SplitBounds
import java.io.PrintWriter
import java.util.concurrent.Executor
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Predicate
import javax.inject.Inject

/** Manages the recent task list from the system, caching it as necessary. */
@LauncherAppSingleton
class RecentTasksList
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val mainThreadExecutor: LooperExecutor,
    private val sysUiProxy: SystemUiProxy,
    private val topTaskTracker: TopTaskTracker,
    tracker: DaggerSingletonTracker,
    private val automationRepository: AutomationRepository,
    @LightweightBackground(UI) private val lightweightBackgroundExecutor: Executor,
    private val desktopState: DesktopState,
) : RecentTasksKeysDataSource {

    private val keyguardManager: KeyguardManager? = context.getSystemService()
    private val virtualDeviceManager: VirtualDeviceManager? = context.getSystemService()

    // The list change id, increments as the task list changes in the system
    private var changeId: Int = 1

    private var resultsBg = INVALID_RESULT
    private var resultsUi = INVALID_RESULT

    private var recentTasksChangedListener: RecentTasksChangedListener? = null

    // Track displays that belong to virtual devices. Tasks on such displays are treated as if
    // they are running on the default display.
    // TODO(b/443015666): Move this logic upstream to WM Shell or WM Core.
    private val virtualDeviceDisplays =
        object : SparseBooleanArray() {
            override fun get(displayId: Int): Boolean {
                if (indexOfKey(displayId) < 0) {
                    val isVirtualDeviceDisplay =
                        (virtualDeviceManager != null &&
                            virtualDeviceManager.getDeviceIdForDisplayId(displayId) !=
                                Context.DEVICE_ID_DEFAULT)
                    put(displayId, isVirtualDeviceDisplay)
                }
                return super.get(displayId)
            }
        }

    init {
        val recentTasksListener =
            object : IRecentTasksListener.Stub() {
                override fun onRecentTasksChanged() {
                    mainThreadExecutor.execute { this@RecentTasksList.onRecentTasksChanged() }
                }

                override fun onRunningTaskAppeared(taskInfo: RunningTaskInfo) {
                    // Do nothing
                }

                override fun onRunningTaskVanished(taskInfo: RunningTaskInfo) {
                    // Do nothing
                }

                override fun onRunningTaskChanged(taskInfo: RunningTaskInfo) {
                    // Do nothing
                }

                override fun onTaskMovedToFront(taskToFront: GroupedTaskInfo) {
                    mainThreadExecutor.execute {
                        topTaskTracker.handleTaskMovedToFront(taskToFront.baseGroupedTask.taskInfo1)
                    }
                }

                override fun onTaskInfoChanged(taskInfo: RunningTaskInfo) {
                    mainThreadExecutor.execute { topTaskTracker.onTaskChanged(taskInfo) }
                }

                override fun onVisibleTasksChanged(visibleTasks: Array<GroupedTaskInfo>) {
                    mainThreadExecutor.execute {
                        topTaskTracker.onVisibleTasksChanged(visibleTasks)
                    }
                }
            }

        tracker.addCloseable(sysUiProxy.recentTasksListeners.register(recentTasksListener))
    }

    /** Fetches the task keys skipping any local cache. */
    fun getTaskKeys(numTasks: Int, callback: Consumer<List<GroupTask>>) {
        // Kick off task loading in the background
        lightweightBackgroundExecutor.execute {
            val tasks = getTaskKeys(numTasks)
            mainThreadExecutor.execute { callback.accept(tasks) }
        }
    }

    /** Fetches the task keys skipping any local cache. */
    @WorkerThread
    override fun getTaskKeys(numTasks: Int): List<GroupTask> =
        loadTasksInBackground(numTasks, requestId = -1, loadKeysOnly = true)

    /**
     * Asynchronously fetches the list of recent tasks, reusing cached list if available.
     *
     * @param loadKeysOnly Whether to load other associated task data, or just the key
     * @param callback The callback to receive the list of recent tasks
     * @return The change id of the current task list
     */
    @Synchronized
    fun getTasks(
        loadKeysOnly: Boolean,
        callback: Consumer<List<GroupTask>>?,
        filter: Predicate<GroupTask>,
    ): Int =
        getTasks(
            loadKeysOnly,
            if (callback == null) null else BiConsumer { tasks, _ -> callback.accept(tasks) },
            filter,
        )

    /**
     * Asynchronously fetches the list of recent tasks, reusing cached list if available.
     *
     * @param loadKeysOnly Whether to load other associated task data, or just the key
     * @param callback The callback to receive the list of recent tasks
     * @return The change id of the current task list
     */
    @Synchronized
    fun getTasks(
        loadKeysOnly: Boolean,
        callback: BiConsumer<List<GroupTask>, Int>?,
        filter: Predicate<GroupTask>,
    ): Int {
        val requestLoadId = changeId
        if (resultsUi.isValidForRequest(requestLoadId, loadKeysOnly)) {
            Log.d(TAG, "getTasks - mResultsUi still valid: ${resultsUi.requestId}")
            // The list is up to date, send the callback on the next frame,
            // so that requestID can be returned first.
            if (callback != null) {
                // Copy synchronously as the changeId might change by next frame
                // and filter GroupTasks
                val result = resultsUi.filter { filter.test(it) }.map { it.copy() }

                mainThreadExecutor.post { callback.accept(result, requestLoadId) }
            }

            return requestLoadId
        }

        // Kick off task loading in the background
        lightweightBackgroundExecutor.execute {
            if (!resultsBg.isValidForRequest(requestLoadId, loadKeysOnly)) {
                resultsBg = loadTasksInBackground(Int.MAX_VALUE, requestLoadId, loadKeysOnly)
                Log.d(TAG, "getTasks - loadTasksInBackground: ${resultsBg.requestId}")
            }
            val loadResult = resultsBg
            mainThreadExecutor.execute {
                resultsUi = loadResult
                Log.d(TAG, "getTasks - updating mResultsUi: ${resultsUi.requestId}")
                if (callback != null) {
                    // filter the tasks if needed before passing them into the callback
                    val result = resultsUi.filter { filter.test(it) }.map { it.copy() }
                    callback.accept(result, requestLoadId)
                }
            }
        }

        return requestLoadId
    }

    /** @return Whether the provided [changeId] is the latest recent tasks list id. */
    @Synchronized fun isTaskListValid(changeId: Int): Boolean = this.changeId == changeId

    fun onRecentTasksChanged() {
        invalidateLoadedTasks()
        recentTasksChangedListener?.onRecentTasksChanged()
    }

    @Synchronized
    private fun invalidateLoadedTasks() {
        Log.d(TAG, "invalidateLoadedTasks - previous requestLoadId: ${resultsBg.requestId}")
        lightweightBackgroundExecutor.execute { resultsBg = INVALID_RESULT }
        resultsUi = INVALID_RESULT
        changeId++
    }

    /** Sets a listener for running tasks */
    fun setRecentTasksChangedListener(listener: RecentTasksChangedListener) {
        recentTasksChangedListener = listener
    }

    /** Removes the previously set running tasks listener */
    fun clearRecentTasksChangedListener() {
        recentTasksChangedListener = null
    }

    /** Loads and creates a list of all the recent tasks. */
    @VisibleForTesting
    fun loadTasksInBackground(
        numTasks: Int,
        requestId: Int,
        loadKeysOnly: Boolean,
    ): TaskLoadResult {
        val currentUserId = Process.myUserHandle().identifier
        val rawTasks: ArrayList<GroupedTaskInfo>
        try {
            rawTasks = sysUiProxy.getRecentTasks(numTasks, currentUserId)
        } catch (e: GetRecentTasksException) {
            return INVALID_RESULT
        }
        // The raw tasks are given in most-recent to least-recent order, we need to reverse it
        rawTasks.reverse()

        val tmpLockedUsers =
            object : SparseBooleanArray() {
                override fun get(key: Int): Boolean {
                    if (!enableLaterIsLockedCheck() && indexOfKey(key) < 0) {
                        // Fill the cached locked state as we fetch
                        put(key, keyguardManager?.isDeviceLocked(key) == true)
                        Log.d(TAG, "loadTasksInBackground - isDeviceLocked($key): ${get(key)}")
                    }
                    return super.get(key)
                }
            }

        val allTasks = TaskLoadResult(requestId, loadKeysOnly, rawTasks.size)

        var isFirstVisibleTaskFound = false
        rawTasks.forEach { rawTask ->
            if (rawTask.isBaseType(TYPE_DESK)) {
                // TYPE_DESK tasks is only created when desktop mode can be entered,
                // leftover TYPE_DESK tasks created when flag was on should be ignored.
                if (desktopState.canEnterDesktopMode) {
                    val desktopTasks = createDesktopTasks(rawTask.baseGroupedTask)
                    allTasks.addAll(desktopTasks)

                    // If any task in desktop group task is visible, set isFirstVisibleTaskFound to
                    // true. This way if there is a transparent task in the list later on, it does
                    // not get its own tile in Overview.
                    if (rawTask.baseGroupedTask.taskInfoList.any { it.isVisible }) {
                        isFirstVisibleTaskFound = true
                    }
                }
                return@forEach
            }

            val keyOnly = loadKeysOnly && !enableShellTopTaskTracking()

            // [getTaskInfo1] will not be null for types below beside [TYPE_DESK]
            val taskInfo1 =
                if (enableShellTopTaskTracking()) rawTask.baseGroupedTask.taskInfo1!!
                else rawTask.taskInfo1!!
            val task1 = createTask(taskInfo1, keyOnly, tmpLockedUsers)

            val task2: Task?
            val splitBounds: SplitBounds?

            if (rawTask.isBaseType(TYPE_SPLIT)) {
                val taskInfo2 =
                    if (enableShellTopTaskTracking()) rawTask.baseGroupedTask.taskInfo2!!
                    else rawTask.taskInfo2!!
                task2 = createTask(taskInfo2, loadKeysOnly, tmpLockedUsers)
                splitBounds =
                    if (enableShellTopTaskTracking()) rawTask.baseGroupedTask.splitBounds
                    else rawTask.splitBounds
            } else {
                task2 = null
                splitBounds = null
            }

            if (!enableShellTopTaskTracking()) {
                if (isFirstVisibleTaskFound) {
                    if (
                        rawTask.isBaseType(TYPE_FULLSCREEN) &&
                            taskInfo1.isTopActivityTransparent &&
                            (taskInfo1.baseIntent.flags and FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0
                    ) {
                        // If there are already visible tasks, then ignore the excluded tasks
                        // and don't add them to the returned list
                        return@forEach
                    }
                } else if (taskInfo1.isVisible && task1 != null) {
                    isFirstVisibleTaskFound = true
                }
            }

            when {
                task1 != null && task2 != null -> allTasks.add(SplitTask(task1, task2, splitBounds))
                task1 != null -> allTasks.add(SingleTask(task1))
                task2 != null -> allTasks.add(SingleTask(task2))
            }
        }

        return allTasks
    }

    private fun createTask(
        taskInfo: TaskInfo,
        keyOnly: Boolean,
        lockedUsers: SparseBooleanArray,
    ): Task? {
        val taskKey = createTaskKey(taskInfo)
        if (isTaskAutomated(taskKey)) return null

        return if (keyOnly) Task(taskKey)
        else Task.from(taskKey, taskInfo, /* isLocked= */ lockedUsers[taskKey.userId])
    }

    private fun isTaskAutomated(taskKey: TaskKey): Boolean {
        if (!hideAutomatedTasksInOverview()) {
            return false
        }
        return automationRepository.isPackageAutomated(taskKey.userId, taskKey.packageName)
    }

    private fun createTask(taskInfo: TaskInfo, minimizedTaskIds: Set<Int>): Task =
        Task.from(createTaskKey(taskInfo), taskInfo, false).apply {
            positionInParent = taskInfo.positionInParent
            appBounds = taskInfo.configuration.windowConfiguration.appBounds
            isVisible = taskInfo.isVisible
            isMinimized = minimizedTaskIds.contains(taskInfo.taskId)
        }

    private fun createTaskKey(taskInfo: TaskInfo): TaskKey {
        val displayId = getRecentsDisplayId(taskInfo.displayId)
        return TaskKey(taskInfo, displayId)
    }

    /**
     * If the display id belongs to a virtual device, it should be treated as if it is running on
     * the default display.
     *
     * @return The mapped display id of the given display id
     */
    fun getRecentsDisplayId(displayId: Int) =
        if (virtualDeviceDisplays[displayId]) Display.DEFAULT_DISPLAY else displayId

    private fun createDesktopTasks(recentTaskInfo: GroupedTaskInfo): List<DesktopTask> {
        val minimizedTaskIdArray = recentTaskInfo.minimizedTaskIds
        val minimizedTaskIds = minimizedTaskIdArray?.toSet() ?: emptySet()
        val deskId = recentTaskInfo.deskId
        val displayId = recentTaskInfo.deskDisplayId
        val tasks =
            recentTaskInfo.taskInfoList
                .map { createTask(it, minimizedTaskIds) }
                .filterNot { isTaskAutomated(it.key) }
        return listOf(DesktopTask(deskId, displayId, tasks))
    }

    fun dump(prefix: String, writer: PrintWriter) {
        writer.println("$prefix RecentTasksList:")
        writer.println("$prefix   mChangeId=$changeId")
        writer.println("$prefix   mResultsUi=[id=" + resultsUi.requestId + ", tasks=")
        resultsUi.forEach { task ->
            task.tasks.forEachIndexed { index, t ->
                val cn = t.topComponent
                writer.println(
                    "$prefix     t${index}: (id=${t.key.id}; package=${if (cn != null) cn.packageName + ")" else "no package)"}"
                )
            }
        }
        writer.println("$prefix   ]")
        val currentUserId = Process.myUserHandle().identifier
        val rawTasks: List<GroupedTaskInfo> =
            try {
                sysUiProxy.getRecentTasks(Int.MAX_VALUE, currentUserId)
            } catch (e: GetRecentTasksException) {
                emptyList()
            }
        writer.println("$prefix   rawTasks=[")
        rawTasks.forEach { writer.println(prefix + it) }
        writer.println("$prefix   ]")
    }

    @VisibleForTesting
    class TaskLoadResult(
        val requestId: Int,
        // If the result was loaded with keysOnly = true
        private val keysOnly: Boolean,
        size: Int,
    ) : ArrayList<GroupTask>(size) {
        fun isValidForRequest(requestId: Int, loadKeysOnly: Boolean): Boolean =
            this.requestId == requestId && (!keysOnly || loadKeysOnly)
    }

    companion object {
        private val INVALID_RESULT = TaskLoadResult(requestId = -1, keysOnly = false, size = 0)
        private const val TAG = "RecentTasksList"
    }
}
