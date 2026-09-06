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

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags.enableCursorDrivenWorkflows
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.ArrowPopup
import com.android.launcher3.popup.RoundedArrowDrawable
import com.android.launcher3.touch.CustomTouchDelegate
import com.android.launcher3.util.ViewCache

/** A container view for overflown apps in the taskbar. */
class OverflownAppsContainerView<T : TaskbarActivityContext>
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ArrowPopup<T>(context, attrs, defStyleAttr),
    TaskbarViewDragDropController.PinnedAppsContainerDelegate {
    private lateinit var overflowIcon: TaskbarOverflowView
    private lateinit var viewCallbacks: TaskbarViewCallbacks
    private lateinit var content: LinearLayout
    private var overflownApps = emptyList<ItemInfo>()
    private val viewCache = ViewCache()

    private val spacing: Int =
        resources.getDimensionPixelSize(R.dimen.overflown_apps_container_spacing)

    private val iconViewSize =
        Utilities.dpToPx(
            mActivityContext.taskbarSpecsEvaluator.taskbarIconTouchSize,
            mActivityContext,
        )
    private val iconPadding =
        Utilities.dpToPx(
            mActivityContext.taskbarSpecsEvaluator.taskbarIconPadding,
            mActivityContext,
        )

    val overflownAppIcons: List<BubbleTextView>
        get() = content.children.filterIsInstance<BubbleTextView>().toList()

    fun init(icon: TaskbarOverflowView, callbacks: TaskbarViewCallbacks) {
        isFocusableInTouchMode = true
        overflowIcon = icon
        viewCallbacks = callbacks
        content = findViewById(R.id.overflown_content)
        content.clipChildren = false
        // Set the horizontal padding to the half of the expected spacing for the children to
        // complement the other half
        content.updatePadding(
            left = spacing / 2,
            top = spacing,
            right = spacing / 2,
            bottom = spacing,
        )
    }

    fun setOverflownApps(list: List<ItemInfo>) {
        for (iconView in content.children) {
            iconView.setOnClickListener(null)
            iconView.setOnLongClickListener(null)
            iconView.setOnHoverListener(null)
            iconView.tag = null

            viewCache.recycleView(iconView.sourceLayoutResId, iconView)
        }
        content.removeAllViews()

        overflownApps = list
        inflateApps()
    }

    private fun createIconForItem(item: ItemInfo): BubbleTextView? {
        if (item !is WorkspaceItemInfo) {
            return null
        }
        val icon = viewCache.getView<View>(R.layout.taskbar_app_icon, mActivityContext, content)

        if (icon !is BubbleTextView) {
            return null
        }
        icon.applyFromWorkspaceItem(item)
        icon.setContainerTextVisibility(false)
        icon.setPadding(iconPadding)

        icon.setOnClickListener(viewCallbacks.iconOnClickListener)
        icon.setOnLongClickListener(viewCallbacks.iconOnLongClickListener)
        icon.setOnHoverListener(TaskbarHoverToolTipController(mActivityContext, this, icon))
        if (enableCursorDrivenWorkflows()) {
            (icon as? CustomTouchDelegate)?.customActionsListener =
                viewCallbacks.iconCustomActionsListener
        }

        icon.layoutParams =
            LayoutParams(iconViewSize, iconViewSize).apply {
                marginStart = spacing / 2
                marginEnd = spacing / 2
            }

        return icon
    }

    private fun inflateApps() {
        for (item in overflownApps) {
            val icon = createIconForItem(item)
            if (icon != null) {
                content.addView(icon)
            }
        }
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_TASKBAR_OVERFLOW != 0

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        when (ev?.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!mActivityContext.dragLayer.isEventOverView(this, ev)) {
                    close(true)
                }
            }
        }
        return false
    }

    override fun getTargetObjectLocation(outPos: Rect?) {
        popupContainer.getDescendantRectRelativeToSelf(overflowIcon, outPos)
    }

    override fun orientAboutObject() {
        super.orientAboutObject()
        // Center the popup horizontally to the overflow icon.
        val centeredX = mTempRect.centerX() - measuredWidth / 2f
        val dragLayerWidth = popupContainer.width
        val insets = popupContainer.insets
        val minX = insets.left.toFloat()
        val maxX = (dragLayerWidth - insets.right - measuredWidth).toFloat()

        x = centeredX.coerceIn(minX, maxX)
    }

    override fun addArrow() {
        super.addArrow()
        alignArrow()
    }

    private fun alignArrow() {
        // Center the arrow to the overflow icon.
        val overflowIconCenterX = mTempRect.centerX().toFloat()
        mArrow.x = overflowIconCenterX - (mArrowWidth / 2f)
    }

    override fun updateArrowColor() {
        if (!Gravity.isVertical(mGravity)) {
            mArrow.background =
                RoundedArrowDrawable(
                    mArrowWidth.toFloat(),
                    mArrowHeight.toFloat(),
                    mArrowPointRadius.toFloat(),
                    mOutlineRadius,
                    measuredWidth.toFloat(),
                    measuredHeight.toFloat(),
                    ((measuredWidth - mArrowWidth) / 2).toFloat(), // arrowOffsetX
                    -mArrowOffsetVertical.toFloat(), // arrowOffsetY
                    !mIsAboveIcon, // isPointingUp
                    true, // leftAligned (doesn't matter for centered arrow)
                    mArrowColor,
                )
            elevation = mElevation
            mArrow.elevation = mElevation
        }
    }

    override fun setPivotForOpenCloseAnimation() {
        pivotX = mArrow.x + mArrowWidth / 2 - x
        pivotY = measuredHeight.toFloat()
    }

    override fun requestFocusOnOpened() = true

    override fun getHitRectForPinRelativeToDragLayer(outRect: Rect?) {
        mActivityContext.dragLayer.getDescendantRectRelativeToSelf(this, outRect)
    }

    override fun isPointOnOverflowIcon(point: FloatArray): Boolean = false

    override fun reserveDropSlotForDragLocation(x: Int) {
        dragDelegate.reserveDropSlotForDragLocation(x)
    }

    override fun updateForDroppedItem(item: ItemInfo): Boolean {
        return dragDelegate.updateForDroppedItem(item)
    }

    override fun releaseDropSlot() {
        dragDelegate.releaseDropSlot()
    }

    override fun removeDraggedView() {
        dragDelegate.removeDraggedView()
    }

    override fun getPinIndex(startingIndex: Int): Int {
        return dragDelegate.getPinIndex(startingIndex)
    }

    override fun updateItemViewVisibilityForDragState(itemView: View, isDragged: Boolean): Boolean {
        if (dragDelegate.updateItemViewVisibilityForDragState(itemView, isDragged)) {
            if (isDragged) {
                overflowIcon.onOverflowItemDragged(itemView.tag as ItemInfo)
            }
            return true
        }
        return false
    }

    private val dragDelegate by lazy {
        object : PinnedAppsDragHelper(context, content, iconViewSize) {
            override fun calculateGhostViewIndex(onScreenLocationX: Int): Int {
                val tempRect = Rect()
                mActivityContext.dragLayer.getDescendantRectRelativeToSelf(content, tempRect)
                val relativeX = onScreenLocationX - tempRect.left
                val itemWidth = iconSize + spacing

                val clampedX = relativeX.coerceIn(0, tempRect.width())

                val count = content.childCount
                val isGhostPresent = dropSpotIndex != -1
                val realCount = if (isGhostPresent) count - 1 else count
                val maxIndex = if (hasHiddenChild()) realCount - 1 else realCount

                return (clampedX / itemWidth).coerceAtMost(maxIndex)
            }

            override fun createGhostViewLayoutParams(iconSize: Int): ViewGroup.LayoutParams {
                return LayoutParams(iconSize, iconSize).apply {
                    marginStart = spacing / 2
                    marginEnd = spacing / 2
                }
            }

            override fun createViewForItem(item: ItemInfo): BubbleTextView? {
                return createIconForItem(item)
            }

            override fun onDragStateChanged() {
                orientAboutObject()
                alignArrow()
            }

            override fun getHitRectForPinRelativeToDragLayer(outRect: Rect?) {}

            override fun isPointOnOverflowIcon(point: FloatArray): Boolean = false
        }
    }
}
