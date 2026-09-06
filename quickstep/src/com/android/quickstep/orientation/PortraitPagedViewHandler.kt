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
package com.android.quickstep.orientation

import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ShapeDrawable
import android.util.FloatProperty
import android.util.Pair
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.View.LAYOUT_DIRECTION_INHERIT
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
import androidx.core.view.updateLayoutParams
import com.android.launcher3.DeviceProfile
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.touch.DefaultPagedViewHandler
import com.android.launcher3.touch.PagedOrientationHandler.Float2DAction
import com.android.launcher3.touch.PagedOrientationHandler.Int2DAction
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.quickstep.views.IconAppChipView
import com.android.wm.shell.shared.split.SplitBounds
import kotlin.math.max
import kotlin.math.min

class PortraitPagedViewHandler : DefaultPagedViewHandler(), RecentsPagedOrientationHandler {
    private val tmpMatrix = Matrix()
    private val tmpRectF = RectF()

    override fun <T> getPrimaryValue(x: T, y: T): T = x

    override fun <T> getSecondaryValue(x: T, y: T): T = y

    override val isLayoutNaturalToLauncher: Boolean = true

    override fun adjustFloatingIconStartVelocity(velocity: PointF) {
        // no-op
    }

    override fun fixBoundsForHomeAnimStartRect(outStartRect: RectF, deviceProfile: DeviceProfile) {
        if (outStartRect.left > deviceProfile.deviceProperties.widthPx) {
            outStartRect.offsetTo(0f, outStartRect.top)
        } else if (outStartRect.left < -deviceProfile.deviceProperties.widthPx) {
            outStartRect.offsetTo(0f, outStartRect.top)
        }
    }

    override fun <T> setSecondary(target: T, action: Float2DAction<T>, param: Float) =
        action.call(target, 0f, param)

    override fun <T> set(
        target: T,
        action: Int2DAction<T>,
        primaryParam: Int,
        secondaryParam: Int,
    ) = action.call(target, primaryParam, secondaryParam)

    override fun getPrimarySize(view: View): Int = view.width

    override fun getPrimarySize(rect: RectF): Float = rect.width()

    override fun getSecondarySize(rect: RectF): Float = rect.height()

    override fun getStart(rect: RectF): Float = rect.left

    override fun getEnd(rect: RectF): Float = rect.right

    override fun rotateInsets(insets: Rect, outInsets: Rect) = outInsets.set(insets)

    override fun getClearAllSidePadding(view: View, isRtl: Boolean): Int =
        (if (isRtl) view.paddingRight else -view.paddingLeft) / 2

    override fun getSecondaryDimension(view: View): Int = view.height

    override val primaryViewTranslate: FloatProperty<View> = LauncherAnimUtils.VIEW_TRANSLATE_X

    override val secondaryViewTranslate: FloatProperty<View> = LauncherAnimUtils.VIEW_TRANSLATE_Y

    override val degreesRotated: Float = 0f

    override val rotation: Int = Surface.ROTATION_0

    override fun setPrimaryScale(view: View, scale: Float) {
        view.scaleX = scale
    }

    override fun setSecondaryScale(view: View, scale: Float) {
        view.scaleY = scale
    }

    override val secondaryTranslationDirectionFactor: Int
        get() = -1

    override fun getSplitTranslationDirectionFactor(
        stagePosition: Int,
        deviceProfile: DeviceProfile,
    ): Int =
        if (
            deviceProfile.sysuiProfile.isLeftRightSplit &&
                stagePosition == SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
        ) {
            -1
        } else {
            1
        }

    override fun getTaskMenuX(x: Float, appChip: IconAppChipView): Float = x

    override fun getTaskMenuY(y: Float, taskMenuView: View, appChip: IconAppChipView): Float =
        y - appChip.menuToCollapsedChipGap

    override fun getAppChipMenuMarginX(appChipView: IconAppChipView, isRtl: Boolean): Int =
        if (isRtl) -appChipView.backgroundMarginTopStart else appChipView.backgroundMarginTopStart

    override fun getAppChipMenuMarginY(appChipView: IconAppChipView, isRtl: Boolean): Int =
        appChipView.menuToCollapsedChipGap

