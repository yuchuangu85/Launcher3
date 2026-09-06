/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep.window

import android.animation.Animator
import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.LocusId
import android.content.pm.ActivityInfo.CONFIG_ORIENTATION
import android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.RemoteAnimationAdapter
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.view.WindowlessWindowManager
import android.window.BackEvent
import android.window.DesktopExperienceFlags
import android.window.InputTransferToken
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.window.RemoteTransition
import android.window.SplashScreen
import android.window.TransitionInfo
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.core.animation.addListener
import androidx.core.view.isVisible
import com.android.app.displaylib.PerDisplayRepository
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BaseActivity
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAnimationRunner
import com.android.launcher3.LauncherAnimationRunner.RemoteAnimationFactory
import com.android.launcher3.LauncherRootView
import com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION
import com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_DURATION
import com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_PRE_DELAY
import com.android.launcher3.R
import com.android.launcher3.SplitScreenUiState
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.compat.AccessibilityManagerCompat
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.dagger.PerDisplayCleanupTask
import com.android.launcher3.dagger.PerDisplaySingleton
import com.android.launcher3.dagger.WindowContext
import com.android.launcher3.desktop.DesktopRecentsTransitionController
import com.android.launcher3.display.DisplayController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory
import com.android.launcher3.statemanager.StatefulContainer
import com.android.launcher3.taskbar.TaskbarInteractor
import com.android.launcher3.testing.TestLogging
import com.android.launcher3.testing.shared.TestProtocol.LAUNCHER_ACTIVITY_LOST_WINDOW_FOCUS_MESSAGE
import com.android.launcher3.testing.shared.TestProtocol.LAUNCHER_ACTIVITY_STOPPED_MESSAGE
import com.android.launcher3.testing.shared.TestProtocol.SEQUENCE_MAIN
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.OverviewReleaseFlags.enablePredictiveBackInOverview
import com.android.launcher3.util.OverviewReleaseFlags.enableRecentsWindowBlur
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.ScreenOnTracker
import com.android.launcher3.util.ScreenOnTracker.ScreenOnListener
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.util.WallpaperColorHints
import com.android.launcher3.util.WindowBlurState.WINDOW_BLUR_STATE
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.views.ScrimView
import com.android.quickstep.BaseContainerInterface
import com.android.quickstep.FallbackWindowInterface
import com.android.quickstep.OverviewComponentObserver
import com.android.quickstep.RecentsAnimationCallbacks
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener
import com.android.quickstep.RecentsAnimationController
import com.android.quickstep.RecentsAnimationTargets
import com.android.quickstep.RecentsModel
import com.android.quickstep.RemoteAnimationTargets
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.TaskViewUtils
import com.android.quickstep.dagger.QuickstepBaseAppComponent
import com.android.quickstep.fallback.FallbackRecentsStateController
import com.android.quickstep.fallback.FallbackWindowRecentsView
import com.android.quickstep.fallback.RecentsDragLayer
import com.android.quickstep.fallback.RecentsState
import com.android.quickstep.fallback.RecentsState.Companion.BACKGROUND_APP
import com.android.quickstep.fallback.RecentsState.Companion.DEFAULT
import com.android.quickstep.fallback.RecentsState.Companion.HIDDEN
import com.android.quickstep.fallback.RecentsState.Companion.MODAL_TASK
import com.android.quickstep.fallback.RecentsState.Companion.OVERVIEW_SPLIT_SELECT
import com.android.quickstep.fallback.toLauncherStateOrdinal
import com.android.quickstep.split.SplitScreenAppResolver
import com.android.quickstep.split.SplitSelectStateController
import com.android.quickstep.util.QuickstepProtoLogGroup
import com.android.quickstep.util.RecentsAtomicAnimationFactory
import com.android.quickstep.util.RecentsWindowProtoLogProxy
import com.android.quickstep.util.SurfaceTransactionApplier
import com.android.quickstep.util.TraceStateLoggerHelper
import com.android.quickstep.views.OverviewActionsView
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import com.android.systemui.animation.back.FlingOnBackAnimationCallback
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.window.flags.Flags.useInputReportedFocusForAccessibility
import com.android.wm.shell.shared.IOverviewOverlayLeashInvalidationCallback
import com.android.wm.shell.shared.desktopmode.DesktopState
import javax.inject.Inject
import javax.inject.Named

