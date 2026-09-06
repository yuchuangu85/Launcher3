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

package com.android.launcher3.dragndrop

import android.content.ClipDescription
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.DragEvent
import android.view.View
import android.view.View.DRAG_FLAG_DISABLE_DEFAULT_POINTER_ICON
import android.view.View.DRAG_FLAG_OPAQUE
import android.widget.ImageView
import com.android.launcher3.views.ActivityContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.roundToInt

/**
 * Production implementation of the controller for system-level drag-and-drop. Injected when {@link
 * com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG} is enabled.
 *
 * @param context The context for which to handle system-level drag-and-drop.
 * @param systemDragListenerFactory The factory used to create listeners for system-level
 *   drag-and-drop. A unique listener instance is created per handled drag-and-drop sequence.
 * @param isHomeScreenFilesFeatureEnabled Whether the home screen files feature is enabled.
 */
class SystemDragControllerImpl
@AssistedInject
constructor(
    private val context: ActivityContext,
    private val systemDragListenerFactory: SystemDragListener.Factory,
    @Assisted private val isHomeScreenFilesFeatureEnabled: Boolean,
) : SystemDragController() {

    private var systemDragListener: SystemDragListener? = null

    override fun onDrag(event: DragEvent): Boolean =
        continueDrag(event) ?: (acceptDrag(event) && startDrag(event))

    override fun startDrag(params: SystemDragParams): DragView? {
        if (params.dragOptions.isAccessibleDrag || params.dragOptions.isKeyboardDrag) {
            Log.i(TAG, "System drag not supported for accessible/keyboard drags")
            return null
        }
        val dragController = context.dragController ?: return null
        val screenPos = params.dragOptions.simulatedDndStartPoint ?: dragController.downPoint
        return createSystemDragListener(params).startDrag(screenPos)?.also { dragView ->
            if (!startSystemDrag(dragView, params)) {
                Log.e(TAG, "System drag failed to start")
                dragController.cancelDrag()
            }
        }
    }

    private fun acceptDrag(event: DragEvent): Boolean {
        // NOTE: We currently only support files dragged from other apps. If the home
        // screen files feature is disabled, we won't be able to handle the drag payload
        // so we can safely ignore the event.
        if (!isHomeScreenFilesFeatureEnabled) {
            return false
        }
        // NOTE: This is an imperfect proxy to restrict drags from other apps to only those
        // originating from DocsUI. This does NOT establish trust and nothing breaks if this proxy
        // fails or is spoofed by another app; it exists solely to polish the user experience if we
        // know we likely can't handle the drag payload on drop.
        // TODO(b/468079600): Remove this check once file copy operations are supported.
        if (
            event.clipDescription?.extras?.keySet()?.any { it.startsWith(DOCS_UI_EXTRA_PREFIX) } !=
                true
        ) {
            return false
        }
        // NOTE: Drops for these mime types are not currently supported so ignore related drag
        // events to avoid giving the user the impression that they are.
        return !(event.clipDescription?.hasMimeType(
            arrayOf(
                ClipDescription.MIMETYPE_APPLICATION_ACTIVITY,
                ClipDescription.MIMETYPE_APPLICATION_SHORTCUT,
                ClipDescription.MIMETYPE_APPLICATION_TASK,
                ClipDescription.MIMETYPE_TEXT_INTENT,
            )
        ) ?: false)
    }

    private fun continueDrag(event: DragEvent): Boolean? = systemDragListener?.onDrag(event)

    private fun createSystemDragListener(params: SystemDragParams? = null): SystemDragListener =
        systemDragListenerFactory.create(::ImageView, params).also { listener ->
            systemDragListener = listener
            listener.setCleanupCallback {
                if (systemDragListener == listener) {
                    systemDragListener = null
                }
            }
        }

    private fun startDrag(event: DragEvent): Boolean =
        context.dragController?.isDragging == false &&
            event.action == DragEvent.ACTION_DRAG_STARTED &&
            createSystemDragListener().onDrag(event)

    private fun startSystemDrag(dragView: DragView, params: SystemDragParams): Boolean =
        context.dragLayer?.let { dragLayer ->
            val dragShadow =
                object : View.DragShadowBuilder() {
                    val h = (params.dragImage.intrinsicHeight * params.initialDragViewScale).toInt()
                    val w = (params.dragImage.intrinsicWidth * params.initialDragViewScale).toInt()

                    val touch =
                        Point(dragView.registrationX, dragView.registrationY).apply {
                            val offsetX = (w - params.dragImage.intrinsicWidth) / 2.0f
                            val offsetY = (h - params.dragImage.intrinsicHeight) / 2.0f
                            offset(offsetX.roundToInt(), offsetY.roundToInt())
                        }

                    override fun onDrawShadow(canvas: Canvas) {
                        val oldAlpha = params.dragImage.alpha
                        val oldBounds = params.dragImage.copyBounds()
                        params.dragImage.alpha = (dragView.alpha * 0xFF).toInt()
                        params.dragImage.bounds = Rect(0, 0, w, h)
                        params.dragImage.draw(canvas)
                        params.dragImage.alpha = oldAlpha
                        params.dragImage.bounds = oldBounds
                    }

                    override fun onProvideShadowMetrics(
                        outShadowSize: Point,
                        outShadowTouchPoint: Point,
                    ) {
                        outShadowSize.set(w, h)
                        outShadowTouchPoint.set(touch.x, touch.y)
                    }
                }

            dragLayer
                .startDragAndDrop(
                    params.clipData,
                    dragShadow,
                    /*localState=*/ null,
                    /*flags=*/ DRAG_FLAG_DISABLE_DEFAULT_POINTER_ICON or
                        DRAG_FLAG_OPAQUE or
                        params.extraDragFlags,
                )
                .also { result ->
                    if (result) {
                        // Synchronize system-level drag shadow opacity with that of the launcher's
                        // internal drag view. The launcher reduces the internal drag view's opacity
                        // when dragging over button drop targets.
                        dragView.addOnAlphaChangeListener { dragLayer.updateDragShadow(dragShadow) }
                    }
                }
        } == true

    companion object {
        private const val TAG = "SystemDragControllerImpl"
    }

    @AssistedFactory
    interface Factory {
        fun create(isHomeScreenFilesFeatureEnabled: Boolean): SystemDragControllerImpl
    }
}
