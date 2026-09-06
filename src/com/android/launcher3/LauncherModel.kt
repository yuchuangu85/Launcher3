/*
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.launcher3

import android.content.Context
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.IconCache
import com.android.launcher3.logging.DumpManager
import com.android.launcher3.logging.DumpManager.LauncherDumpable
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BaseLauncherBinder.BaseLauncherBinderFactory
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.BgDataModel.ModificationSource.UISurface
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.LoaderTask
import com.android.launcher3.model.LoaderTask.LoaderTaskFactory
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.ModelDelegate
import com.android.launcher3.model.ModelInitializer
import com.android.launcher3.model.ModelLauncherCallbacks
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.ModelWriterFactory
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.tasks.CacheDataUpdatedTask
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.views.ActivityContext
import java.io.PrintWriter
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state for the
 * Launcher.
 */
@LauncherAppSingleton
class LauncherModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val taskControllerProvider: Provider<ModelTaskController>,
    private val iconCache: IconCache,
    private val installQueue: ItemInstallQueue,
    @Named("ICONS_DB") dbFileName: String?,
    initializer: ModelInitializer,
    lifecycle: DaggerSingletonTracker,
    val modelDelegate: ModelDelegate,
    private val mBgAllAppsList: AllAppsList,
    private val mBgDataModel: BgDataModel,
    private val loaderFactory: LoaderTaskFactory,
    private val binderFactory: BaseLauncherBinderFactory,
    val modelDbController: ModelDbController,
    private val modelWriterFactory: ModelWriterFactory,
    dumpManager: DumpManager,
) : LauncherDumpable {

    private val mCallbacksList = ArrayList<BgDataModel.Callbacks>(1)

    private val mLock = Any()

    @GuardedBy("mLock") private var mLoaderTask: LoaderTask? = null
    @GuardedBy("mLock") private var mLoadCompleteFuture = CompletableFuture<Unit>()

    // Indicates whether the current model data is valid or not.
    // We start off with everything not loaded. After that, we assume that
    // our monitoring of the package manager provides all updates and we never
    // need to do a requery. This is only ever touched from the loader thread.
    private var mModelLoaded = false
    private var mModelDestroyed = false

    fun isModelLoaded() =
        synchronized(mLock) { mModelLoaded && mLoaderTask == null && !mModelDestroyed }

    /**
     * Returns the ID for the last model load. If the load ID doesn't match for a transaction, the
     * transaction should be ignored.
     */
    var lastLoadId: Int = -1
        private set

    // Runnable to check if the shortcuts permission has changed.
    private val mDataValidationCheck = Runnable {
        if (mModelLoaded) {
            modelDelegate.validateData()
        }
    }

    init {
        if (!dbFileName.isNullOrEmpty()) {
            initializer.initialize(this)
        }
        lifecycle.addCloseable { destroy() }
        modelDelegate.init(this, mBgAllAppsList, mBgDataModel)
        lifecycle.addCloseable(dumpManager.register(this))
    }

    fun newModelCallbacks() = ModelLauncherCallbacks(this::enqueueModelUpdateTask)

    fun getWriter(
        verifyChanges: Boolean,
        activity: ActivityContext,
        owner: BgDataModel.Callbacks?,
    ): IModelWriter =
        modelWriterFactory.create(verifyChanges, activity.cellPosMapper, UISurface(activity), owner)

    /** Called when the model is destroyed */
    fun destroy() {
        mModelDestroyed = true
        MODEL_EXECUTOR.execute { modelDelegate.destroy() }
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally not
     * be called as DB updates are automatically followed by UI update. Calling this too early may
     * cause missing icons or widgets during restore process.
     */
    @VisibleForTesting
    fun forceReload(callerName: String): CompletionStage<Unit> {
        synchronized(mLock) {
            mModelLoaded = false
            return startLoader(callerName)
        }
    }

    /** Reloads the model if it is already in use */
    fun reloadIfActive(callerName: String): CompletionStage<Unit> =
        if (isActive()) forceReload(callerName) else CompletableFuture.completedFuture(Unit)

    /** Rebinds all existing callbacks with already loaded model */
    fun rebindCallbacks(reason: String) {
        if (useModelRepositoryBinding() && isActive()) {
            MODEL_EXECUTOR.execute { mBgDataModel.dispatchRebind(reason) }
        } else {
            if (synchronized(mCallbacksList) { mCallbacksList.isNotEmpty() }) {
                startLoader(reason)
            }
        }
    }

    /** Removes an existing callback */
    fun removeCallbacks(callbacks: BgDataModel.Callbacks) {
        if (useModelRepositoryBinding()) return
        synchronized(mCallbacksList) {
            if (mCallbacksList.remove(callbacks)) {

                // Restart the task in case it was already running
                if (mLoaderTask != null) startLoader("removeCallbacks")
            }
        }
    }

    /**
     * Adds a callbacks to receive model updates
     *
     * @return true if workspace load was performed synchronously
     */
    fun addCallbacksAndLoad(callbacks: BgDataModel.Callbacks): Boolean {
        require(!useModelRepositoryBinding()) { "Use home repository directly" }
        synchronized(mLock) {
            synchronized(mCallbacksList) { mCallbacksList.add(callbacks) }
            return startLoader("addCallbacksAndLoad", arrayOf(callbacks)).isDone
        }
    }

    /** Activates the LauncherModel and begins loading data */
    fun activate() {
        synchronized(mLock) { if (!isActive()) startLoader("activate") }
    }

    /** Starts the loader, and returns a completion stage indicating when the loading is complete */
    fun startLoader(callerName: String): CompletionStage<Unit> = startLoader(callerName, arrayOf())

    private fun startLoader(
        callerName: String,
        newCallbacks: Array<BgDataModel.Callbacks>,
    ): CompletableFuture<Unit> {
        if (mModelDestroyed) return CompletableFuture.completedFuture(Unit)
        // Enable queue before starting loader. It will get disabled in Launcher#finishBindingItems
        installQueue.pauseModelPush(ItemInstallQueue.FLAG_LOADER_RUNNING)
        synchronized(mLock) {
            // If there is already one running, tell it to stop.
            val oldTask = mLoaderTask
            mLoaderTask = null
            oldTask?.stopLocked(callerName)

            val wasRunning = oldTask != null
            val bindDirectly = mModelLoaded && !wasRunning
            val bindAllCallbacks = wasRunning || !bindDirectly || newCallbacks.isEmpty()
            val callbacksList = if (bindAllCallbacks) callbacks else newCallbacks
            val launcherBinder = binderFactory.createBinder(callbacksList)
            if (bindDirectly) {
                // Divide the set of loaded items into those that we are binding synchronously,
                // and everything else that is to be bound normally (asynchronously).
                launcherBinder.bindWorkspace(bindAllCallbacks, /* isBindSync= */ true)
                // For now, continue posting the binding of AllApps as there are other
                // issues that arise from that.
                launcherBinder.bindAllApps()
                launcherBinder.bindWidgets()

                if (Flags.simplifiedLauncherModelBinding())
                    installQueue.resumeModelPush(ItemInstallQueue.FLAG_LOADER_RUNNING)
                return CompletableFuture.completedFuture(Unit)
            } else {
                val task = loaderFactory.newLoaderTask(callerName, launcherBinder)
                mLoaderTask = task

                val lastFuture = mLoadCompleteFuture

                // Complete the last future when this completes, only if it wasn't already completed
                mLoadCompleteFuture = CompletableFuture<Unit>()
                mLoadCompleteFuture.thenApply { lastFuture.complete(it) }

                // Always post the loader task, instead of running directly
                // (even on same thread) so that we exit any nested synchronized blocks
                MODEL_EXECUTOR.post(task)
                return mLoadCompleteFuture
            }
        }
    }

    /**
     * Checks whether the launcher model is active.
     *
     * @return true if the model is loaded or if loader task is running.
     */
    fun isActive(): Boolean = mModelLoaded || mLoaderTask != null

    inner class LoaderTransaction(task: LoaderTask) : AutoCloseable {
        private var mTask: LoaderTask? = null
        private var mIsCommitted = false

        init {
            synchronized(mLock) {
                if (mLoaderTask !== task) {
                    throw CancellationException("Loader already stopped")
                }
                this@LauncherModel.lastLoadId++
                mTask = task
                mModelLoaded = false
            }
        }

        fun commit() {
            synchronized(mLock) {
                // Everything loaded bind the data.
                if (mLoaderTask === mTask) {
                    mModelLoaded = true
                    mIsCommitted = true
                }
            }
            if (Flags.simplifiedLauncherModelBinding())
                installQueue.resumeModelPush(ItemInstallQueue.FLAG_LOADER_RUNNING)
        }

        override fun close() {
            synchronized(mLock) {
                // If we are still the last one to be scheduled, remove ourselves.
                if (mLoaderTask === mTask) {
                    mLoaderTask = null
                    if (mIsCommitted) {
                        mLoadCompleteFuture.complete(Unit)
                    }
                    Log.e(
                        TAG,
                        "Loader task completed, name: [${mTask?.name}], mIsCommitted=$mIsCommitted",
                    )
                }
            }
        }
    }

    @Throws(CancellationException::class)
    fun beginLoader(task: LoaderTask) = LoaderTransaction(task)

    /**
     * Refreshes the cached shortcuts if the shortcut permission has changed. Current implementation
     * simply reloads the workspace, but it can be optimized to use partial updates similar to
     * [UserCache]
     */
    fun validateModelDataOnResume() {
        MODEL_EXECUTOR.handler.removeCallbacks(mDataValidationCheck)
        MODEL_EXECUTOR.post(mDataValidationCheck)
    }

    /** Called when the icons for packages have been updated in the icon cache. */
    fun onPackageIconsUpdated(updatedPackages: HashSet<String?>, user: UserHandle) {
        // If any package icon has changed (app was updated while launcher was dead),
        // update the corresponding shortcuts.
        enqueueModelUpdateTask(
            CacheDataUpdatedTask(CacheDataUpdatedTask.OP_CACHE_UPDATE, user, updatedPackages)
        )
    }

    /** Called when the labels for the widgets has updated in the icon cache. */
    fun onWidgetLabelsUpdated(updatedPackages: HashSet<String?>, user: UserHandle) {
        enqueueModelUpdateTask { taskController, dataModel, _ ->
            dataModel.widgetsModel.onPackageIconsUpdated(updatedPackages, user)
            taskController.bindUpdatedWidgets(dataModel)
        }
    }

    fun enqueueModelUpdateTask(task: ModelUpdateTask) {
        if (mModelDestroyed) {
            return
        }
        MODEL_EXECUTOR.execute {
            if (!isModelLoaded()) {
                // Loader has not yet run.
                return@execute
            }
            task.execute(taskControllerProvider.get(), mBgDataModel, mBgAllAppsList)
        }
    }

    /**
     * A task to be executed on the current callbacks on the UI thread. If there is no current
     * callbacks, the task is ignored.
     */
    fun interface CallbackTask {
        fun execute(callbacks: BgDataModel.Callbacks)
    }

    fun interface ModelUpdateTask {
        fun execute(taskController: ModelTaskController, dataModel: BgDataModel, apps: AllAppsList)
    }

    fun updateAndBindWorkspaceItem(si: WorkspaceItemInfo, info: ShortcutInfo) {
        enqueueModelUpdateTask { taskController, dataModel, _ ->
            si.updateFromDeepShortcutInfo(info, context)
            iconCache.getShortcutIcon(si, info)
            taskController.getModelWriter().updateItemInDatabase(si)
            taskController.bindUpdatedWorkspaceItems(listOf(si))
        }
    }

    fun refreshAndBindWidgetsAndShortcuts(packageUser: PackageUserKey?) {
        enqueueModelUpdateTask { taskController, dataModel, _ ->
            dataModel.widgetsModel.update(packageUser)
            taskController.bindUpdatedWidgets(dataModel)
        }
    }

    override fun dump(prefix: String, writer: PrintWriter, args: Array<String>?) {
        if (args?.getOrNull(0) == "--all") {
            writer.println(prefix + "All apps list: size=" + mBgAllAppsList.data.size)
            for (info in mBgAllAppsList.data) {
                writer.println(
                    "$prefix   title=\"${info.title}\" bitmapIcon=${info.bitmap.icon} componentName=${info.targetPackage}"
                )
            }
            writer.println()
        }
    }

    /** Returns an array of currently attached callbacks */
    val callbacks: Array<BgDataModel.Callbacks>
        get() {
            synchronized(mCallbacksList) {
                return mCallbacksList.toTypedArray<BgDataModel.Callbacks>()
            }
        }

    companion object {
        const val TAG = "Launcher.Model"

        @JvmStatic
        fun useModelRepositoryBinding() =
            Flags.bindModelUsingRepository() &&
                Flags.modelRepository() &&
                Flags.simplifiedLauncherModelBinding()
    }
}
