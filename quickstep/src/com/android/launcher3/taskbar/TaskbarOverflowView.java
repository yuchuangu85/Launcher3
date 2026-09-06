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

package com.android.launcher3.taskbar;

import static com.android.launcher3.Flags.enableLauncherIconShapes;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.icons.IconShape;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;
import com.android.quickstep.util.SingleTask;

import java.util.ArrayList;
import java.util.List;

/**
 * View used as an overflow icon within the taskbar.
 * This view appears when the list of apps (either recent/running tasks or pinned apps)
 * exceeds the available display space on the taskbar. If the display is not wide enough
 * to show all relevant apps, this icon is added to the taskbar. Tapping this icon
 * typically opens a UI that surfaces all the apps in the overflowed category.
 * The icon visually represents some of the items in the overflow, often by stacking icon
 * representations of up to a few apps (e.g., the 4 most recent tasks or a subset of pinned apps).
 * These icons are typically stacked on top of each other in a counter-clockwise manner,
 * partially overlapping.
 */
public class TaskbarOverflowView extends FrameLayout implements Reorderable {

    /**
     * The height divided by the width of the horizontal box containing two overlapping app icons.
     * According to the spec, this ratio is constant for different sizes of taskbar app icons.
     * Assuming the width of this box = taskbar app icon size - 2 paddings - 2 stroke widths, and
     * the height = width * 0.61, which is also equal to the height of a single item in the
     * preview.
     */
    public static final float TWO_ITEM_ICONS_BOX_ASPECT_RATIO = 0.61f;
    public static final long ITEM_ICON_SIZE_ANIMATION_DURATION = 500L;

    private static final int ALPHA_TRANSPARENT = 0;
    private static final int ALPHA_OPAQUE = 255;
    private static final long ANIMATION_DURATION_APPS_TO_LEAVE_BEHIND = 300L;
    private static final long ANIMATION_DURATION_LEAVE_BEHIND_TO_APPS = 500L;
    private static final long ANIMATION_SET_DURATION = 1000L;
    private static final long ITEM_ICON_CENTER_OFFSET_ANIMATION_DURATION = 500L;
    private static final long ITEM_ICON_COLOR_FILTER_OPACITY_ANIMATION_DURATION = 600L;
    private static final long ITEM_ICON_STROKE_WIDTH_ANIMATION_DURATION = 500L;
    private static final long LEAVE_BEHIND_ANIMATIONS_DELAY = 500L;
    private static final long LEAVE_BEHIND_OPACITY_ANIMATION_DURATION = 100L;
    private static final long LEAVE_BEHIND_SIZE_ANIMATION_DURATION = 500L;
    private static final float LEAVE_BEHIND_SIZE_SCALE_DOWN_MULTIPLIER = 0.83f;
    private static final int MAX_ITEMS_IN_PREVIEW = 4;

    public enum OverflowType {
        /** The overflow icon that contains pinned apps. */
        PINNED,
        /** The overflow icon that contains recent tasks where the apps are not pinned. */
        RECENTS
    }

    private OverflowType mOverflowType;

    private static final FloatProperty<TaskbarOverflowView> ITEM_ICON_CENTER_OFFSET =
            new FloatProperty<>("itemIconCenterOffset") {
                @Override
                public Float get(TaskbarOverflowView view) {
                    return view.mItemIconCenterOffset;
                }

                @Override
                public void setValue(TaskbarOverflowView view, float value) {
                    view.mItemIconCenterOffset = value;
                    view.invalidate();
                }
            };

    private static final IntProperty<TaskbarOverflowView> ITEM_ICON_COLOR_FILTER_OPACITY =
            new IntProperty<>("itemIconColorFilterOpacity") {
                @Override
                public Integer get(TaskbarOverflowView view) {
                    return view.mItemIconColorFilterOpacity;
                }

                @Override
                public void setValue(TaskbarOverflowView view, int value) {
                    view.mItemIconColorFilterOpacity = value;
                    view.invalidate();
                }
            };