/**
 * Class that will manage RecentsView lifecycle within a window and interface correctly where
 * needed. This allows us to run RecentsView in a window where needed.
 *
 * todo: b/365776320,b/365777482
 *
 * To add new protologs, see [RecentsWindowProtoLogProxy]. To enable logging to logcat, see
 * [QuickstepProtoLogGroup.Constants.DEBUG_RECENTS_WINDOW]
 */
@PerDisplaySingleton
class RecentsWindowManager
@Inject
constructor(
    @WindowContext private val windowContext: Context,
    private val fallbackWindowInterface: FallbackWindowInterface,
    private val recentsWindowTracker: RecentsWindowTracker,
    wallpaperColorHints: WallpaperColorHints,
    private val systemUiProxy: SystemUiProxy,
    recentsModel: RecentsModel,
    private val screenOnTracker: ScreenOnTracker,
    desktopState: DesktopState,
    displayController: DisplayController,
    @Ui private val uiExecutor: LooperExecutor,
    invariantDeviceProfile: InvariantDeviceProfile,
    lifeCycle: PerDisplayCleanupTask,
    @Named(WINDOW_BLUR_STATE) private val blurState: ListenableRef<Boolean>,
) :
    RecentsWindowContext(windowContext, wallpaperColorHints.hints, invariantDeviceProfile),
    RecentsViewContainer,
    StatefulContainer<RecentsState>,
    ComponentCallbacks {

    companion object {
        private const val HOME_APPEAR_DURATION: Long = 250
        private const val TAG = "RecentsWindow"

        @JvmField
        val REPOSITORY_INSTANCE =
            DaggerSingletonObject<PerDisplayRepository<RecentsWindowManager>>(
                QuickstepBaseAppComponent::getRecentsWindowManagerRepository
            )
    }

    private var recentsView: FallbackWindowRecentsView? = null
    private var windowlessWindowManager: WindowlessWindowManager? = null
    private var surfaceControlViewHost: SurfaceControlViewHost? = null
    private val layoutInflater: LayoutInflater = LayoutInflater.from(this).cloneInContext(this)
    private val stateManager: StateManager<RecentsState, RecentsWindowManager> =
        StateManager<RecentsState, RecentsWindowManager>(this, HIDDEN)
    private var systemUiController: SystemUiController? = null

    // The actual surface containing the view root
    private var recentsWindowSurface: SurfaceControl? = null

    // The overview container surface that holds the recents window surface
    private var overviewOverlay: SurfaceControl? = null

    // The home overlay surface that we'll making the overview container relative to have correct z
    // order
    private var homeOverlay: SurfaceControl? = null
    private var dragLayer: RecentsDragLayer<RecentsWindowManager>? = null
    private val windowRootView = RecentsWindowRootView(this)
    private var windowView: LauncherRootView? = null
    private var actionsView: OverviewActionsView<*>? = null
    private var scrimView: ScrimView? = null

    private var callbacks: RecentsAnimationCallbacks? = null

    @Volatile private var taskbarInteractor: TaskbarInteractor? = null

    private var oldConfiguration: Configuration? = null
    private var oldRotation: Int = -1

    private val depthController =
        if (enableRecentsWindowBlur()) DepthController(this, blurState) else null

    private val splitSelectStateController: SplitSelectStateController =
        SplitSelectStateController(
            /* container= */ this,
            stateManager,
            depthController,
            statsLogManager,
            systemUiProxy,
            recentsModel,
            /* activityBackCallback= */ null,
            SplitScreenUiState(),
            SplitScreenAppResolver(this),
        )

    // Callback array that corresponds to events defined in @ActivityEvent
    private val eventCallbacks =
        listOf(RunnableList(), RunnableList(), RunnableList(), RunnableList())

    val onBackInvokedCallback =
        if (enablePredictiveBackInOverview()) {
            object : FlingOnBackAnimationCallback() {
                override fun onBackInvokedCompat() {
                    Log.d(TAG, "onBackInvokedCompat: displayId=$displayId")
                    stateManager.state.onBackInvoked(this@RecentsWindowManager)
                    TestLogging.recordEvent(SEQUENCE_MAIN, "onBackInvoked")
                }

                override fun onBackStartedCompat(backEvent: BackEvent) {
                    Log.d(TAG, "onBackStartedCompat: displayId=$displayId")
                    stateManager.state.onBackStarted(this@RecentsWindowManager)
                }

                override fun onBackProgressedCompat(backEvent: BackEvent) {
                    Log.d(
                        TAG,
                        "onBackProgressedCompat: displayId=$displayId, progress=${backEvent.progress}",
                    )
                    stateManager.state.onBackProgressed(
                        this@RecentsWindowManager,
                        backEvent.progress,
                    )
                }

                override fun onBackCancelledCompat() {
                    Log.d(TAG, "onBackCancelledCompat: displayId=$displayId")
                }
            }
        } else {
            OnBackInvokedCallback {
                Log.d(TAG, "onBackInvokedCallback: displayId=$displayId")
                stateManager.state.onBackInvoked(this@RecentsWindowManager)
                TestLogging.recordEvent(SEQUENCE_MAIN, "onBackInvoked")
            }
        }

    private val recentsAnimationListener =
        object : RecentsAnimationListener {

            override fun onRecentsAnimationStart(
                controller: RecentsAnimationController?,
                targets: RecentsAnimationTargets?,
                transitionInfo: TransitionInfo?,
            ) {
                super.onRecentsAnimationStart(controller, targets, transitionInfo)
                // When the overview is launched via alt+tab, the subsequent tab to cycle through
                // tasks in overview can only be dispatched to focused views, while focus is
                // requested after posting on the requested view in OverviewCommandHelper. We
                // therefore also need to post this request onto the recents view.
                // (see OverviewCommandHelper#updateRecentsViewFocus)
                if (!useInputReportedFocusForAccessibility()) {
                    return
                }
                if (recentsView?.keyboardFocusTaskView == null) {
                    return
                }
                recentsView?.post { requestInputFocus(focused = true) }
            }

            override fun onRecentsAnimationCanceled(thumbnailDatas: HashMap<Int, ThumbnailData>) {
                super.onRecentsAnimationCanceled(thumbnailDatas)
                onRecentAnimationStopped()
            }

            override fun onRecentsAnimationFinished(controller: RecentsAnimationController) {
                super.onRecentsAnimationFinished(controller)
                onRecentAnimationStopped()
            }
        }

    private val screenChangedListener = ScreenOnListener { isOn ->
        if (!isOn && isRecentsViewVisible()) {
            Log.d(TAG, "screen turned off")
            recentsView?.startHome()
        }
    }

    private val overviewOverlayLeashInvalidationCallback =
        object : IOverviewOverlayLeashInvalidationCallback.Stub() {
            override fun onOverviewOverlayLeashInvalidated() {
                RecentsWindowProtoLogProxy.logOnOverviewOverlayLeashInvalidated()
                cleanUpSurfaceControlViewHost()
            }
        }

    private var activityLaunchAnimationRunner: RemoteAnimationFactory? = null

    private var displayChangesSafeCloseable: SafeCloseable? = null

    init {
        fallbackWindowInterface.setRecentsWindowManager(this)
        if (
            DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX.isTrue &&
                displayId != DEFAULT_DISPLAY &&
                desktopState.canEnterDesktopModeOrShowAppHandle
        ) {
            splitSelectStateController.initSplitFromDesktopController(this)
        }

        displayController.getListenable(displayId)?.let {
            displayChangesSafeCloseable =
                it.changes.forEach(uiExecutor) { _ -> onDisplayInfoChanged() }
        }

        lifeCycle.addTask { destroy() }

        TraceStateLoggerHelper(this).startTraceStateLogger()
    }

    @SuppressLint("InflateParams")
    @UiThread
    fun createWindowView() {
        if (windowView != null) {
            createSurfaceControlViewHost()
            return
        }
        surfaceControlViewHost?.let { cleanUpSurfaceControlViewHostInternal() }

        val applyStyle = { blurEnabled: Boolean ->
            Log.d(TAG, "applyStyle - blurEnabled: ${blurState.value}")
            theme.applyStyle(
                if (blurEnabled) R.style.OverviewBlurStyle else R.style.OverviewBlurFallbackStyle,
                /* force= */ true,
            )
        }
        if (enableRecentsWindowBlur()) {
            applyStyle(blurState.value)
            closeOnDestroy(blurState.forEach(uiExecutor) { blurEnabled -> applyStyle(blurEnabled) })
        } else {
            applyStyle(false)
        }

        windowView =
            layoutInflater.inflate(R.layout.fallback_recents_activity, null) as LauncherRootView
        windowView?.let {
            actionsView = it.findViewById(R.id.overview_actions_view)
            val emptyRecentsMessageView =
                it.findViewById<ViewGroup?>(R.id.empty_recents_message_view)
            recentsView =
                (it.findViewById<ViewStub>(R.id.overview_panel)
                        .apply { layoutResource = R.layout.fallback_window_recents_view }
                        .inflate() as? FallbackWindowRecentsView)
                    ?.apply {
                        init(
                            actionsView,
                            splitSelectStateController,
                            DesktopRecentsTransitionController(
                                stateManager,
                                systemUiProxy,
                                iApplicationThread,
                                depthController,
                            ),
                            SurfaceTransactionApplier(rootView),
                            emptyRecentsMessageView,
                        )
                    }
            actionsView?.apply {
                updateDimension(getDeviceProfile(), recentsView?.lastComputedTaskSize)
                updateVerticalMargin(DisplayController.getNavigationMode(this@RecentsWindowManager))
            }
            scrimView = it.findViewById(R.id.scrim_view)
            dragLayer = it.findViewById(R.id.drag_layer)

            it.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

            windowRootView.addView(it)

            createSurfaceControlViewHost()

            windowRootView
                .findOnBackInvokedDispatcher()
                ?.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    onBackInvokedCallback,
                )
            updateDisallowBack()

            recentsWindowTracker.handleCreate(this)
            onViewCreated()
        }
        systemUiController = SystemUiController(windowView)
        registerComponentCallbacks(this)
    }

    override fun destroy() {
        Log.d(TAG, "destroy")
        super.destroy()
        splitSelectStateController.onDestroy()
        displayChangesSafeCloseable?.close()
        displayChangesSafeCloseable = null
        fallbackWindowInterface.setRecentsWindowManager(null)
        recentsView?.post { requestInputFocus(focused = false) }
        uiExecutor.execute {
            onViewDestroyed()
            hideRecentsWindow()
            cleanUpSurfaceControlViewHostInternal()
            windowRootView
                .findOnBackInvokedDispatcher()
                ?.unregisterOnBackInvokedCallback(onBackInvokedCallback)
            callbacks?.removeListener(recentsAnimationListener)
            unregisterComponentCallbacks(this)
            recentsWindowTracker.onContextDestroyed(this)
            recentsView?.destroy()
            recentsView = null
            windowView = null
        }
    }

    private fun createSurfaceControlViewHost() {
        if (this.surfaceControlViewHost != null) return

        val recentsWindowSurface: SurfaceControl
        val surfaceControlViewHost: SurfaceControlViewHost
        if (Flags.updateRecentsWmWwmConfiguration()) {
            recentsWindowSurface =
                SurfaceControl.Builder()
                    .setContainerLayer()
                    .setName(TAG)
                    .setCallsite(TAG)
                    .build()
                    .also { this.recentsWindowSurface = it }

            val windowlessWindowManager =
                WindowlessWindowManager(
                        windowContext.resources.configuration,
                        recentsWindowSurface,
                        windowRootView.viewRootImpl?.inputToken?.let { InputTransferToken(it) },
                    )
                    .also { this.windowlessWindowManager = it }

            surfaceControlViewHost =
                SurfaceControlViewHost(this, display, windowlessWindowManager, TAG).also {
                    this.surfaceControlViewHost = it
                }
        } else {
            surfaceControlViewHost =
                SurfaceControlViewHost(this, display, windowRootView.viewRootImpl?.inputToken)
                    .also { this.surfaceControlViewHost = it }
            recentsWindowSurface = surfaceControlViewHost.surfacePackage!!.surfaceControl
        }

        surfaceControlViewHost.let { scvh ->
            scvh.setView(windowRootView, getWindowLayoutParams())
            getOverviewOverlay()?.let { overviewOverlay ->
                val transaction =
                    Transaction()
                        .reparent(recentsWindowSurface, overviewOverlay)
                        .show(recentsWindowSurface)

                getHomeTaskOverlay()?.let { homeOverlay ->
                    // Use an arbitrarily large z-order since the home task can have multiple
                    // child tasks
                    transaction.setRelativeLayer(overviewOverlay, homeOverlay, 1000)
                }

                transaction.apply(true)
            } ?: run { Log.e(TAG, "OverviewOverlay is null, can't reparent surface", Exception()) }
        }
    }

    @UiThread
    private fun cleanUpSurfaceControlViewHostInternal() {
        RecentsWindowProtoLogProxy.logCleanUpSurfaceControlViewHostInternal()
        if (Flags.updateRecentsWmWwmConfiguration()) {
            surfaceControlViewHost?.release()
            recentsWindowSurface?.let {
                Transaction().hide(it).apply(true)
                it.release()
            }
        } else {
            surfaceControlViewHost?.let {
                it.surfacePackage?.let { surfacePackage ->
                    Transaction().hide(surfacePackage.surfaceControl).apply(true)
                    surfacePackage.release()
                }
                it.release()
            }
        }
        overviewOverlay?.let {
            systemUiProxy.unregisterOverviewOverlayLeashInvalidationListener(
                displayId,
                overviewOverlayLeashInvalidationCallback,
            )
        }
        homeOverlay = null
        overviewOverlay = null
        surfaceControlViewHost = null
        windowlessWindowManager = null
        recentsWindowSurface = null
    }

    @UiThread
    fun showRecentsWindow(callbacks: RecentsAnimationCallbacks? = null) {
        RecentsWindowProtoLogProxy.logStartRecentsWindow(isShowing(), windowView == null)
        if (isShowing()) {
            return
        }
        createWindowView()
        windowRootView.visibility = View.VISIBLE

        this.callbacks = callbacks
        callbacks?.addListener(recentsAnimationListener)
        screenOnTracker.addListener(screenChangedListener)
    }

    private fun hideRecentsWindow() {
        RecentsWindowProtoLogProxy.logCleanup(isShowing())
        if (isShowing()) {
            AbstractFloatingView.closeAllOpenViews(this, /* animate= */ false)
            recentsView?.viewRootImpl?.touchModeChanged(true)
            windowRootView.visibility = View.GONE
            AccessibilityManagerCompat.sendTestProtocolEventToTest(
                this,
                LAUNCHER_ACTIVITY_LOST_WINDOW_FOCUS_MESSAGE,
            )
            AccessibilityManagerCompat.sendTestProtocolEventToTest(
                this,
                LAUNCHER_ACTIVITY_STOPPED_MESSAGE,
            )
        }
        callbacks?.removeListener(recentsAnimationListener)
        callbacks = null
        screenOnTracker.removeListener(screenChangedListener)
    }

    fun cleanUpSurfaceControlViewHost() {
        uiExecutor.execute {
            stateManager.moveToRestState()

            cleanUpSurfaceControlViewHostInternal()
        }
    }

    override fun handleConfigurationChanged(newConfiguration: Configuration?) {
        val diff = oldConfiguration?.let { newConfiguration?.diff(it) } ?: -1
        val rotation = WindowManagerProxy.INSTANCE[this].getRotation(this)
        if ((diff and (CONFIG_ORIENTATION or CONFIG_SCREEN_SIZE)) != 0 || rotation != oldRotation) {
            onHandleConfigurationChanged()
        }

        if (Flags.updateRecentsWmWwmConfiguration()) {
            windowlessWindowManager?.setConfiguration(newConfiguration)
        }
        oldConfiguration = newConfiguration
        oldRotation = rotation
    }

    private fun onHandleConfigurationChanged() {
        initDeviceProfile()
        AbstractFloatingView.closeOpenViews(
            this,
            true,
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv(),
        )
        dispatchDeviceProfileChanged()

        windowView?.dispatchInsets()
        getStateManager().reapplyState(true /* cancelCurrentAnimation */)
        dragLayer?.recreateControllers()
        updateDisallowBack()
    }

    private fun updateDisallowBack() {
        fallbackWindowInterface.updateDisallowBack()
    }

    private fun onDisplayInfoChanged() {
        initDeviceProfile()
        surfaceControlViewHost?.relayout(getWindowLayoutParams())
    }

    fun getOverviewOverlay(): SurfaceControl? {
        if (overviewOverlay == null) {
            overviewOverlay = systemUiProxy.getOverviewOverlayContainer(displayId)
            overviewOverlay?.let {
                systemUiProxy.registerOverviewOverlayLeashInvalidationCallback(
                    displayId,
                    overviewOverlayLeashInvalidationCallback,
                )
            }
        }
        return overviewOverlay
    }

    private fun getHomeTaskOverlay(): SurfaceControl? {
        // TODO(b/292269949): use the correct home task overlay once available on multiple displays
        if (displayId != DEFAULT_DISPLAY) {
            return null
        }
        if (homeOverlay == null) {
            homeOverlay = systemUiProxy.getHomeTaskOverlayContainer()
        }
        return homeOverlay
    }

    fun requestInputFocus(focused: Boolean) {
        if (!useInputReportedFocusForAccessibility()) {
            return
        }
        surfaceControlViewHost?.requestInputFocus(focused)
    }

    override fun onConfigurationChanged(newConfiguration: Configuration) {
        Log.d(TAG, "onConfigurationChanged: $newConfiguration")
        handleConfigurationChanged(newConfiguration)
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        // Do nothing
    }

    override fun startHome(animated: Boolean, onHomeAnimationComplete: Runnable?) {
        startHomeWithRemoteAnimation(onHomeAnimationComplete = onHomeAnimationComplete)
    }

    @JvmOverloads
    fun startHomeWithRemoteAnimation(
        finishRecentsAnimation: Boolean = true,
        onHomeAnimationComplete: Runnable? = null,
    ) {
        val recentsView: RecentsView<*, *>? = getOverviewPanel()
        if (recentsView == null) {
            onHomeAnimationComplete?.run()
            return
        }
        recentsView.switchToScreenshot {
            if (finishRecentsAnimation) {
                recentsView.finishRecentsAnimation(
                    /* toHome= */ true,
                    { startHomeWithRemoteAnimationInternal(onHomeAnimationComplete) },
                )
            } else {
                startHomeWithRemoteAnimationInternal(onHomeAnimationComplete)
            }
        }
    }

    override fun getActivityLaunchOptions(view: View?, item: ItemInfo?): ActivityOptionsWrapper {
        val taskView =
            view as? TaskView
                ?: return super<RecentsWindowContext>.getActivityLaunchOptions(view, item)
        val recentsView =
            recentsView ?: return super<RecentsWindowContext>.getActivityLaunchOptions(view, item)
        val onEndCallback = RunnableList()

        activityLaunchAnimationRunner =
            object : RemoteAnimationFactory {
                override fun onAnimationStart(
                    transit: Int,
                    apps: Array<RemoteAnimationTarget>?,
                    wallpapers: Array<RemoteAnimationTarget>?,
                    nonApps: Array<RemoteAnimationTarget>?,
                    callback: LauncherAnimationRunner.AnimationResult?,
                ) {
                    if (apps == null || wallpapers == null || nonApps == null || callback == null) {
                        return
                    }
                    val anim =
                        composeRecentsLaunchAnimator(
                                recentsView,
                                taskView,
                                apps,
                                wallpapers,
                                nonApps,
                            )
                            .apply {
                                addListener(
                                    onEnd = {
                                        recentsView.resetTaskVisuals()
                                        stateManager.moveToRestState()
                                    }
                                )
                            }
                    callback.setAnimation(
                        anim,
                        this@RecentsWindowManager,
                        { onEndCallback.executeAllAndDestroy() },
                        true, /* skipFirstFrame */
                    )
                }

                override fun onAnimationCancelled() {
                    onEndCallback.executeAllAndDestroy()
                }
            }

        val wrapper =
            LauncherAnimationRunner(
                uiExecutor.handler,
                activityLaunchAnimationRunner,
                /* startAtFrontOfQueue=*/ true,
            )
        val options =
            ActivityOptions.makeRemoteAnimation(
                RemoteAnimationAdapter(
                    wrapper,
                    RECENTS_LAUNCH_DURATION.toLong(),
                    (RECENTS_LAUNCH_DURATION -
                            STATUS_BAR_TRANSITION_DURATION -
                            STATUS_BAR_TRANSITION_PRE_DELAY)
                        .toLong(),
                ),
                RemoteTransition(
                    wrapper.toRemoteTransition(),
                    iApplicationThread,
                    "LaunchFromRecents",
                ),
            )
        return ActivityOptionsWrapper(options, onEndCallback).apply {
            options.apply {
                splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
                launchDisplayId = taskView.displayId
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
        }
    }

    /** Composes the animations for a launch from the recents list if possible. */
    private fun composeRecentsLaunchAnimator(
        recentsView: RecentsView<*, *>,
        taskView: TaskView,
        appTargets: Array<RemoteAnimationTarget>,
        wallpaperTargets: Array<RemoteAnimationTarget>,
        nonAppTargets: Array<RemoteAnimationTarget>,
    ): AnimatorSet {
        val animatorSet = AnimatorSet()
        val pendingAnimation = PendingAnimation(RECENTS_LAUNCH_DURATION.toLong())
        TaskViewUtils.createRecentsWindowAnimator(
            recentsView,
            taskView,
            /* skipViewChanges= */ true,
            appTargets,
            wallpaperTargets,
            nonAppTargets,
            depthController,
            /* transitionInfo= */ null,
            /* appearedTaskId= */ ActivityTaskManager.INVALID_TASK_ID,
            pendingAnimation,
        )
        animatorSet.play(pendingAnimation.buildAnim())
        return animatorSet
    }

    private fun startHomeWithRemoteAnimationInternal(onHomeAnimationComplete: Runnable?) {
        val displayId = displayId
        val animationToHomeFactory =
            RemoteAnimationFactory {
                _: Int,
                appTargets: Array<RemoteAnimationTarget>?,
                wallpaperTargets: Array<RemoteAnimationTarget>?,
                nonAppTargets: Array<RemoteAnimationTarget>?,
                result: LauncherAnimationRunner.AnimationResult? ->
                result ?: return@RemoteAnimationFactory
                val controller =
                    getStateManager().createAnimationToNewWorkspace(HIDDEN, HOME_APPEAR_DURATION)
                controller.dispatchOnStart()
                val targets =
                    RemoteAnimationTargets(
                        appTargets,
                        wallpaperTargets,
                        nonAppTargets,
                        RemoteAnimationTarget.MODE_OPENING,
                    )
                targets.apps.forEach { Transaction().setAlpha(it.leash, 1f).apply() }
                val anim =
                    AnimatorSet().apply {
                        play(controller.animationPlayer)
                        duration = HOME_APPEAR_DURATION
                    }
                result.setAnimation(
                    anim,
                    this@RecentsWindowManager,
                    {
                        getStateManager().moveToRestState(/* isAnimated= */ true)
                        onHomeAnimationComplete?.run()
                    },
                    true, /* skipFirstFrame */
                )
            }
        val runner = LauncherAnimationRunner(mainThreadHandler, animationToHomeFactory, true)
        val options =
            ActivityOptions.makeRemoteAnimation(
                RemoteAnimationAdapter(runner, HOME_APPEAR_DURATION, 0),
                RemoteTransition(
                    runner.toRemoteTransition(),
                    iApplicationThread,
                    "StartHomeFromRecents",
                ),
            )
        options.launchDisplayId = displayId
        OverviewComponentObserver.startHomeIntentSafely(this, options.toBundle(), TAG, displayId)
    }

    private fun isShowing() = windowView?.parent != null && windowRootView.isVisible

    private fun onRecentAnimationStopped() {
        if (isInState(BACKGROUND_APP)) {
            stateManager.moveToRestState()
        }
    }

    override fun getComponentName() = ComponentName(this, RecentsWindowManager::class.java)

    override fun canStartHomeSafely(): Boolean {
        val overviewCommandHelper =
            appComponent.sysUIConnectionTracker.activeComponent.value
                ?.overviewCommandHelper
                ?.getIfReady()
        return overviewCommandHelper == null ||
            overviewCommandHelper.canStartHomeSafely() ||
            displayId != DEFAULT_DISPLAY
    }

    @AnyThread
    override fun setTaskbarInteractor(taskbarInteractor: TaskbarInteractor?) {
        this.taskbarInteractor = taskbarInteractor
    }

    override fun getTaskbarInteractor() = taskbarInteractor

    override fun collectStateHandlers(out: MutableList<StateManager.StateHandler<RecentsState?>>?) {
        out!!.add(FallbackRecentsStateController(this))
        if (depthController != null) {
            out.add(depthController)
        }
    }

    override fun getStateManager() = stateManager

    override fun shouldAnimateStateChange() = false

    override fun onStateSetStart(state: RecentsState) {
        super.onStateSetStart(state)
        RecentsWindowProtoLogProxy.logOnStateSetStart(state.toString())
    }

    override fun onStateSetEnd(state: RecentsState) {
        super.onStateSetEnd(state)
        RecentsWindowProtoLogProxy.logOnStateSetEnd(state.toString())
        state.applyRecentsWindowVisibility()
        AccessibilityManagerCompat.sendStateEventToTest(baseContext, state.toLauncherStateOrdinal())
    }

    override fun onRepeatStateSetAborted(state: RecentsState) {
        super.onRepeatStateSetAborted(state)
        RecentsWindowProtoLogProxy.logOnRepeatStateSetAborted(state.toString())
        state.applyRecentsWindowVisibility()
    }

    private fun RecentsState.applyRecentsWindowVisibility() {
        if (isRecentsViewVisible()) {
            showRecentsWindow()
        } else {
            hideRecentsWindow()
        }
        updateDisallowBack()
    }

    override fun getSystemUiController(): SystemUiController? {
        if (systemUiController == null) {
            systemUiController = SystemUiController(rootView)
        }
        return systemUiController
    }

    override fun getScrimView() = scrimView

    override fun getBackgroundAppState() = BACKGROUND_APP

    override fun <T : BaseContainerInterface<*, *>?> getContainerInterface() =
        fallbackWindowInterface as T

    override fun <T : View?> getOverviewPanel() = recentsView as T

    override fun getSplitSelectStateController() = splitSelectStateController

    override fun goToRecentsState(
        recentsState: RecentsState,
        animated: Boolean,
        listener: Animator.AnimatorListener?,
    ) {
        stateManager.goToState(recentsState, animated, listener)
    }

    override fun getRootView(): View = windowRootView

    override fun getDragLayer(): BaseDragLayer<RecentsWindowManager>? = dragLayer

    override fun dispatchGenericMotionEvent(ev: MotionEvent?) =
        windowRootView.dispatchGenericMotionEvent(ev)

    override fun dispatchKeyEvent(ev: KeyEvent?) = windowRootView.dispatchKeyEvent(ev)

    override fun onRootViewDispatchKeyEvent(event: KeyEvent?): Boolean {
        TestLogging.recordKeyEvent(SEQUENCE_MAIN, "Key event", event)
        val isBackEvent =
            event?.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BACK
        val isEscEvent =
            event?.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_ESCAPE &&
                event.hasNoModifiers()
        return if (!isEscEvent && !isBackEvent) {
            super.onRootViewDispatchKeyEvent(event)
        } else if (isEscEvent) {
            if (isInState(OVERVIEW_SPLIT_SELECT) || isInState(MODAL_TASK)) {
                stateManager.goToState(DEFAULT, true)
                true
            } else if (isInState(DEFAULT)) {
                stateManager.state.onBackInvoked(this@RecentsWindowManager)
                true
            } else {
                super.onRootViewDispatchKeyEvent(event)
            }
        } else {
            onBackInvokedCallback.onBackInvoked()
            true
        }
    }

    override fun getActionsView() = actionsView

    override fun addForceInvisibleFlag(flag: Int) {}

    override fun clearForceInvisibleFlag(flag: Int) {}

    override fun setLocusContext(id: LocusId?, bundle: Bundle?) {
        // no op
    }

    override fun isStarted(): Boolean {
        return isShowing() && stateManager.state.isRecentsViewVisible()
    }

    /** Adds a callback for the provided activity event */
    override fun addEventCallback(@BaseActivity.ActivityEvent event: Int, callback: Runnable?) {
        eventCallbacks[event].add(callback)
    }

    /** Removes a previously added callback */
    override fun removeEventCallback(@BaseActivity.ActivityEvent event: Int, callback: Runnable?) {
        eventCallbacks[event].remove(callback)
    }

    override fun returnToHomescreenAfterFreeformShortcut() {
        startHomeWithRemoteAnimation()
    }

    override fun isRecentsViewVisible() =
        isShowing() || getStateManager().state!!.isRecentsViewVisible()

    override fun createAtomicAnimationFactory(): AtomicAnimationFactory<RecentsState> =
        RecentsAtomicAnimationFactory(this)

    override fun getDepthController(): DepthController<RecentsState, RecentsWindowManager>? =
        depthController
}
