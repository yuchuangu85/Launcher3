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

package com.android.launcher3.taskbar

import androidx.annotation.VisibleForTesting
import com.android.launcher3.ActivityInteractor
import com.android.launcher3.AsyncAnimatorPlaybackController
import com.android.launcher3.LauncherInteractor
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.ListenableStream
import com.android.launcher3.util.Preconditions
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.dagger.SysUIConnectionSingleton
import com.android.quickstep.views.RecentsViewContainer
import java.io.PrintWriter
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import javax.annotation.concurrent.ThreadSafe
import javax.inject.Inject
import javax.inject.Provider

/**
 * Wrapper of [TaskbarManagerImpl], this class controls which thread the invocation happens. The
 * goal of this class is to minimize the changes to [TaskbarManagerImpl] during migration of
 * rendering taskbar in per-window ui thread.
 */
@ThreadSafe
@SysUIConnectionSingleton
class TaskbarManagerImplWrapper @Inject constructor(implProvider: Provider<TaskbarManagerImpl>) :
    TaskbarManager {

    private lateinit var impl: TaskbarManagerImpl

    init {
        getTaskbarUiThread().execute { impl = implProvider.get() }
    }

    override fun updateTaskbarsVisibility() {
        getTaskbarUiThread().execute { impl.updateTaskbarsVisibility() }
    }

    override fun setActivity(activity: StatefulActivity<*>) {
        getTaskbarUiThread().execute {
            impl.setActivityInteractor(
                if (activity is QuickstepLauncher) LauncherInteractor(activity)
                else ActivityInteractor(activity)
            )
        }
    }

    override fun setRecentsViewContainer(recentsViewContainer: RecentsViewContainer) {
        getTaskbarUiThread().execute {
            impl.setRecentsViewContainerInteractor(recentsViewContainer)
        }
    }

    override fun onSystemUiFlagsChanged(systemUiStateFlags: Long, displayId: Int) {
        getTaskbarUiThread().execute { impl.onSystemUiFlagsChanged(systemUiStateFlags, displayId) }
    }

    override fun onLongPressHomeEnabled(assistantLongPressEnabled: Boolean) {
        getTaskbarUiThread().execute { impl.onLongPressHomeEnabled(assistantLongPressEnabled) }
    }

    override fun setSetupUIVisible(isVisible: Boolean) {
        getTaskbarUiThread().execute { impl.setSetupUIVisible(isVisible) }
    }

    override fun setWallpaperVisible(displayId: Int, isVisible: Boolean) {
        getTaskbarUiThread().execute { impl.setWallpaperVisible(displayId, isVisible) }
    }

    override fun checkNavBarModes(displayId: Int) {
        getTaskbarUiThread().execute { impl.checkNavBarModes(displayId) }
    }

    override fun finishBarAnimations(displayId: Int) {
        getTaskbarUiThread().execute { impl.finishBarAnimations(displayId) }
    }

    override fun touchAutoDim(displayId: Int, reset: Boolean) {
        getTaskbarUiThread().execute { impl.touchAutoDim(displayId, reset) }
    }

    override fun transitionTo(displayId: Int, barMode: Int, animate: Boolean) {
        getTaskbarUiThread().execute { impl.transitionTo(displayId, barMode, animate) }
    }

    override fun appTransitionPending(pending: Boolean) {
        getTaskbarUiThread().execute { impl.appTransitionPending(pending) }
    }

    override fun onRotationProposal(rotation: Int, isValid: Boolean) {
        getTaskbarUiThread().execute { impl.onRotationProposal(rotation, isValid) }
    }

    override fun disableNavBarElements(displayId: Int, state1: Int, state2: Int, animate: Boolean) {
        getTaskbarUiThread().execute {
            impl.disableNavBarElements(displayId, state1, state2, animate)
        }
    }

    override fun onSystemBarAttributesChanged(displayId: Int, behavior: Int) {
        getTaskbarUiThread().execute { impl.onSystemBarAttributesChanged(displayId, behavior) }
    }

    override fun onTransitionModeUpdated(barMode: Int, checkBarModes: Boolean) {
        getTaskbarUiThread().execute { impl.onTransitionModeUpdated(barMode, checkBarModes) }
    }

    override fun onNavButtonsDarkIntensityChanged(darkIntensity: Float) {
        getTaskbarUiThread().execute { impl.onNavButtonsDarkIntensityChanged(darkIntensity) }
    }

    override fun onNavigationBarLumaSamplingEnabled(displayId: Int, enable: Boolean) {
        getTaskbarUiThread().execute { impl.onNavigationBarLumaSamplingEnabled(displayId, enable) }
    }

    override fun createLauncherStartFromSuwAnim(duration: Int): AsyncAnimatorPlaybackController? {
        return impl.createLauncherStartFromSuwAnim(duration)
    }

    override fun shouldForceAllSetFallbackAnimation(): Boolean {
        return impl.shouldForceAllSetFallbackAnimation()
    }

    override fun toggleTaskbarStash() {
        getTaskbarUiThread().execute { impl.currentActivityContext?.toggleTaskbarStash() }
    }

    override fun getStashedHandleViewController(): StashedHandleViewControllerProxy? {
        Preconditions.assertTaskbarUiThread()
        return impl.currentActivityContext?.controllers?.stashedHandleViewController?.let {
            StashedHandleViewControllerProxy(it)
        }
    }

    override fun getPrimaryDisplayUiControllerStream(): ListenableStream<TaskbarUIController> {
        Preconditions.assertTaskbarUiThread()
        return impl.primaryDisplayUiControllerStream
    }

    override fun getTaskbarInteractor(displayId: Int): TaskbarInteractor? {
        return impl.getUIControllerForDisplay(displayId)?.let { TaskbarInteractor(it) }
    }

    /* TODO(b/404636836): Evaluate API calls on returned TaskbarActivityContext */
    override fun getTaskbarForDisplay(displayId: Int): TaskbarActivityContext? {
        return if (::impl.isInitialized) {
            impl.getTaskbarForDisplay(displayId)
        } else {
            null
        }
    }

    override fun updateStashControllerLauncherStateFlag(displayId: Int, isVisible: Boolean) {
        getTaskbarUiThread().execute {
            getTaskbarForDisplay(displayId)?.updateStashControllerLauncherStateFlag(isVisible)
        }
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        // Stay on caller thread because PrinterWriter is not thread safe.
        impl.dumpLogs(prefix, pw)
    }

    @VisibleForTesting
    override fun <T> getFromImplSync(provider: (TaskbarManagerImpl) -> T): T {
        val future = FutureTask(Callable { provider(impl) })
        getTaskbarUiThread().execute(future)
        return future.get()
    }

    @VisibleForTesting
    override fun getCurrentActivityContext(): TaskbarActivityContext? {
        return impl.currentActivityContext
    }

    @VisibleForTesting
    override fun recreateTaskbars() {
        getTaskbarUiThread().execute(impl::recreateTaskbars)
    }

    @VisibleForTesting
    override fun removeAllSystemUiBubbles() {
        SystemUiProxy.INSTANCE[impl.currentActivityContext].removeAllBubbles()
    }

    @VisibleForTesting
    override fun unstashBubbleBarIfStashed() {
        getTaskbarUiThread().execute { impl.currentActivityContext?.unstashBubbleBarIfStashed() }
    }

    @VisibleForTesting
    override fun limitMaxTaskbarIconsNum(maxIconLimitNum: Int) {
        getTaskbarUiThread().execute {
            impl.currentActivityContext?.limitMaxTaskbarIconsNum(maxIconLimitNum)
        }
    }

    @VisibleForTesting
    override fun getStashedTaskbarScale() = impl.currentActivityContext!!.stashedTaskbarScale

    @VisibleForTesting
    override fun removeAllBubbles() {
        getTaskbarUiThread().execute { impl.currentActivityContext!!.removeAllBubbles() }
    }

    @VisibleForTesting
    override fun unstashTaskbarIfStashed(): Boolean =
        getTaskbarUiThread()
            .submit<Boolean> { impl.currentActivityContext!!.unstashTaskbarIfStashed() }
            .get()

    @VisibleForTesting
    override fun enableBlockingTimeoutDuringTests(enableBlockingTimeout: Boolean) {
        impl.currentActivityContext?.enableBlockingTimeoutDuringTests(enableBlockingTimeout)
    }

    @VisibleForTesting
    override fun isTransient(displayId: Int): Boolean {
        return if (::impl.isInitialized) {
            impl.getTaskbarForDisplay(displayId)?.taskbarFeatureEvaluator?.isTransient ?: false
        } else {
            false
        }
    }

    @VisibleForTesting
    override fun injectTestInsights() {
        getTaskbarUiThread().execute(impl::injectTestInsights)
    }
}
