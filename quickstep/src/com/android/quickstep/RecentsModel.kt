/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.ComponentCallbacks
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
import android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.Flags.enableLowResThumbnailPreloading
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.IconChangeTracker
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.ListenableStream
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableStream
import com.android.quickstep.RecentsFilterState.EMPTY_FILTER
import com.android.quickstep.RecentsModel.RecentTasksChangedListener
import com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId
import com.android.quickstep.dagger.QuickstepBaseAppComponent
import com.android.quickstep.recents.data.RecentTasksDataSource
import com.android.quickstep.recents.data.TaskVisualsChangeNotifier
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.TaskVisualsChangeListener
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import java.io.PrintWriter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Predicate
import javax.inject.Inject

/** Singleton class to load and manage recents model. */
@LauncherAppSingleton
class RecentsModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val taskList: RecentTasksList,
    val iconCache: TaskIconCache,
    val thumbnailCache: TaskThumbnailCache,
    taskStackChangeListeners: TaskStackChangeListeners,
    tracker: DaggerSingletonTracker,
    @Ui private val uiExecutor: LooperExecutor,
    iconChangeTracker: IconChangeTracker,
) :
    RecentTasksDataSource,
    TaskStackChangeListener,
    TaskVisualsChangeListener,
    TaskVisualsChangeNotifier {

    private val thumbnailChangeListeners = ConcurrentLinkedQueue<TaskVisualsChangeListener>()
    private val recentTasksChangedListeners = ConcurrentLinkedQueue<RecentTasksChangedListener>()
    private val tasksChangedListenable = MutableListenableStream<Void?>()

    init {
        val recentTasksListObserver = RecentTasksChangedListener {
            if (enableTaskbarUiThread()) {
                tasksChangedListenable.dispatchValue(null)
            } else {
                recentTasksChangedListeners.forEach { it.onRecentTasksChanged() }
            }
        }

        taskList.setRecentTasksChangedListener(recentTasksListObserver)
        iconCache.registerTaskVisualsChangeListener(this)

        val componentCallbacks =
            object : ComponentCallbacks {
                override fun onConfigurationChanged(configuration: Configuration) {
                    // Cache update happens in RecentsViewModelHelper now
                    if (enableLowResThumbnailPreloading()) return

                    updateCacheSizeAndPreloadIfNeeded()
                }

                override fun onLowMemory() {}
            }
        context.registerComponentCallbacks(componentCallbacks)
        tracker.addCloseable { context.unregisterComponentCallbacks(componentCallbacks) }

        taskStackChangeListeners.registerTaskStackListener(this)
        val iconChangeCloseable =
            iconChangeTracker.changes.forEach(uiExecutor) {
                onAppIconChanged(it.mPackageName, it.mUser)
            }

        tracker.addCloseable {
            taskStackChangeListeners.unregisterTaskStackListener(this)
            taskList.clearRecentTasksChangedListener()
            iconChangeCloseable.close()
            iconCache.removeTaskVisualsChangeListener()
        }
    }

    val tasksChanges: ListenableStream<Void?>
        get() = tasksChangedListenable

    /**
     * Fetches the list of recent tasks. Tasks are ordered by recency, with the latest active tasks
     * at the end of the list. Filters out desktop tasks that contain no non-minimized tasks.
     *
     * @param callback The callback to receive the task plan once its complete or null. This is
     *   always called on the UI thread.
     * @return the request id associated with this call.
     */
    override fun getTasks(callback: Consumer<List<GroupTask>>?) =
        taskList.getTasks(loadKeysOnly = false, callback, EMPTY_FILTER)

    /**
     * Fetches the list of recent tasks, based on a filter
     *
     * @param filter Returns true if a GroupTask should be included into the list passed into
     *   callback.
     * @param callback The callback to receive the task plan once its complete or null. This is
     *   always called on the UI thread.
     * @return the request id associated with this call.
     */
    fun getTasks(filter: Predicate<GroupTask>, callback: Consumer<List<GroupTask>>) =
        taskList.getTasks(loadKeysOnly = false, callback, filter)

    /**
     * Fetches the list of recent tasks, based on a filter
     *
     * @param callback The callback to receive the task plan and request ID once its complete or
     *   null. This is always called on the UI thread.
     * @param filter Returns true if a GroupTask should be included into the list passed into
     *   callback.
     * @return the request id associated with this call.
     */
    fun getTasks(callback: BiConsumer<List<GroupTask>, Int>, filter: Predicate<GroupTask>) =
        taskList.getTasks(loadKeysOnly = false, callback, filter)

    /** @return Whether the provided [changeId] is the latest recent tasks list id. */
    fun isTaskListValid(changeId: Int) = taskList.isTaskListValid(changeId)

    /**
     * Checks if a task has been removed or not.
     *
     * @param callback Receives true if task is removed, false otherwise
     * @param filter Returns true if GroupTask should be in the list of considerations
     */
    fun isTaskRemoved(taskId: Int, callback: Consumer<Boolean>, filter: Predicate<GroupTask>) {
        // Invalidate the existing list before checking to ensure this reflects the current state in
        // the system
        taskList.onRecentTasksChanged()
        taskList.getTasks(
            loadKeysOnly = true,
            callback@{ taskGroups ->
                taskGroups.forEach { group ->
                    if (group.containsTask(taskId)) {
                        callback.accept(false)
                        return@callback
                    }
                }
                callback.accept(true)
            },
            filter,
        )
    }

    fun getRecentsDisplayId(displayId: Int) = taskList.getRecentsDisplayId(displayId)

    override fun onTaskStackChangedBackground() {
        // Preloading is handled in PreloadThumbnailUseCase when this flag is enabled
        if (enableLowResThumbnailPreloading()) return

        // Skip if we aren't preloading
        if (!thumbnailCache.isPreloadingEnabled()) return

        // Skip if we are not the current user
        val currentUserId = Process.myUserHandle().identifier
        if (!checkCurrentOrManagedUserId(currentUserId, context)) return

        preloadNonRunningTasks()
    }

    private fun preloadNonRunningTasks() {
        val runningTask = ActivityManagerWrapper.getInstance().runningTask
        val runningTaskId = runningTask?.id ?: -1
        taskList.getTaskKeys(thumbnailCache.getCacheSize()) { taskGroups ->
            taskGroups
                // Skip the running task, it's not going to have an up-to-date snapshot by the
                // time the user next enters overview
                .filterNot { it.containsTask(runningTaskId) }
                .flatMap { it.tasks }
                .forEach { t -> thumbnailCache.updateThumbnailInCache(t, lowResolution = true) }
        }
    }

    override fun onTaskSnapshotChanged(taskId: Int, snapshot: ThumbnailData): Boolean {
        thumbnailCache.updateTaskSnapShot(taskId, snapshot)

        thumbnailChangeListeners.forEach { listener ->
            listener.onTaskThumbnailChanged(taskId, snapshot)?.thumbnail = snapshot
        }
        return true
    }

    override fun onRecentTaskRemovedForAddTask(taskId: Int) {
        removeTask(taskId)
    }

    override fun onTaskRemoved(taskId: Int) {
        removeTask(taskId)
    }

    private fun removeTask(taskId: Int) {
        val stubKey =
            Task.TaskKey(
                taskId,
                /* windowingMode= */ 0,
                Intent(),
                /* sourceComponent= */ null,
                /* userId= */ 0,
                /* lastActiveTime= */ 0,
            )
        thumbnailCache.remove(stubKey)
        iconCache.onTaskRemoved(stubKey)
    }

    fun onTrimMemory(level: Int) {
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            thumbnailCache.highResLoadingState.visible = false
        }
        if (level == TRIM_MEMORY_RUNNING_CRITICAL) {
            // Clear everything once we reach a low-mem situation
            thumbnailCache.clear()
            iconCache.clearCache()
        }
    }

    private fun onAppIconChanged(packageName: String, user: UserHandle) {
        iconCache.invalidateCacheEntries(packageName, user)
        thumbnailChangeListeners.forEach { listener ->
            listener.onTaskIconChanged(packageName, user)
        }
    }

    override fun onTaskIconChanged(taskId: Int) {
        thumbnailChangeListeners.forEach { listener -> listener.onTaskIconChanged(taskId) }
    }

    override fun addThumbnailChangeListener(listener: TaskVisualsChangeListener) {
        thumbnailChangeListeners.add(listener)
    }

    override fun removeThumbnailChangeListener(listener: TaskVisualsChangeListener) {
        thumbnailChangeListeners.remove(listener)
    }

    fun dump(prefix: String, writer: PrintWriter) {
        writer.println(prefix + "RecentsModel:")
        taskList.dump("  ", writer)
    }

    fun registerRecentTasksChangedListener(listener: RecentTasksChangedListener) {
        if (enableTaskbarUiThread()) return

        recentTasksChangedListeners.add(listener)
    }

    fun unregisterRecentTasksChangedListener(listener: RecentTasksChangedListener) {
        if (enableTaskbarUiThread()) return

        recentTasksChangedListeners.remove(listener)
    }

    /** Preloads cache if reloading is enabled and highResLoadingState is enabled. */
    fun preloadCacheIfNeeded() {
        // Preloading is handled in PreloadThumbnailUseCase when this flag is enabled
        if (enableLowResThumbnailPreloading()) return

        if (!isPreloadCacheNeeded()) return

        taskList.getTaskKeys(thumbnailCache.getCacheSize()) { taskGroups ->
            taskGroups.flatMap(GroupTask::tasks).forEach { t ->
                thumbnailCache.updateThumbnailInCache(t, lowResolution = false)
            }
        }
    }

    private fun isPreloadCacheNeeded(): Boolean {
        // Skip if we aren't preloading.
        if (!thumbnailCache.isPreloadingEnabled()) return false

        // Skip if high-res loading state is disabled.
        if (!thumbnailCache.highResLoadingState.isEnabled) return false
        return true
    }

    /** Updates cache size and preloads more tasks if cache size increases */
    fun updateCacheSizeAndPreloadIfNeeded() {
        // If new size is larger than original size, preload more cache to fill the gap
        if (thumbnailCache.updateCacheSizeAndRemoveExcess()) {
            preloadCacheIfNeeded()
        }
    }

    /** Listener for receiving recent tasks changes */
    fun interface RecentTasksChangedListener {
        /** Called when there's a change to recent tasks */
        fun onRecentTasksChanged()
    }

    companion object {
        // We do not need any synchronization for this variable as its only written on UI thread.
        @JvmField
        val INSTANCE: DaggerSingletonObject<RecentsModel> =
            DaggerSingletonObject(QuickstepBaseAppComponent::getRecentsModel)
    }
}
