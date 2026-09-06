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

package com.android.launcher3.widget.resize

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Trace
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.android.launcher3.AppWidgetResizeFrameBase
import com.android.launcher3.CellLayout
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.PopupTouchHandler
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.widget.LauncherAppWidgetHostView

/**
 * A floating view using compose based UI representing the frame shown with resize handles (dots)
 * and buttons around the widgets when you hold press it.
 *
 * Responsibilities:
 * - Provides a sized container for showing the frame
 * - Handle interactions for closing things outside the frame or closing the frame itself
 * - Initialize the compose UI and its necessary dependencies and clean them up when closing.
 */
class AppWidgetResizeFrameCompose
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AppWidgetResizeFrameBase(context, attrs, defStyleAttr),
    DragController.DragSessionListener,
    ResizeManager.ResizeListener,
    View.OnKeyListener {
    private val launcher: Launcher = Launcher.getLauncher(context)
    private val popupTouchHandler = PopupTouchHandler()

    private lateinit var resizeManager: ResizeManager
    private lateinit var viewModel: ResizeFrameViewModel
    private lateinit var widgetView: View
    private lateinit var composeView: ComposeView

    // Extra padding to leave out in frame size for the button content.
    private val touchTargetSize: Int =
        context.resources.getDimensionPixelSize(R.dimen.resize_frame_touch_target_size)

    // Reusable rect for temporary usage in frequently occurring operations.
    private val tempRect = Rect()

    override fun onNewResizeRequest(bounds: Rect) {
        // Adjust the floating view to the requested size, so frame also shows at the size.
        (layoutParams as BaseDragLayer.LayoutParams).apply {
            x = bounds.left - touchTargetSize
            y = bounds.top - touchTargetSize
            width = bounds.width() + 2 * touchTargetSize
            height = bounds.height() + 2 * touchTargetSize
        }

        requestLayout()

        isFocusableInTouchMode = true
        requestFocus()
    }

    @VisibleForTesting fun getViewModelForTest() = viewModel

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        // Let the widget underneath get events; the compose view will react to events based on
        // where its interactable content is shown; and as such we don't want to steal touches.
        return false
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (launcher.dragController.isDragging) {
            return false
        }

        getHitRect(tempRect)
        // Hit rect might not yet be initialized if the resize frame intercepts the touch event
        // (e.g., ACTION_UP from a right-click) before the first layout pass has completed.
        if (tempRect.isEmpty) {
            return false
        }

        if (tempRect.contains(ev.x.toInt(), ev.y.toInt())) {
            return false // let the widget's OR our compose view's handling do its thing.
        }

        // Now that we have intercepted a touch outside of frame, let's close it.
        close(/* animate= */ false)

        // And forward the event over to popup to see if it needs to do anything.
        val popup = PopupContainer.getOpen(launcher)
        return popupTouchHandler.handleTouchEvent(popup, ev)
    }

    override fun onDragSessionStart(dragObject: DropTarget.DragObject?, options: DragOptions?) {
        close(/* animate= */ true) // close frame if user starts dragging widget.
    }

    /** Retrieves the view where accessibility actions happen. */
    override fun getViewForAccessibility(): View = widgetView

    override fun handleClose(animate: Boolean) {
        try {
            Trace.beginSection("closeAppWidgetResizeFrame")
            if (mIsOpen) {
                resizeManager.finish()
            }

            // Mark the frame as closed before removing it from drag layer so drag layer knows not
            // to asynchronously call `close()` again (which happens with `AbstractFloatingView`s
            // that get removed without being closed).
            mIsOpen = false
            launcher.dragController.removeDragSessionListener(this)
            launcher.dragLayer.removeView(this) // removing view should dispose composition.
        } finally {
            Trace.endSection()
        }
    }

    private fun setup(
        widgetView: LauncherAppWidgetHostView,
        cellLayout: CellLayout,
        dragLayer: DragLayer,
    ) {
        this.widgetView = widgetView
        setOnKeyListener(this)

        resizeManager =
            ResizeManager(
                    widgetView = widgetView,
                    cellLayout = cellLayout,
                    cellPosMapper = launcher.cellPosMapper,
                    dragLayer = dragLayer,
                    deviceProfile = launcher.deviceProfile,
                    statsLogManager = launcher.statsLogManager,
                    touchPadding = touchTargetSize,
                )
                .apply { resizeListener = this@AppWidgetResizeFrameCompose }

        viewModel = ResizeFrameViewModel(resizeManager, touchTargetSize)

        composeView =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

                setContent {
                    ResizeFrame(
                        viewModel = viewModel,
                        onInteraction = this@AppWidgetResizeFrameCompose::dismissPopupMenu,
                        onDismiss = {
                            dismissPopupMenu()
                            close(false)
                        },
                    )
                }
            }
        addView(composeView)

        val launcher = Launcher.getLauncher(context)
        launcher.dragController.addDragSessionListener(this)
    }

    /**
     * When user has initiated a resize or tapped on one of handles, we want to close any open
     * popups.
     */
    fun dismissPopupMenu() {
        PopupContainer.getOpen(launcher)?.close(/* animate= */ true)
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        // Clear the frame and give focus to the widget host view when a directional key is pressed.
        if (shouldCloseResizeFrame(keyCode)) {
            close(/* animate= */ false)
            widgetView.requestFocus()
            return true
        }
        return false
    }

    companion object {
        fun show(
            activityContext: ActivityContext,
            widgetView: LauncherAppWidgetHostView,
            cellLayout: CellLayout,
        ): AppWidgetResizeFrameCompose {
            closeAllOpenViewsExcept(activityContext, TYPE_ACTION_POPUP)

            val dragLayer = activityContext.dragLayer as DragLayer
            val resizeFrame =
                activityContext.layoutInflater.inflate(
                    R.layout.app_widget_resize_frame_compose,
                    dragLayer,
                    /*attachToRoot=*/ false,
                ) as AppWidgetResizeFrameCompose

            // Save widget item info as tag on resize frame; so that, the accessibility
            // delegate can attach actions that typically happen on widget (e.g. resize,
            // move) also on the resize frame.
            resizeFrame.apply {
                tag = widgetView.tag
                accessibilityDelegate = activityContext.accessibilityDelegate
                contentDescription =
                    activityContext
                        .asContext()
                        .getString(
                            R.string.widget_frame_name,
                            (widgetView.tag as ItemInfo).contentDescription,
                        )
                (layoutParams as BaseDragLayer.LayoutParams).apply { customPosition = true }

                setup(widgetView, cellLayout, dragLayer)
                dragLayer.addView(this)
                mIsOpen = true
                dragLayer.post { onNewResizeRequest(resizeManager.getCurrentWidgetBounds()) }
            }

            return resizeFrame
        }
    }
}
