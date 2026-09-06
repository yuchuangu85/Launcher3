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

import android.graphics.Rect
import android.graphics.Region
import android.os.Trace
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.android.compose.theme.PlatformTheme
import com.android.internal.jank.InteractionJankMonitor
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext
import com.android.launcher3.util.Executors.ORDERED_BG_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import android.service.personalcontext.PersonalContextManager
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl
import com.android.quickstep.cuebar.domain.interactor.AmbientCueInteractor
import com.android.quickstep.cuebar.logger.AmbientCueAceLogger
import com.android.quickstep.cuebar.logger.AmbientCueLoggerImpl
import com.android.quickstep.cuebar.ui.AmbientCueContainer
import com.android.quickstep.cuebar.ui.utils.AmbientCueAnimationState
import com.android.quickstep.cuebar.ui.utils.AmbientCueJankMonitor
import com.android.quickstep.cuebar.ui.viewmodel.AmbientCueViewModel
import com.android.systemui.shared.Flags.cueBarAceMigration
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_VISIBLE
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE
import java.io.PrintWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel

class CueBarController(private val activity: TaskbarActivityContext) :
    TaskbarControllers.LoggableTaskbarController {

    private lateinit var taskbarControllers: TaskbarControllers
    private var pillBoundsInWindow: Rect? = null
    private var internalComposeView: ComposeView? = null
    private val coroutineScope = CoroutineScope(getTaskbarUiThread().asCoroutineDispatcher())
    private var mOverlayContext: TaskbarOverlayContext? = null
    private var cueBar: View? = null
    private var isHiding = false
    // Deprecated, please use ambientCueAceLogger instead.
    private val ambientCueLogger = AmbientCueLoggerImpl(activity.packageManager)
    private val personalContextManager: PersonalContextManager? =
        activity.getSystemService(PersonalContextManager::class.java)
    private val ambientCueAceLogger = AmbientCueAceLogger(personalContextManager)
    val ambientCueRepository =
        AmbientCueRepositoryImpl(
            activity,
            ambientCueLogger,
            ambientCueAceLogger,
            ORDERED_BG_EXECUTOR,
            getTaskbarUiThread(),
        )
    private val ambientCueInteractor = AmbientCueInteractor(ambientCueRepository)
    private val lp =
        InsettableFrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            .apply { ignoreInsets = true }
    val isExpanded: Boolean
        get() = ambientCueViewModel.isExpanded

    val isVisible: Boolean
        get() = ambientCueViewModel.isVisible

    private val ambientCueViewModel: AmbientCueViewModel =
        AmbientCueViewModel(
                ambientCueInteractor = ambientCueInteractor,
                launcherPrefs = LauncherPrefs.get(activity),
                scope = coroutineScope,
                ambientCueLogger = ambientCueLogger,
                ambientCueAceLogger = ambientCueAceLogger,
                isDesktopFormFactor = activity.isDesktopFormFactor(),
                uiExecutor = getTaskbarUiThread(),
            )
            .apply {
                onVisibilityChanged = { isCueBarVisible ->
                    onCueBarVisibilityChanged(isCueBarVisible)
                }
            }

    fun init(controllers: TaskbarControllers) {
        if (!cueBarAceMigration()) {
            return
        }
        ambientCueRepository.connectToAce()
        taskbarControllers = controllers
        createCueBar()
    }

    private fun createCueBar() {
        val viewModelFactory =
            object : AmbientCueViewModel.Factory {
                override fun create(): AmbientCueViewModel {
                    return ambientCueViewModel
                }
            }
        val composeView = ComposeView(activity)
        val ambientCueJankMonitor =
            AmbientCueJankMonitor(InteractionJankMonitor.getInstance(), composeView)
        cueBar =
            composeView.apply {
                addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            Trace.beginAsyncSection("CueBarAttached", 0)
                        }

                        override fun onViewDetachedFromWindow(v: View) {
                            Trace.endAsyncSection("CueBarAttached", 0)
                        }
                    }
                )
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setContent {
                    PlatformTheme {
                        AmbientCueContainer(
                            modifier = Modifier.fillMaxSize(),
                            ambientCueViewModelFactory = viewModelFactory,
                            onShouldInterceptTouches = { intercept, composeRect: ComposeRect? ->
                                if (composeRect != null && !composeRect.isEmpty) {
                                    if (pillBoundsInWindow == null) {
                                        pillBoundsInWindow = Rect()
                                    }
                                    pillBoundsInWindow?.set(
                                        composeRect.left.toInt(),
                                        composeRect.top.toInt(),
                                        composeRect.right.toInt(),
                                        composeRect.bottom.toInt(),
                                    )
                                } else if (isExpanded) {
                                    pillBoundsInWindow = null
                                }
                            },
                            onAnimationStateChange = { cujType, animationState ->
                                ambientCueJankMonitor.onAnimationStateChange(
                                    cujType,
                                    animationState,
                                )
                                handleAnimationStateChange(animationState)
                            },
                        )
                    }
                }
            }
    }

    private fun animateStashHandleAlpha(show: Boolean) {
        val stashedHandleAlpha =
            taskbarControllers.stashedHandleViewController.stashedHandleAlpha.get(
                StashedHandleViewController.ALPHA_INDEX_CUEBAR_HIDDEN
            )
        stashedHandleAlpha
            .animateToValue(if (show) 1f else 0f)
            .setDuration(STASHED_HANDLE_ALPHA_ANIMATION_DURATION_MS)
            .start()
    }

    private fun handleAnimationStateChange(state: AmbientCueAnimationState) {
        if (isHiding && state == AmbientCueAnimationState.END) {
            Log.d(TAG, "Animation finished and view is marked for hiding. Removing view.")
            if (cueBar != null && cueBar?.parent != null) {
                taskbarControllers.taskbarOverlayController.hideWindow()
            }
            animateStashHandleAlpha(true)
        }
    }

    fun cleanUpOverlay() {
        internalComposeView?.disposeComposition()
        if (mOverlayContext == null) {
            return
        }
        isHiding = false
        mOverlayContext?.dragLayer?.removeView(internalComposeView)
        internalComposeView = null
        mOverlayContext = null
    }

    fun onDestroy() {
        if (!cueBarAceMigration()) {
            return
        }
        Log.d(TAG, "CuebarController destroy")
        cleanUpOverlay()
        taskbarControllers.sharedState?.cueBarVisible = false
        ambientCueViewModel.deactivate()
        ambientCueRepository.disconnectFromAce()
        coroutineScope.cancel()
    }

    companion object {
        private const val TAG = "CueBarController"
        private const val STASHED_HANDLE_ALPHA_ANIMATION_DURATION_MS = 150L
    }

    override fun dumpLogs(prefix: String?, pw: PrintWriter?) {
        pw ?: return
        pw.println("$prefix CueBarController:")
        ambientCueRepository.dump(pw, "$prefix  ")
    }

    fun onTaskbarStatusUpdated(visible: Boolean, stashed: Boolean) {
        if (!cueBarAceMigration()) {
            return
        }
        ambientCueRepository.isTaskBarVisible.dispatchValue(visible && !stashed)
    }

    fun onRecentsButtonLayoutChanged(bounds: Rect) {
        if (!cueBarAceMigration()) {
            return
        }
        ambientCueRepository.recentsButtonPosition.dispatchValue(bounds)
    }

    private fun onCueBarVisibilityChanged(isCueBarVisible: Boolean) {
        if (!cueBarAceMigration()) {
            return
        }
        taskbarControllers.sharedState?.cueBarVisible = isCueBarVisible

        val context = taskbarControllers.taskbarActivityContext
        if (context != null && context.isThreeButtonNav) {
            taskbarControllers.navbarButtonsViewController?.setCueBarVisible(isCueBarVisible)
        }
        animateStashHandleAlpha(!isCueBarVisible)

        if (isCueBarVisible) {
            isHiding = false // Cancel any pending hide

            if (cueBar?.parent == null || mOverlayContext == null) {
                Log.w(TAG, "CueBar parent is null. Window was likely destroyed. Re-requesting.")
                ambientCueViewModel.activate()
                mOverlayContext = taskbarControllers.taskbarOverlayController.requestCueBarWindow()
                createCueBar()
            }
            (cueBar?.parent as? ViewGroup)?.removeView(cueBar)
            mOverlayContext?.dragLayer?.addView(cueBar)
            cueBar?.layoutParams = lp
            internalComposeView = cueBar as ComposeView
            // Set the persistent view to VISIBLE. The compose state will
            // automatically trigger the entry animation.
            cueBar?.visibility = View.VISIBLE
        } else {
            Log.d(TAG, "Marking CueBarView for hiding. Will set GONE after animation.")
            isHiding = true
        }
    }

    /** Updates the CueBar repository values based on the current [systemUiStateFlags]. */
    fun updateStateForSysuiFlags(systemUiStateFlags: Long) {
        if (!cueBarAceMigration()) {
            return
        }
        val isImeVisible = (systemUiStateFlags and SYSUI_STATE_IME_VISIBLE) != 0L
        ambientCueRepository.isImeVisible.dispatchValue(isImeVisible)
        // Note: TaskbarActivityContext.ENABLE_TASKBAR_BEHIND_SHADE is not used here, assuming
        // the notification panel always occludes the CueBar.
        val isNotificationPanelVisible =
            (systemUiStateFlags and SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE) != 0L
        ambientCueRepository.isOccludedBySystemUi.dispatchValue(isNotificationPanelVisible)
    }

    /**
     * Adds the touchable bounds of the CueBar to the given [region].
     *
     * If the CueBar is expanded, the entire ComposeView bounds are added. Otherwise, only the
     * bounds of the pill-shaped CueBar are added to the [region].
     */
    fun addTouchableRegion(region: Region) {
        if (cueBar == null || cueBar?.visibility != View.VISIBLE) {
            return
        }
        val boundsToUse = pillBoundsInWindow
        // Add cueBar bounds to the provided touchable region
        if (boundsToUse != null && !boundsToUse.isEmpty) {
            region.op(boundsToUse, Region.Op.UNION)
        } else if (isExpanded) {
            // pillBoundsInWindow is null, but we are explicitly expanded.
            // Makes the ComposeView (the fullscreen scrim) touchable.
            val location = IntArray(2)
            cueBar!!.getLocationInWindow(location)
            val viewWidth = cueBar!!.width
            val viewHeight = cueBar!!.height
            val fullscreenBounds =
                Rect(location[0], location[1], location[0] + viewWidth, location[1] + viewHeight)
            if (!fullscreenBounds.isEmpty) {
                region.op(fullscreenBounds, Region.Op.UNION)
            }
        }
    }
}