    override fun getTaskMenuHeight(
        taskInsetMargin: Float,
        deviceProfile: DeviceProfile,
        taskMenuX: Float,
        taskMenuY: Float,
    ): Int =
        deviceProfile.deviceProperties.heightPx -
            deviceProfile.insets.top -
            taskMenuY.toInt() -
            deviceProfile.overviewActionsClaimedSpaceBelow

    override fun setTaskOptionsMenuLayoutOrientation(
        deviceProfile: DeviceProfile,
        taskMenuLayout: LinearLayout,
        dividerSpacing: Int,
        dividerDrawable: ShapeDrawable,
    ) {
        taskMenuLayout.orientation = LinearLayout.VERTICAL
        dividerDrawable.intrinsicHeight = dividerSpacing
        taskMenuLayout.dividerDrawable = dividerDrawable
    }

    override fun setLayoutParamsForTaskMenuOptionItem(
        lp: LinearLayout.LayoutParams,
        viewGroup: LinearLayout,
        deviceProfile: DeviceProfile,
    ) {
        viewGroup.orientation = LinearLayout.HORIZONTAL
        lp.width = LinearLayout.LayoutParams.MATCH_PARENT
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    override fun updateDwbBannerLayout(
        taskViewWidth: Int,
        taskViewHeight: Int,
        isGroupedTaskView: Boolean,
        deviceProfile: DeviceProfile,
        snapshotViewWidth: Int,
        snapshotViewHeight: Int,
        banner: View,
    ) {
        banner.pivotX = 0f
        banner.pivotY = 0f
        banner.rotation = degreesRotated
        banner.updateLayoutParams<FrameLayout.LayoutParams> {
            if (isGroupedTaskView) {
                gravity =
                    Gravity.BOTTOM or
                        (if (deviceProfile.sysuiProfile.isLeftRightSplit) Gravity.START
                        else Gravity.CENTER_HORIZONTAL)
                width = snapshotViewWidth
            } else {
                width = MATCH_PARENT
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
        }
    }

    override fun updateAppTimerLayout(
        taskViewWidth: Int,
        taskViewHeight: Int,
        isGroupedTaskView: Boolean,
        deviceProfile: DeviceProfile,
        snapshotViewWidth: Int,
        snapshotViewHeight: Int,
        appTimerToast: View,
    ) {
        appTimerToast.updateLayoutParams<ConstraintLayout.LayoutParams> {
            appTimerToast.layoutDirection = LAYOUT_DIRECTION_INHERIT
            startToStart = PARENT_ID
            endToEnd = PARENT_ID
            bottomToBottom = PARENT_ID
            topToTop = UNSET
            width =
                if (isGroupedTaskView) {
                    snapshotViewWidth
                } else {
                    MATCH_PARENT
                }
        }

        appTimerToast.pivotX = 0f
        appTimerToast.pivotY = 0f
        appTimerToast.rotation = degreesRotated

        appTimerToast.translationX = 0f
        appTimerToast.translationY = 0f
    }

    override fun getDwbBannerTranslations(
        taskViewWidth: Int,
        taskViewHeight: Int,
        splitBounds: SplitBounds?,
        deviceProfile: DeviceProfile,
        desiredTaskId: Int,
        banner: View,
    ): Pair<Float, Float> {
        if (splitBounds == null) return Pair(0f, 0f)

        val translationX =
            if (
                deviceProfile.sysuiProfile.isLeftRightSplit &&
                    desiredTaskId == splitBounds.rightBottomTaskId
            ) {
                taskViewWidth * (splitBounds.leftTopTaskPercent + splitBounds.dividerPercent)
            } else 0f
        val translationY =
            if (
                !deviceProfile.sysuiProfile.isLeftRightSplit &&
                    desiredTaskId == splitBounds.leftTopTaskId
            ) {
                -taskViewHeight * (splitBounds.rightBottomTaskPercent + splitBounds.dividerPercent)
            } else 0f
        return Pair(translationX, translationY)
    }

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */

    override val upDownSwipeDirection: SingleAxisSwipeDetector.Direction =
        SingleAxisSwipeDetector.VERTICAL

    // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
    override fun getUpDirection(isRtl: Boolean): Int = SingleAxisSwipeDetector.DIRECTION_POSITIVE

    // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
    override fun getDownDirection(isRtl: Boolean): Int = SingleAxisSwipeDetector.DIRECTION_NEGATIVE

    // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
    override fun isGoingUp(displacement: Float, isRtl: Boolean): Boolean = displacement < 0

    // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
    override fun getTaskDragDisplacementFactor(isRtl: Boolean): Int = 1

    override fun getTaskDismissVerticalDirection(): Int = -1

    override fun getTaskDismissLength(secondaryDimension: Int, taskThumbnailBounds: Rect): Int =
        taskThumbnailBounds.bottom

    override fun getTaskLaunchLength(secondaryDimension: Int, taskThumbnailBounds: Rect): Int =
        secondaryDimension - taskThumbnailBounds.bottom

    override fun extendRectForPrimaryTranslation(rect: Rect, translation: Int) {
        if (translation < 0) {
            rect.left += translation
        } else {
            rect.right += translation
        }
    }

    override fun extendRectForSecondaryTranslation(rect: Rect, translation: Int) {
        if (translation < 0) {
            rect.top += translation
        } else {
            rect.bottom += translation
        }
    }

    /* -------------------- */

    override fun getDistanceToBottomOfRect(dp: DeviceProfile, rect: Rect): Int =
        dp.deviceProperties.heightPx - rect.bottom

    override fun getSplitPositionOptions(dp: DeviceProfile): List<SplitPositionOption> =
        when {
            dp.deviceProperties.isLargeScreen -> {
                Utilities.getSplitPositionOptions(dp)
            }

            dp.isSeascape -> {
                listOf(
                    SplitPositionOption(
                        R.drawable.ic_split_horizontal,
                        R.string.recent_task_option_split_screen,
                        SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT,
                        SplitConfigurationOptions.STAGE_TYPE_MAIN,
                    )
                )
            }

            dp.sysuiProfile.isLeftRightSplit -> {
                listOf(
                    SplitPositionOption(
                        R.drawable.ic_split_horizontal,
                        R.string.recent_task_option_split_screen,
                        SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT,
                        SplitConfigurationOptions.STAGE_TYPE_MAIN,
                    )
                )
            }

            else -> {
                // Only add top option
                listOf(
                    SplitPositionOption(
                        R.drawable.ic_split_vertical,
                        R.string.recent_task_option_split_screen,
                        SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT,
                        SplitConfigurationOptions.STAGE_TYPE_MAIN,
                    )
                )
            }
        }

    override fun getInitialSplitPlaceholderBounds(
        placeholderHeight: Int,
        placeholderInset: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
        out: Rect,
    ) {
        val screenWidth = dp.deviceProperties.widthPx
        val screenHeight = dp.deviceProperties.heightPx
        val pinToRight = stagePosition == SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
        val insetSizeAdjustment = getPlaceholderSizeAdjustment(dp, pinToRight)

        out.set(0, 0, screenWidth, placeholderHeight + insetSizeAdjustment)
        if (!dp.sysuiProfile.isLeftRightSplit) {
            // portrait, phone or tablet - spans width of screen, nothing else to do
            out.inset(placeholderInset, 0)

            // Adjust the top to account for content off screen. This will help to animate the view
            // in with rounded corners.
            val totalHeight =
                (1.0f * screenHeight / 2 * (screenWidth - 2 * placeholderInset) / screenWidth)
                    .toInt()
            out.top -= (totalHeight - placeholderHeight)
            return
        }

        // Now we rotate the portrait rect depending on what side we want pinned
        val postRotateScale = screenHeight.toFloat() / screenWidth
        tmpMatrix.reset()
        tmpMatrix.postRotate(if (pinToRight) 90f else 270f)
        tmpMatrix.postTranslate(
            (if (pinToRight) screenWidth else 0).toFloat(),
            (if (pinToRight) 0 else screenWidth).toFloat(),
        )
        // The placeholder height stays constant after rotation, so we don't change width scale
        tmpMatrix.postScale(1f, postRotateScale)

        tmpRectF.set(out)
        tmpMatrix.mapRect(tmpRectF)
        tmpRectF.inset(0f, placeholderInset.toFloat())
        tmpRectF.roundOut(out)

        // Adjust the top to account for content off screen. This will help to animate the view in
        // with rounded corners.
        val totalWidth =
            (1.0f * screenWidth / 2 * (screenHeight - 2 * placeholderInset) / screenHeight).toInt()
        val width = out.width()
        if (pinToRight) {
            out.right += totalWidth - width
        } else {
            out.left -= totalWidth - width
        }
    }

    override fun updateSplitIconParams(
        out: View,
        onScreenRectCenterX: Float,
        onScreenRectCenterY: Float,
        fullscreenScaleX: Float,
        fullscreenScaleY: Float,
        drawableWidth: Int,
        drawableHeight: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
    ) {
        val pinToRight = stagePosition == SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
        val insetAdjustment = getPlaceholderSizeAdjustment(dp, pinToRight) / 2f
        if (!dp.sysuiProfile.isLeftRightSplit) {
            out.x = (onScreenRectCenterX / fullscreenScaleX - 1.0f * drawableWidth / 2)
            out.y =
                ((onScreenRectCenterY + insetAdjustment) / fullscreenScaleY -
                    1.0f * drawableHeight / 2)
        } else {
            if (pinToRight) {
                out.x =
                    ((onScreenRectCenterX - insetAdjustment) / fullscreenScaleX -
                        1.0f * drawableWidth / 2)
            } else {
                out.x =
                    ((onScreenRectCenterX + insetAdjustment) / fullscreenScaleX -
                        1.0f * drawableWidth / 2)
            }
            out.y = (onScreenRectCenterY / fullscreenScaleY - 1.0f * drawableHeight / 2)
        }
    }

    /**
     * The split placeholder comes with a default inset to buffer the icon from the top of the
     * screen. But if the device already has a large inset (from cutouts etc), use that instead.
     */
    private fun getPlaceholderSizeAdjustment(dp: DeviceProfile, pinToRight: Boolean): Int {
        val insetThickness =
            if (!dp.deviceProperties.isLandscape) {
                dp.insets.top
            } else {
                if (pinToRight) dp.insets.right else dp.insets.left
            }
        return max((insetThickness - dp.sysuiProfile.splitPlaceholderInset).toDouble(), 0.0).toInt()
    }

    override fun setSplitInstructionsParams(
        out: View,
        dp: DeviceProfile,
        splitInstructionsHeight: Int,
        splitInstructionsWidth: Int,
    ) {
        out.pivotX = 0f
        out.pivotY = splitInstructionsHeight.toFloat()
        out.rotation = degreesRotated
        val distanceToEdge =
            if (dp.deviceProperties.isPhone) {
                if (dp.deviceProperties.isLandscape) {
                    out.resources.getDimensionPixelSize(
                        R.dimen.split_instructions_bottom_margin_phone_landscape
                    )
                } else {
                    out.resources.getDimensionPixelSize(
                        R.dimen.split_instructions_bottom_margin_phone_portrait
                    )
                }
            } else {
                dp.overviewActionsClaimedSpaceBelow
            }

        // Center the view in case of unbalanced insets on left or right of screen
        val insetCorrectionX = (dp.insets.right - dp.insets.left) / 2
        // Adjust for any insets on the bottom edge
        val insetCorrectionY = dp.insets.bottom
        out.translationX = insetCorrectionX.toFloat()
        out.translationY = (-distanceToEdge + insetCorrectionY).toFloat()
        val lp = out.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        out.layoutParams = lp
    }

    override fun getFinalSplitPlaceholderBounds(
        splitDividerSize: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
        out1: Rect,
        out2: Rect,
    ) {
        val screenHeight = dp.deviceProperties.heightPx
        val screenWidth = dp.deviceProperties.widthPx
        out1.set(0, 0, screenWidth, screenHeight / 2 - splitDividerSize)
        out2.set(0, screenHeight / 2 + splitDividerSize, screenWidth, screenHeight)
        if (!dp.sysuiProfile.isLeftRightSplit) {
            // Portrait - the window bounds are always top and bottom half
            return
        }

        // Now we rotate the portrait rect depending on what side we want pinned
        val pinToRight = stagePosition == SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
        val postRotateScale = screenHeight.toFloat() / screenWidth

        tmpMatrix.reset()
        tmpMatrix.postRotate(if (pinToRight) 90f else 270f)
        tmpMatrix.postTranslate(
            (if (pinToRight) screenHeight else 0).toFloat(),
            (if (pinToRight) 0 else screenWidth).toFloat(),
        )
        tmpMatrix.postScale(1 / postRotateScale, postRotateScale)

        tmpRectF.set(out1)
        tmpMatrix.mapRect(tmpRectF)
        tmpRectF.roundOut(out1)

        tmpRectF.set(out2)
        tmpMatrix.mapRect(tmpRectF)
        tmpRectF.roundOut(out2)
    }

    override fun setSplitTaskSwipeRect(
        dp: DeviceProfile,
        outRect: Rect,
        splitInfo: SplitBounds,
        desiredStagePosition: Int,
    ) {
        val topLeftTaskPercent = splitInfo.leftTopTaskPercent
        val dividerBarPercent = splitInfo.dividerPercent

        val taskbarHeight =
            if (dp.taskbarProfile.isTransientTaskbar) 0 else dp.taskbarProfile.height
        val scale =
            outRect.height().toFloat() / (dp.deviceProperties.availableHeightPx - taskbarHeight)
        val topTaskHeight = dp.deviceProperties.availableHeightPx * topLeftTaskPercent
        val scaledTopTaskHeight = topTaskHeight * scale
        val dividerHeight = dp.deviceProperties.availableHeightPx * dividerBarPercent
        val scaledDividerHeight = dividerHeight * scale

        if (desiredStagePosition == SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT) {
            if (dp.sysuiProfile.isLeftRightSplit) {
                outRect.right = outRect.left + Math.round(outRect.width() * topLeftTaskPercent)
            } else {
                outRect.bottom = Math.round(outRect.top + scaledTopTaskHeight)
            }
        } else {
            if (dp.sysuiProfile.isLeftRightSplit) {
                outRect.left +=
                    Math.round(outRect.width() * (topLeftTaskPercent + dividerBarPercent))
            } else {
                outRect.top += Math.round(scaledTopTaskHeight + scaledDividerHeight)
            }
        }
    }

    /**
     * @param inSplitSelection Whether user currently has a task from this task group staged for
     *   split screen. If true, we have custom translations/scaling in place for the remaining
     *   snapshot, so we'll skip setting translation/scale here.
     */
    override fun measureGroupedTaskViewThumbnailBounds(
        primarySnapshot: View,
        secondarySnapshot: View,
        parentWidth: Int,
        parentHeight: Int,
        splitBoundsConfig: SplitBounds,
        dp: DeviceProfile,
        isRtl: Boolean,
        inSplitSelection: Boolean,
    ) {
        val dividerScale = splitBoundsConfig.dividerPercent
        val taskViewSizes =
            getGroupedTaskViewSizes(dp, splitBoundsConfig, parentWidth, parentHeight)
        if (!inSplitSelection) {
            // Reset translations that aren't used in this method, but are used in other
            // `RecentsPagedOrientationHandler` variants.
            primarySnapshot.translationY = 0f

            if (dp.sysuiProfile.isLeftRightSplit) {
                val scaledDividerBar = Math.round(parentWidth * dividerScale)
                if (isRtl) {
                    val translationX = taskViewSizes.second.x + scaledDividerBar
                    primarySnapshot.translationX = -translationX.toFloat()
                    secondarySnapshot.translationX = 0f
                } else {
                    val translationX = taskViewSizes.first.x + scaledDividerBar
                    secondarySnapshot.translationX = translationX.toFloat()
                    primarySnapshot.translationX = 0f
                }
                secondarySnapshot.translationY = 0f
            } else {
                val finalDividerHeight = Math.round(parentHeight * dividerScale).toFloat()
                val translationY = taskViewSizes.first.y + finalDividerHeight
                secondarySnapshot.translationY = translationY

                // Reset unused translations.
                secondarySnapshot.translationX = 0f
                primarySnapshot.translationX = 0f
            }
        }

        primarySnapshot.measure(
            View.MeasureSpec.makeMeasureSpec(taskViewSizes.first.x, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(taskViewSizes.first.y, View.MeasureSpec.EXACTLY),
        )
        secondarySnapshot.measure(
            View.MeasureSpec.makeMeasureSpec(taskViewSizes.second.x, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(taskViewSizes.second.y, View.MeasureSpec.EXACTLY),
        )
    }

    override fun getGroupedTaskViewSizes(
        dp: DeviceProfile,
        splitBoundsConfig: SplitBounds,
        parentWidth: Int,
        parentHeight: Int,
    ): Pair<Point, Point> {
        val dividerScale = splitBoundsConfig.dividerPercent
        val taskPercent = splitBoundsConfig.leftTopTaskPercent

        val firstTaskViewSize = Point()
        val secondTaskViewSize = Point()

        if (dp.sysuiProfile.isLeftRightSplit) {
            val scaledDividerBar = Math.round(parentWidth * dividerScale)
            firstTaskViewSize.x = Math.round(parentWidth * taskPercent)
            firstTaskViewSize.y = parentHeight

            secondTaskViewSize.x = parentWidth - firstTaskViewSize.x - scaledDividerBar
            secondTaskViewSize.y = parentHeight
        } else {
            val taskbarHeight =
                if (dp.taskbarProfile.isTransientTaskbar) 0 else dp.taskbarProfile.height
            val scale =
                parentHeight.toFloat() / (dp.deviceProperties.availableHeightPx - taskbarHeight)
            val topTaskHeight = dp.deviceProperties.availableHeightPx * taskPercent
            val finalDividerHeight = Math.round(parentHeight * dividerScale).toFloat()
            val scaledTopTaskHeight = topTaskHeight * scale
            firstTaskViewSize.x = parentWidth
            firstTaskViewSize.y = Math.round(scaledTopTaskHeight)

            secondTaskViewSize.x = parentWidth
            secondTaskViewSize.y =
                Math.round((parentHeight - firstTaskViewSize.y - finalDividerHeight))
        }

        return Pair(firstTaskViewSize, secondTaskViewSize)
    }

    override fun setTaskIconParams(
        iconParams: FrameLayout.LayoutParams,
        taskIconMargin: Int,
        taskIconHeight: Int,
        isRtl: Boolean,
    ) {
        iconParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        // Reset margins, since they may have been set on rotation
        iconParams.rightMargin = 0
        iconParams.leftMargin = iconParams.rightMargin
        iconParams.bottomMargin = 0
        iconParams.topMargin = iconParams.bottomMargin
    }

    override fun setIconAppChipChildrenParams(
        iconParams: FrameLayout.LayoutParams,
        chipChildMarginStart: Int,
    ) {
        iconParams.marginStart = chipChildMarginStart
        iconParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        iconParams.topMargin = 0
    }

    override fun setIconAppChipMenuParams(
        iconAppChipView: IconAppChipView,
        iconMenuParams: FrameLayout.LayoutParams,
        iconMenuMargin: Int,
        thumbnailTopMargin: Int,
    ) {
        iconMenuParams.gravity = Gravity.TOP or Gravity.START
        iconMenuParams.marginStart = iconMenuMargin
        iconMenuParams.topMargin = thumbnailTopMargin
        iconMenuParams.bottomMargin = 0
        iconMenuParams.marginEnd = 0

        iconAppChipView.pivotX = 0f
        iconAppChipView.pivotY = 0f
        iconAppChipView.setSplitTranslationY(0f)
        iconAppChipView.rotation = degreesRotated
    }

    /**
     * @param inSplitSelection Whether user currently has a task from this task group staged for
     *   split screen. If true, we have custom translations in place for the remaining icon, so
     *   we'll skip setting translations here.
     */
    override fun setSplitIconParams(
        primaryAppChipView: IconAppChipView,
        secondaryAppChipView: IconAppChipView,
        primarySnapshotWidth: Int,
        primarySnapshotHeight: Int,
        groupedTaskViewHeight: Int,
        groupedTaskViewWidth: Int,
        isRtl: Boolean,
        deviceProfile: DeviceProfile,
        splitConfig: SplitBounds,
        inSplitSelection: Boolean,
    ) {
        val primaryIconParams = primaryAppChipView.layoutParams as FrameLayout.LayoutParams
        val secondaryIconParams = secondaryAppChipView.layoutParams as FrameLayout.LayoutParams

        primaryIconParams.gravity = Gravity.TOP or Gravity.START
        secondaryIconParams.gravity = Gravity.TOP or Gravity.START
        secondaryIconParams.topMargin = primaryIconParams.topMargin
        secondaryIconParams.marginStart = primaryIconParams.marginStart
        if (!inSplitSelection) {
            if (deviceProfile.sysuiProfile.isLeftRightSplit) {
                if (isRtl) {
                    val secondarySnapshotWidth = groupedTaskViewWidth - primarySnapshotWidth
                    primaryAppChipView.setSplitTranslationX(-secondarySnapshotWidth.toFloat())
                } else {
                    val dividerSize = Math.round(groupedTaskViewWidth * splitConfig.dividerPercent)
                    secondaryAppChipView.setSplitTranslationX(
                        primarySnapshotWidth.toFloat() + dividerSize
                    )
                }
            } else {
                primaryAppChipView.setSplitTranslationX(0f)
                secondaryAppChipView.setSplitTranslationX(0f)
                val dividerThickness =
                    min(
                            splitConfig.visualDividerBounds.width().toDouble(),
                            splitConfig.visualDividerBounds.height().toDouble(),
                        )
                        .toInt()
                secondaryAppChipView.setSplitTranslationY(
                    (primarySnapshotHeight +
                            (if (deviceProfile.deviceProperties.isLargeScreen) 0
                            else dividerThickness))
                        .toFloat()
                )
            }
        }

        primaryAppChipView.layoutParams = primaryIconParams
        secondaryAppChipView.layoutParams = secondaryIconParams
    }

    override fun getDefaultSplitPosition(deviceProfile: DeviceProfile): Int {
        check(deviceProfile.deviceProperties.isLargeScreen) {
            "Default position available only for large screens"
        }
        return if (deviceProfile.sysuiProfile.isLeftRightSplit) {
            SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
        } else {
            SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
        }
    }

    override fun <T> getSplitSelectTaskOffset(
        primary: FloatProperty<T>,
        secondary: FloatProperty<T>,
        deviceProfile: DeviceProfile,
    ): Pair<FloatProperty<T>, FloatProperty<T>> =
        if (deviceProfile.sysuiProfile.isLeftRightSplit) { // or seascape
            Pair(primary, secondary)
        } else {
            Pair(secondary, primary)
        }

    override fun getFloatingTaskOffscreenTranslationTarget(
        floatingTask: View,
        onScreenRect: RectF,
        @StagePosition stagePosition: Int,
        dp: DeviceProfile,
    ): Float {
        if (dp.sysuiProfile.isLeftRightSplit) {
            val currentTranslationX = floatingTask.translationX
            return if (stagePosition == SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT)
                currentTranslationX - onScreenRect.width()
            else currentTranslationX + onScreenRect.width()
        } else {
            val currentTranslationY = floatingTask.translationY
            return currentTranslationY - onScreenRect.height()
        }
    }

    override fun setFloatingTaskPrimaryTranslation(
        floatingTask: View,
        translation: Float,
        dp: DeviceProfile,
    ) {
        if (dp.sysuiProfile.isLeftRightSplit) {
            floatingTask.translationX = translation
        } else {
            floatingTask.translationY = translation
        }
    }

    override fun getFloatingTaskPrimaryTranslation(floatingTask: View, dp: DeviceProfile): Float =
        if (dp.sysuiProfile.isLeftRightSplit) floatingTask.translationX
        else floatingTask.translationY

    override fun getHandlerTypeForLogging(): LauncherAtom.TaskSwitcherContainer.OrientationHandler =
        LauncherAtom.TaskSwitcherContainer.OrientationHandler.PORTRAIT
}