    private static final FloatProperty<TaskbarOverflowView> ITEM_ICON_SIZE =
            new FloatProperty<>("itemIconSize") {
                @Override
                public Float get(TaskbarOverflowView view) {
                    return view.mItemIconSize;
                }

                @Override
                public void setValue(TaskbarOverflowView view, float value) {
                    view.mItemIconSize = value;
                    view.invalidate();
                }
            };

    private static final FloatProperty<TaskbarOverflowView> ITEM_ICON_STROKE_WIDTH =
            new FloatProperty<>("itemIconStrokeWidth") {
                @Override
                public Float get(TaskbarOverflowView view) {
                    return view.mItemIconStrokeWidth;
                }

                @Override
                public void setValue(TaskbarOverflowView view, float value) {
                    view.mItemIconStrokeWidth = value;
                    view.invalidate();
                }
            };

    private static final IntProperty<TaskbarOverflowView> LEAVE_BEHIND_OPACITY =
            new IntProperty<>("leaveBehindOpacity") {
                @Override
                public Integer get(TaskbarOverflowView view) {
                    return view.mLeaveBehindOpacity;
                }

                @Override
                public void setValue(TaskbarOverflowView view, int value) {
                    view.mLeaveBehindOpacity = value;
                    view.invalidate();
                }
            };

    private static final FloatProperty<TaskbarOverflowView> LEAVE_BEHIND_SIZE =
            new FloatProperty<>("leaveBehindSize") {
                @Override
                public Float get(TaskbarOverflowView view) {
                    return view.mLeaveBehindSize;
                }

                @Override
                public void setValue(TaskbarOverflowView view, float value) {
                    view.mLeaveBehindSize = value;
                    view.invalidate();
                }
            };

    private boolean mIsRtlLayout;
    private final List<TaskbarOverflowItem> mItems = new ArrayList<>();
    private int mIconSize;
    private Paint mItemBackgroundPaint;
    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private float mScaleForReorderBounce = 1f;
    private int mItemBackgroundColor;
    private int mLeaveBehindColor;

    // Active means the overflow icon has been pressed, which replaces the app icons with the
    // leave-behind circle and shows the KQS UI.
    private boolean mIsActive = false;
    private ValueAnimator mStateTransitionAnimationWrapper;

    private float mItemIconCenterOffsetDefault;
    private float mItemIconCenterOffset;  // [0..mItemIconCenterOffsetDefault]
    private int mItemIconColorFilterOpacity;  // [ALPHA_TRANSPARENT..ALPHA_OPAQUE]
    private float mItemIconSizeDefault;
    private float mItemIconSizeScaledDown;
    private float mItemIconSize;  // [mItemIconSizeScaledDown..mItemIconSizeDefault]
    private float mItemIconStrokeWidthDefault;
    private float mItemIconStrokeWidth;  // [0..mItemIconStrokeWidthDefault]
    private int mLeaveBehindOpacity;  // [ALPHA_TRANSPARENT..ALPHA_OPAQUE]
    private float mLeaveBehindSizeScaledDown;
    private float mLeaveBehindSizeDefault;
    private float mLeaveBehindSize;  // [mLeaveBehindSizeScaledDown..mLeaveBehindSizeDefault]
    private boolean mIsFirstItemHiddenForAnimation;

    // Information about an item that is currently being dragged from the overflow.
    // While being dragged, this item is hidden from the overflow's visual representation.
    private int mHiddenDraggedItemId = NO_ID;

    public TaskbarOverflowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TaskbarOverflowView(Context context) {
        super(context);
        init();
    }

