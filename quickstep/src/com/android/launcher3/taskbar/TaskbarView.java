/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.os.Trace.TRACE_TAG_APP;
import static android.os.Trace.traceBegin;
import static android.os.Trace.traceEnd;
import static android.window.DesktopModeFlags.ENABLE_TASKBAR_OVERFLOW;

import static com.android.launcher3.BubbleTextView.DISPLAY_TASKBAR;
import static com.android.launcher3.Flags.enableCursorDrivenWorkflows;
import static com.android.launcher3.Flags.enableLauncherIconShapes;
import static com.android.launcher3.LauncherAnimUtils.getScaleProperty;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
import static com.android.launcher3.Utilities.dpToPx;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.view.DisplayCutout;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.app.animation.Interpolators;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.celllayout.CellInfo;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags;
import com.android.launcher3.icons.IconShape;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.TaskItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.taskbar.TaskbarOverflowView.OverflowType;
import com.android.launcher3.taskbar.customization.TaskbarAllAppsButtonContainer;
import com.android.launcher3.taskbar.customization.TaskbarDividerContainer;
import com.android.launcher3.taskbar.customization.TaskbarSpecsEvaluator;
import com.android.launcher3.taskbar.customization.containers.TaskbarPinnedAppIconContainer;
import com.android.launcher3.taskbar.customization.viewfactory.TaskbarPinnedAppsIconsViewFactory;
import com.android.launcher3.taskbar.handoff.HandoffSuggestion;
import com.android.launcher3.touch.CustomTouchDelegate;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.PredictedAppIcon;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SingleTask;
import com.android.quickstep.util.SplitTask;
import com.android.quickstep.views.TaskViewType;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends FrameLayout implements FolderIcon.FolderIconParent, Insettable,
        DeviceProfile.OnDeviceProfileChangeListener,
        TaskbarViewDragDropController.PinnedAppsContainerDelegate {
    // The number of icons always present in the taskbar, including the All Apps button and the
    // divider.
    private static final int NUM_ALWAYS_VISIBLE_TASKBAR_ICONS = 2;
    private static final Rect sTmpRect = new Rect();
    private final int mUnpinnedHitRectBuffer;
    private final int mPinnedHitRectBuffer;
    private final Rect mIconLayoutBounds;
    private final int mIconTouchSize;
    private final int mItemMarginLeftRight;
    private final int mItemPadding;
    private final int mFolderLeaveBehindColor;
    private final int[] mFirstIconViewLocation = new int[2];
    private final int[] mLastIconViewLocation = new int[2];
    private final boolean mIsRtl;

    private final TaskbarUiState mTaskbarUiState;

    private final TaskbarActivityContext mActivityContext;
    @Nullable private BubbleBarLocation mBubbleBarLocation = null;

    // Initialized in init.
    private TaskbarViewCallbacks mControllerCallbacks;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    // Only non-null when the corresponding Folder is open.
    @Nullable private FolderIcon mLeaveBehindFolderIcon;

    // Only non-null when device supports having an All Apps button.
    private final TaskbarAllAppsButtonContainer mAllAppsButtonContainer;

    // Only non-null when device supports having a Divider button.
    @Nullable private TaskbarDividerContainer mTaskbarDividerContainer;

    // Only non-null when taskbar customization is enabled.
    @Nullable private TaskbarPinnedAppIconContainer mHotseatIconsContainer;

    // Only non-null when device supports having a Taskbar Overflow button for pinned items.
    @Nullable private TaskbarOverflowView mTaskbarPinnedOverflowView;

    // Only non-null when device supports having a Taskbar Overflow button for recent tasks.
    @Nullable private TaskbarOverflowView mTaskbarRecentsOverflowView;

    // Only non-null when there is an ongoing task icon in animation.
    @Nullable private Animator mOngoingRecentIconAnimation;

    private final TaskbarPinnedAppsIconsViewFactory mItemViewFactory;

    private int mMaxNumIconsLimitForTest = -1;

    // Iterates within child views of TaskbarView
    private int mNextViewIndex = 0;
    // Iterates within child views of mHotseatIconsContainer (if non-null)
    private int mNextHotseatIndex = 0;


    private int mNumbersOfTaskbarIconsOverflowing = 0;

    private PinnedAppsDragHelper mDragDelegate;

    public int getIgnoreTaskbarIconCount() {
        return mIgnoreTaskbarIconCount;
    }

    // TODO: clean it up in follow up cl with removal of taskbar icon alignment.
    // Only used for edge of 3 button navigation mode, where we need to hide icons which go
    // beyond the bounds.
    private int mIgnoreTaskbarIconCount = 0;
    /**
     * Whether the divider is between Hotseat icons and Recents,
     * instead of between All Apps button and Hotseat.
     */
    private boolean mAddedDividerForRecents;

    private final View mQsb;

    private final float mTransientTaskbarMinWidth;

    private boolean mShouldTryStartAlign;

    private int mMaxNumIcons = 0;
    private int mIdealNumIcons = 0;

    private final int mAllAppsButtonTranslationOffset;

    private int mNumStaticViews;

    private Set<GroupTask> mPrevRecentTasks = Collections.emptySet();
    private Set<GroupTask> mPrevOverflowTasks = Collections.emptySet();

    public TaskbarView(@NonNull Context context) {
        this(context, null);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mActivityContext = ActivityContext.lookupContext(context);
        mIconLayoutBounds = mActivityContext.getTransientTaskbarBounds();
        Resources resources = getResources();
        mIsRtl = Utilities.isRtl(resources);
        mTaskbarUiState = TaskbarUiStateMonitor.INSTANCE.get(context)
                .getTaskbarUiState(context.getDisplayId());
        mUnpinnedHitRectBuffer = resources.getDimensionPixelSize(
            R.dimen.taskbar_unpinned_hit_rect_buffer);
        mPinnedHitRectBuffer = resources.getDimensionPixelSize(
            R.dimen.taskbar_pinned_hit_rect_buffer);

        mTaskbarUiState.setIsTaskbarViewShown(isShown());
        mTransientTaskbarMinWidth = resources.getDimension(R.dimen.transient_taskbar_min_width);

        // TODO: Disable touch events on QSB otherwise it can crash.
        mQsb = LauncherComponentProvider.get(context).getQsbWidgetFactory().createView(this);
        onDeviceProfileChanged(mActivityContext.getDeviceProfile());

        final TaskbarSpecsEvaluator specsEvaluator = mActivityContext.getTaskbarSpecsEvaluator();
        int actualMargin = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
        int actualIconSize =
                dpToPx(specsEvaluator.getTaskbarIconSize().getSize(), mActivityContext);
        int visualIconSize = (int) (actualIconSize * ICON_VISIBLE_AREA_FACTOR);

        mIconTouchSize = dpToPx(specsEvaluator.getTaskbarIconTouchSize(), mActivityContext);

        // We layout the icons to be of mIconTouchSize in width and height
        mItemMarginLeftRight = actualMargin - (mIconTouchSize - visualIconSize) / 2;

        if (Flags.enableTaskbarIconContainer()) {
            mHotseatIconsContainer =
                    TaskbarPinnedAppIconContainer.create(context, mItemMarginLeftRight);
        }

        mItemPadding = dpToPx(specsEvaluator.getTaskbarIconPadding(), mActivityContext);

        mFolderLeaveBehindColor = Themes.getAttrColor(mActivityContext,
                android.R.attr.textColorTertiary);

        // Needed to draw folder leave-behind when opening one.
        setWillNotDraw(false);

        mAllAppsButtonContainer = (TaskbarAllAppsButtonContainer) inflate(
                R.layout.taskbar_all_apps_button_container);
        mAllAppsButtonTranslationOffset = (int) getResources().getDimension(
                mAllAppsButtonContainer.getAllAppsButtonTranslationXOffset(
                        mActivityContext.isTransientTaskbar()));

        mItemViewFactory = new TaskbarPinnedAppsIconsViewFactory(mActivityContext, this);

        mTaskbarDividerContainer = (TaskbarDividerContainer) inflate(
                R.layout.taskbar_divider_button_container);

        if (ENABLE_TASKBAR_OVERFLOW.isTrue()) {
            mTaskbarRecentsOverflowView = TaskbarOverflowView.inflateIcon(OverflowType.RECENTS,
                    this, mIconTouchSize, mItemPadding);
            mTaskbarRecentsOverflowView.setId(R.id.taskbar_overflow_view);
        }

        if (TaskbarPopupController.canPinAppsOverflow()) {
            mTaskbarPinnedOverflowView = TaskbarOverflowView.inflateIcon(OverflowType.PINNED, this,
                    mIconTouchSize, mItemPadding);
        }
    }

    /**
     * @return the maximum number of 'icons' that can fit in the taskbar.
     */
    private int calculateMaxNumIcons() {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int availableWidth = deviceProfile.getDeviceProperties().getWidthPx();
        int defaultEdgeMargin = getResources().getDimensionPixelSize(
                deviceProfile.inv.inlineNavButtonsEndSpacing);
        int spaceForBubbleBar =
                Math.round(mControllerCallbacks.getBubbleBarMaxCollapsedWidthIfVisible());

        // Reserve space required for edge margins, or for navbar if shown. If task bar needs to be
        // center aligned with nav bar shown, reserve space on both sides.
        availableWidth -= Math.max(
                defaultEdgeMargin + spaceForBubbleBar,
                deviceProfile.getHotseatProfile().getBarEndOffset());
        availableWidth -= Math.max(
                defaultEdgeMargin + (mShouldTryStartAlign ? 0 : spaceForBubbleBar),
                mShouldTryStartAlign ? 0 : deviceProfile.getHotseatProfile().getBarEndOffset());

        // The space taken by an item icon used during layout.
        int iconSize = 2 * mItemMarginLeftRight + mIconTouchSize;

        int additionalIcons = 0;

        if (mTaskbarDividerContainer != null) {
            // Space for divider icon is reduced during layout compared to normal icon size, reserve
            // space for the divider separately.
            availableWidth -= iconSize - 4 * mItemMarginLeftRight;
            ++additionalIcons;
        }

        // All apps icon takes less space compared to normal icon size, reserve space for the icon
        // separately.
        boolean forceTransientTaskbarSize = canTransitionToTransientTaskbar();
        availableWidth -= iconSize - (int) getResources().getDimension(
                mAllAppsButtonContainer.getAllAppsButtonTranslationXOffset(
                        forceTransientTaskbarSize || mActivityContext.isTransientTaskbar()));
        ++additionalIcons;

        int maxIcons = Math.floorDiv(availableWidth, iconSize) + additionalIcons;
        return Math.min(maxIcons,
                mMaxNumIconsLimitForTest > 0 ? mMaxNumIconsLimitForTest : maxIcons);
    }

    /**
     * Whether the taskbar in the state context supports transition to a transient taskbar (e.g.
     * using a popup menu).
     */
    boolean canTransitionToTransientTaskbar() {
        return mActivityContext.getTaskbarFeatureEvaluator()
                .getSupportsTransitionToTransientTaskbar();
    }

    /**
     * Recalculates the max number of icons the taskbar view can show without entering overflow.
     * Returns whether the max number of icons changed and the change affects the number of icons
     * that should be shown in the taskbar.
     */
    boolean updateMaxNumIcons() {
        int oldMaxNumIcons = mMaxNumIcons;
        mMaxNumIcons = calculateMaxNumIcons();
        return oldMaxNumIcons != mMaxNumIcons
                && (mIdealNumIcons > oldMaxNumIcons || mIdealNumIcons > mMaxNumIcons);
    }

    /**
     * Pre-adds views that are always children of this view for LayoutTransition support.
     * <p>
     * Normally these views are removed and re-added when updating hotseat and recents. This
     * approach does not behave well with LayoutTransition, so we instead need to add them
     * initially and avoid removing them during updates.
     */
    private int addStaticViews() {
        int numStaticViews = 1;
        addView(mAllAppsButtonContainer);

        if (mHotseatIconsContainer != null) {
            addView(mHotseatIconsContainer, mIsRtl ? 0 : numStaticViews);
            numStaticViews++;
        }

        if (mActivityContext.getDeviceProfile().getHotseatProfile().isQsbInline()) {
            addView(mQsb, mIsRtl ? numStaticViews : 0);
            mQsb.setVisibility(View.INVISIBLE);
            numStaticViews++;
        }
        return numStaticViews;
    }

    @Override
    public void setVisibility(int visibility) {
        boolean changed = getVisibility() != visibility;
        super.setVisibility(visibility);
        if (changed && mControllerCallbacks != null) {
            mControllerCallbacks.notifyVisibilityChanged();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivityContext.addOnDeviceProfileChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mShouldTryStartAlign = mActivityContext.shouldStartAlignTaskbar();
        ViewGroup.LayoutParams lp = mQsb.getLayoutParams();
        if (lp != null) {
            lp.width = dp.getHotseatProfile().getQsbWidth();
            lp.height = dp.getHotseatProfile().getQsbHeight();
        }
    }

    /**
     * Returns the icon touch size.
     */
    public int getIconTouchSize() {
        return mIconTouchSize;
    }

    protected void init(TaskbarViewCallbacks callbacks) {
        // set taskbar pane title so that accessibility service know it window and focuses.
        setAccessibilityPaneTitle(getContext().getString(R.string.taskbar_a11y_title));
        mControllerCallbacks = callbacks;
        mIconClickListener = mControllerCallbacks.getIconOnClickListener();
        mIconLongClickListener = mControllerCallbacks.getIconOnLongClickListener();

        mAllAppsButtonContainer.setUpCallbacks(callbacks);
        if (mTaskbarRecentsOverflowView != null) {
            mTaskbarRecentsOverflowView.setOnClickListener(
                    mControllerCallbacks.getRecentsOverflowOnClickListener());
            mTaskbarRecentsOverflowView.setOnLongClickListener(
                    mControllerCallbacks.getRecentsOverflowOnLongClickListener());
            setHoverListenerForIcon(mTaskbarRecentsOverflowView);
        }

        if (mHotseatIconsContainer != null) {
            mHotseatIconsContainer.setUpCallbacks(callbacks);
        }

        if (mTaskbarPinnedOverflowView != null) {
            mTaskbarPinnedOverflowView.setOnClickListener(
                    mControllerCallbacks.getPinnedOverflowOnClickListener());
            mTaskbarPinnedOverflowView.setOnLongClickListener(
                    mControllerCallbacks.getPinnedOverflowOnLongClickListener());
            setHoverListenerForIcon(mTaskbarPinnedOverflowView);
        }

        mMaxNumIcons = calculateMaxNumIcons();

        mDragDelegate = new PinnedAppsDragHelper(getContext(), this, mIconTouchSize) {
            @Nullable
            @Override
            public BubbleTextView createViewForItem(@NonNull ItemInfo item) {
                return createIconViewForItem(item, getChildCount());
            }

            @Override
            public int calculateGhostViewIndex(int onScreenLocationX) {
                getHitRectForPinRelativeToDragLayer(sTmpRect);
                // RTL in HotseatIconsContainer has different logic in TaskbarView.
                int direction = (mIsRtl && mHotseatIconsContainer != null) ? -1 : 1;

                int iconAreaStartX = direction == -1 ? (sTmpRect.right - mPinnedHitRectBuffer)
                        : (sTmpRect.left + mPinnedHitRectBuffer);
                if (mHotseatIconsContainer == null) {
                    iconAreaStartX += mAllAppsButtonContainer.getSpaceNeeded() * direction;
                }
                int clampedX = Math.max(sTmpRect.left, Math.min(sTmpRect.right, onScreenLocationX));
                int relativeX = (clampedX - iconAreaStartX) * direction;
                int slotWidth = mIconTouchSize + (2 * mItemMarginLeftRight);

                return Math.max(0, relativeX / slotWidth);
            }

            @NonNull
            @Override
            public ViewGroup.LayoutParams createGhostViewLayoutParams(int iconSize) {
                TaskbarLayoutParams lp = new TaskbarLayoutParams(iconSize, iconSize);
                lp.setMargins(mItemMarginLeftRight, 0, mItemMarginLeftRight, 0);
                return lp;
            }

            @Override
            public int calculateDropIndexInContainer(int dropIndex, int hiddenChildIndex) {
                int dropSpotOffset =
                        mActivityContext.getDeviceProfile().getHotseatProfile().isQsbInline()
                                ? 2 : 1;
                int dividerIndex = indexOfChild(mTaskbarDividerContainer);

                int maxIndex = indexOfChild(mTaskbarPinnedOverflowView);
                if (maxIndex < 0 && dividerIndex > dropSpotOffset) {
                    maxIndex =  dividerIndex;
                } else if (maxIndex < 0) {
                    maxIndex = getChildCount();
                }

                if (dividerIndex == dropSpotOffset) {
                    dropSpotOffset++;
                }

                int targetIndex = Math.min(dropIndex, maxIndex - 1) + dropSpotOffset;
                if (hiddenChildIndex > -1 && hiddenChildIndex < targetIndex) {
                    targetIndex++;
                }
                return Math.min(targetIndex, mNextViewIndex);
            }

            @Override
            public void reserveDropSlotForDragLocation(int onScreenLocationX) {
                if (mHotseatIconsContainer != null) {
                    int index = calculateGhostViewIndex(onScreenLocationX);
                    mHotseatIconsContainer.reserveDropSlot(index);
                    setDropSpotIndex(index);
                    onDragStateChanged();
                    return;
                }
                super.reserveDropSlotForDragLocation(onScreenLocationX);
            }

            @Override
            public void onDragStateChanged() {
                rearrangeItemsForDrag();
            }

            @Override
            public boolean isPointOnOverflowIcon(@NonNull float[] point) {
                return false;
            }

            @Override
            public void getHitRectForPinRelativeToDragLayer(@Nullable Rect outRect) {
                TaskbarView.this.getHitRectForPinRelativeToDragLayer(outRect);
            }
        };
    }

    private BubbleTextView createIconViewForItem(@NonNull ItemInfo item, int index) {
        View icon = mItemViewFactory.getView(item, index);
        if (icon instanceof BubbleTextView btv) {
            if (item instanceof WorkspaceItemInfo wii) {
                btv.applyFromWorkspaceItem(wii);
            }

            // Move the first icon in the overflow icon to the end of the pinned section.
            icon.setLayoutParams(new TaskbarLayoutParams(mIconTouchSize, mIconTouchSize));
            icon.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
            setClickAndLongClickListenersForIcon(icon);
            setHoverListenerForIcon(icon);
            return btv;
        }
        return null;
    }

    protected void rearrangeItemsForDrag() {
        if (mHotseatIconsContainer != null) {
            mHotseatIconsContainer.rearrangeItemsForDrag();
            return;
        }

        if (getNumOfVisibleIconsInPinnedSection()
                == mActivityContext.getTaskbarSpecsEvaluator().getNumShownHotseatIcons()) {
            return;
        }

        if (getNumOfVisibleIconsInPinnedSection()
                > mActivityContext.getTaskbarSpecsEvaluator().getNumShownHotseatIcons()) {
            TaskbarOverflowView overflowView = getTaskbarPinnedOverflowView();
            if (overflowView == null || !isOverflowViewShowing()) {
                // TODO: Group the extra icons to an overflow view.
                return;
            }

            int overflowIdx = indexOfChild(overflowView);
            View viewToMove = null;
            for (int i = overflowIdx - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (child.getVisibility() == View.VISIBLE
                        && !(child instanceof TaskbarDropTargetGhostView)) {
                    viewToMove = child;
                    break;
                }
            }

            if (viewToMove != null && viewToMove.getTag() instanceof ItemInfo itemToMove) {
                overflowView.prependItem(new ItemInfoWrapper(itemToMove, mActivityContext));
                removeAndRecycle(viewToMove);
            }
        } else {
            TaskbarOverflowView overflowView = getTaskbarPinnedOverflowView();
            if (overflowView == null || !isOverflowViewShowing()) {
                return;
            }
            TaskbarOverflowItem movedItemWrapper = overflowView.removeFirstVisibleItem();
            if (movedItemWrapper == null) {
                return;
            }

            ItemInfo movedItem = null;
            if (movedItemWrapper instanceof ItemInfoWrapper itemInfoWrapper) {
                movedItem = itemInfoWrapper.getItemInfo();
            }
            if (movedItem == null) {
                return;
            }

            // Create the view for the item to move.
            int index = indexOfChild(overflowView);
            View newView = createIconViewForItem(movedItem, getChildCount());
            if (newView != null) {
                addView(newView, index);
            }
        }
    }

    void updatePinningPopupEventHandlers() {
        boolean supportsPinningPopup =
                mActivityContext.getTaskbarFeatureEvaluator().getSupportsPinningPopup();
        if (mTaskbarDividerContainer != null) {
            mTaskbarDividerContainer.setUpCallbacks(
                    supportsPinningPopup ? mControllerCallbacks : null);
        }

        setOnTouchListener(
                supportsPinningPopup ? mControllerCallbacks.getTaskbarTouchListener() : null);
    }

    private void removeAndRecycle(View view) {
        removeAndRecycle(this, view);
    }

    private void removeAndRecycle(ViewGroup parent, View view) {
        parent.removeView(view);
        view.setOnClickListener(null);
        view.setOnLongClickListener(null);
        if (!(view.getTag() instanceof CollectionInfo)) {
            mActivityContext.getViewCache().recycleView(view.getSourceLayoutResId(), view);
        }
        view.setTag(null);
    }

    protected void updateItems(
            ItemInfo[] hotseatItemInfos,
            List<GroupTask> recentTasks,
            List<HandoffSuggestion> handoffSuggestions) {
        updateItems(hotseatItemInfos, recentTasks, handoffSuggestions, false);
    }

    /**
     * Inflates/binds the hotseat items, recent tasks, and handoff suggestions to the view.
     *
     * @param forceUpdateHotseat Whether to force update every hotseat icon.
     */
    protected void updateItems(
            ItemInfo[] hotseatItemInfos,
            List<GroupTask> recentTasks,
            List<HandoffSuggestion> handoffSuggestions,
            boolean forceUpdateHotseat) {

        if (mActivityContext.isDestroyed()) return;

        traceBegin(TRACE_TAG_APP, "TaskbarView#updateItems");

        // Filter out unsupported items.
        hotseatItemInfos = Arrays.stream(hotseatItemInfos)
                .filter(Objects::nonNull)
                .toArray(ItemInfo[]::new);
        recentTasks = recentTasks.stream()
                .filter(it -> it instanceof SingleTask || it instanceof SplitTask)
                .toList();

        mNumbersOfTaskbarIconsOverflowing = Math.min(
                (hotseatItemInfos.length + recentTasks.size() + NUM_ALWAYS_VISIBLE_TASKBAR_ICONS)
                        - mMaxNumIcons, 0);

        if (mNumStaticViews == 0) {
            mNumStaticViews = addStaticViews();
        }

        // Skip static views and potential All Apps divider, if they are on the left.
        mNextViewIndex = mIsRtl ? 0 : mNumStaticViews;
        if (getChildAt(mNextViewIndex) == mTaskbarDividerContainer && !mAddedDividerForRecents) {
            mNextViewIndex++;
        }

        mIgnoreTaskbarIconCount = getIgnoreCountForTaskbarIcons(recentTasks.size(),
                hotseatItemInfos.length);

        // If pinned apps overflows, the maximum length of hotseat is still the same where the
        // last item is replaced by the overflow icon.
        final int hotseatItemLength = TaskbarPopupController.canPinAppsOverflow() ? Math.min(
                hotseatItemInfos.length,
                mActivityContext.getTaskbarSpecsEvaluator().getNumShownHotseatIcons())
                : hotseatItemInfos.length;

        // Update left section.
        if (mIsRtl) {
            updateHandoffSuggestions(handoffSuggestions);
            updateRecents(recentTasks.reversed(), hotseatItemLength);
        } else if (mHotseatIconsContainer == null) {
            updateHotseatItems(hotseatItemInfos, forceUpdateHotseat);
        } else {
            // TODO(b/315355128) : remove the logic for ignore icon when container overflow is
            //  enabled in future.
            mHotseatIconsContainer.updateIcons(
                    getItemInfoListForPinnedIconsContainer(Arrays.asList(hotseatItemInfos)),
                    forceUpdateHotseat);
        }


        // Now at theoretical position for recent apps divider.
        updateRecentsDivider(!recentTasks.isEmpty());
        if (getChildAt(mNextViewIndex) == mTaskbarDividerContainer) {
            mNextViewIndex++;
        }

        // Update right section.
        if (mIsRtl && mHotseatIconsContainer == null) {
            updateHotseatItems(hotseatItemInfos, forceUpdateHotseat);
        } else if (mIsRtl && mHotseatIconsContainer != null) {
            // TODO(b/315355128) : remove the logic for ignore icon when container overflow is
            //  enabled in future.
            mHotseatIconsContainer.updateIcons(
                    getItemInfoListForPinnedIconsContainer(Arrays.asList(hotseatItemInfos)),
                    forceUpdateHotseat);
        } else {
            updateRecents(recentTasks, hotseatItemLength);
            updateHandoffSuggestions(handoffSuggestions);
        }

        // Recents divider always takes priority.
        if (!mAddedDividerForRecents) {
            updateAllAppsDivider();
        }

        mAllAppsButtonContainer.updateTaskbarMinimalState(isTaskbarInMinimalState());
        traceEnd(TRACE_TAG_APP);
    }

    private List<ItemInfo> getItemInfoListForPinnedIconsContainer(List<ItemInfo> itemInfos) {
        // Mainly done for testing
        if (TaskbarPopupController.canPinAppsOverflow()) {
            return itemInfos;
        }
        return itemInfos.subList(0, itemInfos.size() - mIgnoreTaskbarIconCount);
    }

    public int getNumbersOfTaskbarIconsOverflowing() {
        return mNumbersOfTaskbarIconsOverflowing;
    }

    public boolean isTaskbarInMinimalState() {
        return getIconViews().length <= 1;
    }

    private void updateRecentsDivider(boolean hasRecents) {
        if (hasRecents && !mAddedDividerForRecents) {
            mAddedDividerForRecents = true;

            // Remove possible All Apps divider.
            if (getChildAt(mNumStaticViews) == mTaskbarDividerContainer) {
                mNextViewIndex--; // All Apps divider on the left. Need to account for removing it.
            }
            removeView(mTaskbarDividerContainer);

            addView(mTaskbarDividerContainer, mNextViewIndex);
        } else if (!hasRecents && mAddedDividerForRecents) {
            mAddedDividerForRecents = false;
            removeViewAt(mNextViewIndex);
        }
    }

    private void updateAllAppsDivider() {
        // Index where All Apps divider would be if it is already in Taskbar.
        final int expectedAllAppsDividerIndex = getExpectedAllAppsDividerIndex();
        if (getChildAt(expectedAllAppsDividerIndex) == mTaskbarDividerContainer) {
            // Already has divider.
            boolean isOnlyAllAppsAndDividerVisible = getTotalNumberOfIcons() == 2;
            if (isOnlyAllAppsAndDividerVisible) removeView(mTaskbarDividerContainer);
            return;
        }

        boolean hasAtLeastOneIcon = mHotseatIconsContainer == null
                ? getChildCount() >= mNumStaticViews + 1
                : getChildCount() - mNumStaticViews == 0
                        && mHotseatIconsContainer.getChildCount() > 0;
        if (hasAtLeastOneIcon) {
            // Static views with at least one app icon so add divider. For RTL, add it after the
            // icon that is at the expected index.
            addView(
                    mTaskbarDividerContainer,
                    mIsRtl ? expectedAllAppsDividerIndex + 1 : expectedAllAppsDividerIndex);
        }
    }

    private int getExpectedAllAppsDividerIndex() {
        if (mHotseatIconsContainer == null) {
            return mIsRtl
                    ? getChildCount() - mNumStaticViews - 1
                    : mNumStaticViews;
        } else {
            return mIsRtl
                    ? getChildCount() - mNumStaticViews
                    : mNumStaticViews - 1; // -1 to exclude mHotseatIconsContainer
        }
    }

    /**
     * Calculate how many icon we need to not show in Taskbar that are present in hotseat.
     */
    private int getIgnoreCountForTaskbarIcons(int recentsIcons, int hotseatIcons) {
        if (TaskbarPopupController.canPinAppsOverflow()) {
            return 0;
        }

        // Add icon for all apps and divider line.
        int icons = 2;

        int effectiveRecentIconsCount = ENABLE_TASKBAR_OVERFLOW.isTrue() ? Math.min(recentsIcons, 1)
                : recentsIcons;
        return Math.min(
                // Ensure at least one hotseat icon is left.
                Math.max(hotseatIcons, 1) - 1,
                Math.max(0, icons + effectiveRecentIconsCount + hotseatIcons - mMaxNumIcons));
    }

    private void updateHotseatItems(ItemInfo[] hotseatItemInfos, boolean forceUpdate) {
        traceBegin(TRACE_TAG_APP, "TaskbarView#updateHotseatItems");
        int numViewsAnimated = 0;
        final int numMaxIcons =
                mActivityContext.getTaskbarSpecsEvaluator().getNumShownHotseatIcons();
        final int hotseatLength = hotseatItemInfos.length;
        final boolean hasOverflow =
                mTaskbarPinnedOverflowView != null && hotseatLength > numMaxIcons;

        // The starting index of the pinned items on the taskbar.
        int onTaskbarStartIdx = 0;
        // The last index of the pinned items on the taskbar. This does not include the overflow
        // icon and the items inside the overflow icon if the pinned items overflow.
        int onTaskbarEndIdx = hotseatLength;

        boolean hasHotseatContainer = mHotseatIconsContainer != null;
        mNextHotseatIndex = hasHotseatContainer
                ? 0 // Start the count at 0 because the views are in a separate container
                : mNextViewIndex;

        if (hasOverflow) {
            final int itemsNotOverflown = numMaxIcons - 1;
            onTaskbarStartIdx = mIsRtl ? hotseatLength - itemsNotOverflown : 0;
            onTaskbarEndIdx = mIsRtl ? hotseatLength : itemsNotOverflown;

            final int overflownStartIndex = mIsRtl ? 0 : onTaskbarEndIdx;
            final int overflownEndIndex = mIsRtl ? onTaskbarStartIdx : hotseatLength;
            final List<ItemInfo> overflownItems = Arrays.asList(hotseatItemInfos).subList(
                    overflownStartIndex, overflownEndIndex);
            mTaskbarPinnedOverflowView.setItems(
                    overflownItems.stream().map(
                            iteminfo -> new ItemInfoWrapper(iteminfo, mActivityContext)).toList());
            if (mIsRtl) {
                maybeAddPinOverflowView();
            }
        } else if (isOverflowViewShowing()) {
            removeView(mTaskbarPinnedOverflowView);
            mTaskbarPinnedOverflowView.clearItems();
        }

        // if there are ignore icons and make sure we are not removing more icons than we have.
        // mainly problem for tests.
        if (onTaskbarEndIdx - mIgnoreTaskbarIconCount >= 0) {
            onTaskbarEndIdx -= mIgnoreTaskbarIconCount;
        }

        for (ItemInfo hotseatItemInfo : Arrays.asList(hotseatItemInfos).subList(onTaskbarStartIdx,
                onTaskbarEndIdx)) {
            // Replace any Hotseat views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            boolean isCollection = false;
            if (hotseatItemInfo.isPredictedItem()) {
                expectedLayoutResId = R.layout.taskbar_predicted_app_icon;
            } else if (hotseatItemInfo instanceof CollectionInfo ci) {
                expectedLayoutResId = ci.itemType == ITEM_TYPE_APP_GROUP
                        ? R.layout.app_pair_icon
                        : R.layout.folder_icon;
                isCollection = true;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }

            View hotseatView = null;
            while (isNextViewInSection(ItemInfo.class)) {
                hotseatView = getChildAt(mNextViewIndex);

                // see if the view can be reused
                if ((hotseatView.getSourceLayoutResId() != expectedLayoutResId)
                        || (isCollection && (hotseatView.getTag() != hotseatItemInfo))) {
                    // Unlike for BubbleTextView, we can't reapply a new FolderInfo after inflation,
                    // so if the info changes we need to reinflate. This should only happen if a new
                    // folder is dragged to the position that another folder previously existed.
                    removeAndRecycle(hotseatView);
                    hotseatView = null;
                } else {
                    // View found
                    break;
                }
            }

            if (!forceUpdate && hotseatView != null
                    && TaskItemInfo.isSameItem(hotseatItemInfo, hotseatView.getTag())) {
                // Might have been wrapped in TaskItemInfo by recents update.
                hotseatView.setTag(hotseatItemInfo);
                mNextHotseatIndex++;
                mNextViewIndex = mNextHotseatIndex;
                continue;
            }

            if (hotseatView == null) {
                if (isCollection) {
                    CollectionInfo collectionInfo = (CollectionInfo) hotseatItemInfo;
                    switch (hotseatItemInfo.itemType) {
                        case ITEM_TYPE_FOLDER:
                            hotseatView = FolderIcon.inflateFolderAndIcon(
                                    expectedLayoutResId, mActivityContext, this,
                                    (FolderInfo) collectionInfo);
                            ((FolderIcon) hotseatView).setTextVisible(false);
                            break;
                        case ITEM_TYPE_APP_GROUP:
                            hotseatView = AppPairIcon.inflateIcon(
                                    expectedLayoutResId, mActivityContext, this,
                                    (AppPairInfo) collectionInfo, DISPLAY_TASKBAR);
                            ((AppPairIcon) hotseatView).getTitleTextView()
                                    .setContainerTextVisibility(false);
                            break;
                        default:
                            traceEnd(TRACE_TAG_APP); // updateHotseatItems
                            throw new IllegalStateException(
                                    "Unexpected item type: " + hotseatItemInfo.itemType);
                    }
                } else {
                    hotseatView = inflate(expectedLayoutResId);
                }
                LayoutParams lp = new TaskbarLayoutParams(mIconTouchSize, mIconTouchSize);
                hotseatView.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                addView(hotseatView, mNextViewIndex, lp);
            } else if (hotseatView instanceof FolderIcon fi) {
                fi.onItemsChanged(false);
                fi.getFolder().reapplyItemInfo();
            }

            if (hotseatView.getLayoutParams() instanceof TaskbarLayoutParams tlp) {
                tlp.bindInfo = new CellInfo(hotseatView,
                        hotseatItemInfo.screenId, hotseatItemInfo.container,
                        hotseatItemInfo.cellX, hotseatItemInfo.cellY,
                        hotseatItemInfo.spanX, hotseatItemInfo.spanY);
            }

            // Apply the Hotseat ItemInfos, or hide the view if there is none for a given index.
            if (hotseatView instanceof BubbleTextView btv
                    && hotseatItemInfo instanceof WorkspaceItemInfo workspaceInfo) {
                if (btv instanceof PredictedAppIcon pai) {
                    if (pai.applyFromWorkspaceItemWithAnimation(workspaceInfo, numViewsAnimated)) {
                        numViewsAnimated++;
                    }
                } else {
                    btv.applyFromWorkspaceItem(workspaceInfo);
                }
            }
            setClickAndLongClickListenersForIcon(hotseatView);
            setHoverListenerForIcon(hotseatView);

            mNextHotseatIndex++;
            mNextViewIndex = mNextHotseatIndex;
        }

        while (isNextViewInSection(ItemInfo.class)) {
            removeAndRecycle(getChildAt(mNextViewIndex));
        }

        if (hasOverflow && !mIsRtl) {
            maybeAddPinOverflowView();
        }
        traceEnd(TRACE_TAG_APP);
    }

    @VisibleForTesting
    boolean isOverflowViewShowing() {
        if (mHotseatIconsContainer != null) {
            return mHotseatIconsContainer.isOverflowViewShowing();
        }
        return indexOfChild(mTaskbarPinnedOverflowView) != -1;
    }

    private void maybeAddPinOverflowView() {
        if (!TaskbarPopupController.canPinAppsOverflow()) {
            return;
        }
        if (!isOverflowViewShowing()) {
            addView(mTaskbarPinnedOverflowView, mNextViewIndex);
        }
        // [mNextViewIndex] follows the same index as [mNextHotseatIndex] so updates both
        // pointer here.
        mNextHotseatIndex++;
        mNextViewIndex++;
    }

    private void updateRecents(List<GroupTask> recentTasks, int hotseatSize) {
        traceBegin(TRACE_TAG_APP, "TaskbarView#updateRecents");
        boolean supportsOverflow = ENABLE_TASKBAR_OVERFLOW.isTrue()
                && mActivityContext.isTaskbarShowingDesktopTasks()
                && recentTasks.size() > 1;
        int overflowSize = 0;
        boolean hasOverflow = false;
        int indexOfIconInOverfow = 0;
        if (supportsOverflow && mTaskbarRecentsOverflowView != null) {
            // Need to account for All Apps and the divider. If we need to have an overflow, we will
            // have a divider for recents.
            final int nonTaskIconsToBeAdded = 2;
            mIdealNumIcons = hotseatSize + recentTasks.size() + nonTaskIconsToBeAdded;
            overflowSize = mIdealNumIcons - mMaxNumIcons;
            hasOverflow = overflowSize > 0;

            // RTL case is handled after we add the recent icons, because the button needs to
            // then be to the right of them.
            if (hasOverflow && !mIsRtl) {
                if (mPrevOverflowTasks.isEmpty()) {
                    // If the icon moving to overflow icon is the first one within the icon, it
                    // should be targeting index 1 instead of index 0.
                    // Same logic applies to the last icon moving out of the overflow icon.
                    indexOfIconInOverfow = 1;
                    addView(mTaskbarRecentsOverflowView, mNextViewIndex);
                }
                // NOTE: If overflow already existed, assume the overflow view is already
                // at the correct position.
                mNextViewIndex++;
            } else if (!hasOverflow && !mPrevOverflowTasks.isEmpty()) {
                removeView(mTaskbarRecentsOverflowView);
                indexOfIconInOverfow = 1;
                mTaskbarRecentsOverflowView.clearItems();
            }
        } else if (mTaskbarRecentsOverflowView != null && !mPrevOverflowTasks.isEmpty()) {
            // Handle the case when closing all the windows together such as "clear all"
            // from overview.
            removeView(mTaskbarRecentsOverflowView);
            mTaskbarRecentsOverflowView.clearItems();
        }

        // An extra item needs to be added to overflow button to account for the space taken up by
        // the overflow button.
        final int itemsToAddToOverflow =
                hasOverflow ? Math.min(overflowSize + 1, recentTasks.size()) : 0;
        final Set<GroupTask> overflownRecentsSet;
        if (hasOverflow && mTaskbarRecentsOverflowView != null) {
            final int startIndex = mIsRtl ? recentTasks.size() - itemsToAddToOverflow : 0;
            final int endIndex = mIsRtl ? recentTasks.size() : itemsToAddToOverflow;
            List<GroupTask> overflownRecents = new ArrayList<>(
                    recentTasks.subList(startIndex, endIndex));
            if (mIsRtl) Collections.reverse(overflownRecents);
            mTaskbarRecentsOverflowView.setItems(
                    overflownRecents.stream().map(
                            t -> new TaskWrapper(mActivityContext, ((SingleTask) t))).toList());
            overflownRecentsSet = new ArraySet<>(overflownRecents);
        } else {
            overflownRecentsSet = Collections.emptySet();
        }

        // Add Recent/Running icons.
        final Set<GroupTask> recentTasksSet = new ArraySet<>(recentTasks);
        final int startIndex = mIsRtl ? 0 : itemsToAddToOverflow;
        final int endIndex =
                mIsRtl ? recentTasks.size() - itemsToAddToOverflow : recentTasks.size();
        for (GroupTask task : recentTasks.subList(startIndex, endIndex)) {
            traceBegin(TRACE_TAG_APP, "TaskbarView#updateRecents.task");
            // Replace any Recent views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            boolean isCollection = false;
            if (!(task instanceof SingleTask)) {
                if (task.taskViewType == TaskViewType.DESKTOP) {
                    expectedLayoutResId = -1;
                } else {
                    expectedLayoutResId = R.layout.app_pair_icon;
                }
                isCollection = true;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }

            View recentIcon = null;
            // If a task is new, we should try to reuse a view so that it animates in when it is
            // added. This only works for LTR more right now, so we do not reuse views of previous
            // tasks for new icons in RTL mode.
            final boolean canReuseView = (!mIsRtl || mPrevRecentTasks.contains(task))
                    && !mPrevOverflowTasks.contains(task);
            while (canReuseView && isNextViewInSection(GroupTask.class)) {
                recentIcon = getChildAt(mNextViewIndex);
                GroupTask tag = (GroupTask) recentIcon.getTag();

                // see if the view can be reused
                if (recentIcon.getSourceLayoutResId() != expectedLayoutResId
                        || (isCollection && tag != task && !(tag instanceof SplitTask))
                        // Remove view corresponding to removed task so that it animates out.
                        || !recentTasksSet.contains(tag)
                        || overflownRecentsSet.contains(tag)) {
                    if (overflownRecentsSet.contains(tag)) {
                        animateToOverflowOnOverlay(recentIcon, indexOfIconInOverfow);
                        indexOfIconInOverfow = 0;
                    }
                    removeAndRecycle(recentIcon);
                    recentIcon = null;
                } else {
                    // View found
                    break;
                }
            }

            if (recentIcon != null && task.equals(recentIcon.getTag())) {
                recentIcon.setTag(task); // Reference may have changed.
                mNextViewIndex++;
                traceEnd(TRACE_TAG_APP); // updateRecents.task
                continue;
            }

            boolean isFromOverflow = false;
            if (recentIcon == null) {
                if (task instanceof SingleTask) {
                    recentIcon = inflate(expectedLayoutResId);
                } else if (task instanceof SplitTask st) {
                    recentIcon = AppPairIcon.inflateIcon(expectedLayoutResId, mActivityContext,
                            this, st.toAppPairInfo(), DISPLAY_TASKBAR);
                    ((AppPairIcon) recentIcon).getTitleTextView().setContainerTextVisibility(false);
                    recentIcon.setTag(task);
                }
                LayoutParams lp = new TaskbarLayoutParams(mIconTouchSize, mIconTouchSize);
                recentIcon.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                addView(recentIcon, mNextViewIndex, lp);
                if (mPrevOverflowTasks.contains(task)) {
                    isFromOverflow = true;
                }
            } else if (recentIcon instanceof AppPairIcon api && task instanceof SplitTask st) {
                api.updateInfo(st.toAppPairInfo());
            }

            if (recentIcon instanceof BubbleTextView btv) {
                if (isFromOverflow) {
                    animateFromOverflowOnOverlay(
                            recentIcon, (SingleTask) task, indexOfIconInOverfow);
                    indexOfIconInOverfow = 0;
                }
                applyGroupTaskToBubbleTextView(btv, task);
            }
            setClickAndLongClickListenersForIcon(recentIcon);
            setHoverListenerForIcon(recentIcon);
            mNextViewIndex++;
            traceEnd(TRACE_TAG_APP);
        }

        while (isNextViewInSection(GroupTask.class)) {
            View recentIconToRemove = getChildAt(mNextViewIndex);
            GroupTask taskTag = (GroupTask) recentIconToRemove.getTag();
            if (mIsRtl && overflownRecentsSet.contains(taskTag)) {
                animateToOverflowOnOverlay(recentIconToRemove, indexOfIconInOverfow);
                indexOfIconInOverfow = mPrevOverflowTasks.isEmpty() ? 1 : 0;
            }
            removeAndRecycle(recentIconToRemove);
        }

        if (mIsRtl && hasOverflow) {
            if (mPrevOverflowTasks.isEmpty()) {
                addView(mTaskbarRecentsOverflowView, mNextViewIndex);
            }
            mNextViewIndex++;
        }

        mPrevRecentTasks = recentTasksSet;
        mPrevOverflowTasks = overflownRecentsSet;
        traceEnd(TRACE_TAG_APP);
    }

    private void updateHandoffSuggestions(List<HandoffSuggestion> handoffSuggestions) {
        Set<HandoffSuggestion> tasksToAdd = new HashSet<>(handoffSuggestions);
        while (isNextViewInSection(HandoffSuggestion.class)) {
            View view = getChildAt(mNextViewIndex);
            if (tasksToAdd.contains(view.getTag())) {
                tasksToAdd.remove(view.getTag());
                mNextViewIndex++;
            } else {
                removeAndRecycle(getChildAt(mNextViewIndex));
            }
        }

        for (HandoffSuggestion handoffSuggestion : tasksToAdd) {
            View recentIcon = inflate(R.layout.taskbar_app_icon);
            LayoutParams lp = new TaskbarLayoutParams(mIconTouchSize, mIconTouchSize);
            recentIcon.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
            addView(recentIcon, mNextViewIndex++, lp);
            applyHandoffSuggestionToBubbleTextView((BubbleTextView) recentIcon, handoffSuggestion);
            setClickAndLongClickListenersForIcon(recentIcon);
        }
    }

    public void applyHandoffSuggestionToBubbleTextView(
        BubbleTextView bubbleTextView,
        HandoffSuggestion handoffSuggestion) {

        ItemInfoWithIcon itemInfoWithIcon = handoffSuggestion.getItemInfoWithIcon();
        if (itemInfoWithIcon != null) {
            bubbleTextView.applyFromItemInfoWithIcon(itemInfoWithIcon);
        }

        bubbleTextView.setTag(handoffSuggestion);
    }

    private void animateToOverflowOnOverlay(View icon, int indexOfIconInOverfow) {
        if (mTaskbarRecentsOverflowView == null) {
            removeAndRecycle(icon);
            return;
        }

        if (mOngoingRecentIconAnimation != null && mOngoingRecentIconAnimation.isRunning()) {
            mOngoingRecentIconAnimation.end();
        }

        BubbleTextView ghostIcon = createGhostIcon(
                (GroupTask) icon.getTag(), icon.getX(), icon.getY(), 1.0f);

        // Add a PreDrawListener to the target view to ensure the animation will not run
        // until the overflow view layout is ready.
        mTaskbarRecentsOverflowView.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        mTaskbarRecentsOverflowView.getViewTreeObserver()
                                .removeOnPreDrawListener(this);
                        mTaskbarRecentsOverflowView.setFirstItemHiddenForAnimation(true);

                        float endX = mTaskbarRecentsOverflowView.getX();
                        float endY = mTaskbarRecentsOverflowView.getY();
                        PointF overlayOffsets =
                                mTaskbarRecentsOverflowView.getOverlayOffsetsForFirstItem(
                                        /* isMovingAway= */ false, indexOfIconInOverfow);
                        Animator scaleAnim = ObjectAnimator.ofFloat(
                                ghostIcon, getScaleProperty(), 1f,
                                TaskbarOverflowView.TWO_ITEM_ICONS_BOX_ASPECT_RATIO);
                        Runnable onEnd = () ->
                                mTaskbarRecentsOverflowView.setFirstItemHiddenForAnimation(false);

                        startGhostIconAnimation(
                                ghostIcon, endX + overlayOffsets.x, endY + overlayOffsets.y,
                                scaleAnim, onEnd);

                        return true;
                    }
                });
    }

    @VisibleForTesting
    boolean isRecentsOverflowViewFirstItemHiddenForAnimation() {
        return mTaskbarRecentsOverflowView != null
                && mTaskbarRecentsOverflowView.isFirstItemHiddenForAnimation();
    }

    private void animateFromOverflowOnOverlay(View actualIcon, SingleTask task,
            int indexOfIconInOverfow) {
        if (mTaskbarRecentsOverflowView == null) {
            return;
        }

        if (mOngoingRecentIconAnimation != null && mOngoingRecentIconAnimation.isRunning()) {
            mOngoingRecentIconAnimation.end();
        }

        // Add the icon back to Taskbar, but make it invisible while the overlay animates.
        actualIcon.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        actualIcon.getViewTreeObserver().removeOnPreDrawListener(this);
                        actualIcon.setAlpha(0f);

                        PointF overlayOffsets =
                                mTaskbarRecentsOverflowView.getOverlayOffsetsForFirstItem(
                                        /* isMovingAway= */ true, indexOfIconInOverfow);
                        float startX = mTaskbarRecentsOverflowView.getX() + overlayOffsets.x;
                        float startY = mTaskbarRecentsOverflowView.getY() + overlayOffsets.y;

                        BubbleTextView ghostIcon = createGhostIcon(
                                task, startX, startY,
                                TaskbarOverflowView.TWO_ITEM_ICONS_BOX_ASPECT_RATIO);

                        // Animate the overlay icon to the final position.
                        Animator scaleAnim = ObjectAnimator.ofFloat(ghostIcon, getScaleProperty(),
                                TaskbarOverflowView.TWO_ITEM_ICONS_BOX_ASPECT_RATIO, 1f);
                        Runnable onEnd = () -> actualIcon.setAlpha(1f);
                        startGhostIconAnimation(ghostIcon, actualIcon.getX(), actualIcon.getY(),
                                scaleAnim, onEnd);
                        return true;
                    }
                });
    }

    private BubbleTextView createGhostIcon(GroupTask task, float x, float y, float scale) {
        BubbleTextView ghostIcon = (BubbleTextView) inflate(R.layout.taskbar_app_icon);
        ghostIcon.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
        applyGroupTaskToBubbleTextView(ghostIcon, task);
        ghostIcon.measure(
                MeasureSpec.makeMeasureSpec(mIconTouchSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mIconTouchSize, MeasureSpec.EXACTLY));
        ghostIcon.layout(0, 0, mIconTouchSize, mIconTouchSize);
        ghostIcon.setX(x);
        ghostIcon.setY(y);
        ghostIcon.setScaleX(scale);
        ghostIcon.setScaleY(scale);

        getOverlay().add(ghostIcon);
        return ghostIcon;
    }

    private void startGhostIconAnimation(BubbleTextView ghostIcon, float endX, float endY,
            Animator scaleAnimator, Runnable onEndAction) {
        AnimatorSet anim = new AnimatorSet();
        anim.playTogether(
                ObjectAnimator.ofFloat(ghostIcon, View.X, endX),
                ObjectAnimator.ofFloat(ghostIcon, View.Y, endY),
                scaleAnimator);

        anim.setDuration(TaskbarOverflowView.ITEM_ICON_SIZE_ANIMATION_DURATION);
        anim.setInterpolator(Interpolators.EMPHASIZED);

        anim.addListener(AnimatorListeners.forEndCallback(() -> {
            getOverlay().remove(ghostIcon);
            if (onEndAction != null) {
                onEndAction.run();
            }
            mOngoingRecentIconAnimation = null;
        }));

        mOngoingRecentIconAnimation = anim;
        anim.start();
    }

    private boolean isNextViewInSection(Class<?> tagClass) {
        return mNextViewIndex < getChildCount()
                && tagClass.isInstance(getChildAt(mNextViewIndex).getTag());
    }

    protected View mapOverItems(ViewGroup parent, @NonNull ItemOperator op) {
        final int itemCount = parent.getChildCount();
        for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
            View item = parent.getChildAt(itemIdx);
            if (item instanceof TaskbarPinnedAppIconContainer tic) {
                mapOverItems(tic, op);
            }
            if (item.getTag() instanceof ItemInfo itemInfo && op.evaluate(itemInfo, item)) {
                return item;
            }
        }
        return null;
    }

    /** Binds the SingleTask to the BubbleTextView to be ready to present to the user. */
    public void applyGroupTaskToBubbleTextView(BubbleTextView btv, GroupTask groupTask) {
        if (!(groupTask instanceof SingleTask singleTask)) {
            return;
        }
        traceBegin(TRACE_TAG_APP, "TaskbarView#applyGroupTaskToBubbleTextView");

        Task task = singleTask.getTask();
        // TODO(b/344038728): use FastBitmapDrawable instead of Drawable, to get disabled state
        //  while dragging.
        BitmapInfo bitmapInfo = groupTask.getBitmapInfos().get(0);
        ThemeManager themeManager = ThemeManager.INSTANCE.get(mActivityContext);
        @DrawableCreationFlags int creationFlags =
                themeManager.isIconThemeEnabled() ? FLAG_THEMED : 0;
        @Nullable IconShape iconShape =
                enableLauncherIconShapes() ? themeManager.getIconShapeData().getValue() : null;
        final Drawable taskIcon = Optional.ofNullable(bitmapInfo)
                .map(bi -> bi.newIcon(mActivityContext, creationFlags, iconShape))
                .orElse(null);

        btv.applyIconAndLabel(taskIcon, task.title, task.titleDescription);
        btv.setTag(singleTask);
        traceEnd(TRACE_TAG_APP);
    }

    /**
     * Sets OnClickListener and OnLongClickListener for the given view.
     */
    public void setClickAndLongClickListenersForIcon(View icon) {
        icon.setOnClickListener(mIconClickListener);
        icon.setOnLongClickListener(mIconLongClickListener);
        if (enableCursorDrivenWorkflows()) {
            if (icon instanceof CustomTouchDelegate customTouchDelegate) {
                customTouchDelegate.setCustomActionsListener(
                        mControllerCallbacks.getIconCustomActionsListener());
            }
        } else {
            // Add right-click support to btv icons.
            icon.setOnTouchListener((v, event) -> {
                if (event.isFromSource(InputDevice.SOURCE_MOUSE)
                        && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                        && (v instanceof BubbleTextView || v instanceof FolderIcon)) {
                    mActivityContext.showPopupMenuForIcon(v);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Sets OnHoverListener for the given view.
     */
    private void setHoverListenerForIcon(View icon) {
        if (Boolean.TRUE.equals(icon.getTag(R.id.taskbar_icon_has_hover_listener))) {
            // Creating hover listener is expensive due to view inflation, so reuse if possible.
            return;
        }
        icon.setOnHoverListener(mControllerCallbacks.getIconOnHoverListener(icon));
        icon.setTag(R.id.taskbar_icon_has_hover_listener, true);
    }

    /** Updates taskbar icons accordingly to the new bubble bar location. */
    public void onBubbleBarLocationUpdated(BubbleBarLocation location) {
        if (mBubbleBarLocation == location) return;
        mBubbleBarLocation = location;
        requestLayout();
    }

    /**
     * Returns translation X for the taskbar icons for provided {@link BubbleBarLocation}. If the
     * bubble bar is not enabled, or location of the bubble bar is the same, or taskbar is not start
     * aligned - returns 0.
     */
    public float getTranslationXForBubbleBarPosition(BubbleBarLocation location) {
        if (!mControllerCallbacks.isBubbleBarEnabled()
                || location == mBubbleBarLocation
                || !mActivityContext.shouldStartAlignTaskbar()
        ) {
            return 0;
        }
        Rect iconsBounds = getTransientTaskbarIconLayoutBoundsInParent();

        return getTaskBarIconsEndForBubbleBarLocation(location) - iconsBounds.right;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int spaceNeeded = getIconLayoutWidth();
        boolean layoutRtl = isLayoutRtl();
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int navSpaceNeeded = deviceProfile.getHotseatProfile().getBarEndOffset();
        int centerAlignIconEnd = (right + left + spaceNeeded) / 2;
        int iconEnd = centerAlignIconEnd;
        if (mShouldTryStartAlign) {
            int startSpacingPx =
                    deviceProfile.getHotseatProfile().getInlineNavButtonsEndSpacingPx();
            if (mControllerCallbacks.isBubbleBarEnabled()
                    && mBubbleBarLocation != null
                    && mActivityContext.shouldStartAlignTaskbar()) {
                iconEnd = (int) getTaskBarIconsEndForBubbleBarLocation(mBubbleBarLocation);
            } else {
                if (layoutRtl) {
                    iconEnd = right - startSpacingPx;
                } else {
                    iconEnd = startSpacingPx + spaceNeeded;
                }
                boolean needMoreSpaceForNav = layoutRtl
                        ? navSpaceNeeded > (iconEnd - spaceNeeded)
                        : iconEnd > (right - navSpaceNeeded);
                if (needMoreSpaceForNav) {
                    // Add offset to account for nav bar when taskbar is centered
                    int offset = layoutRtl
                            ? navSpaceNeeded - (centerAlignIconEnd - spaceNeeded)
                            : (right - navSpaceNeeded) - centerAlignIconEnd;
                    iconEnd = centerAlignIconEnd + offset;
                }
            }
        }

        // Currently, we support only one device with display cutout and we only are concern about
        // it when the bottom rect is present and non empty
        DisplayCutout displayCutout = getDisplay().getCutout();
        if (displayCutout != null && !displayCutout.getBoundingRectBottom().isEmpty()) {
            Rect cutoutBottomRect = displayCutout.getBoundingRectBottom();
            // when cutout present at the bottom of screen align taskbar icons to cutout offset
            // if taskbar icon overlaps with cutout
            int taskbarIconLeftBound = iconEnd - spaceNeeded;
            int taskbarIconRightBound = iconEnd;

            boolean doesTaskbarIconsOverlapWithCutout =
                    taskbarIconLeftBound <= cutoutBottomRect.centerX()
                            && cutoutBottomRect.centerX() <= taskbarIconRightBound;

            if (doesTaskbarIconsOverlapWithCutout) {
                if (!layoutRtl) {
                    iconEnd = spaceNeeded + cutoutBottomRect.width();
                } else {
                    iconEnd = right - cutoutBottomRect.width();
                }
            }
        }

        sTmpRect.set(mIconLayoutBounds);

        // Layout the children
        mIconLayoutBounds.right = iconEnd;
        mIconLayoutBounds.top = (bottom - top - mIconTouchSize) / 2;
        mIconLayoutBounds.bottom = mIconLayoutBounds.top + mIconTouchSize;

        // With rtl layout, the all apps button will be translated by `allAppsButtonOffset` after
        // layout completion (by `TaskbarViewController`). Offset the icon end by the same amount
        // when laying out icons, so the taskbar content remains centered after all apps button
        // translation.
        if (layoutRtl) {
            iconEnd += mAllAppsButtonTranslationOffset;
        }

        mControllerCallbacks.onPreLayoutChildren();

        int count = getChildCount();
        for (int i = count; i > 0; i--) {
            View child = getChildAt(i - 1);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            if (child == mQsb) {
                int qsbStart;
                int qsbEnd;
                if (layoutRtl) {
                    qsbStart = iconEnd + mItemMarginLeftRight;
                    qsbEnd = qsbStart + deviceProfile.getHotseatProfile().getQsbWidth();
                } else {
                    qsbEnd = iconEnd - mItemMarginLeftRight;
                    qsbStart = qsbEnd - deviceProfile.getHotseatProfile().getQsbWidth();
                }
                int qsbTop = (bottom - top - deviceProfile.getHotseatProfile().getQsbHeight()) / 2;
                int qsbBottom = qsbTop + deviceProfile.getHotseatProfile().getQsbHeight();
                child.layout(qsbStart, qsbTop, qsbEnd, qsbBottom);
            } else if (child == mAllAppsButtonContainer) {
                iconEnd -= mItemMarginLeftRight;
                int iconStart = iconEnd - mAllAppsButtonContainer.getSpaceNeeded();
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart - mItemMarginLeftRight;
            } else if (child == mTaskbarDividerContainer) {
                iconEnd += mItemMarginLeftRight;
                int iconStart = iconEnd - mTaskbarDividerContainer.getSpaceNeeded();
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart + mItemMarginLeftRight;
            } else if (child instanceof TaskbarPinnedAppIconContainer tic) {
                iconEnd -= mItemMarginLeftRight;
                int iconStart = iconEnd - tic.getSpaceNeeded();
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart - mItemMarginLeftRight;
            } else {
                iconEnd -= mItemMarginLeftRight;
                int iconStart = iconEnd - mIconTouchSize;
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart - mItemMarginLeftRight;
            }
        }

        mIconLayoutBounds.left = iconEnd;

        // Adjust the icon layout bounds by the amount by which all apps button will be translated
        // post layout to maintain margin between all apps button and the edge of the transient
        // taskbar background. Done for ltr layout only - for rtl layout, the offset needs to be
        // adjusted on the right, which is done by offsetting `iconEnd` after setting
        // `mIconLayoutBounds.right`.
        if (!layoutRtl) {
            mIconLayoutBounds.left += mAllAppsButtonTranslationOffset;
        }

        if (mIconLayoutBounds.right - mIconLayoutBounds.left < mTransientTaskbarMinWidth) {
            int center = mIconLayoutBounds.centerX();
            int distanceFromCenter = (int) mTransientTaskbarMinWidth / 2;
            mIconLayoutBounds.right = center + distanceFromCenter;
            mIconLayoutBounds.left = center - distanceFromCenter;
        }

        if (!sTmpRect.equals(mIconLayoutBounds)) {
            mControllerCallbacks.notifyIconLayoutBoundsChanged();
        }
    }

    /**
     * Returns whether the given MotionEvent, *in screen coordinates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        int xInOurCoordinates = (int) ev.getRawX();
        int yInOurCoordinates = (int) ev.getRawY();
        return isShown() && getTaskbarIconsActualBounds().contains(xInOurCoordinates,
                yInOurCoordinates);
    }

    /**
     * Returns the current visual taskbar icons bounds (unlike `mIconLayoutBounds` which contains
     * bounds for transient mode only).
     */
    Rect getTaskbarIconsActualBounds() {
        View[] iconViews = getIconViews();
        if (iconViews.length == 0) {
            return new Rect();
        }
        iconViews[0].getLocationOnScreen(mFirstIconViewLocation);
        iconViews[iconViews.length - 1].getLocationOnScreen(mLastIconViewLocation);

        return new Rect(
                mFirstIconViewLocation[0],
                mFirstIconViewLocation[1],
                mLastIconViewLocation[0] + mIconTouchSize,
                mLastIconViewLocation[1] + mIconTouchSize);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        mTaskbarUiState.setIsTaskbarViewShown(isShown());
    }

    /**
     * Gets visual bounds of the taskbar view. The visual bounds correspond to the taskbar touch
     * area, rather than layout placement in the parent view.
     */
    public Rect getTransientTaskbarIconLayoutBounds() {
        return new Rect(mIconLayoutBounds);
    }

    /** Gets taskbar layout bounds in parent view. */
    public Rect getTransientTaskbarIconLayoutBoundsInParent() {
        Rect actualBounds = new Rect(mIconLayoutBounds);
        actualBounds.top = getTop();
        actualBounds.bottom = getBottom();
        return actualBounds;
    }

    /** Returns the total number of icons in the taskbar. **/
    public int getTotalNumberOfIcons() {
        int numContainers = 0;
        int numIconsInContainers = 0;
        for (int i = getChildCount() - 1; i >= 0; --i) {
            if (getChildAt(i) instanceof TaskbarPinnedAppIconContainer tic) {
                numContainers++;
                numIconsInContainers += tic.getChildCount();
            }
        }

        int count = getChildCount()
                - numContainers
                + numIconsInContainers;
        if (mActivityContext.getDeviceProfile().getHotseatProfile().isQsbInline()) {
            count--; // Exclude QSB
        }
        // count can be negative if views aren't added
        return Math.max(0, count);
    }
    /**
     * Returns the space used by the icons.
     */
    private int getIconLayoutWidth() {
        return getIconLayoutWidth(getNumberOfVisibleIcons());
    }

    /**
     * Return the space needed based on the number of taskbar icons supplied vs existing children.
     */
    private int getIconLayoutWidth(int expectedNumberOfTaskbarIcons) {
        int iconLayoutBoundsWidth =
                expectedNumberOfTaskbarIcons * (mItemMarginLeftRight * 2 + mIconTouchSize);

        if (expectedNumberOfTaskbarIcons > 1) {
            // We are removing 4 * mItemMarginLeftRight as there should be no space between
            // All Apps icon, divider icon, and first app icon in taskbar
            iconLayoutBoundsWidth -= mItemMarginLeftRight * 4;
        }

        // The all apps button container gets offset horizontally, reducing the overall taskbar
        // view size.
        iconLayoutBoundsWidth -= mAllAppsButtonTranslationOffset;

        return iconLayoutBoundsWidth;
    }

    /** Returns the number of visible icons in the taskbar. **/
    private int getNumberOfVisibleIcons() {
        // Subtract the number of icons by 1 if a child is hidden due to dragging process.
        // If mHotseatIconsContainer is not null, need to subtract the number of invisible icons
        // inside mHotseatIconsContainer.
        int count = getTotalNumberOfIcons()
                - (mDragDelegate.hasHiddenChild() ? 1 : 0)
                - ((mHotseatIconsContainer != null)
                        ? mHotseatIconsContainer.getChildCount()
                                - mHotseatIconsContainer.getVisibleChildCount()
                        : 0);
        // count can be negative if views aren't added
        return Math.max(0, count);
    }

    /**
     * Returns the app icons currently shown in the taskbar. The returned list does not include qsb,
     * but it includes all apps button and icon divider views.
     */
    public View[] getIconViews() {
        final int count = getChildCount();
        final int totalCount = getTotalNumberOfIcons();
        if (totalCount == 0) {
            return new View[0];
        }
        View[] icons = new View[totalCount];
        int insertionPoint = 0;
        for (int i = 0; i < count; i++) {
            if (getChildAt(i) == mQsb) continue;
            if (getChildAt(i) instanceof TaskbarPinnedAppIconContainer tic) {
                int ticCount = tic.getChildCount();
                if (mIsRtl) {
                    for (int j = ticCount - 1; j >= 0; j--) {
                        icons[insertionPoint++] = tic.getChildAt(j);
                    }
                } else {
                    for (int j = 0; j < ticCount; j++) {
                        icons[insertionPoint++] = tic.getChildAt(j);
                    }
                }
                continue;
            }
            icons[insertionPoint++] = getChildAt(i);
        }
        return icons;
    }

    protected int getNumOfVisibleIconsInPinnedSection() {
        ViewGroup parent = this;
        if (mHotseatIconsContainer != null) {
            parent = mHotseatIconsContainer;
        }
        final int totalChild = parent.getChildCount();
        int count = 0;
        for (int i = 0; i < totalChild; i++) {
            View icon = parent.getChildAt(i);
            if (icon.getVisibility() == View.GONE) {
                continue;
            }
            if (icon instanceof TaskbarDropTargetGhostView || icon.getTag() instanceof ItemInfo
                    || icon instanceof TaskbarOverflowView) {
                count++;
            }
        }
        return count;
    }

    /**
     * The max number of icon views the taskbar can have when taskbar overflow is enabled.
     */
    int getMaxNumIconViews() {
        return mMaxNumIcons;
    }

    void limitMaxNumIconViewsForTest(int maxNumIconLimit) {
        mMaxNumIconsLimitForTest = maxNumIconLimit;
    }

    /**
     * Returns the all apps button in the taskbar.
     */
    public TaskbarAllAppsButtonContainer getAllAppsButtonContainer() {
        return mAllAppsButtonContainer;
    }

    /**
     * Returns the taskbar divider in the taskbar.
     */
    @Nullable
    public TaskbarDividerContainer getTaskbarDividerViewContainer() {
        return mTaskbarDividerContainer;
    }

    /**
     * Returns the taskbar recent tasks overflow view in the taskbar.
     */
    @Nullable
    public TaskbarOverflowView getTaskbarRecentsOverflowView() {
        return mTaskbarRecentsOverflowView;
    }

    /**
     * Returns the taskbar overflow view for pinned apps in the taskbar.
     */
    @Nullable
    public TaskbarOverflowView getTaskbarPinnedOverflowView() {
        if (mHotseatIconsContainer != null) {
            return mHotseatIconsContainer.getOverflowView();
        }
        return mTaskbarPinnedOverflowView;
    }

    /**
     * Returns the taskbar overflow view for pinned apps in the taskbar.
     */
    @Nullable
    public TaskbarPinnedAppIconContainer getTaskbarHotseatIconsContainer() {
        return mHotseatIconsContainer;
    }

    /**
     * Returns whether the divider is between Hotseat icons and Recents,
     * instead of between All Apps button and Hotseat.
     */
    public boolean isDividerForRecents() {
        return mAddedDividerForRecents;
    }

    /**
     * Returns the QSB in the taskbar.
     */
    public View getQsb() {
        return mQsb;
    }

    // FolderIconParent implemented methods.

    @Override
    public void drawFolderLeaveBehindForIcon(FolderIcon child) {
        mLeaveBehindFolderIcon = child;
        invalidate();
    }

    @Override
    public void clearFolderLeaveBehind(FolderIcon child) {
        mLeaveBehindFolderIcon = null;
        invalidate();
    }

    // End FolderIconParent implemented methods.

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLeaveBehindFolderIcon != null) {
            canvas.save();
            canvas.translate(
                    mLeaveBehindFolderIcon.getLeft() + mLeaveBehindFolderIcon.getTranslationX(),
                    mLeaveBehindFolderIcon.getTop());
            PreviewBackground previewBackground = mLeaveBehindFolderIcon.getFolderBackground();
            previewBackground.drawLeaveBehind(canvas, mFolderLeaveBehindColor);
            canvas.restore();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mActivityContext.isDestroyed()) return;
        super.dispatchDraw(canvas);
    }

    private View inflate(@LayoutRes int layoutResId) {
        return mActivityContext.getViewCache().getView(layoutResId, mActivityContext, this);
    }

    @Override
    public void setInsets(Rect insets) {
        // Ignore, we just implement Insettable to draw behind system insets.
    }

    public boolean areIconsVisible() {
        // Consider the overall visibility
        return getVisibility() == VISIBLE;
    }

    /**
     * @return The all apps button horizontal offset used to calculate the taskbar contents width
     * during layout.
     */
    public int getAllAppsButtonTranslationXOffsetUsedForLayout() {
        return mAllAppsButtonTranslationOffset;
    }

    /**
     * This method only works for bubble bar enabled in persistent task bar and the taskbar is start
     * aligned.
     */
    private float getTaskBarIconsEndForBubbleBarLocation(BubbleBarLocation location) {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        boolean navbarOnRight = location.isOnLeft(isLayoutRtl());
        int navSpaceNeeded = deviceProfile.getHotseatProfile().getBarEndOffset();
        if (navbarOnRight) {
            return getWidth() - navSpaceNeeded;
        } else {
            return navSpaceNeeded + getIconLayoutWidth();
        }
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        return new TaskbarLayoutParams(lp);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new TaskbarLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof TaskbarLayoutParams;
    }

    @Override
    public void reserveDropSlotForDragLocation(int x) {
        mDragDelegate.reserveDropSlotForDragLocation(x);
    }

    @Override
    public boolean updateItemViewVisibilityForDragState(View itemView, boolean isDragged) {
        if (mHotseatIconsContainer != null) {
            return mHotseatIconsContainer.updateItemViewVisibilityForDragState(itemView, isDragged);
        }
        boolean needsToRearrangeItems = !isDragged && mDragDelegate.hasHiddenChild();
        if (mDragDelegate.updateItemViewVisibilityForDragState(itemView, isDragged)) {
            if (needsToRearrangeItems) {
                rearrangeItemsForDrag();
            }
            return true;
        }

        return false;
    }

    @Override
    public void releaseDropSlot() {
        if (mHotseatIconsContainer != null) {
            mHotseatIconsContainer.releaseDropSlot();
            mDragDelegate.onDragStateChanged();
            return;
        }
        mDragDelegate.releaseDropSlot();
    }

    @Override
    public void removeDraggedView() {
        if (mHotseatIconsContainer != null) {
            mHotseatIconsContainer.removeDraggedView();
            return;
        }

        mDragDelegate.removeDraggedView();
    }

    @Override
    public int getPinIndex(int startingIndex) {
        // RTL in HotseatIconsContainer has different logic so the index starts from right to left.
        if (mIsRtl && mHotseatIconsContainer != null
                && mDragDelegate.getPinIndex(startingIndex) != -1) {
            return mHotseatIconsContainer.getVisibleChildCount()
                    - mDragDelegate.getPinIndex(startingIndex) - 1;
        }

        return mDragDelegate.getPinIndex(startingIndex);
    }

    @Override
    public void getHitRectForPinRelativeToDragLayer(Rect outRect) {
        // If mHotseatIconsContainer is set, use its bounds directly.
        if (mHotseatIconsContainer != null) {
            mActivityContext.getDragLayer().getDescendantRectRelativeToSelf(
                    mHotseatIconsContainer, outRect);
            outRect.inset(-mPinnedHitRectBuffer, 0);
            return;
        }

        // Otherwise use the area between all apps button and divider.
        mActivityContext.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
        int taskbarLeftInDragLayer = outRect.left;
        View[] iconViews = getIconViews();

        if (mIsRtl) {
            // RTL: Pinned section is on the right.
            outRect.right = taskbarLeftInDragLayer + mAllAppsButtonContainer.getRight()
                    - mAllAppsButtonTranslationOffset;

            if (mAddedDividerForRecents && mTaskbarDividerContainer != null) {
                outRect.left += mTaskbarDividerContainer.getRight();
            } else {
                // If there's no divider, iconViews contains pinned apps only.
                outRect.left = iconViews.length > 0
                        ? taskbarLeftInDragLayer + iconViews[0].getLeft()
                        : outRect.right;
            }
        } else {
            outRect.left += mAllAppsButtonTranslationOffset + mAllAppsButtonContainer.getLeft();

            if (mAddedDividerForRecents && mTaskbarDividerContainer != null) {
                outRect.right = taskbarLeftInDragLayer + mTaskbarDividerContainer.getLeft();
            } else {
                outRect.right = iconViews.length > 0
                        ? taskbarLeftInDragLayer + iconViews[iconViews.length - 1].getRight()
                        : outRect.left;
            }
        }

        // Adding a padding to the left and right bound for dropping leftmost/rightmost to reorder.
        outRect.left -= mPinnedHitRectBuffer;
        outRect.right += mPinnedHitRectBuffer;
    }

    @Override
    public boolean isPointOnOverflowIcon(@NonNull float[] point) {
        TaskbarOverflowView overflowIcon = getTaskbarPinnedOverflowView();
        if (overflowIcon == null) {
            return false;
        }
        final Rect overflowIconRect = new Rect();
        mActivityContext.getDragLayer().getDescendantRectRelativeToSelf(overflowIcon,
                overflowIconRect);
        return overflowIconRect.contains(Math.round(point[0]), Math.round(point[1]));
    }

    /**
     * Cleans up the cached drag state in the overflow view.
     *
     * @param itemDropped True if the dragged object was successfully dropped.
     */
    public void cleanUpOverflowDragState(boolean itemDropped) {
        TaskbarOverflowView overflowIcon = getTaskbarPinnedOverflowView();
        if (overflowIcon == null) {
            return;
        }
        overflowIcon.onItemDragEnded(itemDropped);
    }

    @Override
    public boolean updateForDroppedItem(@NonNull ItemInfo item) {
        if (mHotseatIconsContainer != null) {
            return mHotseatIconsContainer.updateForDroppedItem(item);
        }
        return mDragDelegate.updateForDroppedItem(item);
    }

    public static class TaskbarLayoutParams extends FrameLayout.LayoutParams {

        @Nullable public CellInfo bindInfo;

        public TaskbarLayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public TaskbarLayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public TaskbarLayoutParams(int width, int height) {
            super(width, height);
        }
    }
}
