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

import android.content.ClipData
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.util.Log
import android.view.DragEvent
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.views.ActivityContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * Listener for a single system-level drag-and-drop sequence.
 *
 * @param context The context associated with the sequence.
 * @param idp The invariant device profile used to generate drag images.
 * @param imageViewFactory The factory used to create image views.
 * @param params The parameters used for the sequence.
 */
class SystemDragListener
@AssistedInject
constructor(
    context: ActivityContext,
    private val idp: InvariantDeviceProfile,
    @Assisted private val imageViewFactory: (Context) -> ImageView,
    @Assisted private var params: SystemDragParams?,
) :
    BaseItemDragListener<ActivityContext>(
        /*previewRect=*/ Rect(),
        /*previewBitmapWidth=*/ 0,
        /*previewViewWidth*/ 0,
    ),
    DragController.DragSessionListener {

    private var cleanupCallback: Runnable? = null
    private var dragImage: ImageView? = null
    private var dragView: DragView? = null

    init {
        val closeAllOpenViews = params?.closeAllOpenViews ?: true
        val isStarted = context.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        initInternal(context, isStarted, closeAllOpenViews)
        context.dragController.addDragSessionListener(this)
    }

    /**
     * NOTE: This wildcard mime type will cause the listener to handle a system-level drag-and-drop
     * sequence for any {@link ClipData}, even if its payload will be rejected on drop. This is
     * necessary because {@link ClipData} is not exposed until {@link DragEvent.ACTION_DROP}, so
     * there is no app-agnostic way of determining payload acceptability during {@link
     * DragEvent.ACTION_DRAG_STARTED}.
     *
     * @see https://developer.android.com/reference/android/view/DragEvent
     */
    override fun getMimeType(): String = "*/*"

    /**
     * Sets a callback to be run when the listener is cleaned up.
     *
     * @param callback The callback to be run.
     */
    fun setCleanupCallback(callback: Runnable?) {
        cleanupCallback = callback
    }

    /**
     * Starts a system-level drag-and-drop sequence.
     *
     * @param screenPos The start position for the sequence.
     * @return The drag view for the sequence if started successfully.
     */
    fun startDrag(screenPos: Point): DragView? =
        params?.run {
            startDrag(
                /*previewRect=*/ Rect(),
                /*previewBitmapWidth=*/ 0,
                /*previewViewWidth=*/ 0,
                screenPos,
                dragOptions,
            )
            dragView
        }

    override fun onDrag(event: DragEvent): Boolean {
        with(event) {
            if (action == DragEvent.ACTION_DROP) {
                try {
                    (params?.dragInfo as? SystemDragItemInfo)?.apply {
                        payload =
                            SystemDragItemInfo.UriListPayload(
                                permissions = mContext.requestDragAndDropPermissions(event),
                                uriList =
                                    clipData?.let { clipData ->
                                        (0 until clipData.itemCount)
                                            .mapNotNull(clipData::getItemAt)
                                            .mapNotNull(ClipData.Item::getUri)
                                            .distinct()
                                    },
                            )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to obtain URI permissions", e)
                }
            }
            // NOTE: The system-provided drag image will be hidden so make the launcher-provided
            // drag image opaque. This allows the launcher to animate its own drag image back to its
            // final position.
            if (action == DragEvent.ACTION_DRAG_ENDED || action == DragEvent.ACTION_DROP) {
                dragImage?.alpha = 1.0f
            }
        }
        return super.onDrag(event)
    }

    override fun onDragSessionEnd() {
        mContext.dragController.removeDragSessionListener(this)
        postCleanup()
    }

    override fun startDrag(
        previewRect: Rect,
        previewBitmapWidth: Int,
        previewViewWidth: Int,
        screenPos: Point,
        options: DragOptions,
    ) {
        if (dragView != null) {
            return
        }

        mContext.dragController?.run {
            val params =
                this@SystemDragListener.params
                    ?: createDragImage()
                        .let { dragImage ->
                            SystemDragParams(
                                clipData = null,
                                extraDragFlags = 0,
                                closeAllOpenViews = true,
                                dragImage = dragImage,
                                dragInfo = SystemDragItemInfo(),
                                dragLayerX = screenPos.x - (dragImage.intrinsicWidth / 2),
                                dragLayerY = screenPos.y - (dragImage.intrinsicHeight / 2),
                                dragOptions = options,
                                dragRegion = Rect(),
                                dragSource = this@SystemDragListener,
                                dragViewScaleOnDrop = 1.0f,
                                draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON),
                                initialDragViewScale = 1.0f,
                            )
                        }
                        .also { params -> this@SystemDragListener.params = params }

            params.dragOptions.apply {
                isSystemDrag = true
                simulatedDndStartPoint = screenPos
            }

            // NOTE: The launcher-provided drag image is initially transparent so as not to clash
            // with the system-provided drag image, the latter being preferred since it paints at a
            // higher z-index than other application windows. The launcher-provided drag image will
            // be made opaque after the system-provided drag image is hidden on drag end. This
            // allows the launcher to animate its own drag image back to its final position.
            dragImage =
                imageViewFactory.invoke(mContext.asContext()).apply {
                    val h = params.dragImage.intrinsicHeight
                    val w = params.dragImage.intrinsicWidth
                    alpha = 0.0f
                    layoutParams = ViewGroup.LayoutParams(w, h)
                    setImageDrawable(params.dragImage)
                    measure(makeMeasureSpec(w, EXACTLY), makeMeasureSpec(h, EXACTLY))
                }

            dragView =
                startDrag(
                    dragImage,
                    params.draggableView,
                    params.dragLayerX,
                    params.dragLayerY,
                    params.dragSource,
                    params.dragInfo,
                    params.dragRegion,
                    params.initialDragViewScale,
                    params.dragViewScaleOnDrop,
                    params.dragOptions,
                )
        }
    }

    override fun postCleanup() {
        super.postCleanup()
        cleanupCallback?.run()
    }

    private fun createDragImage(): Drawable =
        object : DrawableWrapper(BitmapInfo.LOW_RES_INFO.newIcon(mContext.asContext())) {
            override fun getIntrinsicHeight(): Int = idp.iconBitmapSize

            override fun getIntrinsicWidth(): Int = idp.iconBitmapSize
        }

    companion object {
        private const val TAG = "SystemDragListener"
    }

    @AssistedFactory
    interface Factory {
        fun create(
            imageViewFactory: (Context) -> ImageView,
            params: SystemDragParams?,
        ): SystemDragListener
    }
}