    /**
     * Inflates the taskbar overflow button view.
     * @param type The type of the overflow button.
     * @param group The parent view.
     * @param iconSize The size of the overflow button icon.
     * @param padding The internal padding of the overflow view.
     * @return A taskbar overflow button.
     */
    public static TaskbarOverflowView inflateIcon(OverflowType type, ViewGroup group, int iconSize,
            int padding) {
        LayoutInflater inflater = LayoutInflater.from(group.getContext());
        TaskbarOverflowView icon = (TaskbarOverflowView) inflater.inflate(
                R.layout.taskbar_overflow_view, group, false);
        icon.mOverflowType = type;
        icon.setContentDescription(icon.getTextForTooltipPopup());
        icon.mIconSize = iconSize;

        final float taskbarIconRadius =
                (iconSize - padding * 2f) * IconNormalizer.ICON_VISIBLE_AREA_FACTOR / 2f;

        icon.mLeaveBehindSizeDefault = taskbarIconRadius;  // 1/2 of taskbar app icon size
        icon.mLeaveBehindSizeScaledDown =
                icon.mLeaveBehindSizeDefault * LEAVE_BEHIND_SIZE_SCALE_DOWN_MULTIPLIER;
        icon.mLeaveBehindSize = icon.mLeaveBehindSizeScaledDown;

        icon.mItemIconStrokeWidthDefault =
                taskbarIconRadius / 10f;  // 1/20 of taskbar app icon size
        icon.mItemIconStrokeWidth = icon.mItemIconStrokeWidthDefault;

        icon.mItemIconSizeDefault = 2f * taskbarIconRadius * TWO_ITEM_ICONS_BOX_ASPECT_RATIO;
        icon.mItemIconSizeScaledDown = icon.mLeaveBehindSizeScaledDown;
        icon.mItemIconSize = icon.mItemIconSizeDefault;

        icon.mItemIconCenterOffsetDefault = taskbarIconRadius
                - icon.mItemIconSizeDefault * IconNormalizer.ICON_VISIBLE_AREA_FACTOR / 2f
                - icon.mItemIconStrokeWidthDefault;
        icon.mItemIconCenterOffset = icon.mItemIconCenterOffsetDefault;

        return icon;
    }

    private void init() {
        mIsRtlLayout = Utilities.isRtl(getResources());
        mItemBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mItemBackgroundColor = getContext().getColor(
                com.android.internal.R.color.materialColorInverseOnSurface);
        mLeaveBehindColor = Themes.getAttrColor(getContext(), android.R.attr.textColorTertiary);

        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        drawAppIcons(canvas);
        drawLeaveBehindCircle(canvas);
    }

    private void drawAppIcons(@NonNull Canvas canvas) {
        mItemBackgroundPaint.setColor(mItemBackgroundColor);
        float canvasCenterXY = mIconSize / 2f;
        int adjustedItemIconSize = Math.round(mItemIconSize);
        float itemIconRadius = adjustedItemIconSize / 2f;

        List<TaskbarOverflowItem> visibleItems = getVisibleItems();
        int itemsToShow = Math.min(visibleItems.size(), MAX_ITEMS_IN_PREVIEW);

        ThemeManager themeManager = ThemeManager.INSTANCE.get(getContext());
        @DrawableCreationFlags int creationFlags =
                themeManager.isIconThemeEnabled() ? FLAG_THEMED : 0;
        @Nullable IconShape iconShape =
                enableLauncherIconShapes() ? themeManager.getIconShapeData().getValue() : null;

        for (int i = itemsToShow - 1; i >= 0; --i) {
            int indexDrawn = mOverflowType == OverflowType.PINNED ? i : itemsToShow - i - 1;
            Drawable icon = visibleItems.get(indexDrawn).getDrawableIcon(creationFlags, iconShape);
            if (icon == null) {
                continue;
            }

            if (i == 0 && mIsFirstItemHiddenForAnimation) {
                continue;
            }

            float itemCenterX = getItemXOffset(mItemIconCenterOffset, mIsRtlLayout, i, itemsToShow);
            float itemCenterY = getItemYOffset(mItemIconCenterOffset, i, itemsToShow);

            Drawable iconCopy = icon.getConstantState().newDrawable().mutate();
            iconCopy.setBounds(0, 0, adjustedItemIconSize, adjustedItemIconSize);
            iconCopy.setColorFilter(new BlendModeColorFilter(
                    ColorUtils.setAlphaComponent(mLeaveBehindColor, mItemIconColorFilterOpacity),
                    BlendMode.SRC_ATOP));

            canvas.save();
            canvas.translate(
                    canvasCenterXY + itemCenterX - itemIconRadius,
                    canvasCenterXY + itemCenterY - itemIconRadius);
            canvas.drawCircle(itemIconRadius, itemIconRadius,
                    itemIconRadius * IconNormalizer.ICON_VISIBLE_AREA_FACTOR + mItemIconStrokeWidth,
                    mItemBackgroundPaint);
            iconCopy.draw(canvas);
            canvas.restore();
        }
    }

