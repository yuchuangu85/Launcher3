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
package com.android.quickstep

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.graphics.drawable.toDrawable
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.display.DisplayController
import com.android.launcher3.display.LauncherDisplayInfo
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.AppInfoCachingLogic
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.SimpleThreadFactory
import com.android.launcher3.util.PostUnlockObject
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.quickstep.util.IconLabelUtil.getBadgedContentDescription
import com.android.quickstep.util.TaskKeyLruCache
import com.android.quickstep.util.TaskVisualsChangeListener
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.systemui.shared.system.PackageManagerWrapper
import java.util.concurrent.Executor
import java.util.concurrent.Executors.newSingleThreadExecutor
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Manages the caching of task icons and related data. */
class TaskIconCache
@Inject
constructor(
    @ApplicationContext private val context: Context,
    displayController: DisplayController,
    private val dispatcherProvider: DispatcherProvider,
    themeManagerWrapper: PostUnlockObject<ThemeManager>,
    private val launcherIconCache: PostUnlockObject<IconCache>,
    @Ui private val uiExecutor: Executor,
    daggerSingletonTracker: DaggerSingletonTracker,
) : TaskIconDataSource {
    // This bg executor executes thread-unsafe tasks like getBitmapInfoCacheEntry(), getCacheEntry()
    // and createIconFactory(), thus it must be single threaded.
    private val singleThreadedBgExecutor = TASK_IMAGE_CACHE_EXECUTOR

    private val appInfoCachingLogic =
        AppInfoCachingLogic(pm = context.packageManager, instantAppResolver = { it.isInstantApp })

    private val recentsIconCacheSize = context.resources.getInteger(R.integer.recentsIconCacheSize)
    private val bitmapInfoCache = TaskKeyLruCache<TaskBitmapInfoCacheEntry>(recentsIconCacheSize)

    private var taskVisualsChangeListener: TaskVisualsChangeListener? = null

    init {
        // TODO (b/397205964): this will need to be updated when we support caches for different
        //  displays.
        displayController.listenable?.let {
            daggerSingletonTracker.addCloseable(
                it.changes.forEach(MAIN_EXECUTOR) { flags -> onDisplayInfoChanged(flags) }
            )
        }

        // Add a theme change listener when the device is unlocked. See b/393248495 for details.
        themeManagerWrapper.whenAvailable(uiExecutor) { themeManager ->
            val themeChangeListener = ThemeManager.ThemeChangeListener { clearCache() }
            themeManager.addChangeListener(themeChangeListener)

            Runnable { themeManager.removeChangeListener(themeChangeListener) }
        }
        daggerSingletonTracker.addCloseable(themeManagerWrapper)
    }

    private fun onDisplayInfoChanged(flags: Int) {
        if ((flags and LauncherDisplayInfo.CHANGE_DENSITY) != 0) {
            clearCache()
        }
    }

    override suspend fun getIcon(task: Task): TaskCacheEntry {
        task.icon?.let { icon ->
            // Nothing to load, the icon is already loaded
            return TaskCacheEntry(icon, task.titleDescription ?: "", task.title ?: "")
        }

        // Return from cache if present
        bitmapInfoCache.getAndInvalidateIfModified(task.key)?.let {
            return it.toTaskCacheEntry(context)
        }

        return withContext(dispatcherProvider.ioBackground) {
            val entry =
                getBitmapInfoCacheEntry(task)
                    .apply {
                        task.icon = bitmapInfo.newIcon(context)
                        task.titleDescription = contentDescription
                        task.title = title
                    }
                    .toTaskCacheEntry(context)
            dispatchIconUpdate(task.key.id)
            return@withContext entry
        }
    }

    /**
     * Asynchronously fetches the icon and other [task] data, returned through [callback].
     *
     * Returns a [CancellableTask] to cancel the request.
     */
    @AnyThread
    fun getIconInBackground(
        task: Task,
        callbackExecutor: Executor,
        callback: GetTaskIconCallback,
    ): CancellableTask<*>? {
        task.icon?.let {
            // Nothing to load, the icon is already loaded
            callbackExecutor.execute {
                callback.onTaskIconReceived(it, task.titleDescription ?: "", task.title ?: "")
            }
            return null
        }

        return getBitmapInfoInBackground(task, callbackExecutor) {
            bitmapInfo,
            title,
            contentDescription ->
            val icon = bitmapInfo.newIcon(context)
            task.icon = icon
            callback.onTaskIconReceived(icon, title, contentDescription)
        }
    }

    /**
     * Asynchronously fetches the icon [BitmapInfo] and other [task] data, returned through
     * [callback].
     *
     * Returns a [CancellableTask] to cancel the request.
     */
    @AnyThread
    fun getBitmapInfoInBackground(
        task: Task,
        callbackExecutor: Executor,
        callback: GetTaskBitmapInfoCallback,
    ): CancellableTask<*>? {
        bitmapInfoCache.getAndInvalidateIfModified(task.key)?.let {
            task.titleDescription = it.contentDescription
            task.title = it.title
            callbackExecutor.execute {
                callback.onBitmapInfoReceived(it.bitmapInfo, it.contentDescription, it.title)
            }
            return null
        }

        val request =
            CancellableTask(
                { getBitmapInfoCacheEntry(task) },
                callbackExecutor,
                { result: TaskBitmapInfoCacheEntry ->
                    task.titleDescription = result.contentDescription
                    task.title = result.title
                    callback.onBitmapInfoReceived(
                        result.bitmapInfo,
                        result.contentDescription,
                        result.title,
                    )
                    dispatchIconUpdate(task.key.id)
                },
            )
        singleThreadedBgExecutor.execute(request)
        return request
    }

    /** Clears the icon cache */
    fun clearCache() {
        // Clear on caller and background thread. The cache clears are synchronized.
        resetFactory()
        singleThreadedBgExecutor.execute { resetFactory() }
    }

    fun onTaskRemoved(taskKey: TaskKey) {
        bitmapInfoCache.remove(taskKey)
    }

    fun invalidateCacheEntries(pkg: String, handle: UserHandle) {
        singleThreadedBgExecutor.execute {
            val keyCheck = { key: TaskKey ->
                pkg == key.packageName && handle.identifier == key.userId
            }
            bitmapInfoCache.removeAll(keyCheck)
        }
    }

    @WorkerThread
    private fun getBitmapInfoCacheEntry(task: Task): TaskBitmapInfoCacheEntry {
        val key = task.key
        val activityInfo =
            PackageManagerWrapper.getInstance().getActivityInfo(key.component, key.userId)
        val bitmapInfo = getBitmapInfo(task)

        return (if (activityInfo == null) {
                // Skip loading the content description if the activity no longer exists
                TaskBitmapInfoCacheEntry(bitmapInfo)
            } else {
                TaskBitmapInfoCacheEntry(
                    bitmapInfo,
                    getBadgedContentDescription(
                        context,
                        activityInfo,
                        task.key.userId,
                        task.taskDescription,
                    ),
                    Utilities.trim(activityInfo.loadLabel(context.packageManager)),
                )
            })
            .also { bitmapInfoCache.put(task.key, it) }
    }

    private fun getIcon(desc: ActivityManager.TaskDescription, userId: Int): Bitmap? =
        desc.inMemoryIcon
            ?: ActivityManager.TaskDescription.loadTaskDescriptionIcon(desc.iconFilename, userId)

    @WorkerThread
    private fun getBitmapInfo(task: Task): BitmapInfo {
        val ic = launcherIconCache.getIfReady() ?: return BitmapInfo.LOW_RES_INFO
        val desc = task.taskDescription
        val key = task.key

        val user = UserHandle.of(key.userId)

        // Load icon
        val icon = getIcon(desc, key.userId)
        val userInfo = UserCache.INSTANCE.get(context).getUserInfo(user)

        if (icon != null) {
            return ic.iconFactory.use {
                it.createBadgedIconBitmap(
                    icon.toDrawable(context.resources),
                    IconOptions()
                        .setUser(userInfo)
                        .setExtractedColor(0)
                        .setWrapperBackgroundColor(desc.primaryColor),
                )
            }
        }

        val activityInfo =
            PackageManagerWrapper.getInstance().getActivityInfo(key.component, key.userId)
                ?: return ic.getDefaultIcon(user)
        val appInfo = activityInfo.applicationInfo
        val request = ic.getIconLoadRequest(appInfo, appInfoCachingLogic)
        return ic.iconFactory.use {
            it.createBadgedIconBitmap(
                request.getIcon(activityInfo),
                IconOptions()
                    .setUser(userInfo)
                    .setInstantApp(appInfo.isInstantApp)
                    .setExtractedColor(0)
                    .setWrapperBackgroundColor(desc.primaryColor)
                    .setSourceHint(request.sourceHint),
            )
        }
    }

    @WorkerThread
    private fun resetFactory() {
        bitmapInfoCache.evictAll()
    }

    data class TaskCacheEntry(
        val icon: Drawable,
        val contentDescription: String = "",
        val title: String = "",
    )

    private data class TaskBitmapInfoCacheEntry(
        val bitmapInfo: BitmapInfo,
        val contentDescription: String = "",
        val title: String = "",
    ) {
        fun toTaskCacheEntry(context: Context): TaskCacheEntry {
            return TaskCacheEntry(bitmapInfo.newIcon(context), contentDescription, title)
        }
    }

    /** Callback used when retrieving app icons from cache. */
    fun interface GetTaskIconCallback {
        /** Called when task icon is retrieved. */
        fun onTaskIconReceived(icon: Drawable, contentDescription: String, title: String)
    }

    /** Callback used when retrieving app [BitmapInfo] instances from cache. */
    fun interface GetTaskBitmapInfoCallback {
        /** Called when task [BitmapInfo] is retrieved. */
        fun onBitmapInfoReceived(bitmapInfo: BitmapInfo, contentDescription: String, title: String)
    }

    fun registerTaskVisualsChangeListener(newListener: TaskVisualsChangeListener?) {
        taskVisualsChangeListener = newListener
    }

    fun removeTaskVisualsChangeListener() {
        taskVisualsChangeListener = null
    }

    private fun dispatchIconUpdate(taskId: Int) {
        taskVisualsChangeListener?.onTaskIconChanged(taskId)
    }

    companion object {
        val TASK_IMAGE_CACHE_EXECUTOR: Executor =
            newSingleThreadExecutor(
                SimpleThreadFactory("TaskThumbnailIconCache-", Process.THREAD_PRIORITY_BACKGROUND)
            )
    }
}
