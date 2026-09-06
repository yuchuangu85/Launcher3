/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.views

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.View
import android.view.View.OnClickListener
import androidx.core.view.isVisible
import com.android.app.tracing.traceSection
import com.android.launcher3.Flags.enableRefactorDigitalWellbeingToast
import com.android.launcher3.model.data.TaskViewItemInfo
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.ViewUtils.addAccessibleChildToList
import com.android.quickstep.recents.domain.usecase.ThumbnailPosition
import com.android.quickstep.recents.ui.mapper.TaskUiStateMapper
import com.android.quickstep.recents.ui.viewmodel.TaskData
import com.android.quickstep.task.thumbnail.TaskContentView
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData

/** Holder for all Task dependent information. */
class TaskContainer(
    val taskView: TaskView,
    val task: Task,
    val taskContentView: TaskContentView,
    val snapshotView: TaskThumbnailView,
    val iconView: IconAppChipView,
    /**
     * This technically can be a vanilla [android.view.TouchDelegate] class, however that class
     * requires setting the touch bounds at construction, so we'd repeatedly be created many
     * instances unnecessarily as scrolling occurs, whereas [TransformingTouchDelegate] allows touch
     * delegated bounds only to be updated.
     */
    val iconTouchDelegate: TransformingTouchDelegate,
    /** Defaults to STAGE_POSITION_UNDEFINED if in not a split screen task view */
    @SplitConfigurationOptions.StagePosition val stagePosition: Int,
    val digitalWellBeingToast: DigitalWellBeingToast?,
    taskOverlayFactory: TaskOverlayFactory,
) {
    val overlay: TaskOverlayFactory.TaskOverlay<*> = taskOverlayFactory.createOverlay(this)
    var thumbnailPosition: ThumbnailPosition? = null
    private var overlayEnabledStatus = false

    internal var thumbnailData: ThumbnailData? = null
        private set

    val thumbnail: Bitmap?
        /** If possible don't use this. It should be replaced as part of b/331753115. */
        get() = thumbnailData?.thumbnail

    var isThumbnailValid: Boolean = false
        internal set

    val shouldShowSplashView: Boolean
        get() = taskView.shouldShowSplash()

    /** Builds proto for logging */
    val itemInfo: TaskViewItemInfo
        get() = TaskViewItemInfo(taskView, this)

    fun bind() =
        traceSection("TaskContainer.bind") {
            digitalWellBeingToast?.bind(task, taskView, snapshotView, stagePosition)
        }

    fun destroy() =
        traceSection("TaskContainer.destroy") {
            digitalWellBeingToast?.destroy()
            taskContentView.scaleX = 1f
            taskContentView.scaleY = 1f
            overlay.reset()
            isThumbnailValid = false
            thumbnailData = null
            snapshotView.onRecycle()
        }

    fun setOverlayEnabled(enabled: Boolean, thumbnailPosition: ThumbnailPosition) {
        if (overlayEnabledStatus != enabled || this.thumbnailPosition != thumbnailPosition) {
            overlayEnabledStatus = enabled

            refreshOverlay(thumbnailPosition)
        }
    }

    fun refreshOverlay(thumbnailPosition: ThumbnailPosition) =
        traceSection("TaskContainer.refreshOverlay") {
            this.thumbnailPosition = thumbnailPosition
            if (overlayEnabledStatus) {
                overlay.initOverlay(
                    task,
                    thumbnailData?.thumbnail,
                    thumbnailPosition.matrix,
                    thumbnailPosition.isRotated,
                )
            } else {
                overlay.reset()
            }
        }

    fun addChildForAccessibility(outChildren: ArrayList<View>) {
        addAccessibleChildToList(iconView, outChildren)
        addAccessibleChildToList(taskContentView, outChildren)
        digitalWellBeingToast?.let { addAccessibleChildToList(it, outChildren) }
        overlay.addChildForAccessibility(outChildren)
    }

    fun setState(
        state: TaskData?,
        hasHeader: Boolean,
        canShowAppTimer: Boolean,
        clickCloseListener: OnClickListener?,
    ) =
        traceSection("TaskContainer.setState") {
            taskContentView.setState(
                TaskUiStateMapper.toTaskHeaderState(state, hasHeader, clickCloseListener),
                TaskUiStateMapper.toTaskThumbnailUiState(state),
                TaskUiStateMapper.toTaskAppTimerUiState(canShowAppTimer, stagePosition, state),
                state?.taskId,
            )
            thumbnailData = if (state is TaskData.Data) state.thumbnailData else null
            overlay.setThumbnailState(thumbnailData)
        }

    fun updateTintAmount(tintAmount: Float) {
        snapshotView.updateTintAmount(tintAmount)
    }

    /**
     * Updates the progress of the menu opening animation.
     *
     * This function propagates the given `progress` value to the `thumbnailView` allowing the
     * thumbnail view to animate its visual state in sync with the menu's opening/closing
     * transition.
     *
     * @param progress The progress of the menu opening animation (from closed=0 to fully open=1)
     */
    fun updateMenuOpenProgress(progress: Float) {
        snapshotView.updateMenuOpenProgress(progress)
    }

    /**
     * Updates the thumbnail splash progress for a given task.
     *
     * This function manages the visual feedback of a "splash" effect that can be displayed over a
     * thumbnail image, typically during loading or updating. It calculates the alpha (transparency)
     * of the splash based on the provided progress and then applies this alpha to the thumbnail
     * view if it should be displayed.
     *
     * @param progress The progress of the operation, ranging from 0.0 to 1.0
     */
    fun updateThumbnailSplashProgress(progress: Float) {
        snapshotView.updateSplashAlpha(progress)
    }

    fun updateThumbnailMatrix(matrix: Matrix) {
        snapshotView.setImageMatrix(matrix)
    }

    fun onTaskViewDisplayConfigChanged() {
        taskContentView.onTaskViewDisplayConfigChanged(
            taskView.layoutParams.width,
            taskView.layoutParams.height,
            taskView is GroupedTaskView,
            (taskView as? GroupedTaskView)?.splitBoundsConfig,
            taskView.pagedOrientationHandler,
            stagePosition,
        )
    }

    fun digitalWellBeingBannerHeight(): Int {
        if (enableRefactorDigitalWellbeingToast()) {
            return taskContentView.getTaskAppTimerToastHeight() ?: 0
        }

        if (digitalWellBeingToast?.isVisible == true) {
            return digitalWellBeingToast.height
        }

        return 0
    }

    companion object {
        const val TAG = "TaskContainer"
    }
}