    private void drawLeaveBehindCircle(@NonNull Canvas canvas) {
        mItemBackgroundPaint.setColor(
                ColorUtils.setAlphaComponent(mLeaveBehindColor, mLeaveBehindOpacity));

        final float xyCenter = mIconSize / 2f;
        canvas.drawCircle(xyCenter, xyCenter, mLeaveBehindSize / 2f, mItemBackgroundPaint);
    }

    /**
     * Listener for changes in the overflow items.
     */
    public interface OnChangeListener {
        /** Called when the items in the overflow have changed. */
        void onItemsChanged();
    }

    private OnChangeListener mOnChangeListener;

    /** Sets the listener for changes in the overflow items. */
    public void setOnChangeListener(OnChangeListener listener) {
        mOnChangeListener = listener;
    }

    /**
     * Returns a list of items to be displayed in the overflow icon, excluding the item
     * currently being dragged.
     *
     * @return A list of {@link TaskbarOverflowItem} currently visible in the overflow.
     */
    private List<TaskbarOverflowItem> getVisibleItems() {
        if (mHiddenDraggedItemId == NO_ID) {
            return mItems;
        }
        return mItems.stream()
                .filter(item -> item.getItemId() != mHiddenDraggedItemId)
                .toList();
    }

    private int getVisibleItemsCount() {
        return (mHiddenDraggedItemId == NO_ID) ? mItems.size() : Math.max(0, mItems.size() - 1);
    }

    /**
     * Clears the list of tasks tracked by the view.
     */
    public void clearItems() {
        mItems.clear();
        mHiddenDraggedItemId = NO_ID;

        invalidate();
        if (mOnChangeListener != null) {
            mOnChangeListener.onItemsChanged();
        }
    }

    /**
     * Adds an item to the list of items at front position.
     * @param item The item to add.
     */
    public void prependItem(TaskbarOverflowItem item) {
        mItems.addFirst(item);

        invalidate();
        if (mOnChangeListener != null) {
            mOnChangeListener.onItemsChanged();
        }
    }

    /**
     * Removes the first visible item from the item list.
     * @return The removed item, or null if no visible items exist.
     */
    @Nullable
    public TaskbarOverflowItem removeFirstVisibleItem() {
        if (mItems.isEmpty()) {
            return null;
        }

        int indexToRemove = 0;
        if (mItems.getFirst().getItemId() == mHiddenDraggedItemId) {
            if (mItems.size() == 1) {
                return null;
            }
            indexToRemove = 1;
        }

        TaskbarOverflowItem item = mItems.remove(indexToRemove);

        invalidate();
        if (mOnChangeListener != null) {
            mOnChangeListener.onItemsChanged();
        }

        return item;
    }

    /**
     * Update the view to represent a new list of recent tasks.
     * @param items Items to be shown in the view.
     */
    public void setItems(List<? extends TaskbarOverflowItem> items) {
        mItems.clear();
        mItems.addAll(items);
        mHiddenDraggedItemId = NO_ID;

        invalidate();
        if (mOnChangeListener != null) {
            mOnChangeListener.onItemsChanged();
        }
    }

    /**
     * Handles the event when an item from the overflow is being dragged.
     * <p>
     * This method removes the item from the pinned list and caches its information so that it can
     * be restored if the drag is cancelled.
     *
     * @param info  The {@link ItemInfo} of the item being dragged.
     */
    public void onOverflowItemDragged(ItemInfo info) {
        mHiddenDraggedItemId = info.id;

        invalidate();
        if (mOnChangeListener != null) {
            mOnChangeListener.onItemsChanged();
        }
    }

    /**
     * Handles the event when a drag operation that originated from the overflow has ended.
     *
     * @param itemDropped {@code true} if the item was successfully dropped, {@code false}
     *                    otherwise.
     */
    public void onItemDragEnded(boolean itemDropped) {
        if (mHiddenDraggedItemId == NO_ID) {
            return;
        }
        if (itemDropped) {
            // If the item is dropped successfully, the view is redrawn with the model update.
            mHiddenDraggedItemId = NO_ID;
            return;
        }

        mHiddenDraggedItemId = NO_ID;
        invalidate();
        if (mOnChangeListener != null) {
            mOnChangeListener.onItemsChanged();
        }
    }

    @VisibleForTesting
    public List<Integer> getItemIds() {
        return mItems.stream().map(TaskbarOverflowItem::getItemId).toList();
    }

    public List<ItemInfo> getOverflowInfoList() {
        return getVisibleItems().stream().map(
                item -> ((ItemInfoWrapper) item).getItemInfo()).toList();
    }

    /**
     * Updates the first item in the preview to be hidden to allow another icon to animate into
     * its place.
     * @param isHidden The hidden state for the first item in the preview is hidden.
     */
    public void setFirstItemHiddenForAnimation(boolean isHidden) {
        if (mIsFirstItemHiddenForAnimation != isHidden) {
            mIsFirstItemHiddenForAnimation = isHidden;
            invalidate();
        }
    }

    /**
     * Returns {@code true} if the first item in the preview is hidden to allow another icon
     * to animate into its place.
     */
    public boolean isFirstItemHiddenForAnimation() {
        return mIsFirstItemHiddenForAnimation;
    }

    /**
     * Called when a task is updated. If the task is contained within the view, it's cached value
     * gets updated. If the task is shown within the icon, invalidates the view, so the task icon
     * gets updated.
     * @param singleTask The updated SingeTask.
     */
    public void updateTaskIsShown(SingleTask singleTask) {
        List<TaskbarOverflowItem> visibleItems = getVisibleItems();
        for (int i = 0; i < visibleItems.size(); ++i) {
            if (visibleItems.get(i) instanceof TaskWrapper taskItem
                    && taskItem.getItemId() == singleTask.getTask().key.id) {
                visibleItems.set(i, new TaskWrapper(getContext(), singleTask));
                if (i >= visibleItems.size() - MAX_ITEMS_IN_PREVIEW) {
                    invalidate();
                }
                break;
            }
        }
    }

    /**
     * @return Tooltip to be used for the taskbar overflow view - returns null if the view should
     *         not have a tooltip.
     */
    public String getTextForTooltipPopup() {
        return switch (mOverflowType) {
            case PINNED -> getResources().getString(R.string.taskbar_pinned_overflow_a11y_title);
            case RECENTS -> getResources().getString(R.string.taskbar_recents_overflow_a11y_title);
        };
    }

    /**
     * Returns the view's state (whether it shows a set of app icons or a leave-behind circle).
     */
    public boolean getIsActive() {
        return mIsActive;
    }

    /**
     * Updates the view's state to draw either a set of app icons or a leave-behind circle.
     * @param isActive The next state of the view.
     */
    public void setIsActive(boolean isActive) {
        if (mIsActive == isActive) {
            return;
        }
        mIsActive = isActive;

        if (mStateTransitionAnimationWrapper != null
                && mStateTransitionAnimationWrapper.isRunning()) {
            mStateTransitionAnimationWrapper.reverse();
            return;
        }

        final AnimatorSet stateTransitionAnimation = getStateTransitionAnimation();
        mStateTransitionAnimationWrapper = ValueAnimator.ofFloat(0, 1f);
        mStateTransitionAnimationWrapper.setDuration(mIsActive
                ? ANIMATION_DURATION_APPS_TO_LEAVE_BEHIND
                : ANIMATION_DURATION_LEAVE_BEHIND_TO_APPS);
        mStateTransitionAnimationWrapper.setInterpolator(
                mIsActive ? Interpolators.STANDARD : Interpolators.EMPHASIZED);
        mStateTransitionAnimationWrapper.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mStateTransitionAnimationWrapper = null;
            }
        });
        mStateTransitionAnimationWrapper.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animator) {
                        stateTransitionAnimation.setCurrentPlayTime(
                                (long) (ANIMATION_SET_DURATION * animator.getAnimatedFraction()));
                    }
                });
        mStateTransitionAnimationWrapper.start();
    }

    private AnimatorSet getStateTransitionAnimation() {
        final AnimatorSet animation = new AnimatorSet();
        animation.setInterpolator(Interpolators.LINEAR);
        animation.playTogether(
                buildAnimator(ITEM_ICON_CENTER_OFFSET, 0f, mItemIconCenterOffsetDefault,
                        ITEM_ICON_CENTER_OFFSET_ANIMATION_DURATION, 0L,
                        ITEM_ICON_CENTER_OFFSET_ANIMATION_DURATION),
                buildAnimator(ITEM_ICON_COLOR_FILTER_OPACITY, ALPHA_OPAQUE, ALPHA_TRANSPARENT,
                        ITEM_ICON_COLOR_FILTER_OPACITY_ANIMATION_DURATION, 0L,
                        ANIMATION_SET_DURATION - ITEM_ICON_COLOR_FILTER_OPACITY_ANIMATION_DURATION),
                buildAnimator(ITEM_ICON_SIZE, mItemIconSizeScaledDown, mItemIconSizeDefault,
                        ITEM_ICON_SIZE_ANIMATION_DURATION, 0L,
                        ITEM_ICON_SIZE_ANIMATION_DURATION),
                buildAnimator(ITEM_ICON_STROKE_WIDTH, 0f, mItemIconStrokeWidthDefault,
                        ITEM_ICON_STROKE_WIDTH_ANIMATION_DURATION, 0L,
                        ITEM_ICON_STROKE_WIDTH_ANIMATION_DURATION),
                buildAnimator(LEAVE_BEHIND_OPACITY, ALPHA_OPAQUE, ALPHA_TRANSPARENT,
                        LEAVE_BEHIND_OPACITY_ANIMATION_DURATION, LEAVE_BEHIND_ANIMATIONS_DELAY,
                        ANIMATION_SET_DURATION - LEAVE_BEHIND_ANIMATIONS_DELAY
                                - LEAVE_BEHIND_OPACITY_ANIMATION_DURATION),
                buildAnimator(LEAVE_BEHIND_SIZE, mLeaveBehindSizeDefault,
                        mLeaveBehindSizeScaledDown, LEAVE_BEHIND_SIZE_ANIMATION_DURATION,
                        LEAVE_BEHIND_ANIMATIONS_DELAY, 0L)
        );
        return animation;
    }

    private ObjectAnimator buildAnimator(IntProperty<TaskbarOverflowView> property,
            int finalValueWhenAnimatingToLeaveBehind, int finalValueWhenAnimatingToAppIcons,
            long duration, long delayWhenAnimatingToLeaveBehind,
            long delayWhenAnimatingToAppIcons) {
        final ObjectAnimator animator = ObjectAnimator.ofInt(this, property,
                mIsActive ? finalValueWhenAnimatingToLeaveBehind
                        : finalValueWhenAnimatingToAppIcons);
        applyTiming(animator, duration, delayWhenAnimatingToLeaveBehind,
                delayWhenAnimatingToAppIcons);
        return animator;
    }

    private ObjectAnimator buildAnimator(FloatProperty<TaskbarOverflowView> property,
            float finalValueWhenAnimatingToLeaveBehind, float finalValueWhenAnimatingToAppIcons,
            long duration, long delayWhenAnimatingToLeaveBehind,
            long delayWhenAnimatingToAppIcons) {
        final ObjectAnimator animator = ObjectAnimator.ofFloat(this, property,
                mIsActive ? finalValueWhenAnimatingToLeaveBehind
                        : finalValueWhenAnimatingToAppIcons);
        applyTiming(animator, duration, delayWhenAnimatingToLeaveBehind,
                delayWhenAnimatingToAppIcons);
        return animator;
    }

    private void applyTiming(ObjectAnimator animator, long duration,
            long delayWhenAnimatingToLeaveBehind,
            long delayWhenAnimatingToAppIcons) {
        animator.setDuration(duration);
        animator.setStartDelay(
                mIsActive ? delayWhenAnimatingToLeaveBehind : delayWhenAnimatingToAppIcons);
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    private float getItemXOffset(float baseOffset, boolean isRtl, int itemIndex, int itemCount) {
        // Both RTL and the type of overflow result in reverse x position of the apps in the icon.
        // Determining the position by calculating an operator.
        int orderOperator = (isRtl ^ mOverflowType.equals(OverflowType.PINNED)) ? 1 : -1;

        /*
         * Icon illustration with 4 items in LTR:
         *        Pinned overflow view           Recents overflow view
         *             | 0 | 1 |                       | 1 | 0 |
         *             | 3 | 2 |                       | 2 | 3 |
         *
         * Comments below are for LTR Recents overflow view, where the orderOperator is -1.
         */

        // Item with index 1 is on the left in all cases.
        if (itemIndex == 1) {
            return orderOperator * baseOffset;
        }

        // First item is centered if total number of items shown is 3, on the right otherwise.
        if (itemIndex == 0) {
            if (itemCount == 3) {
                return 0;
            }
            return -orderOperator * baseOffset;
        }

        // Last item is on the right when there are more than 2 items (case which is already handled
        // as `itemIndex == 1`).
        if (itemIndex == itemCount - 1) {
            return -orderOperator * baseOffset;
        }

        return orderOperator * baseOffset;
    }

    private float getItemYOffset(float baseOffset, int itemIndex, int itemCount) {
        // If icon contains two items, they are both centered vertically.
        if (itemCount == 2) {
            return 0;
        }
        // First half of items is on top, later half is on bottom.
        return (itemIndex + 1 <= itemCount / 2 ? -1 : 1) * baseOffset;
    }

    /**
     * Calculate the x and y offsets of the first item.
     */
    public PointF getOverlayOffsetsForFirstItem(boolean isMovingAway, int indexOfItem) {
        int itemsToShow = Math.min(getVisibleItemsCount(), MAX_ITEMS_IN_PREVIEW);
        int totalItems = isMovingAway && itemsToShow < MAX_ITEMS_IN_PREVIEW
                ? itemsToShow + 1 : itemsToShow;

        // Reverse the overlay offset item index for the special case of overflow icon removing
        // from view in RTL layout,
        if (mIsRtlLayout && itemsToShow == 0 && isMovingAway) {
            indexOfItem = indexOfItem == 0 ? 1 : 0;
        }

        float xOffset = getItemXOffset(
                mItemIconCenterOffset, mIsRtlLayout, indexOfItem, totalItems);
        float yOffset = getItemYOffset(mItemIconCenterOffset, indexOfItem, totalItems);

        return new PointF(xOffset, yOffset);
    }
}
