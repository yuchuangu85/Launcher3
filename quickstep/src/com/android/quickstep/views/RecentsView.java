/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.quickstep.views;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.os.Trace.traceBegin;
import static android.os.Trace.traceEnd;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.app.animation.Interpolators.ACCELERATE_0_75;
import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.app.animation.Interpolators.DECELERATE_2;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.internal.jank.Cuj.CUJ_LAUNCHER_RECENTS_TO_HOME;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.BaseActivity.STATE_HANDLER_INVISIBILITY_FLAGS;
import static com.android.launcher3.Flags.enableLowResThumbnailPreloading;
import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.VIEW_BACKGROUND_COLOR;
import static com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;
import static com.android.launcher3.Utilities.debugLog;
import static com.android.launcher3.Utilities.getTrimmedStackTrace;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.Utilities.squaredTouchSlop;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_ACTIONS_SPLIT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_ORIENTATION_CHANGED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_CLEAR_ALL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_SWIPE_DOWN;
import static com.android.launcher3.statehandlers.DesktopVisibilityController.INACTIVE_DESK_ID;
import static com.android.launcher3.testing.shared.TestProtocol.DISMISS_ANIMATION_ENDS_MESSAGE;
import static com.android.launcher3.touch.PagedOrientationHandler.CANVAS_TRANSLATE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.launcher3.util.OverviewReleaseFlags.enableOverviewPagination;
import static com.android.launcher3.util.SystemUiController.UI_STATE_FULLSCREEN_TASK;
import static com.android.quickstep.BaseContainerInterface.getTaskDimension;
import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;
import static com.android.quickstep.fallback.RecentsStateUtilsKt.toRecentsState;
import static com.android.quickstep.util.ExternalDisplaysKt.isExternalDisplay;
import static com.android.quickstep.util.LogUtils.splitFailureMessage;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_ACTIONS_IN_MENU;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_DESKTOP;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NON_ZERO_ROTATION;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_RECENTS;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_TASKS;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_SPLIT_SELECT_ACTIVE;
import static com.android.quickstep.views.RecentsViewUtils.DESK_EXPLODE_PROGRESS;
import static com.android.quickstep.views.TaskView.SPLIT_ALPHA;
import static com.android.wm.shell.Flags.enableCreateAnyBubble;

import static java.util.Objects.requireNonNull;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.LocusId;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Pair;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.ListView;
import android.widget.OverScroller;
import android.widget.Toast;
import android.window.DesktopExperienceFlags;
import android.window.PictureInPictureSurfaceTransaction;
import android.window.TransitionInfo;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewKt;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.android.internal.jank.Cuj;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.MotionEventsUtils;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringProperty;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.desktop.DesktopRecentsTransitionController;
import com.android.launcher3.deviceprofile.OverviewProfile;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.taskbar.bubbles.BubbleHelper;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.TranslateEdgeEffect;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.util.ViewEx;
import com.android.launcher3.util.ViewPool;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.HighResLoadingState;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RecentsFilterState;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.RemoteTargetGluer;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.SplitRecentsAnimUtils;
import com.android.quickstep.SplitSelectionListener;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.recents.viewmodel.RecentsViewModel;
import com.android.quickstep.split.SplitAnimationController.Companion.SplitAnimInitProps;
import com.android.quickstep.split.SplitAnimationTimings;
import com.android.quickstep.split.SplitSelectStateController;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActiveGestureProtoLogProxy;
import com.android.quickstep.util.AnimUtils;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.FontUtils;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.SingleTask;
import com.android.quickstep.util.SplitTask;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskGridNavHelper;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TaskVisualsChangeListener;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.VibrationConstants;
import com.android.quickstep.window.RecentsWindowManager;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.common.pip.IPipAnimationListener;
import com.android.wm.shell.common.pip.IPipAnimationListener.PipResources;
import com.android.wm.shell.shared.GroupedTaskInfo;
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.shared.split.SplitBounds;

import kotlin.Unit;
import kotlin.collections.CollectionsKt;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.inject.Inject;

/**
 * A list of recent tasks.
 *
 * @param <CONTAINER_TYPE> : the container that should host recents view
 * @param <STATE_TYPE>     : the type of base state that will be used
 */
public abstract class RecentsView<
        CONTAINER_TYPE extends Context & RecentsViewContainer & StatefulContainer<STATE_TYPE>,
        STATE_TYPE extends BaseState<STATE_TYPE>> extends PagedView implements Insettable,
        HighResLoadingState.HighResLoadingStateChangedCallback,
        TaskVisualsChangeListener {

    protected static final String TAG = "RecentsView";
    private static final String PAGE_SCROLL_TAG = "RecentsPageScroll";

    public static final FloatProperty<RecentsView<?, ?>> CONTENT_ALPHA =
            new FloatProperty<>("contentAlpha") {
                @Override
                public void setValue(RecentsView view, float v) {
                    view.setContentAlpha(v);
                }

                @Override
                public Float get(RecentsView view) {
                    return view.getContentAlpha();
                }
            };

    public static final FloatProperty<RecentsView<?, ?>> FULLSCREEN_PROGRESS =
            new FloatProperty<>("fullscreenProgress") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setFullscreenProgress(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mFullscreenProgress;
                }
            };

    public static final FloatProperty<RecentsView<?, ?>> TASK_MODALNESS =
            new FloatProperty<>("taskModalness") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setTaskModalness(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mTaskModalness;
                }
            };

    public static final FloatProperty<RecentsView<?, ?>> ADJACENT_PAGE_HORIZONTAL_OFFSET =
            new FloatProperty<>("adjacentPageHorizontalOffset") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    if (recentsView.mAdjacentPageHorizontalOffset != v) {
                        recentsView.mAdjacentPageHorizontalOffset = v;
                        recentsView.updatePageOffsets();
                    }
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mAdjacentPageHorizontalOffset;
                }
            };

    public static final FloatProperty<RecentsView<?, ?>> RUNNING_TASK_ATTACH_ALPHA =
            new FloatProperty<>("runningTaskAttachAlpha") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.mRunningTaskAttachAlpha = v;
                    recentsView.applyAttachAlpha();
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mRunningTaskAttachAlpha;
                }
            };

    public static final int SCROLL_VIBRATION_PRIMITIVE =
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
    public static final float SCROLL_VIBRATION_PRIMITIVE_SCALE = 0.6f;
    public static final VibrationEffect SCROLL_VIBRATION_FALLBACK =
            VibrationConstants.EFFECT_TEXTURE_TICK;
    public static final int UNBOUND_TASK_VIEW_ID = -1;

    /**
     * Can be used to tint the color of the RecentsView to simulate a scrim that can views
     * excluded from. Really should be a proper scrim.
     */
    private static final FloatProperty<RecentsView<?, ?>> COLOR_TINT =
            new FloatProperty<>("colorTint") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setColorTint(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.getColorTint();
                }
            };

    /**
     * Even though {@link TaskView} has distinct offsetTranslationX/Y and resistance property, they
     * are currently both used to apply secondary translation. Should their use cases change to be
     * more specific, we'd want to create a similar FloatProperty just for a TaskView's
     * offsetX/Y property
     */
    public static final FloatProperty<RecentsView<?, ?>> TASK_SECONDARY_TRANSLATION =
            new FloatProperty<>("taskSecondaryTranslation") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setTaskViewsResistanceTranslation(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mTaskViewsSecondaryTranslation;
                }
            };

    /**
     * Even though {@link TaskView} has distinct offsetTranslationX/Y and resistance property, they
     * are currently both used to apply secondary translation. Should their use cases change to be
     * more specific, we'd want to create a similar FloatProperty just for a TaskView's
     * offsetX/Y property
     */
    public static final FloatProperty<RecentsView<?, ?>> TASK_PRIMARY_SPLIT_TRANSLATION =
            new FloatProperty<>("taskPrimarySplitTranslation") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setTaskViewsPrimarySplitTranslation(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mTaskViewsPrimarySplitTranslation;
                }
            };

    public static final FloatProperty<RecentsView<?, ?>> TASK_SECONDARY_SPLIT_TRANSLATION =
            new FloatProperty<>("taskSecondarySplitTranslation") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setTaskViewsSecondarySplitTranslation(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mTaskViewsSecondarySplitTranslation;
                }
            };

    /** Same as normal SCALE_PROPERTY, but also updates page offsets that depend on this scale. */
    public static final FloatProperty<RecentsView<?, ?>> RECENTS_SCALE_PROPERTY =
            new FloatProperty<>("recentsScale") {
                @Override
                public void setValue(RecentsView view, float scale) {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                    view.mLastComputedTaskStartPushOutDistance = null;
                    view.mLastComputedTaskEndPushOutDistance = null;
                    view.runActionOnRemoteHandles(new Consumer<RemoteTargetHandle>() {
                        @Override
                        public void accept(RemoteTargetHandle remoteTargetHandle) {
                            remoteTargetHandle.getTaskViewSimulator().recentsViewScale.value =
                                    scale;
                        }
                    });
                    view.setTaskViewsResistanceTranslation(view.mTaskViewsSecondaryTranslation);
                    view.updateTaskViewsSnapshotRadius();
                    view.updatePageOffsets();
                }

                @Override
                public Float get(RecentsView view) {
                    return view.getScaleX();
                }
            };

    /**
     * Progress of Recents view from carousel layout to grid layout. If Recents is not shown as a
     * grid, then the value remains 0.
     */
    public static final FloatProperty<RecentsView<?, ?>> RECENTS_GRID_PROGRESS =
            new FloatProperty<>("recentsGrid") {
                @Override
                public void setValue(RecentsView view, float gridProgress) {
                    view.setGridProgress(gridProgress);
                }

                @Override
                public Float get(RecentsView view) {
                    return view.mGridProgress;
                }
            };

    public static final FloatProperty<RecentsView<?, ?>> DESKTOP_CAROUSEL_DETACH_PROGRESS =
            new FloatProperty<>("desktopCarouselDetachProgress") {
                @Override
                public void setValue(RecentsView view, float offset) {
                    view.mDesktopCarouselDetachProgress = offset;
                    view.applyAttachAlpha();
                    view.updatePageOffsets();
                }

                @Override
                public Float get(RecentsView view) {
                    return view.mDesktopCarouselDetachProgress;
                }
            };

    /**
     * Alpha of the task thumbnail splash, where being in BackgroundAppState has a value of 1, and
     * being in any other state has a value of 0.
     */
    public static final FloatProperty<RecentsView<?, ?>> TASK_THUMBNAIL_SPLASH_ALPHA =
            new FloatProperty<>("taskThumbnailSplashAlpha") {
                @Override
                public void setValue(RecentsView view, float taskThumbnailSplashAlpha) {
                    view.setTaskThumbnailSplashAlpha(taskThumbnailSplashAlpha);
                }

                @Override
                public Float get(RecentsView view) {
                    return view.mTaskThumbnailSplashAlpha;
                }
            };

    // OverScroll constants
    private static final int OVERSCROLL_PAGE_SNAP_ANIMATION_DURATION = 270;

    private static final float SIGNIFICANT_MOVE_SCREEN_WIDTH_PERCENTAGE = 0.15f;

    private static final float FOREGROUND_SCRIM_TINT = 0.32f;

    protected final BaseContainerInterface<STATE_TYPE, ?> mContainerInterface;
    @Nullable
    protected RecentsAnimationController mRecentsAnimationController;
    @Nullable
    protected SurfaceTransactionApplier mSyncTransactionApplier;
    protected int mTaskWidth;
    protected int mTaskHeight;
    // Used to position the top of a task in the top row of the grid
    private float mTaskGridVerticalDiff;
    // The vertical space one grid task takes + space between top and bottom row.
    private float mTopBottomRowHeightDiff;
    // mTaskGridVerticalDiff and mTopBottomRowHeightDiff summed together provides the top
    // position for bottom row of grid tasks.

    @Nullable
    protected RemoteTargetHandle[] mRemoteTargetHandles;
    protected final Rect mLastComputedTaskSize = new Rect();
    protected final Rect mLastComputedGridSize = new Rect();
    protected final Rect mLastComputedGridTaskSize = new Rect();
    // How much a task that is directly offscreen will be pushed out due to RecentsView scale/pivot.
    @Nullable
    protected Float mLastComputedTaskStartPushOutDistance = null;
    @Nullable
    protected Float mLastComputedTaskEndPushOutDistance = null;
    protected boolean mEnableDrawingLiveTile = false;
    protected final Rect mTempRect = new Rect();
    protected final RectF mTempRectF = new RectF();
    private final PointF mTempPointF = new PointF();
    private final Matrix mTempMatrix = new Matrix();
    private final float[] mTempFloat = new float[1];
    private final ArraySet<OnScrollChangedListener> mScrollListeners = new ArraySet<>();

    // The threshold at which we update the SystemUI flags when animating from the task into the app
    public static final float UPDATE_SYSUI_FLAGS_THRESHOLD = 0.85f;

    protected final CONTAINER_TYPE mContainer;
    private final float mFastFlingVelocity;
    private final int mScrollHapticMinGapMillis;
    private final int mSplitPlaceholderSize;
    private final int mSplitPlaceholderInset;
    private final ClearAllButton mClearAllButton;
    @Nullable
    private AddDesktopButton mAddDesktopButton = null;
    private final Rect mClearAllButtonDeadZoneRect = new Rect();
    private final Rect mTaskViewDeadZoneRect = new Rect();
    private final Rect mTopRowDeadZoneRect = new Rect();
    private final Rect mBottomRowDeadZoneRect = new Rect();

    /**
     * Reflects if Recents is currently in the middle of a gesture, and if so, which related
     * [GroupedTaskInfo] is running. If a gesture is not in progress, this will be null.
     */
    private @Nullable GroupedTaskInfo mActiveGestureGroupedTaskInfo;

    /**
     * Getting views should be done via {@link #getTaskViewFromPool(TaskViewType)}
     */
    private final ViewPool<TaskView> mTaskViewPool;
    private final ViewPool<GroupedTaskView> mGroupedTaskViewPool;
    private final ViewPool<DesktopTaskView> mDesktopTaskViewPool;

    protected final TaskOverlayFactory mTaskOverlayFactory;

    protected boolean mDisallowScrollToClearAll;
    private boolean mOverlayEnabled;
    protected boolean mFreezeViewVisibility;
    private boolean mOverviewGridEnabled;
    private boolean mOverviewFullscreenEnabled;
    private boolean mOverviewSelectEnabled;

    private boolean mShouldClampScrollOffset;
    private final int mClampedScrollOffsetBound;

    private float mAdjacentPageHorizontalOffset = 0;
    private float mDesktopCarouselDetachProgress = 0;
    protected float mTaskViewsSecondaryTranslation = 0;
    protected float mTaskViewsPrimarySplitTranslation = 0;
    protected float mTaskViewsSecondarySplitTranslation = 0;
    // Progress from 0 to 1 where 0 is a carousel and 1 is a 2 row grid.
    private float mGridProgress = 0;
    private float mTaskThumbnailSplashAlpha = 0;
    private boolean mBorderEnabled = false;
    private boolean mShowAsGridLastOnLayout = false;
    protected final IntSet mTopRowIdSet = new IntSet();
    private int mClearAllShortTotalWidthTranslation = 0;

    // The GestureEndTarget that is still in progress.
    @Nullable
    protected GestureState.GestureEndTarget mCurrentGestureEndTarget;

    private float mColorTint;
    private final int mTintingColor;
    @Nullable
    private ObjectAnimator mTintingAnimator;

    private int mOverScrollShift = 0;
    private long mScrollLastHapticTimestamp;

    private int mKeyboardTaskFocusSnapAnimationDuration;

    protected Map<TaskView, Integer> mTaskViewsDismissPrimaryTranslations = new HashMap<>();

    /**
     * TODO: Call reloadIdNeeded in onTaskStackChanged.
     */
    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
            if (!mHandleTaskStackChanges) {
                return;
            }
            // Check this is for the right user
            if (!checkCurrentOrManagedUserId(userId, getContext())) {
                return;
            }

            // Remove the task immediately from the task list
            TaskView taskView = getTaskViewByTaskId(taskId);
            if (taskView != null) {
                removeView(taskView);
            }
        }

        @Override
        public void onTaskRemoved(int taskId) {
            if (!mHandleTaskStackChanges) {
                Log.d(TAG, "onTaskRemoved: " + taskId + ", not handling task stack changes");
                return;
            }

            TaskContainer taskContainer = mUtils.getTaskContainerById(taskId);
            if (taskContainer == null) {
                Log.d(TAG, "onTaskRemoved: " + taskId + ", no associated Task");
                return;
            }
            Log.d(TAG, "onTaskRemoved: " + taskId);
            TaskKey taskKey = taskContainer.getTask().key;
            UI_HELPER_EXECUTOR.execute(new CancellableTask<>(
                    () -> PackageManagerWrapper.getInstance()
                            .getActivityInfo(taskKey.getComponent(), taskKey.userId) == null,
                    MAIN_EXECUTOR,
                    apkRemoved -> {
                        if (apkRemoved) {
                            dismissTask(taskId, /* removeTask= */false);
                        } else {
                            mRecentsModel.isTaskRemoved(taskKey.id, taskRemoved -> {
                                if (taskRemoved) {
                                    dismissTask(taskId, /* removeTask= */false);
                                }
                            }, RecentsFilterState.getDisplayIdFilter(mContainer.getDisplayId()));
                        }
                    }));
        }

        @Override
        public void onTaskDisplayChanged(int taskId, int newDisplayId) {
            Log.d(TAG, "onTaskDisplayChanged: " + taskId + ", new displayId = " + newDisplayId);
            if (!mHandleTaskStackChanges) {
                return;
            }
            final int mappedDisplayId = mRecentsModel.getRecentsDisplayId(newDisplayId);
            if (mappedDisplayId != mContainer.getDisplayId()) {
                dismissTask(taskId, /* removeTask= */ false);
            }
        }

        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
            if (enableCreateAnyBubble()) {
                if (BubbleHelper.isAppBubbleTask(task) && mHandleTaskStackChanges) {
                    // Remove task from recents if it moved to a bubble, but keep it running
                    dismissTask(task.taskId, /* removeTask= */ false);
                }
            }
        }

        @Override
        public void onTaskStackChangedBackground() {
            if (!mOverviewStateEnabled) {
                mHelper.startPreloading();
            }
        }
    };

    private final PinnedStackAnimationListener mIPipAnimationListener =
            new PinnedStackAnimationListener();
    private PipResources mPipResources = new PipResources();

    // Used to keep track of the last requested task list id, so that we do not request to load the
    // tasks again if we have already requested it and the task list has not changed
    private int mAppliedTaskListChangeId = -1;

    // Only valid until the launcher state changes to NORMAL
    /**
     * ID for the current running TaskView view, unique amongst TaskView instances. ID's are set
     * through {@link #getTaskViewFromPool(TaskViewType)} and incremented by
     * {@link #mTaskViewIdCount}.
     */
    protected int mRunningTaskViewId = -1;
    private int mTaskViewIdCount;
    protected boolean mRunningTaskTileHidden;

    private boolean mTaskIconVisible = true;
    private boolean mRunningTaskShowScreenshot = false;
    private float mRunningTaskAttachAlpha;

    private boolean mOverviewStateEnabled;
    private boolean mHandleTaskStackChanges;
    private boolean mTouchDownToStartHome;
    private final float mSquaredTouchSlop;
    private int mDownX;
    private int mDownY;

    @Nullable
    private PendingAnimation mPendingAnimation;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected float mContentAlpha = 1;
    @ViewDebug.ExportedProperty(category = "launcher")
    protected float mFullscreenProgress = 0;
    /**
     * How modal is the current task to be displayed, 1 means the task is fully modal and no other
     * tasks are show. 0 means the task is displays in context in the list with other tasks.
     */
    @ViewDebug.ExportedProperty(category = "launcher")
    protected float mTaskModalness = 0;

    // Keeps track of task id whose visual state should not be reset
    private int mIgnoreResetTaskId = -1;
    protected boolean mLoadPlanEverApplied;

    // Variables for empty state
    @Nullable private ViewGroup mEmptyRecentsMessageView;
    private Drawable mEmptyIcon;
    private CharSequence mEmptyMessage;
    private TextPaint mEmptyMessagePaint;
    private Point mLastMeasureSize = new Point();
    private int mEmptyMessagePadding;
    private boolean mShowEmptyMessage;
    @Nullable
    private OnEmptyMessageUpdatedListener mOnEmptyMessageUpdatedListener;
    @Nullable
    private Layout mEmptyTextLayout;

    /**
     * Placeholder view indicating where the first split screen selected app will be placed
     */
    @Nullable
    protected SplitSelectStateController mSplitSelectStateController;

    /**
     * The first task that split screen selection was initiated with. When split select state is
     * initialized, we create a dismiss animtion for this TaskView but don't actually remove the
     * task since the user might back out. As such, we also ensure this View doesn't go back into
     * the {@link #mTaskViewPool}, see {@link #onViewRemoved(View)}.
     */
    @Nullable
    private TaskView mSplitHiddenTaskView;
    @Nullable
    private TaskView mSecondSplitHiddenView;
    @Nullable
    private SplitBounds mSplitBoundsConfig;
    private final Toast mSplitUnsupportedToast = Toast.makeText(getContext(),
            R.string.toast_split_app_unsupported, Toast.LENGTH_SHORT);

    @Nullable
    private SplitSelectSource mSplitSelectSource;

    private final SplitSelectionListener mSplitSelectionListener = new SplitSelectionListener() {
        @Override
        public void onSplitSelectionConfirmed() {
        }

        @Override
        public void onSplitSelectionActive() {
            Log.d(TAG, "onSplitSelectionActive");
            mClearAllButton.setSplitSelectionActive(true);
        }

        @Override
        public void onSplitSelectionExit(boolean launchedSplit) {
            Log.d(TAG, "onSplitSelectionExit");
            resetFromSplitSelectionState();
            mClearAllButton.setSplitSelectionActive(false);
        }
    };

    /**
     * Keeps track of the index of the TaskView that split screen was initialized with so we know
     * where to insert it back into list of taskViews in case user backs out of entering split
     * screen.
     * NOTE: This index is the index while {@link #mSplitHiddenTaskView} was a child of recentsView,
     * this doesn't get adjusted to reflect the new child count after the taskView is dismissed/
     * removed from recentsView
     */
    private int mSplitHiddenTaskViewIndex = -1;
    @Nullable
    private FloatingTaskView mSecondFloatingTaskView;
    /**
     * A fullscreen scrim that goes behind the splitscreen animation to hide color conflicts and
     * possible flickers. Removed after tasks + divider finish animating in.
     */
    private View mSplitScrim;

    /**
     * The task to be removed and immediately re-added. Should not be added to task pool.
     */
    @Nullable
    private TaskView mMovingTaskView;

    private OverviewActionsView mActionsView;

    @Nullable
    private DesktopRecentsTransitionController mDesktopRecentsTransitionController;

    private boolean mIs3PLauncher = false;

    @Nullable
    private RunnableList mSideTaskLaunchCallback;
    @Nullable
    private TaskLaunchListener mTaskLaunchListener;
    @Nullable
    private Runnable mOnTaskLaunchCancelledRunnable;

    private int mOffsetMidpointIndexOverride = INVALID_PAGE;

    /**
     * Whether or not any task has been dismissed i.e. swiped away by the user, in the lifetime of
     * RecentsView being open and displayed to the user. It is reset in the {@link #reset()} method
     * i.e. when RecentsView closes.
     */
    protected boolean mAnyTaskHasBeenDismissed;

    @Inject RecentsViewModel mRecentsViewModel;
    @Inject RecentsViewModelHelper mHelper;
    @Inject DesktopState mDesktopState;
    @Inject DesktopVisibilityController mDesktopVisibilityController;
    @Inject OverviewComponentObserver mOverviewComponentObserver;
    @Inject RecentsModel mRecentsModel;
    @Inject SystemUiProxy mSystemUiProxy;
    @Inject TopTaskTracker mTopTaskTracker;
    @Inject VibratorWrapper mVibratorWrapper;
    @Inject RecentsOrientedState mOrientationState;

    // Package-private for Dagger only, should not use directly
    @VisibleForTesting
    @Inject RecentsViewUtils.Factory mUtilsFactory;
    @VisibleForTesting
    @Inject RecentsDismissUtils.Factory mDismissUtilsFactory;

    protected final RecentsViewUtils mUtils;
    protected final RecentsDismissUtils mDismissUtils;

    private final boolean mIsMultipleDesktopFrontendEnabled;

    private final Matrix mTmpMatrix = new Matrix();

    private int mTaskViewCount = 0;

    protected final BlurUtils mBlurUtils = new BlurUtils(this);

    private final Runnable mPreloadRunnable = () -> {
        if (!mOverviewStateEnabled) {
            mHelper.startPreloading();
        }
    };

    public RecentsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setEnableFreeScroll(true);

        mContainer = RecentsViewContainer.containerFromContext(context);
        mContainerInterface = mContainer.getContainerInterface();

        initialiseInjectables();
        mUtils = mUtilsFactory.create(this);
        mDismissUtils = mDismissUtilsFactory.create(this);

        mOrientationState.setRotationChangeListener(this::animateRecentsRotationInPlace);
        final int rotation = mContainer.getDisplay().getRotation();
        mOrientationState.setRecentsRotation(rotation);

        mScrollHapticMinGapMillis = getResources()
                .getInteger(R.integer.recentsScrollHapticMinGapMillis);
        mFastFlingVelocity = getResources()
                .getDimensionPixelSize(R.dimen.recents_fast_fling_velocity);

        mClearAllButton = (ClearAllButton) LayoutInflater.from(context)
                .inflate(R.layout.overview_clear_all_button, this, false);
        mClearAllButton.setOnClickListener(this::dismissAllTasks);

        mIsMultipleDesktopFrontendEnabled = mDesktopState.isDesktopModeSupportedOnDisplay(
                mContainer.getDisplay());
        if (mIsMultipleDesktopFrontendEnabled) {
            mAddDesktopButton = (AddDesktopButton) LayoutInflater.from(context).inflate(
                    R.layout.overview_add_desktop_button, this, false);
            mAddDesktopButton.setOnClickListener(view -> {
                AddDesktopButton button = (AddDesktopButton) view;
                button.setContentVisibility(/* toVisible= */ false, /* animate= */ true, () -> {
                    createDesk();
                    button.setContentVisibility(/* toVisible= */ true, /* animate= */ true);
                });
            });

            // Update its visibility based on whether we can create a desk or not.
            mUtils.onCanCreateDesksChanged(
                    mDesktopVisibilityController.getCanCreateDesks().getValue());
            ViewEx.registerLifecycleTask(this,
                    () -> mDesktopVisibilityController.getCanCreateDesks().forEach(
                            mContainer.getUiExecutor(), b -> {
                                mUtils.onCanCreateDesksChanged(b);
                                return Unit.INSTANCE;
                            }));
        }

        mTaskViewPool = new ViewPool<>(context, this, R.layout.task, 20 /* max size */,
                10 /* initial size */);
        int groupedViewPoolInitialSize = 2;
        mGroupedTaskViewPool = new ViewPool<>(context, this,
                R.layout.task_grouped, 20 /* max size */, groupedViewPoolInitialSize);
        int desktopViewPoolInitialSize = mDesktopState.canEnterDesktopMode() ? 1 : 0;
        mDesktopTaskViewPool = new ViewPool<>(context, this, R.layout.task_desktop,
                5 /* max size */, desktopViewPoolInitialSize);

        setOrientationHandler(mOrientationState.getOrientationHandler());
        mIsRtl = getPagedOrientationHandler().getRecentsRtlSetting(getResources());
        setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mSplitPlaceholderSize = getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_size);
        mSplitPlaceholderInset = getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_inset);
        mSquaredTouchSlop = squaredTouchSlop(context);
        mClampedScrollOffsetBound = getResources().getDimensionPixelSize(
                R.dimen.transient_taskbar_clamped_offset_bound);

        mTaskOverlayFactory = LauncherComponentProvider.get(context).getTaskOverlayFactory();

        // Initialize quickstep specific cache params here, as this is constructed only once
        mContainer.getViewCache().setCacheSize(R.layout.digital_wellbeing_toast, 5);

        mTintingColor = getForegroundScrimDimColor(context);
    }

    protected abstract void initialiseInjectables();

    public OverScroller getScroller() {
        return mScroller;
    }

    public boolean isRtl() {
        return mIsRtl;
    }

    @Override
    protected void initEdgeEffect() {
        mEdgeGlowLeft = new TranslateEdgeEffect(getContext());
        mEdgeGlowRight = new TranslateEdgeEffect(getContext());
    }

    @Override
    protected void drawEdgeEffect(Canvas canvas) {
        // Do not draw edge effect
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        // Draw overscroll
        if (mAllowOverScroll && (!mEdgeGlowRight.isFinished() || !mEdgeGlowLeft.isFinished())) {
            final int restoreCount = canvas.save();

            int primarySize = getPagedOrientationHandler().getPrimaryValue(getWidth(), getHeight());
            int scroll = OverScroll.dampedScroll(getUndampedOverScrollShift(), primarySize);
            getPagedOrientationHandler().setPrimary(canvas, CANVAS_TRANSLATE, scroll);

            if (mOverScrollShift != scroll) {
                mOverScrollShift = scroll;
                dispatchScrollChanged();
            }

            super.dispatchDraw(canvas);
            canvas.restoreToCount(restoreCount);
        } else {
            if (mOverScrollShift != 0) {
                mOverScrollShift = 0;
                dispatchScrollChanged();
            }
            super.dispatchDraw(canvas);
        }
        if (mEnableDrawingLiveTile && mRemoteTargetHandles != null) {
            redrawLiveTile();
        }
    }

    @Override
    protected boolean canScroll(float absVScroll, float absHScroll) {
        if (isModal()) {
            return false;
        }
        return super.canScroll(absVScroll, absHScroll);
    }

    private float getUndampedOverScrollShift() {
        final int width = getWidth();
        final int height = getHeight();
        int primarySize = getPagedOrientationHandler().getPrimaryValue(width, height);
        int secondarySize = getPagedOrientationHandler().getSecondaryValue(width, height);

        float effectiveShift = 0;
        if (!mEdgeGlowLeft.isFinished()) {
            mEdgeGlowLeft.setSize(secondarySize, primarySize);
            if (((TranslateEdgeEffect) mEdgeGlowLeft).getTranslationShift(mTempFloat)) {
                effectiveShift = mTempFloat[0];
                postInvalidateOnAnimation();
            }
        }
        if (!mEdgeGlowRight.isFinished()) {
            mEdgeGlowRight.setSize(secondarySize, primarySize);
            if (((TranslateEdgeEffect) mEdgeGlowRight).getTranslationShift(mTempFloat)) {
                effectiveShift -= mTempFloat[0];
                postInvalidateOnAnimation();
            }
        }

        return effectiveShift * primarySize;
    }

    /**
     * Returns the view shift due to overscroll
     */
    public int getOverScrollShift() {
        return mOverScrollShift;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateTaskStackListenerState();
    }

    public void init(OverviewActionsView actionsView, SplitSelectStateController splitController,
            @Nullable DesktopRecentsTransitionController desktopRecentsTransitionController,
            SurfaceTransactionApplier surfaceTransactionApplier,
            @Nullable ViewGroup emptyRecentsMessageView) {
        // OverviewActionsView related.
        mIs3PLauncher = !mOverviewComponentObserver.isHomeAndOverviewSame();
        mActionsView = actionsView;
        mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, !hasTaskViews());
        // Update flags for 1p/3p launchers
        mActionsView.updateFor3pLauncher(mIs3PLauncher);

        // RecentsViewContainer provided dependencies.
        mSplitSelectStateController = splitController;
        mDesktopRecentsTransitionController = desktopRecentsTransitionController;
        // Set in launcher to be in sync with the other Surface transactions e.g. in
        // BaseDepthController for applying blur.
        mSyncTransactionApplier = surfaceTransactionApplier;

        // Empty Recents related.
        mEmptyRecentsMessageView = emptyRecentsMessageView;
        if (mEmptyRecentsMessageView != null) {
            updateEmptyMessageConfiguration();
        } else {
            mEmptyIcon = mContext.getDrawable(R.drawable.ic_view_carousel);
            mEmptyIcon.setCallback(this);
            mEmptyMessage = mContext.getText(R.string.recents_empty_message);
            mEmptyMessagePaint = new TextPaint();
            mEmptyMessagePadding = getResources().getDimensionPixelSize(
                    R.dimen.recents_empty_message_text_padding);
            mEmptyMessagePaint.setColor(
                    getResources().getColor(R.color.materialColorOnSurface, mContext.getTheme()));
            mEmptyMessagePaint.setTextSize(getResources()
                    .getDimension(R.dimen.recents_empty_message_text_size));
            mEmptyMessagePaint.setTypeface(FontUtils.getTypeFace(getResources()));
            mEmptyMessagePaint.setAntiAlias(true);
            setWillNotDraw(false);
        }
        ViewEx.registerLifecycleTask(this,
                () -> mSystemUiProxy.getPipAnimationListeners().register(mIPipAnimationListener));
        updateEmptyMessage();
    }

    public SplitSelectStateController getSplitSelectController() {
        return mSplitSelectStateController;
    }

    @AnyThread
    public boolean isSplitSelectionActive() {
        return mSplitSelectStateController != null
                && mSplitSelectStateController.isSplitSelectActive();
    }

    /**
     * See overridden implementations
     *
     * @return {@code true} if child TaskViews can be launched when user taps on them
     */
    public boolean canLaunchFullscreenTask() {
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTaskStackListenerState();
        if (!enableLowResThumbnailPreloading()) {
            mRecentsModel.getThumbnailCache().getHighResLoadingState().addCallback(this);
        }
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
        runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle.getTransformParams()
                .setSyncTransactionApplier(mSyncTransactionApplier));
        mRecentsModel.addThumbnailChangeListener(this);
        mIPipAnimationListener.setActivityAndRecentsView(mContainer, this);

        // Late initializer for SystemUiProxy
        mSystemUiProxy.addOnStateChangeListener(mPreloadRunnable);
        mOrientationState.initListeners();
        mTaskOverlayFactory.initListeners();
        mSplitSelectStateController.registerSplitListener(mSplitSelectionListener);
        if (mIsMultipleDesktopFrontendEnabled) {
            mDesktopVisibilityController.registerDesktopVisibilityListener(mUtils);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        updateTaskStackListenerState();
        if (!enableLowResThumbnailPreloading()) {
            mRecentsModel.getThumbnailCache().getHighResLoadingState().removeCallback(this);
        }
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
        mSyncTransactionApplier = null;
        runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle.getTransformParams()
                .setSyncTransactionApplier(null));
        executeSideTaskLaunchCallback();
        mRecentsModel.removeThumbnailChangeListener(this);
        mSystemUiProxy.removeOnStateChangeListener(mPreloadRunnable);
        mIPipAnimationListener.setActivityAndRecentsView(null, null);
        mOrientationState.destroyListeners();
        mTaskOverlayFactory.removeListeners();
        mSplitSelectStateController.unregisterSplitListener(mSplitSelectionListener);
        if (mIsMultipleDesktopFrontendEnabled) {
            mDesktopVisibilityController.unregisterDesktopVisibilityListener(mUtils);
        }
        mTaskLaunchListener = null;
        mOnTaskLaunchCancelledRunnable = null;
        reset();
    }

    /**
     * Execute clean-up logic needed when the view is destroyed.
     */
    public void destroy() {
        Log.d(TAG, "destroy");
        mTaskViewPool.cancelOngoingInitializations();
        mGroupedTaskViewPool.cancelOngoingInitializations();
        mDesktopTaskViewPool.cancelOngoingInitializations();
        mOrientationState.setRotationChangeListener(null);
        mHelper.onDestroy();
        mUtils.destroy();
    }

    @Override
    public void onViewRemoved(View child) {
        traceBegin(Trace.TRACE_TAG_APP, "RecentsView.onViewRemoved");
        super.onViewRemoved(child);
        // Clear the task data for the removed child if it was visible unless:
        // - It's the initial taskview for entering split screen, we only pretend to dismiss the
        // task
        // - It's the running task to be moved to the front, we immediately re-add the task
        if (child instanceof TaskView) {
            mTaskViewCount = Math.max(0, --mTaskViewCount);
            if (child != mSplitHiddenTaskView && child != mMovingTaskView) {
                clearAndRecycleTaskView((TaskView) child);
            }
        }
        traceEnd(Trace.TRACE_TAG_APP);
    }

    private void clearAndRecycleTaskView(TaskView taskView) {
        if (taskView instanceof GroupedTaskView) {
            mGroupedTaskViewPool.recycle((GroupedTaskView) taskView);
        } else if (taskView instanceof DesktopTaskView) {
            mDesktopTaskViewPool.recycle((DesktopTaskView) taskView);
        } else {
            mTaskViewPool.recycle(taskView);
        }
        mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, !hasTaskViews());
    }

    @Override
    public void onViewAdded(View child) {
        traceBegin(Trace.TRACE_TAG_APP, "RecentsView.onViewAdded");
        super.onViewAdded(child);
        if (child instanceof TaskView) {
            mTaskViewCount++;
        }
        if (mAddDesktopButton != null && child instanceof AddDesktopButton) {
            mAddDesktopButton.setContentAlpha(mContentAlpha);
        } else if (child instanceof ClearAllButton) {
            mClearAllButton.setContentAlpha(mContentAlpha);
        } else {
            child.setAlpha(mContentAlpha);
        }
        // RecentsView is set to RTL in the constructor when system is using LTR. Here we set the
        // child direction back to match system settings.
        child.setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, !hasTaskViews());
        if (mEmptyRecentsMessageView == null) {
            updateEmptyMessage();
        }
        traceEnd(Trace.TRACE_TAG_APP);
    }

    @Override
    public void draw(Canvas canvas) {
        maybeDrawEmptyMessage(canvas);
        super.draw(canvas);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        if (isModal()) {
            // Do not scroll when clicking on a modal grid task, as it will already be centered
            // on screen.
            return false;
        }
        return super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }

    public void addSideTaskLaunchCallback(RunnableList callback) {
        addSideTaskLaunchCallback(callback::executeAllAndDestroy);
    }

    public void addSideTaskLaunchCallback(Runnable runnable) {
        if (mSideTaskLaunchCallback == null) {
            mSideTaskLaunchCallback = new RunnableList();
        }
        mSideTaskLaunchCallback.add(runnable);
    }

    /**
     * This is a one-time callback when touching in live tile mode. It's reset to null right
     * after it's called.
     */
    public void setTaskLaunchListener(@Nullable TaskLaunchListener taskLaunchListener) {
        mTaskLaunchListener = taskLaunchListener;
    }

    public void onTaskLaunchedInLiveTileMode() {
        if (mTaskLaunchListener != null) {
            mTaskLaunchListener.onTaskLaunched();
            mTaskLaunchListener = null;
        }
    }

    /**
     * This is a one-time callback when touching in live tile mode. It's reset to null right
     * after it's called.
     */
    public void setTaskLaunchCancelledRunnable(Runnable onTaskLaunchCancelledRunnable) {
        mOnTaskLaunchCancelledRunnable = onTaskLaunchCancelledRunnable;
    }

    public void onTaskLaunchedInLiveTileModeCancelled() {
        if (mOnTaskLaunchCancelledRunnable != null) {
            mOnTaskLaunchCancelledRunnable.run();
            mOnTaskLaunchCancelledRunnable = null;
        }
    }

    private void executeSideTaskLaunchCallback() {
        if (mSideTaskLaunchCallback != null) {
            mSideTaskLaunchCallback.executeAllAndDestroy();
            mSideTaskLaunchCallback = null;
        }
    }

    /**
     * TODO(b/195675206) Check both taskIDs from runningTaskViewId
     *  and launch if either of them is {@param taskId}
     */
    public void launchSideTaskInLiveTileModeForRestartedApp(int taskId) {
        int runningTaskViewId = getTaskViewIdFromTaskId(taskId);
        if (mRunningTaskViewId == -1 ||
                mRunningTaskViewId != runningTaskViewId ||
                mRemoteTargetHandles == null) {
            return;
        }

        TransformParams params = mRemoteTargetHandles[0].getTransformParams();
        RemoteAnimationTargets targets = params.getTargetSet();
        if (targets != null && targets.findTask(taskId) != null) {
            launchSideTaskInLiveTileMode(taskId, targets.apps, targets.wallpapers,
                    targets.nonApps, /* transitionInfo= */ null);
        }
    }

    public void launchSideTaskInLiveTileMode(int taskId, RemoteAnimationTarget[] apps,
            RemoteAnimationTarget[] wallpaper, RemoteAnimationTarget[] nonApps,
            @Nullable TransitionInfo transitionInfo) {
        AnimatorSet anim = new AnimatorSet();
        TaskView taskView = getTaskViewByTaskId(taskId);
        if (taskView == null
                || !isTaskViewVisible(taskView)
                || isTaskOnDesktopLaunchingFullscreen(taskId, taskView, apps)) {
            // TODO: Refine this animation.
            SurfaceTransactionApplier surfaceApplier =
                    new SurfaceTransactionApplier(mContainer.getDragLayer());
            ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
            appAnimator.setDuration(RECENTS_LAUNCH_DURATION);
            appAnimator.setInterpolator(ACCELERATE_DECELERATE);
            final Matrix matrix = new Matrix();
            appAnimator.addUpdateListener(valueAnimator -> {
                float percent = valueAnimator.getAnimatedFraction();
                SurfaceTransaction transaction = new SurfaceTransaction();
                for (int i = apps.length - 1; i >= 0; --i) {
                    RemoteAnimationTarget app = apps[i];

                    float dx = mContainer.getDeviceProfile().getDeviceProperties().getWidthPx() * (1 - percent) / 2
                            + app.screenSpaceBounds.left * percent;
                    float dy = mContainer.getDeviceProfile().getDeviceProperties().getHeightPx() * (1 - percent) / 2
                            + app.screenSpaceBounds.top * percent;
                    matrix.setScale(percent, percent);
                    matrix.postTranslate(dx, dy);
                    transaction.forSurface(app.leash)
                            .setAlpha(percent)
                            .setMatrix(matrix);
                }
                surfaceApplier.scheduleApply(transaction);
            });
            appAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    final SurfaceTransaction showTransaction = new SurfaceTransaction();
                    for (int i = apps.length - 1; i >= 0; --i) {
                        showTransaction.getTransaction().show(apps[i].leash);
                        showTransaction.forSurface(apps[i].leash).setLayer(
                                Integer.MAX_VALUE - 1000 + apps[i].prefixOrderIndex);
                    }
                    surfaceApplier.scheduleApply(showTransaction);
                }
            });
            anim.play(appAnimator);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    finishRecentsAnimation(false /* toRecents */, true /*shouldPip*/, () -> {
                        if (mContainer instanceof RecentsWindowManager recentsWindowManager) {
                            recentsWindowManager.getStateManager().moveToRestState();
                        }
                    });
                }
            });
            if (mUtils.isTaskLaunchingInFreeFromWindow(taskId, apps)) {
                Log.d(TAG,
                        "launchSideTaskInLiveTileMode - return to desktop due to freeform task "
                                + "launching");
                returnToDesktop();
            }
        } else {
            TaskViewUtils.composeRecentsLaunchAnimator(anim, taskView, apps, wallpaper, nonApps,
                    true /* launcherClosing */, getStateManager(), this,
                    getDepthController(), transitionInfo, /* appearedTaskId= */ taskId);
        }
        anim.start();
    }

    private static boolean isTaskOnDesktopLaunchingFullscreen(
            int taskId, TaskView taskView, RemoteAnimationTarget[] apps) {
        if (!(taskView instanceof DesktopTaskView)) {
            return false;
        }
        return Arrays.stream(apps).anyMatch(t -> t.taskId == taskId
                && t.windowConfiguration.getWindowingMode() == WINDOWING_MODE_FULLSCREEN);
    }

    public boolean isTaskViewVisible(TaskView tv) {
        if (showAsGrid()) {
            int screenStart = getPagedOrientationHandler().getPrimaryScroll(this);
            int screenEnd = screenStart + getPagedOrientationHandler().getMeasuredSize(this);
            return isTaskViewWithinBounds(tv, screenStart, screenEnd, /*taskViewTranslation=*/ 0);
        } else {
            // For now, just check if it's the active task or an adjacent task
            return Math.abs(indexOfChild(tv) - getNextPage()) <= 1;
        }
    }

    public boolean isTaskViewFullyVisible(TaskView tv) {
        if (showAsGrid()) {
            int screenStart = getPagedOrientationHandler().getPrimaryScroll(this);
            int screenEnd = screenStart + getPagedOrientationHandler().getMeasuredSize(this);
            return isTaskViewFullyWithinBounds(tv, screenStart, screenEnd);
        } else {
            // For now, just check if it's the active task
            return indexOfChild(tv) == getNextPage();
        }
    }

    @Nullable
    protected TaskView getLastGridTaskView() {
        return getLastGridTaskView(mUtils.getTopRowIdArray(), mUtils.getBottomRowIdArray());
    }

    @Nullable
    protected TaskView getLastGridTaskView(IntArray topRowIdArray, IntArray bottomRowIdArray) {
        if (topRowIdArray.isEmpty() && bottomRowIdArray.isEmpty()) {
            return null;
        }
        int lastTaskViewId = topRowIdArray.size() >= bottomRowIdArray.size() ? topRowIdArray.get(
                topRowIdArray.size() - 1) : bottomRowIdArray.get(bottomRowIdArray.size() - 1);
        return getTaskViewFromTaskViewId(lastTaskViewId);
    }

    protected int getSnapToLastTaskScrollDiff() {
        // Snap to a position where ClearAll is just invisible.
        int screenStart = getPagedOrientationHandler().getPrimaryScroll(this);
        int clearAllScroll = getScrollForPage(indexOfChild(mClearAllButton));
        int clearAllWidth = getPagedOrientationHandler().getPrimarySize(mClearAllButton);
        int lastTaskScroll = getLastTaskScroll(clearAllScroll, clearAllWidth);
        return screenStart - lastTaskScroll;
    }

    protected int getLastTaskScroll(int clearAllScroll, int clearAllWidth) {
        int distance = clearAllWidth + getClearAllExtraPageSpacing();
        return clearAllScroll + (mIsRtl ? distance : -distance);
    }

    @Nullable
    public RunnableList returnToDesktop() {
        return mUtils.returnToDesktop();
    }

    /*
     * Returns if TaskView is within screen bounds defined in [screenStart, screenEnd].
     *
     * @param taskViewTranslation taskView is considered within bounds if either translated or
     * original position of taskView is within screen bounds.
     */
    protected boolean isTaskViewWithinBounds(TaskView taskView, int screenStart, int screenEnd,
            int taskViewTranslation) {
        int taskStart = getPagedOrientationHandler().getChildStart(taskView)
                + (int) taskView.getOffsetAdjustment(showAsGrid());
        int taskSize = (int) (getPagedOrientationHandler().getMeasuredSize(taskView)
                * taskView.getSizeAdjustment(showAsFullscreen()));
        int taskEnd = taskStart + taskSize;

        int translatedTaskStart = taskStart + taskViewTranslation;
        int translatedTaskEnd = taskEnd + taskViewTranslation;

        taskStart = Math.min(taskStart, translatedTaskStart);
        taskEnd = Math.max(taskEnd, translatedTaskEnd);

        return (taskStart >= screenStart && taskStart <= screenEnd) || (taskEnd >= screenStart
                && taskEnd <= screenEnd);
    }

    private boolean isTaskViewFullyWithinBounds(TaskView tv, int start, int end) {
        int taskStart = getPagedOrientationHandler().getChildStart(tv)
                + (int) tv.getOffsetAdjustment(showAsGrid());
        int taskSize = (int) (getPagedOrientationHandler().getMeasuredSize(tv)
                * tv.getSizeAdjustment(showAsFullscreen()));
        int taskEnd = taskStart + taskSize;
        return taskStart >= start && taskEnd <= end;
    }

    /**
     * Returns true if the given TaskView is in expected scroll position.
     */
    public boolean isTaskInExpectedScrollPosition(@NonNull TaskView taskView) {
        return getScrollForPage(indexOfChild(taskView))
                == getPagedOrientationHandler().getPrimaryScroll(this);
    }

    /**
     * Returns a {@link TaskView} that has taskId matching {@code taskId} or null if no match.
     */
    @Nullable
    public TaskView getTaskViewByTaskId(int taskId) {
        if (taskId == INVALID_TASK_ID) {
            return null;
        }

        for (TaskView taskView : getTaskViews()) {
            if (taskView.containsTaskId(taskId)) {
                return taskView;
            }
        }
        return null;
    }

    /**
     * Returns a {@link TaskView} that has taskIds matching {@code taskIds} or null if no match.
     */
    @Nullable
    public TaskView getTaskViewByTaskIds(int[] taskIds) {
        if (!hasAllValidTaskIds(taskIds)) {
            return null;
        }

        // We're looking for a taskView that matches these ids, regardless of order
        int[] taskIdsCopy = Arrays.copyOf(taskIds, taskIds.length);
        Arrays.sort(taskIdsCopy);

        for (TaskView taskView : getTaskViews()) {
            int[] taskViewIdsCopy = taskView.getTaskIds();
            Arrays.sort(taskViewIdsCopy);
            if (Arrays.equals(taskIdsCopy, taskViewIdsCopy)) {
                return taskView;
            }
        }
        return null;
    }

    /** Returns false if {@code taskIds} is null or contains any invalid values, true otherwise */
    private boolean hasAllValidTaskIds(int[] taskIds) {
        return taskIds != null
                && taskIds.length > 0
                && Arrays.stream(taskIds).noneMatch(taskId -> taskId == INVALID_TASK_ID);
    }

    public void setOverviewStateEnabled(boolean enabled) {
        if (mOverviewStateEnabled && !enabled) {
            mHelper.startPreloading();
        }
        mOverviewStateEnabled = enabled;
        updateTaskStackListenerState();
        mOrientationState.setRotationWatcherEnabled(enabled);
        if (!enabled) {
            mSplitBoundsConfig = null;
            mTaskOverlayFactory.clearAllActiveState();
        }
        updateLocusId();
    }

    /**
     * Enable or disable showing border on hover and focus change on task views
     */
    public void setTaskBorderEnabled(boolean enabled) {
        mBorderEnabled = enabled;
        for (TaskView taskView : getTaskViews()) {
            taskView.setBorderEnabled(enabled);
        }
        mClearAllButton.setBorderEnabled(enabled);
        if (mAddDesktopButton != null) {
            mAddDesktopButton.setBorderEnabled(enabled);
        }
    }

    /**
     * Whether the Clear All button is hidden or fully visible. Used to determine if center
     * displayed page is a task or the Clear All button.
     *
     * @return True = Clear All button not fully visible, center page is a task. False = Clear All
     * button fully visible, center page is Clear All button.
     */
    public boolean isClearAllHidden() {
        return mClearAllButton.getAlpha() != 1f;
    }

    @Override
    protected void onPageBeginTransition() {
        super.onPageBeginTransition();
        if (!mContainer.getDeviceProfile().getDeviceProperties().isLargeScreen()) {
            mActionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING, true);
        }
        if (toRecentsState(getStateManager().getState()) == RecentsState.DEFAULT) {
            InteractionJankMonitorWrapper.begin(/* view= */ this, Cuj.CUJ_RECENTS_SCROLLING);
        }
    }

    @Override
    protected void onPageEndTransition() {
        super.onPageEndTransition();
        ActiveGestureProtoLogProxy.logOnPageEndTransition(getNextPage());
        if (!mContainer.getDeviceProfile().getDeviceProperties().isLargeScreen()) {
            mActionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING, false);
        }
        InteractionJankMonitorWrapper.end(Cuj.CUJ_RECENTS_SCROLLING);
    }

    @Override
    protected boolean isSignificantMove(float absoluteDelta, int pageOrientedSize) {
        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
        if (!deviceProfile.getDeviceProperties().isLargeScreen()) {
            return super.isSignificantMove(absoluteDelta, pageOrientedSize);
        }

        return absoluteDelta
                > deviceProfile.getDeviceProperties().getAvailableWidthPx() * SIGNIFICANT_MOVE_SCREEN_WIDTH_PERCENTAGE;
    }

    @Override
    protected void updateIsBeingDraggedOnTouchDown(MotionEvent ev) {
        // Do not allow mouse to drag RecentsView on action down.
        if (!shouldAllowDrag(ev)) {
            return;
        }
        super.updateIsBeingDraggedOnTouchDown(ev);
    }

    @Override
    protected boolean shouldIgnoreMouseClickAndDrag(MotionEvent ev) {
        return !shouldAllowDrag(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);

        if (showAsGrid()) {
            for (TaskView taskView : getTaskViews()) {
                if (isTaskViewVisible(taskView) && taskView.offerTouchToChildren(ev)) {
                    // Keep consuming events to pass to delegate
                    return true;
                }
            }
        } else {
            TaskView taskView = getCurrentPageTaskView();
            if (taskView != null && taskView.offerTouchToChildren(ev)) {
                // Keep consuming events to pass to delegate
                return true;
            }
        }

        final int x = (int) ev.getX();
        final int y = (int) ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_UP:
                if (mTouchDownToStartHome) {
                    TaskView taskView = getCurrentPageTaskView();
                    if (isExternalDisplay(mContainer.getDisplayId()) && taskView != null
                            && !taskView.isBeingDismissed() && isTaskViewVisible(taskView)) {
                        taskView.launchWithAnimation();
                    } else {
                        startHome();
                    }
                }
                mTouchDownToStartHome = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                mTouchDownToStartHome = false;
                break;
            case MotionEvent.ACTION_MOVE:
                // Passing the touch slop will not allow dismiss to home
                if (mTouchDownToStartHome &&
                        (isHandlingTouch() ||
                                squaredHypot(mDownX - x, mDownY - y) > mSquaredTouchSlop)) {
                    mTouchDownToStartHome = false;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                // Touch down anywhere but the deadzone around the visible clear all button and
                // between the task views will start home on touch up
                if (!isHandlingTouch() && !isModal()) {
                    if (mShowEmptyMessage) {
                        mTouchDownToStartHome = true;
                    } else {
                        updateDeadZoneRects();
                        final boolean clearAllButtonDeadZoneConsumed =
                                mClearAllButton.getAlpha() == 1
                                        && mClearAllButtonDeadZoneRect.contains(x, y);
                        final boolean cameFromNavBar = (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
                        int adjustedX = x + getScrollX();
                        if (!clearAllButtonDeadZoneConsumed && !cameFromNavBar
                                && !mTaskViewDeadZoneRect.contains(adjustedX, y)
                                && !mTopRowDeadZoneRect.contains(adjustedX, y)
                                && !mBottomRowDeadZoneRect.contains(adjustedX, y)) {
                            mTouchDownToStartHome = true;
                        }
                    }
                }
                mDownX = x;
                mDownY = y;
                break;
        }

        return isHandlingTouch();
    }

    @Override
    protected void onNotSnappingToPageInFreeScroll() {
        int finalPos = mScroller.getFinalX();
        if (finalPos > mMinScroll && finalPos < mMaxScroll) {
            int firstPageScroll = getScrollForPage(!mIsRtl ? 0 : getPageCount() - 1);
            int lastPageScroll = getScrollForPage(!mIsRtl ? getPageCount() - 1 : 0);

            // If scrolling ends in the half of the added space that is closer to
            // the end, settle to the end. Otherwise snap to the nearest page.
            // If flinging past one of the ends, don't change the velocity as it
            // will get stopped at the end anyway.
            int pageSnapped = finalPos < (firstPageScroll + mMinScroll) / 2
                    ? mMinScroll
                    : finalPos > (lastPageScroll + mMaxScroll) / 2
                            ? mMaxScroll
                            : getScrollForPage(mNextPage);

            if (showAsGrid()) {
                if (isSplitSelectionActive()) {
                    return;
                }
                TaskView taskView = getTaskViewAt(mNextPage);
                boolean shouldSnapToLargeTask = taskView != null && taskView.isLargeTile();
                boolean shouldSnapToSmallTask =
                        taskView != null && taskView == mUtils.getFirstSmallTaskView();
                boolean shouldSnapToClearAll = mNextPage == indexOfChild(mClearAllButton);
                boolean shouldSnapToLastTask = taskView != null && getScrollForPage(
                        indexOfChild(taskView)) == getScrollForPage(indexOfChild(
                        getLastGridTaskView(mUtils.getTopRowIdArray(),
                                mUtils.getBottomRowIdArray())));
                boolean shouldSnap =
                        shouldSnapToLargeTask || shouldSnapToClearAll || shouldSnapToSmallTask
                                || shouldSnapToLastTask;
                if (!shouldSnap) {
                    return;
                }
            }

            mScroller.setFinalX(pageSnapped);
            // Ensure the scroll/snap doesn't happen too fast;
            int extraScrollDuration = OVERSCROLL_PAGE_SNAP_ANIMATION_DURATION
                    - mScroller.getDuration();
            if (extraScrollDuration > 0) {
                mScroller.extendDuration(extraScrollDuration);
            }
            debugLog(PAGE_SCROLL_TAG, "onNotSnappingToPageInFreeScroll - mNextPage: " + mNextPage
                    + ", scrollSnapped: " + pageSnapped);
        }
    }

    @Override
    protected void onEdgeAbsorbingScroll() {
        vibrateForScroll();
    }

    @Override
    protected void onScrollOverPageChanged() {
        vibrateForScroll();
    }

    private void vibrateForScroll() {
        long now = SystemClock.uptimeMillis();
        if (now - mScrollLastHapticTimestamp > mScrollHapticMinGapMillis) {
            mScrollLastHapticTimestamp = now;
            mVibratorWrapper.vibrate(SCROLL_VIBRATION_PRIMITIVE,
                    SCROLL_VIBRATION_PRIMITIVE_SCALE, SCROLL_VIBRATION_FALLBACK);
        }
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev, float touchSlopScale) {
        // Enables swiping to the left or right only if the task overlay is not modal, and event
        // is not from a mouse.
        if (!isModal() && shouldAllowDrag(ev)) {
            super.determineScrollingStart(ev, touchSlopScale);
        }
    }

    private boolean shouldAllowDrag(MotionEvent ev) {
        boolean isMouseDrag = ev.isFromSource(InputDevice.SOURCE_MOUSE)
                && !MotionEventsUtils.isTrackpadScroll(ev)
                && !MotionEventsUtils.isTrackpadFourFingerSwipe(ev);
        return !(enableOverviewPagination() && isMouseDrag);
    }

    /**
     * Moves the running task to the expected position in the carousel. In tablets, this minimize
     * animation required to move the running task into expected position.
     */
    public void moveRunningTaskToExpectedPosition() {
        TaskView runningTaskView = getRunningTaskView();
        if (runningTaskView == null || mCurrentPage != indexOfChild(runningTaskView)) {
            return;
        }

        int runningTaskExpectedIndex = mUtils.getRunningTaskExpectedIndex(runningTaskView);
        if (mCurrentPage == runningTaskExpectedIndex) {
            return;
        }

        int primaryScroll = getPagedOrientationHandler().getPrimaryScroll(this);
        int currentPageScroll = getScrollForPage(mCurrentPage);
        mCurrentPageScrollDiff = primaryScroll - currentPageScroll;

        mMovingTaskView = runningTaskView;
        removeView(runningTaskView);
        mMovingTaskView = null;
        runningTaskView.resetPersistentViewTransforms();

        addView(runningTaskView, runningTaskExpectedIndex);
        setCurrentPage(runningTaskExpectedIndex);

        updateTaskSize();
    }

    @Override
    protected void onScrollerAnimationAborted() {
        ActiveGestureProtoLogProxy.logOnScrollerAnimationAborted();
    }

    @Override
    protected boolean isPageScrollsInitialized() {
        return super.isPageScrollsInitialized() && mLoadPlanEverApplied;
    }

    protected void applyLoadPlan(List<GroupTask> taskGroups, int taskListChangeId) {
        if (mPendingAnimation != null) {
            final List<GroupTask> finalTaskGroups = taskGroups;
            mPendingAnimation.addEndListener(
                    success -> applyLoadPlan(finalTaskGroups, taskListChangeId));
            return;
        }

        if (taskGroups == null) {
            Log.d(TAG,
                    "applyLoadPlan - taskGroups is null - taskListChangeId: " + taskListChangeId);
        } else {
            Log.d(TAG, "applyLoadPlan - taskGroups: " + taskGroups.stream().map(
                    GroupTask::toString).toList() + ", taskListChangeId: " + taskListChangeId);
        }
        if (!mLoadPlanEverApplied) {
            mLoadPlanEverApplied = true;
            mPageScrolls = null;
        }
        if (taskGroups == null || taskGroups.isEmpty()) {
            removeAllTaskViews();
            onTaskStackUpdated();
            // With all tasks removed, touch handling in PagedView is disabled and we need to reset
            // touch state or otherwise values will be obsolete.
            resetTouchState();
            if (isPageScrollsInitialized()) {
                onPageScrollsInitialized();
            }
            return;
        }

        // Start here to avoid early returns and empty cases which have special logic
        traceBegin(Trace.TRACE_TAG_APP, "RecentsView.applyLoadPlan");

        TaskView currentTaskView = getTaskViewAt(mCurrentPage);
        int[] currentTaskIds = null;
        // Track the current DesktopTaskView through [deskId] as a desk can be empty without any
        // tasks.
        int currentTaskViewDeskId = INACTIVE_DESK_ID;
        if (currentTaskView instanceof DesktopTaskView desktopTaskView) {
            currentTaskViewDeskId = desktopTaskView.getDeskId();
        } else if (currentTaskView != null) {
            currentTaskIds = currentTaskView.getTaskIds();
        }

        TaskView ignoreResetTaskView =
                mIgnoreResetTaskId == INVALID_TASK_ID
                        ? null : getTaskViewByTaskId(mIgnoreResetTaskId);

        // Save running task ID if it exists before rebinding all taskViews, otherwise the task from
        // the runningTaskView currently bound could get assigned to another TaskView
        TaskView runningTaskView = getRunningTaskView();
        int[] runningTaskIds = null;

        // Track the running TaskView through [deskId] as a desk can be empty without any tasks.
        int runningTaskViewDeskId = INACTIVE_DESK_ID;
        if (runningTaskView instanceof DesktopTaskView desktopTaskView) {
            runningTaskViewDeskId = desktopTaskView.getDeskId();
        } else if (runningTaskView != null) {
            runningTaskIds = runningTaskView.getTaskIds();
        }

        // Removing views sets the currentPage to 0, so we save this and restore it after
        // the new set of views are added
        int previousCurrentPage = mCurrentPage;
        int previousFocusedPage = indexOfChild(getFocusedChild());
        // TaskIds will no longer be valid after remove and re-add, clearing mTopRowIdSet.
        mAnyTaskHasBeenDismissed = false;
        mTopRowIdSet.clear();
        traceBegin(Trace.TRACE_TAG_APP, "RecentsView.applyLoadPlan.removeAllViews");
        removeAllViews();
        traceEnd(Trace.TRACE_TAG_APP);
        // If we are entering Overview as a result of initiating a split from somewhere else
        // (e.g. split from Home), we need to make sure the staged app is not drawn as a thumbnail.
        int stagedTaskIdToBeRemoved;
        if (isSplitSelectionActive()) {
            stagedTaskIdToBeRemoved = mSplitSelectStateController.getInitialTaskId();
            updateCurrentTaskActionsVisibility();
        } else {
            stagedTaskIdToBeRemoved = INVALID_TASK_ID;
        }

        // Clear out desktop view if it is set

        // Move Desktop Tasks to the end of the list
        taskGroups = mUtils.sortDesktopTasksToFront(taskGroups);

        if (mAddDesktopButton != null) {
            // Add `mAddDesktopButton` as the first child.
            addView(mAddDesktopButton);
        }
        traceBegin(Trace.TRACE_TAG_APP, "RecentsView.applyLoadPlan.forLoop");

        // Add views as children based on whether it's grouped or single task. Looping through
        // taskGroups backwards populates the thumbnail grid from least recent to most recent.
        for (int i = taskGroups.size() - 1; i >= 0; i--) {
            GroupTask groupTask = taskGroups.get(i);
            boolean containsStagedTask = stagedTaskIdToBeRemoved != INVALID_TASK_ID
                    && groupTask.containsTask(stagedTaskIdToBeRemoved);
            boolean shouldSkipGroupTask = containsStagedTask && groupTask instanceof SingleTask;

            if ((isSplitSelectionActive() && groupTask.taskViewType == TaskViewType.DESKTOP)
                    || shouldSkipGroupTask) {
                // To avoid these tasks from being chosen as the app pair, the creation of a
                // TaskView is bypassed. The staged task is already selected for the app pair,
                // and the Desktop task should be hidden when selecting a pair.
                continue;
            }

            // If we need to remove half of a pair of tasks, force a TaskView with Type.SINGLE
            // to be a temporary container for the remaining task.
            traceBegin(Trace.TRACE_TAG_APP, "RecentsView.applyLoadPlan.forLoop.createTaskView");
            TaskView taskView = getTaskViewFromPool(
                    containsStagedTask ? TaskViewType.SINGLE : groupTask.taskViewType);
            traceEnd(Trace.TRACE_TAG_APP);
            traceBegin(Trace.TRACE_TAG_APP, "RecentsView.applyLoadPlan.forLoop.bind");
            if (taskView instanceof GroupedTaskView groupedTaskView) {
                groupedTaskView.bind((SplitTask) groupTask, mOrientationState, mTaskOverlayFactory);
            } else if (taskView instanceof DesktopTaskView desktopTaskView) {
                desktopTaskView.bind((DesktopTask) groupTask, mOrientationState,
                        mTaskOverlayFactory);
            } else if (groupTask instanceof SplitTask splitTask) {
                Task task = splitTask.getTopLeftTask().key.id == stagedTaskIdToBeRemoved
                        ? splitTask.getBottomRightTask()
                        : splitTask.getTopLeftTask();
                taskView.bind(new SingleTask(task), mOrientationState, mTaskOverlayFactory);
            } else {
                taskView.bind((SingleTask) groupTask, mOrientationState, mTaskOverlayFactory);
            }
            traceEnd(Trace.TRACE_TAG_APP);
            traceBegin(Trace.TRACE_TAG_APP, "RecentsView.applyLoadPlan.forLoop.addTaskView");
            addView(taskView);
            traceEnd(Trace.TRACE_TAG_APP);
        }
        // For loop end trace
        traceEnd(Trace.TRACE_TAG_APP);

        addView(mClearAllButton);

        traceBegin(Trace.TRACE_TAG_APP, "RecentsView.applyLoadPlan.layouts");
        updateTaskSize();
        mUtils.updateChildTaskOrientations();
        traceEnd(Trace.TRACE_TAG_APP);

        TaskView newRunningTaskView = mUtils.getDesktopTaskViewForDeskId(runningTaskViewDeskId);
        if (newRunningTaskView == null) {
            // Update mRunningTaskViewId to be the new TaskView that was assigned by binding
            // the full list of tasks to taskViews
            newRunningTaskView = getTaskViewByTaskIds(runningTaskIds);
        }
        if (newRunningTaskView != null) {
            setRunningTaskViewId(newRunningTaskView.getTaskViewId());
        } else {
            if (mActiveGestureGroupedTaskInfo != null) {
                // This will update mRunningTaskViewId and create a stub view if necessary.
                // We try to avoid this because it can cause a scroll jump, but it is needed
                // for cases where the running task isn't included in this load plan (e.g. if
                // the current running task is excludedFromRecents.)
                showCurrentTask(mActiveGestureGroupedTaskInfo, "applyLoadPlan");
                newRunningTaskView = getRunningTaskView();
            } else {
                setRunningTaskViewId(INVALID_TASK_ID);
            }
        }

        int targetPage = -1;
        if (mNextPage != INVALID_PAGE) {
            // Restore mCurrentPage but don't call setCurrentPage() as that clobbers the scroll.
            mCurrentPage = previousCurrentPage;
            currentTaskView = mUtils.getDesktopTaskViewForDeskId(currentTaskViewDeskId);
            if (currentTaskView == null) {
                currentTaskView = getTaskViewByTaskIds(currentTaskIds);
            }
            if (currentTaskView != null) {
                targetPage = indexOfChild(currentTaskView);
            }
        } else if (previousFocusedPage != INVALID_PAGE) {
            targetPage = previousFocusedPage;
        } else {
            targetPage = indexOfChild(mUtils.getExpectedCurrentTask(newRunningTaskView));
        }
        if (targetPage != -1 && mCurrentPage != targetPage) {
            int finalTargetPage = targetPage;
            runOnPageScrollsInitialized(() -> setCurrentPage(finalTargetPage));
        }

        traceBegin(Trace.TRACE_TAG_APP, "RecentsView.applyLoadPlan.cleanupStates");
        if (mIgnoreResetTaskId != INVALID_TASK_ID &&
                getTaskViewByTaskId(mIgnoreResetTaskId) != ignoreResetTaskView) {
            // If the taskView mapping is changing, do not preserve the visuals. Since we are
            // mostly preserving the first task, and new taskViews are added to the end, it should
            // generally map to the same task.
            mIgnoreResetTaskId = INVALID_TASK_ID;
        }

        mAppliedTaskListChangeId = taskListChangeId;
        resetTaskVisuals();
        onTaskStackUpdated();
        updateEnabledOverlays();
        if (isPageScrollsInitialized()) {
            onPageScrollsInitialized();
        }
        traceEnd(Trace.TRACE_TAG_APP);

        // applyLoadPlan end trace
        traceEnd(Trace.TRACE_TAG_APP);
    }

    private boolean isModal() {
        return mTaskModalness > 0;
    }

    protected void removeAllTaskViews() {
        // This handles an edge case where applyLoadPlan happens during a gesture when the only
        // Task is one with excludeFromRecents, in which case we should not remove it.
        CollectionsKt
                .filter(getTaskViews(), taskView -> !isGestureActive() || !taskView.isRunningTask())
                .forEach(this::removeView);
        if (!hasTaskViews()) {
            removeView(mAddDesktopButton);
            removeView(mClearAllButton);
        }
    }

    /** Returns true if there are at least one TaskView has been added to the RecentsView. */
    public boolean hasTaskViews() {
        return mUtils.hasTaskViews();
    }

    public int getTaskViewCount() {
        return mTaskViewCount;
    }

    /** Counts {@link TaskView}s that are not {@link DesktopTaskView} instances. */
    public int getNonDesktopTaskViewCount() {
        return mUtils.getNonDesktopTaskViewCount();
    }

    /** Counts {@link TaskView}s that are {@link DesktopTaskView} instances. */
    public int getDesktopTaskViewCount() {
        return mUtils.getDesktopTaskViewCount();
    }

    /**
     * Returns the number of tasks in the top row of the overview grid.
     */
    public int getTopRowTaskCountForTablet() {
        return mTopRowIdSet.size();
    }

    /**
     * Returns the number of tasks in the bottom row of the overview grid.
     */
    public int getBottomRowTaskCountForTablet() {
        return getTaskViewCount() - mTopRowIdSet.size();
    }

    protected void onTaskStackUpdated() {
        // Lazily update the empty message only when the task stack is reapplied
        updateEmptyMessage();
    }

    protected void resetTaskVisuals(TaskView taskView) {
        taskView.resetViewTransforms();
        taskView.setIconVisibleForGesture(mTaskIconVisible);
        taskView.setStableAlpha(mContentAlpha);
        taskView.setFullscreenProgress(mFullscreenProgress);
        taskView.setModalness(mTaskModalness);
        taskView.setTaskThumbnailSplashAlpha(mTaskThumbnailSplashAlpha);
        taskView.setBorderEnabled(mBorderEnabled);

        if (taskView instanceof DesktopTaskView desktopTaskView) {
            desktopTaskView.setExplodeProgress(mUtils.getDeskExplodeProgress());
        }
    }

    public void resetTaskVisuals() {
        for (TaskView taskView : getTaskViews()) {
            if (Arrays.stream(taskView.getTaskIds()).noneMatch(
                    taskId -> taskId == mIgnoreResetTaskId)) {
                resetTaskVisuals(taskView);
            }
        }
        // resetTaskVisuals is called at the end of dismiss animation which could update
        // primary and secondary translation of the live tile cut out. We will need to do so
        // here accordingly.
        runActionOnRemoteHandles(remoteTargetHandle -> {
            TaskViewSimulator simulator = remoteTargetHandle.getTaskViewSimulator();
            simulator.taskPrimaryTranslation.value = 0;
            simulator.taskSecondaryTranslation.value = 0;
            simulator.fullScreenProgress.value = 0;
            simulator.recentsViewScale.value = 1;
        });
        // Reapply runningTask related attributes as they might have been reset by
        // resetViewTransforms().
        setRunningTaskViewShowScreenshot(mRunningTaskShowScreenshot);
        applyAttachAlpha();

        updateCurveProperties();
        // Update the set of visible task's data
        loadVisibleTaskData();
        setTaskModalness(0);
        setColorTint(0);
    }

    public void setFullscreenProgress(float fullscreenProgress) {
        mFullscreenProgress = fullscreenProgress;
        for (TaskView taskView : getTaskViews()) {
            taskView.setFullscreenProgress(mFullscreenProgress);
        }
        mClearAllButton.setFullscreenProgress(fullscreenProgress);

        // Fade out the actions view quickly (0.1 range)
        mActionsView.getFullscreenAlpha().updateValue(
                mapToRange(fullscreenProgress, 0, 0.1f, 1f, 0f, LINEAR));
    }

    private void updateTaskStackListenerState() {
        boolean handleTaskStackChanges = isAttachedToWindow()
                && ((mOverviewStateEnabled && getWindowVisibility() == VISIBLE)
                || mActiveGestureGroupedTaskInfo != null);
        if (handleTaskStackChanges != mHandleTaskStackChanges) {
            Log.d(TAG, "updateTaskStackListenerState: " + handleTaskStackChanges);
            mHandleTaskStackChanges = handleTaskStackChanges;
            if (handleTaskStackChanges) {
                reloadIfNeeded();
            }
        }
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);

        // Update DeviceProfile dependant state.
        DeviceProfile dp = mContainer.getDeviceProfile();
        setOverviewGridEnabled(
                getStateManager().getState().displayOverviewTasksAsGrid(dp));
        mActionsView.updateHiddenFlags(HIDDEN_ACTIONS_IN_MENU,
                dp.getDeviceProperties().isLargeScreen());
        setPageSpacing(dp.getOverviewProfile().getPageSpacing());

        // Propagate DeviceProfile change event.
        runActionOnRemoteHandles(
                remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator().setDp(dp));
        mOrientationState.setDeviceProfile(dp);

        // Update RecentsView and TaskView's DeviceProfile dependent layout.
        updateOrientationHandler();
        mActionsView.updateDimension(dp, mLastComputedTaskSize);
    }

    private void updateOrientationHandler() {
        updateOrientationHandler(true);
    }

    private void updateOrientationHandler(boolean forceRecreateDragLayerControllers) {
        // Handle orientation changes.
        RecentsPagedOrientationHandler oldOrientationHandler = getPagedOrientationHandler();
        setOrientationHandler(mOrientationState.getOrientationHandler());

        mIsRtl = getPagedOrientationHandler().getRecentsRtlSetting(getResources());
        setLayoutDirection(mIsRtl
                ? View.LAYOUT_DIRECTION_RTL
                : View.LAYOUT_DIRECTION_LTR);
        mClearAllButton.setLayoutDirection(mIsRtl
                ? View.LAYOUT_DIRECTION_LTR
                : View.LAYOUT_DIRECTION_RTL);
        mClearAllButton.setRotation(getPagedOrientationHandler().getDegreesRotated());

        boolean isOrientationHandlerChanged =
                !getPagedOrientationHandler().equals(oldOrientationHandler);
        if (forceRecreateDragLayerControllers || isOrientationHandlerChanged) {
            // Changed orientations, update controllers so they intercept accordingly.
            mContainer.getDragLayer().recreateControllers();
            onOrientationChanged();
            resetTaskVisuals();
            // Log fake orientation changed.
            if (isOrientationHandlerChanged) {
                logOrientationChanged();
            }
        }

        mActionsView.updateHiddenFlags(
                HIDDEN_NON_ZERO_ROTATION,
                mOrientationState.shouldHideActionButtons()
        );

        // Recalculate DeviceProfile dependent layout.
        updateSizeAndPadding();

        // Update TaskView's DeviceProfile dependent layout.
        mUtils.updateChildTaskOrientations();

        requestLayout();
        // Reapply the current page to update page scrolls.
        setCurrentPage(mCurrentPage);
    }

    private void onOrientationChanged() {
        // If overview is in modal state when rotate, reset it to overview state without running
        // animation.
        setModalStateEnabled(/* taskId= */ INVALID_TASK_ID, /* animate= */ false);
        if (isSplitSelectionActive()) {
            onRotateInSplitSelectionState();
        }
    }

    // Update task size and padding that are dependent on DeviceProfile and insets.
    private void updateSizeAndPadding() {
        DeviceProfile dp = mContainer.getDeviceProfile();
        getTaskSize(mLastComputedTaskSize);
        mTaskWidth = mLastComputedTaskSize.width();
        mTaskHeight = mLastComputedTaskSize.height();
        setPadding(mLastComputedTaskSize.left - mInsets.left,
                mLastComputedTaskSize.top - mInsets.top,
                dp.getDeviceProperties().getWidthPx() - mInsets.right - mLastComputedTaskSize.right,
                dp.getDeviceProperties().getHeightPx() - mInsets.bottom - mLastComputedTaskSize.bottom);

        mContainerInterface.calculateGridSize(dp, mLastComputedGridSize);
        mContainerInterface.calculateGridTaskSize(mContainer, dp, mLastComputedGridTaskSize,
                getPagedOrientationHandler());

        mTaskGridVerticalDiff = mLastComputedGridTaskSize.top - mLastComputedTaskSize.top;
        mTopBottomRowHeightDiff = mLastComputedGridTaskSize.height()
                + dp.getOverviewProfile().getRowSpacing();

        // Force TaskView to update size from thumbnail
        updateTaskSize();
        updatePivots();
    }

    /**
     * Updates TaskView scaling and translation required to support variable width.
     */
    protected void updateTaskSize() {
        if (!hasTaskViews()) {
            return;
        }

        float accumulatedTranslationX = 0;
        for (TaskView taskView : getTaskViews()) {
            taskView.updateTaskSize(mLastComputedTaskSize, mLastComputedGridTaskSize);
            taskView.setNonGridTranslationX(accumulatedTranslationX);
            // Compensate space caused by TaskView scaling.
            float widthDiff =
                    taskView.getLayoutParams().width * (1 - taskView.getNonGridScale());
            accumulatedTranslationX += mIsRtl ? widthDiff : -widthDiff;
        }

        mClearAllButton.setFullscreenTranslationPrimary(accumulatedTranslationX);

        float taskAlignmentTranslationY = getTaskAlignmentTranslationY();
        mClearAllButton.setTaskAlignmentTranslationY(taskAlignmentTranslationY);
        if (mAddDesktopButton != null) {
            mAddDesktopButton.setTranslationY(taskAlignmentTranslationY);
        }

        updateGridProperties();
    }

    public void getTaskSize(Rect outRect) {
        mContainerInterface.calculateTaskSize(mContainer, mContainer.getDeviceProfile(), outRect,
                getPagedOrientationHandler());
    }

    /**
     * Returns the currently selected TaskView in Select mode.
     */
    @Nullable
    public TaskView getSelectedTaskView() {
        return mUtils.getSelectedTaskView();
    }

    /**
     * Sets the selected TaskView in Select mode.
     */
    public void setSelectedTask(int lastSelectedTaskId) {
        mUtils.setSelectedTaskView(getTaskViewByTaskId(lastSelectedTaskId));
    }

    /**
     * Get the Y translation that should be applied to the non-TaskView item inside the RecentsView
     * (ClearAllButton and AddDesktopButton) in the original layout position, before scrolling. This
     * is done to make sure the button is aligned to the middle of Task thumbnail in y coordinate.
     */
    private float getTaskAlignmentTranslationY() {
        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
        if (deviceProfile.getDeviceProperties().isLargeScreen()) {
            return deviceProfile.getOverviewProfile().getRowSpacing();
        }
        return 0f;
    }

    protected Rect getTaskBounds(TaskView taskView) {
        int selectedPage = indexOfChild(taskView);
        int primaryScroll = getPagedOrientationHandler().getPrimaryScroll(this);
        int selectedPageScroll = getScrollForPage(selectedPage);
        boolean isTopRow = mTopRowIdSet.contains(taskView.getTaskViewId());
        Rect outRect = new Rect(
                taskView.isGridTask() ? mLastComputedGridTaskSize : mLastComputedTaskSize);
        outRect.offset(
                -(primaryScroll - (selectedPageScroll + getOffsetFromScrollPosition(selectedPage))),
                (int) (showAsGrid() && !isTopRow ? mTopBottomRowHeightDiff : 0));
        return outRect;
    }

    /** Gets the last computed task size */
    public Rect getLastComputedTaskSize() {
        return mLastComputedTaskSize;
    }

    public Rect getLastComputedGridTaskSize() {
        return mLastComputedGridTaskSize;
    }

    /** Gets the task size for modal state. */
    public void getModalTaskSize(Rect outRect) {
        mContainerInterface.calculateModalTaskSize(mContainer, mContainer.getDeviceProfile(),
                outRect, getPagedOrientationHandler());
    }

    @Override
    protected boolean computeScrollHelper() {
        boolean scrolling = super.computeScrollHelper();
        boolean isFlingingFast = false;
        updateCurveProperties();
        if (scrolling || isHandlingTouch()) {
            if (scrolling) {
                // Check if we are flinging quickly to disable high res thumbnail loading
                isFlingingFast = mScroller.getCurrVelocity() > mFastFlingVelocity;
            }

            // After scrolling, update the visible task's data
            loadVisibleTaskData();

            recalculateTaskViewScreenEdgeIntersections();
        }

        // Update the high res thumbnail loader state
        if (enableLowResThumbnailPreloading()) {
            mRecentsViewModel.setHighResThumbnailsRequired(!isFlingingFast);
        } else {
            mRecentsModel.getThumbnailCache().getHighResLoadingState().setFlingingFast(
                    isFlingingFast);
        }
        return scrolling;
    }

    private void recalculateTaskViewScreenEdgeIntersections() {
        RecentsPagedOrientationHandler pagedOrientationHandler = getPagedOrientationHandler();
        final int pageOrientedSize = pagedOrientationHandler.getMeasuredSize(this);
        final int screenStart = pagedOrientationHandler.getPrimaryScroll(this);
        final int screenEnd = screenStart + pageOrientedSize;
        final boolean showAsGrid = showAsGrid();
        final boolean showAsFullscreen = showAsFullscreen();

        getTaskViews().forEachWithIndexInParent((index, taskView) -> {
            float taskSize = pagedOrientationHandler.getMeasuredSize(taskView)
                    * taskView.getSizeAdjustment(showAsFullscreen);
            float taskStart = (pagedOrientationHandler.getChildStart(taskView)
                    + taskView.getOffsetAdjustment(showAsGrid));
            float taskEnd = taskStart + taskSize;

            boolean intersectsEndOfScreen = taskStart < screenEnd && screenEnd < taskEnd;
            boolean intersectsStartOfScreen = taskStart < screenStart && screenStart < taskEnd;
            boolean intersectsEdgeOfScreen = intersectsEndOfScreen || intersectsStartOfScreen;
            taskView.onIntersectScreenEdgeChanged(intersectsEdgeOfScreen);
        });
    }

    protected OverviewActionsView getActionsView() {
        return mActionsView;
    }

    /**
     * Scales and adjusts translation of adjacent pages as if on a curved carousel.
     */
    public void updateCurveProperties() {
        if (getPageCount() == 0 || getPageAt(0).getMeasuredWidth() == 0) {
            return;
        }
        int scroll = getPagedOrientationHandler().getPrimaryScroll(this);
        mClearAllButton.onRecentsViewScroll(scroll, mOverviewGridEnabled);

        // Clear all button alpha was set by the previous line.
        mActionsView.getIndexScrollAlpha().updateValue(1 - mClearAllButton.getScrollAlpha());
    }

    @Override
    protected int getDestinationPage(int scaledScroll) {
        if (!mContainer.getDeviceProfile().getDeviceProperties().isLargeScreen()) {
            return super.getDestinationPage(scaledScroll);
        }
        if (!isPageScrollsInitialized()) {
            Log.e(TAG,
                    "Cannot get destination page: RecentsView not properly initialized",
                    new IllegalStateException());
            return INVALID_PAGE;
        }

        // When in tablet with variable task width, return the page which scroll is closest to
        // screenStart instead of page nearest to center of screen.
        int minDistanceFromScreenStart = Integer.MAX_VALUE;
        int minDistanceFromScreenStartIndex = INVALID_PAGE;
        for (int i = 0; i < getChildCount(); ++i) {
            // Do not set the destination page to the AddDesktopButton, which has the same page
            // scrolls as the first [TaskView] and shouldn't be scrolled to.
            if (getChildAt(i) instanceof AddDesktopButton) {
                continue;
            }
            int distanceFromScreenStart = Math.abs(mPageScrolls[i] - scaledScroll);
            if (distanceFromScreenStart < minDistanceFromScreenStart) {
                minDistanceFromScreenStart = distanceFromScreenStart;
                minDistanceFromScreenStartIndex = i;
            }
        }
        return minDistanceFromScreenStartIndex;
    }

    /**
     * Iterates through all the tasks, and loads the associated task data for newly visible tasks,
     * and unloads the associated task data for tasks that are no longer visible.
     */
    public void loadVisibleTaskData() {
        boolean hasLeftOverview = !mOverviewStateEnabled && mScroller.isFinished();
        if (hasLeftOverview || mAppliedTaskListChangeId == -1) {
            // Skip loading visible task data if we've already left the overview state, or if the
            // task list hasn't been loaded yet (the task views will not reflect the task list)
            return;
        }
        mRecentsViewModel.updateVisibleTasks(mUtils.getVisibleTaskIds());
    }

    @Override
    public void onHighResLoadingStateChanged(boolean enabled) {
        if (enableLowResThumbnailPreloading()) return;

        // Preload cache so when user goes to overview, the task thumbnails appear without delay
        if (mRecentsViewModel.getVisibleTaskIds().isEmpty()) {
            mRecentsModel.preloadCacheIfNeeded();
        }
    }

    public void startHome() {
        startHome(mContainer.isStarted(), /* onHomeAnimationComplete= */ null);
    }

    public void startHome(@Nullable Runnable onHomeAnimationComplete) {
        startHome(mContainer.isStarted(), onHomeAnimationComplete);
    }

    public void startHome(boolean animated, @Nullable Runnable onHomeAnimationComplete) {
        if (!canStartHomeSafely()) {
            if (onHomeAnimationComplete != null) {
                onHomeAnimationComplete.run();
            }
            return;
        }
        try {
            if (mUtils.isInDesktopFirstMode()) {
                // Always attempt to return to desktop in desktop-first mode, and fallback to
                // startHome if it cannot be performed.
                RunnableList runnableList = returnToDesktop();
                if (runnableList != null) {
                    if (onHomeAnimationComplete != null) {
                        runnableList.add(onHomeAnimationComplete);
                    }
                    return;
                }
            }

            InteractionJankMonitorWrapper.begin(this, CUJ_LAUNCHER_RECENTS_TO_HOME);
            mContainer.startHome(animated, () -> {
                InteractionJankMonitorWrapper.end(CUJ_LAUNCHER_RECENTS_TO_HOME);
                if (onHomeAnimationComplete != null) {
                    onHomeAnimationComplete.run();
                }
            });
        } finally {
            AbstractFloatingView.closeAllOpenViews(mContainer, mContainer.isStarted());
        }
    }

    /** Returns whether user can start home based on state in {@link OverviewCommandHelper}. */
    protected abstract boolean canStartHomeSafely();

    /** Returns the state manager used in RecentsView **/
    public abstract StateManager<STATE_TYPE,
            ? extends StatefulContainer<STATE_TYPE>> getStateManager();

    public void reset() {
        Log.d(TAG, "reset - mEnableDrawingLiveTile: " + mEnableDrawingLiveTile
                + ", mRecentsAnimationController: " + mRecentsAnimationController);
        setCurrentTask(-1);
        mCurrentPageScrollDiff = 0;
        mIgnoreResetTaskId = -1;
        mAppliedTaskListChangeId = -1;
        mAnyTaskHasBeenDismissed = false;
        setTaskIconVisible(true);
        setActiveGestureGroupedTaskInfo(null);
        if (mAddDesktopButton != null) {
            mAddDesktopButton.setGestureAlpha(1f);
        }
        setKeyboardFocusTask(KeyboardFocusTask.Unfocused.INSTANCE);

        if (mEnableDrawingLiveTile && mRecentsAnimationController != null) {
            // We own mRecentsAnimationController, finish it now to clean up.
            finishRecentsAnimation(true /* toHome */, null);
        } else {
            // We don't own mRecentsAnimationController, just clear the reference.
            if (mRecentsAnimationController != null) {
                Log.d(TAG, "reset "
                        + "- clean up mRecentsAnimationController: " + mRecentsAnimationController
                        + ", partial trace:\n"
                        + getTrimmedStackTrace("RecentsView.reset"));
                mRecentsAnimationController = null;
            }
            cleanupRemoteTargets();
        }
        setEnableDrawingLiveTile(false);
        mBlurUtils.setDrawLiveTileBelowRecents(false);
        mClearAllButton.setSplitSelectionActive(false);

        // TODO(b/391842220): This should not need to be explicitly called from here. When TVs
        //  are added and removed with the RecentsView lifecycle, this can be removed.
        //  This is was added because without it cancelling jobs was happening after work was
        //  scheduled for those jobs resulting in delays.
        mUtils.getTaskViews().forEach(TaskView::cancelJobs);

        // These are relatively expensive and don't need to be done this frame (RecentsView isn't
        // visible anyway), so defer by a frame to get off the critical path, e.g. app to home.
        // Defer onto the main thread rather than the view message queue since this will not always
        // be called in the Recents In Window case.
        MAIN_EXECUTOR.getHandler().post(this::onReset);
    }

    private void onReset() {
        setCurrentPage(0);
        LayoutUtils.setViewEnabled(mActionsView, true);
        if (mOrientationState.setGestureActive(false)) {
            updateOrientationHandler(/* forceRecreateDragLayerControllers = */ false);
        }
        mRecentsViewModel.onReset();
        executeSideTaskLaunchCallback();
    }

    public int getRunningTaskViewId() {
        return mRunningTaskViewId;
    }

    protected int[] getTaskIdsForRunningTaskView() {
        return getTaskIdsForTaskViewId(mRunningTaskViewId);
    }

    private int[] getTaskIdsForTaskViewId(int taskViewId) {
        // For now 2 distinct task IDs is max for split screen
        TaskView runningTaskView = getTaskViewFromTaskViewId(taskViewId);
        if (runningTaskView == null) {
            return new int[0];
        }

        return runningTaskView.getTaskIds();
    }

    public @Nullable TaskView getRunningTaskView() {
        return getTaskViewFromTaskViewId(mRunningTaskViewId);
    }

    @Nullable
    TaskView getTaskViewFromTaskViewId(int taskViewId) {
        if (taskViewId == -1) {
            return null;
        }

        for (TaskView taskView : getTaskViews()) {
            if (taskView.getTaskViewId() == taskViewId) {
                return taskView;
            }
        }
        return null;
    }

    public int getRunningTaskIndex() {
        TaskView taskView = getRunningTaskView();
        return taskView == null ? -1 : indexOfChild(taskView);
    }

    protected @Nullable TaskView getHomeTaskView() {
        return null;
    }

    /**
     * Handle the edge case where Recents could increment task count very high over long
     * period of device usage. Probably will never happen, but meh.
     */
    protected TaskView getTaskViewFromPool(TaskViewType type) {
        TaskView taskView = switch (type) {
            case GROUPED -> mGroupedTaskViewPool.getView();
            case DESKTOP -> mDesktopTaskViewPool.getView();
            default -> mTaskViewPool.getView();
        };
        taskView.initialiseInjectables(mContainer.getActivityComponent());
        taskView.setTaskViewId(mTaskViewIdCount);
        if (mTaskViewIdCount == Integer.MAX_VALUE) {
            mTaskViewIdCount = 0;
        } else {
            mTaskViewIdCount++;
        }

        return taskView;
    }

    /**
     * Get the index of the task view whose id matches {@param taskId}.
     *
     * @return -1 if there is no task view for the task id, else the index of the task view.
     */
    public int getTaskIndexForId(int taskId) {
        TaskView tv = getTaskViewByTaskId(taskId);
        return tv == null ? -1 : indexOfChild(tv);
    }

    protected int getClearAllShortTotalWidthTranslation() {
        return mClearAllShortTotalWidthTranslation;
    }

    /**
     * Reloads the view if anything in recents changed.
     */
    public void reloadIfNeeded() {
        if (!mRecentsModel.isTaskListValid(mAppliedTaskListChangeId)) {
            mRecentsModel.getTasks(
                    this::applyLoadPlan,
                    RecentsFilterState.getDisplayIdFilter(mContainer.getDisplayId()));
            Log.d(TAG, "reloadIfNeeded - getTasks: " + mAppliedTaskListChangeId);
            mRecentsViewModel.refreshAllTaskData();
        } else {
            Log.d(TAG, "reloadIfNeeded - task list still valid: " + mAppliedTaskListChangeId);
        }
    }

    private void setActiveGestureGroupedTaskInfo(GroupedTaskInfo groupedTaskInfo) {
        mActiveGestureGroupedTaskInfo = groupedTaskInfo;
        updateTaskStackListenerState();
    }

    /**
     * Called when a gesture from an app is starting.
     */
    public void onGestureAnimationStart(GroupedTaskInfo groupedTaskInfo) {
        Log.d(TAG, "onGestureAnimationStart - groupedTaskInfo: " + groupedTaskInfo);
        setActiveGestureGroupedTaskInfo(groupedTaskInfo);

        // This needs to be called before the other states are set since it can create the task view
        if (mOrientationState.setGestureActive(true)) {
            reapplyActiveRotation();
            // Force update to ensure the initial task size is computed even if the orientation has
            // not changed.
            updateSizeAndPadding();
        }

        showCurrentTask(groupedTaskInfo, "onGestureAnimationStart");
        setEnableFreeScroll(false);
        setEnableDrawingLiveTile(false);
        setRunningTaskHidden(true);
        setTaskIconVisible(false);
        if (mAddDesktopButton != null) {
            mAddDesktopButton.setGestureAlpha(0f);
        }
    }

    /**
     * Returns whether the running task's attach alpha should be updated during the attach animation
     */
    public boolean shouldUpdateRunningTaskAlpha() {
        return getRunningTaskView() instanceof DesktopTaskView;
    }

    /**
     * Returns if a gesture is currently active.
     */
    public boolean isGestureActive() {
        return mActiveGestureGroupedTaskInfo != null;
    }

    /**
     * Called only when a swipe-up gesture from an app has completed. Only called after
     * {@link #onGestureAnimationStart} and {@link #onGestureAnimationEnd()}.
     */
    public void onSwipeUpAnimationSuccess() {
        startIconFadeInOnGestureComplete();
    }

    private void animateRecentsRotationInPlace(int newRotation) {
        if (mOrientationState.isRecentsActivityRotationAllowed()
                || mOrientationState.isLauncherFixedLandscape()) {
            // Let system take care of the rotation
            return;
        }

        if (mRunningTaskShowScreenshot) {
            animateRotation(newRotation);
        } else {
            // Animate the rotation and stops running task
            switchToScreenshot(() -> {
                animateRotation(newRotation);
                finishRecentsAnimation(true /* toHome */, false /* shouldPip */,
                        null /* onFinishComplete */);
            });
        }
    }

    private void animateRotation(int newRotation) {
        AbstractFloatingView.closeAllOpenViewsExcept(mContainer, false, TYPE_REBIND_SAFE);
        AnimatorSet pa = setRecentsChangedOrientation(true);
        pa.addListener(AnimatorListeners.forSuccessCallback(() -> {
            setLayoutRotation(newRotation, mOrientationState.getDisplayRotation());
            mContainer.getDragLayer().recreateControllers();
            setRecentsChangedOrientation(false).start();
        }));
        pa.start();
    }

    public AnimatorSet setRecentsChangedOrientation(boolean fadeOut) {
        AnimatorSet as = new AnimatorSet();
        as.play(ObjectAnimator.ofFloat(this, View.ALPHA, fadeOut ? 0 : 1));
        return as;
    }

    /**
     * Called when a gesture from an app has finished, and an end target has been determined.
     */
    public void onPrepareGestureEndAnimation(
            AnimatorSet animatorSet, GestureState.GestureEndTarget endTarget,
            RemoteTargetHandle[] remoteTargetHandles, boolean isHandlingAtomicEvent) {
        mUtils.onPrepareGestureEndAnimation(animatorSet, endTarget, remoteTargetHandles,
                isHandlingAtomicEvent);
    }

    /**
     * Called when a gesture from an app has finished, and the animation to the target has ended.
     */
    public void onGestureAnimationEnd() {
        setActiveGestureGroupedTaskInfo(null);
        if (mOrientationState.setGestureActive(false)) {
            updateOrientationHandler(/* forceRecreateDragLayerControllers = */ false);
        }

        setEnableFreeScroll(true);
        setEnableDrawingLiveTile(mCurrentGestureEndTarget == GestureState.GestureEndTarget.RECENTS);
        Log.d(TAG, "onGestureAnimationEnd - mEnableDrawingLiveTile: " + mEnableDrawingLiveTile);
        if (mEnableDrawingLiveTile) {
            mBlurUtils.setDrawLiveTileBelowRecents(true);
            redrawLiveTile();
        }
        setRunningTaskHidden(false);
        startIconFadeInOnGestureComplete();
        setTaskIconVisible(true);
        mUtils.startAddDesktopButtonFadeInOnGestureComplete();
        animateActionsViewIn();

        if (mEnableDrawingLiveTile) {
            for (TaskView taskView : getTaskViews()) {
                if (taskView instanceof DesktopTaskView desktopTaskView) {
                    desktopTaskView.setRemoteTargetHandles(mRemoteTargetHandles);
                }
            }
            TaskView runningTaskView = getRunningTaskView();
            if (showAsGrid() && runningTaskView != null) {
                runActionOnRemoteHandles(remoteTargetHandle -> {
                    TaskViewSimulator taskViewSimulator = remoteTargetHandle.getTaskViewSimulator();
                    // After settling in Overview, recentsScroll will be used to adjust horizontally
                    // location and taskGridTranslationX doesn't needs to be applied.
                    taskViewSimulator.taskGridTranslationX.value = 0;
                    taskViewSimulator.taskGridTranslationY.value =
                            runningTaskView.getGridTranslationY();
                });
            }
        } else {
            setCurrentTask(-1);
        }

        mCurrentGestureEndTarget = null;
    }

    /**
     * Returns true to avoid adding a stub task even when [getRunningTaskViewFromGroupedTaskInfo]
     * cannot find a [runningTaskView].
     */
    protected boolean shouldAvoidAddingStubTaskView(GroupedTaskInfo groupedTaskInfo) {
        return false;
    }

    /**
     * Creates a task view (if necessary) to represent the tasks with the {@param groupedTaskInfo}.
     *
     * All subsequent calls to reload will keep the task as the first item until {@link #reset()}
     * is called.  Also scrolls the view to this task.
     */
    private void showCurrentTask(GroupedTaskInfo groupedTaskInfo, String caller) {
        Log.d(TAG, "showCurrentTask(" + caller + ") - groupedTaskInfo: " + groupedTaskInfo);
        if (groupedTaskInfo == null) {
            return;
        }

        final int runningTaskViewId;
        TaskView runningTaskView = mUtils.getRunningTaskViewFromGroupTaskInfo(groupedTaskInfo);
        if (runningTaskView != null) {
            runningTaskViewId = runningTaskView.getTaskViewId();
        } else if (!shouldAvoidAddingStubTaskView(groupedTaskInfo)) {
            boolean wasEmpty = getChildCount() == 0;
            // Add an empty view for now until the task plan is loaded and applied
            final TaskView taskView;
            if (groupedTaskInfo.isBaseType(GroupedTaskInfo.TYPE_DESK)) {
                taskView = mUtils.createDesktopTaskViewForActiveDesk(groupedTaskInfo);
            } else if (groupedTaskInfo.isBaseType(GroupedTaskInfo.TYPE_SPLIT)) {
                taskView = getTaskViewFromPool(TaskViewType.GROUPED);
                // When we create a placeholder task view mSplitBoundsConfig will be null, but with
                // the actual app running we won't need to show the thumbnail until all the tasks
                // load later anyways
                ((GroupedTaskView) taskView).bind(
                        new SplitTask(Task.from(groupedTaskInfo.getTaskInfo1()),
                                Task.from(groupedTaskInfo.getTaskInfo2()), mSplitBoundsConfig),
                        mOrientationState, mTaskOverlayFactory);
            } else {
                taskView = getTaskViewFromPool(TaskViewType.SINGLE);
                taskView.bind(new SingleTask(Task.from(groupedTaskInfo.getTaskInfo1())),
                        mOrientationState, mTaskOverlayFactory);
            }
            if (mAddDesktopButton != null && wasEmpty) {
                addView(mAddDesktopButton);
            }
            addView(taskView, mUtils.getRunningTaskExpectedIndex(taskView));
            runningTaskViewId = taskView.getTaskViewId();
            if (wasEmpty) {
                addView(mClearAllButton);
            }

            // Measure and layout immediately so that the scroll values is updated instantly
            // as the user might be quick-switching
            measure(makeMeasureSpec(getMeasuredWidth(), EXACTLY),
                    makeMeasureSpec(getMeasuredHeight(), EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        } else {
            runningTaskViewId = -1;
        }

        boolean runningTaskTileHidden = mRunningTaskTileHidden;
        setCurrentTask(runningTaskViewId);

        runOnPageScrollsInitialized(() -> setCurrentPage(getRunningTaskIndex()));
        setRunningTaskViewShowScreenshot(false);
        setRunningTaskHidden(runningTaskTileHidden);
        // Update task size after setting current task.
        updateTaskSize();
        mUtils.updateChildTaskOrientations();
    }

    /**
     * Sets the running task id, cleaning up the old running task if necessary.
     */
    public void setCurrentTask(int runningTaskViewId) {
        if (mRunningTaskViewId == runningTaskViewId) {
            return;
        }

        if (mRunningTaskViewId != -1) {
            // Reset the state on the old running task view
            setTaskIconVisible(true);
            setRunningTaskViewShowScreenshot(true);
            setRunningTaskHidden(false);
        }
        setRunningTaskViewId(runningTaskViewId);
    }

    private void setRunningTaskViewId(int runningTaskViewId) {
        mRunningTaskViewId = runningTaskViewId;

        TaskView runningTaskView = getTaskViewFromTaskViewId(runningTaskViewId);
        mRecentsViewModel.updateRunningTask(
                runningTaskView != null ? runningTaskView.getTaskIdSet()
                        : Collections.emptySet());
    }

    private int getTaskViewIdFromTaskId(int taskId) {
        TaskView taskView = getTaskViewByTaskId(taskId);
        return taskView != null ? taskView.getTaskViewId() : -1;
    }

    /**
     * Hides the tile associated with {@link #mRunningTaskViewId}
     */
    public void setRunningTaskHidden(boolean isHidden) {
        mRunningTaskTileHidden = isHidden;
        // mRunningTaskAttachAlpha can be changed by RUNNING_TASK_ATTACH_ALPHA animation without
        // changing mRunningTaskTileHidden.
        mRunningTaskAttachAlpha = isHidden ? 0f : 1f;
        TaskView runningTask = getRunningTaskView();
        if (runningTask == null) {
            return;
        }
        applyAttachAlpha();
        if (!isHidden) {
            AccessibilityManagerCompat.sendCustomAccessibilityEvent(
                    runningTask, AccessibilityEvent.TYPE_VIEW_FOCUSED, null);
        }
    }

    private void applyAttachAlpha() {
        // Only hide non running task carousel when it's fully off screen, otherwise it needs to
        // be visible to move to on screen.
        mUtils.applyAttachAlpha(
                /*nonRunningTaskCarouselHidden=*/mDesktopCarouselDetachProgress == 1f);
    }

    private void setRunningTaskViewShowScreenshot(boolean showScreenshot) {
        mRunningTaskShowScreenshot = showScreenshot;
        TaskView runningTaskView = getRunningTaskView();
        if (runningTaskView != null) {
            runningTaskView.setShouldShowScreenshot(mRunningTaskShowScreenshot);
        }
        mRecentsViewModel.setRunningTaskShowScreenshot(showScreenshot);
    }

    /**
     * Updates icon visibility when going in or out of overview.
     */
    public void setTaskIconVisible(boolean isVisible) {
        if (mTaskIconVisible != isVisible) {
            mTaskIconVisible = isVisible;
            for (TaskView taskView : getTaskViews()) {
                taskView.setIconVisibleForGesture(mTaskIconVisible);
            }
        }
    }

    private void animateActionsViewIn() {
        if (!showAsGrid()) {
            ObjectAnimator actionsViewAlphaAnimator = ObjectAnimator.ofFloat(
                    mActionsView.getVisibilityAlpha(),
                    AnimatedFloat.VALUE, 1);
            actionsViewAlphaAnimator.setDuration(TaskView.FADE_IN_ICON_DURATION);
            // Set autocancel to prevent race-conditiony setting of alpha from other animations
            actionsViewAlphaAnimator.setAutoCancel(true);
            actionsViewAlphaAnimator.start();
        }
    }

    /**
     * Updates icon visibility when gesture is settled.
     */
    public void startIconFadeInOnGestureComplete() {
        mTaskIconVisible = true;
        for (TaskView taskView : getTaskViews()) {
            taskView.startIconFadeInOnGestureComplete();
        }
    }

    /**
     * Updates TaskView and ClearAllButtion scaling and translation required to turn into grid
     * layout.
     * Skips rebalance.
     */
    protected void updateGridProperties() {
        updateGridProperties(null);
    }

    /**
     * Updates TaskView and ClearAllButton scaling and translation required to turn into grid
     * layout.
     * This method only calculates the potential position and depends on {@link #setGridProgress} to
     * apply the actual scaling and translation.
     *
     * @param lastVisibleTaskViewDuringDismiss which TaskView to start rebalancing from. Use
     *                                         `null` to skip rebalance.
     */
    protected void updateGridProperties(TaskView lastVisibleTaskViewDuringDismiss) {
        if (!hasTaskViews()) {
            return;
        }

        DeviceProfile deviceProfile = mContainer.getDeviceProfile();

        int topRowWidth = 0;
        int bottomRowWidth = 0;
        int largeTileRowWidth = 0;
        float topAccumulatedTranslationX = 0;
        float bottomAccumulatedTranslationX = 0;

        // Horizontal grid translation for each task.
        Map<TaskView, Float> gridTranslations = new HashMap<>();

        TaskView lastLargeTaskView = mUtils.getLastLargeTaskView();
        int lastLargeTaskViewShift = 0;
        int largeTaskWidthAndSpacing = 0;
        int snappedTaskRowWidth = 0;
        int expectedSnapReferenceTaskRowWidth = 0;
        TaskView snappedTaskView = isKeyboardTaskFocusPending()
                ? getKeyboardFocusTaskView() : getNextPageTaskView();
        TaskView homeTaskView = getHomeTaskView();
        boolean encounteredHomeTaskView = false;
        // Determine the first grid task, or the last desktop task when there are no grid tasks, and
        // ensure it can be snapped to its expected position.
        TaskView snapReferenceTaskView = getFirstNonDesktopTaskView();
        if (snapReferenceTaskView == null) {
            snapReferenceTaskView = getLastDesktopTaskView();
        }

        // Don't clear the top row, if the user has dismissed a task, to maintain the task order.
        if (!mAnyTaskHasBeenDismissed) {
            mTopRowIdSet.clear();
        }

        // Consecutive task views in the top row or bottom row, which means another one set will
        // be cleared up while starting to add TaskViews to one of them. Also means only one of
        // them can be non-empty at most.
        Set<TaskView> lastTopTaskViews = new HashSet<>();
        Set<TaskView> lastBottomTaskViews = new HashSet<>();

        int largeTasksCount = 0;
        // True if the last large TaskView has been visited during the TaskViews iteration.
        boolean encounteredLastLargeTaskView = false;
        // True if the highest index visible TaskView has been visited during the TaskViews
        // iteration.
        boolean encounteredLastVisibleTaskView = false;
        for (TaskView taskView : getTaskViews()) {
            if (taskView == lastLargeTaskView) {
                encounteredLastLargeTaskView = true;
            }
            if (taskView == lastVisibleTaskViewDuringDismiss) {
                encounteredLastVisibleTaskView = true;
            }
            float gridTranslation = 0f;
            int taskWidthAndSpacing = taskView.getLayoutParams().width + mPageSpacing;
            // Evenly distribute tasks between rows unless rearranging due to task dismissal, in
            // which case keep tasks in their respective rows. For the running task, don't join
            // the grid.
            if (taskView.isLargeTile()) {
                largeTasksCount++;
                // DesktopTaskView`s are hidden during split select state, so we shouldn't count
                // them when calculating row width.
                if (!(taskView instanceof DesktopTaskView && isSplitSelectionActive())) {
                    topRowWidth += taskWidthAndSpacing;
                    bottomRowWidth += taskWidthAndSpacing;
                    largeTileRowWidth += taskWidthAndSpacing;
                }
                gridTranslation += lastLargeTaskViewShift;
                gridTranslation += mIsRtl ? taskWidthAndSpacing : -taskWidthAndSpacing;

                // Center view vertically in case it's from different orientation.
                taskView.setGridTranslationY((mLastComputedTaskSize.height()
                        - taskView.getLayoutParams().height) / 2f);

                largeTaskWidthAndSpacing = taskWidthAndSpacing;

                if (taskView == snappedTaskView) {
                    snappedTaskRowWidth = largeTileRowWidth;
                }
                if (taskView == snapReferenceTaskView) {
                    expectedSnapReferenceTaskRowWidth = largeTileRowWidth;
                }
            } else {
                if (encounteredLastLargeTaskView) {
                    // For tasks after the last large task, shift by large task's width and spacing.
                    gridTranslation +=
                            mIsRtl ? largeTaskWidthAndSpacing : -largeTaskWidthAndSpacing;
                } else {
                    // For TaskViews before the last large TaskViews, accumulate the width and
                    // spacing to calculate the distance the large TaskView needs to shift.
                    // This could happen for example after multiple times of dismissing the
                    // large TaskView, the triggered rebalance might set a non-first TaskView
                    // inside `mChildren` as the new large TaskView.
                    lastLargeTaskViewShift += mIsRtl ? taskWidthAndSpacing : -taskWidthAndSpacing;
                }
                int taskViewId = taskView.getTaskViewId();

                boolean isTopRow;
                if (mAnyTaskHasBeenDismissed) {
                    // Rebalance the grid starting after a certain index.
                    if (encounteredLastVisibleTaskView) {
                        mTopRowIdSet.remove(taskViewId);
                        isTopRow = topRowWidth <= bottomRowWidth;
                    } else {
                        isTopRow = mTopRowIdSet.contains(taskViewId);
                    }
                } else {
                    isTopRow = topRowWidth <= bottomRowWidth;
                }

                if (isTopRow) {
                    if (homeTaskView != null && !encounteredHomeTaskView) {
                        // TaskView will be dismissed when swipe up, don't count towards row width.
                        encounteredHomeTaskView = true;
                    } else {
                        topRowWidth += taskWidthAndSpacing;
                    }
                    mTopRowIdSet.add(taskViewId);
                    taskView.setGridTranslationY(mTaskGridVerticalDiff);

                    // Move horizontally into empty space.
                    float widthOffset = 0;
                    for (TaskView bottomTaskView : lastBottomTaskViews) {
                        widthOffset += bottomTaskView.getLayoutParams().width + mPageSpacing;
                    }

                    float currentTaskTranslationX = mIsRtl ? widthOffset : -widthOffset;
                    gridTranslation += topAccumulatedTranslationX + currentTaskTranslationX;
                    topAccumulatedTranslationX += currentTaskTranslationX;
                    lastTopTaskViews.add(taskView);
                    lastBottomTaskViews.clear();
                } else {
                    bottomRowWidth += taskWidthAndSpacing;

                    // Move into bottom row.
                    taskView.setGridTranslationY(mTopBottomRowHeightDiff + mTaskGridVerticalDiff);

                    // Move horizontally into empty space.
                    float widthOffset = 0;
                    for (TaskView topTaskView : lastTopTaskViews) {
                        widthOffset += topTaskView.getLayoutParams().width + mPageSpacing;
                    }

                    float currentTaskTranslationX = mIsRtl ? widthOffset : -widthOffset;
                    gridTranslation += bottomAccumulatedTranslationX + currentTaskTranslationX;
                    bottomAccumulatedTranslationX += currentTaskTranslationX;
                    lastBottomTaskViews.add(taskView);
                    lastTopTaskViews.clear();
                }
                int taskViewRowWidth = isTopRow ? topRowWidth : bottomRowWidth;
                if (taskView == snappedTaskView) {
                    snappedTaskRowWidth = taskViewRowWidth;
                }
                if (taskView == snapReferenceTaskView) {
                    expectedSnapReferenceTaskRowWidth = taskViewRowWidth;
                }
            }
            gridTranslations.put(taskView, gridTranslation);
        }

        // We need to maintain snapped task's page scroll invariant between quick switch and
        // overview, so we sure snapped task's grid translation is 0, and add a non-fullscreen
        // translationX that is the same as snapped task's full scroll adjustment.
        float snappedTaskNonGridScrollAdjustment = 0;
        float snappedTaskGridTranslationX = 0;
        if (snappedTaskView != null) {
            snappedTaskNonGridScrollAdjustment = snappedTaskView.getScrollAdjustment(
                    /*gridEnabled=*/false);
            snappedTaskGridTranslationX = gridTranslations.getOrDefault(snappedTaskView, 0f);
        }

        // Use the accumulated translation of the row containing the last task.
        float clearAllAccumulatedTranslation = !lastTopTaskViews.isEmpty()
                ? topAccumulatedTranslationX : bottomAccumulatedTranslationX;

        // If the last task is on the shorter row, ClearAllButton will embed into the shorter row
        // which is not what we want. Compensate the width difference of the 2 rows in that case.
        float shorterRowCompensation = 0;
        if (topRowWidth <= bottomRowWidth) {
            if (!lastTopTaskViews.isEmpty()) {
                shorterRowCompensation = bottomRowWidth - topRowWidth;
            }
        } else {
            if (!lastBottomTaskViews.isEmpty()) {
                shorterRowCompensation = topRowWidth - bottomRowWidth;
            }
        }
        float clearAllShorterRowCompensation =
                mIsRtl ? -shorterRowCompensation : shorterRowCompensation;

        // If the total width is shorter than one grid's width, move ClearAllButton further away
        // accordingly. Update longRowWidth if ClearAllButton has been moved.
        float clearAllShortTotalWidthTranslation = 0;
        int longRowWidth = Math.max(topRowWidth, bottomRowWidth);

        // If snap reference task is not in the expected position (mLastComputedTaskSize) and being
        // too close to ClearAllButton, then apply extra translation to ClearAllButton.
        int rowWidthAfterSnapReferenceTask = longRowWidth - expectedSnapReferenceTaskRowWidth;
        int snapReferenceTaskWidthAndSpacing =
                (snapReferenceTaskView != null
                        ? snapReferenceTaskView.getLayoutParams().width
                        : 0
                ) + mPageSpacing;
        int firstTaskStart = mLastComputedGridSize.left + rowWidthAfterSnapReferenceTask
                + snapReferenceTaskWidthAndSpacing;
        int expectedFirstTaskStart = mLastComputedTaskSize.right;
        if (firstTaskStart < expectedFirstTaskStart) {
            mClearAllShortTotalWidthTranslation = expectedFirstTaskStart - firstTaskStart;
            clearAllShortTotalWidthTranslation = mIsRtl
                    ? -mClearAllShortTotalWidthTranslation : mClearAllShortTotalWidthTranslation;
            if (snappedTaskRowWidth == longRowWidth) {
                // Updated snappedTaskRowWidth as well if it's same as longRowWidth.
                snappedTaskRowWidth += mClearAllShortTotalWidthTranslation;
            }
            longRowWidth += mClearAllShortTotalWidthTranslation;
        } else {
            mClearAllShortTotalWidthTranslation = 0;
        }

        float clearAllTotalTranslationX =
                clearAllAccumulatedTranslation + clearAllShorterRowCompensation
                        + clearAllShortTotalWidthTranslation + snappedTaskNonGridScrollAdjustment;
        if (largeTasksCount > 0) {
            // Shift by large task's width and spacing if there are large tasks.
            clearAllTotalTranslationX +=
                    mIsRtl ? largeTaskWidthAndSpacing : -largeTaskWidthAndSpacing;
        }

        // Make sure there are enough space between snapped page and ClearAllButton, for the case
        // of swiping up after quick switch.
        if (snappedTaskView != null) {
            int distanceFromClearAll = longRowWidth - snappedTaskRowWidth;
            // ClearAllButton should be off screen when snapped task is in its snapped position.
            int minimumDistance =
                    (mIsRtl
                            ? mLastComputedTaskSize.left
                            : deviceProfile.getDeviceProperties().getWidthPx() - mLastComputedTaskSize.right)
                            - deviceProfile.getOverviewProfile().getGridSideMargin() - mPageSpacing
                            + (mTaskWidth - snappedTaskView.getLayoutParams().width)
                            - mClearAllShortTotalWidthTranslation;
            if (distanceFromClearAll < minimumDistance) {
                int distanceDifference = minimumDistance - distanceFromClearAll;
                snappedTaskGridTranslationX += mIsRtl ? distanceDifference : -distanceDifference;
            }
        }

        for (TaskView taskView : getTaskViews()) {
            taskView.setGridTranslationX(
                    gridTranslations.getOrDefault(taskView, 0f) - snappedTaskGridTranslationX
                            + snappedTaskNonGridScrollAdjustment);
        }

        if (mAddDesktopButton != null) {
            TaskView firstTaskView = getFirstTaskView();
            float translationX = 0f;
            if (firstTaskView != null) {
                translationX += firstTaskView.getGridTranslationX();
            }
            if (lastLargeTaskViewShift != 0) {
                // If the large task is inserted between `firstTaskView` and
                // `mAddDesktopButton`, shift `mAddDesktopButton` to accommodate.
                translationX += largeTaskWidthAndSpacing;
            }
            mAddDesktopButton.setGridTranslationX(translationX);
        }

        mClearAllButton.setGridTranslationPrimary(
                clearAllTotalTranslationX - snappedTaskGridTranslationX);
        mClearAllButton.setGridScrollOffset(
                mIsRtl ? mLastComputedTaskSize.left - mLastComputedGridSize.left
                        : mLastComputedTaskSize.right - mLastComputedGridSize.right);
        setGridProgress(mGridProgress);
    }

    /**
     * Moves TaskView and ClearAllButton between carousel and 2 row grid.
     *
     * @param gridProgress 0 = carousel; 1 = 2 row grid.
     */
    private void setGridProgress(float gridProgress) {
        mGridProgress = gridProgress;

        for (TaskView taskView : getTaskViews()) {
            taskView.setGridProgress(gridProgress);
        }
        mClearAllButton.setGridProgress(gridProgress);
    }

    private void setTaskThumbnailSplashAlpha(float taskThumbnailSplashAlpha) {
        mTaskThumbnailSplashAlpha = taskThumbnailSplashAlpha;
        for (TaskView taskView : getTaskViews()) {
            taskView.setTaskThumbnailSplashAlpha(taskThumbnailSplashAlpha);
        }
    }

    /**
     * Returns true if the task can be launched via swipe down gesture.
     */
    public boolean shouldSwipeDownLaunchTaskView(@Nullable TaskView taskView) {
        return mUtils.shouldSwipeDownLaunchTaskView(taskView);
    }

    public void setIgnoreResetTask(int taskId) {
        mIgnoreResetTaskId = taskId;
    }

    /**
     * Places an {@link FloatingTaskView} on top of the thumbnail for {@link #mSplitHiddenTaskView}
     * and then animates it into the split position that was desired
     */
    private void createInitialSplitSelectAnimation(PendingAnimation anim) {
        getPagedOrientationHandler().getInitialSplitPlaceholderBounds(mSplitPlaceholderSize,
                mSplitPlaceholderInset, mContainer.getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), mTempRect);
        SplitAnimationTimings timings = AnimUtils.getDeviceOverviewToSplitTimings(
                mContainer.getDeviceProfile().getDeviceProperties().isLargeScreen());

        RectF startingTaskRect = new RectF();
        safeRemoveDragLayerView(mSplitSelectStateController.getFirstFloatingTaskView());
        SplitAnimInitProps splitAnimInitProps =
                mSplitSelectStateController.getSplitAnimationController().getFirstAnimInitViews(
                        () -> mSplitHiddenTaskView, () -> mSplitSelectSource);
        if (splitAnimInitProps == null) {
            Log.w(TAG, "createInitialSplitSelectAnimation: skipping SplitAnimation as properties"
                    + " are null");
            return;
        }
        if (mSplitSelectStateController.isAnimateCurrentTaskDismissal()) {
            // Create the split select animation from Overview
            mSplitHiddenTaskView.setThumbnailVisibility(false,
                    mSplitSelectStateController.getInitialTaskId());
            anim.setViewAlpha(splitAnimInitProps.getIconView(), 0, clampToProgress(LINEAR,
                    timings.getIconFadeStartOffset(),
                    timings.getIconFadeEndOffset()));
        }

        FloatingTaskView firstFloatingTaskView = FloatingTaskView.getFloatingTaskView(mContainer,
                splitAnimInitProps.getOriginalView(),
                splitAnimInitProps.getOriginalBitmap(),
                splitAnimInitProps.getIconDrawable(), startingTaskRect);
        firstFloatingTaskView.setAlpha(1);
        firstFloatingTaskView.addStagingAnimation(anim, startingTaskRect, mTempRect,
                splitAnimInitProps.getFadeWithThumbnail(), splitAnimInitProps.isStagedTask());
        mSplitSelectStateController.setFirstFloatingTaskView(firstFloatingTaskView);

        // Allow user to click staged app to launch into fullscreen
        firstFloatingTaskView.setOnClickListener(view ->
                mSplitSelectStateController.getSplitAnimationController().
                        playAnimPlaceholderToFullscreen(mContainer, view,
                                Optional.of(() -> mSplitSelectStateController.resetState())));
        firstFloatingTaskView.setContentDescription(splitAnimInitProps.getContentDescription());

        // SplitInstructionsView: animate in
        safeRemoveDragLayerView(mSplitSelectStateController.getSplitInstructionsView());
        SplitInstructionsView splitInstructionsView =
                SplitInstructionsView.getSplitInstructionsView(mContainer);
        splitInstructionsView.setAlpha(0);
        anim.setViewAlpha(splitInstructionsView, 1, clampToProgress(LINEAR,
                timings.getInstructionsContainerFadeInStartOffset(),
                timings.getInstructionsContainerFadeInEndOffset()));
        anim.addFloat(splitInstructionsView, SplitInstructionsView.UNFOLD, 0.1f, 1,
                clampToProgress(EMPHASIZED_DECELERATE,
                        timings.getInstructionsUnfoldStartOffset(),
                        timings.getInstructionsUnfoldEndOffset()));
        mSplitSelectStateController.setSplitInstructionsView(splitInstructionsView);

        InteractionJankMonitorWrapper.begin(this, Cuj.CUJ_SPLIT_SCREEN_ENTER,
                "First tile selected");
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                switchToScreenshot(
                        () -> finishRecentsAnimation(true /* toHome */,
                                false /* shouldPip */, null /* onFinishComplete */));
            }
        });
        anim.addEndListener(success -> {
            if (success) {
                InteractionJankMonitorWrapper.end(Cuj.CUJ_SPLIT_SCREEN_ENTER);
            } else {
                // If transition to split select was interrupted, clean up to prevent glitches
                mSplitSelectStateController.resetState();
                InteractionJankMonitorWrapper.cancel(Cuj.CUJ_SPLIT_SCREEN_ENTER);
            }

            updateCurrentTaskActionsVisibility();
        });
    }

    /**
     * Hides all overview actions if user is halfway through split selection, shows otherwise.
     * We only show split option if device is large screen.
     */
    protected void updateCurrentTaskActionsVisibility() {
        TaskView taskView = getCurrentPageTaskView();
        boolean isCurrentSplit = taskView instanceof GroupedTaskView;
        GroupedTaskView groupedTaskView = isCurrentSplit ? (GroupedTaskView) taskView : null;
        // Update flags to see if entire actions bar should be hidden.
        mActionsView.updateHiddenFlags(HIDDEN_SPLIT_SELECT_ACTIVE, isSplitSelectionActive());
        // Update flags to see if actions bar should show buttons for a single task or a pair of
        // tasks.
        boolean canSaveAppPair = isCurrentSplit
                && !mIs3PLauncher
                && getSplitSelectController().getAppPairsController().canSaveAppPair(
                        groupedTaskView);
        mActionsView.updateForGroupedTask(isCurrentSplit, canSaveAppPair);

        boolean isCurrentDesktop = taskView instanceof DesktopTaskView;
        mActionsView.updateHiddenFlags(HIDDEN_DESKTOP, isCurrentDesktop);
    }

    /**
     * Iterate the grid by columns instead of by TaskView index, starting after the last large task
     * and up to the last balanced column.
     *
     * @return the highest visible TaskView between both rows
     */
    protected TaskView getHighestVisibleTaskView() {
        if (mTopRowIdSet.isEmpty()) return null; // return earlier

        TaskView lastVisibleTaskView = null;
        IntArray topRowIdArray = mUtils.getTopRowIdArray();
        IntArray bottomRowIdArray = mUtils.getBottomRowIdArray();
        int balancedColumns = Math.min(bottomRowIdArray.size(), topRowIdArray.size());

        for (int i = 0; i < balancedColumns; i++) {
            TaskView topTask = getTaskViewFromTaskViewId(topRowIdArray.get(i));

            if (isTaskViewVisible(topTask)) {
                TaskView bottomTask = getTaskViewFromTaskViewId(bottomRowIdArray.get(i));
                lastVisibleTaskView =
                        indexOfChild(topTask) > indexOfChild(bottomTask) ? topTask : bottomTask;
            } else if (lastVisibleTaskView != null) {
                break;
            }
        }

        return lastVisibleTaskView;
    }

    protected void removeGroupTaskInternal(@NonNull GroupTask groupTask) {
        Log.d(TAG, "removeGroupTaskInternal: groupTask=" + groupTask);
        UI_HELPER_EXECUTOR
                .getHandler()
                .post(
                        () -> {
                            if (groupTask instanceof DesktopTask desktopTask) {
                                SystemUiProxy.INSTANCE
                                        .get(getContext())
                                        .removeDesk(desktopTask.getDeskId(),
                                                DesktopModeTransitionSource.RECENTS);
                            } else {
                                for (Task task : groupTask.getTasks()) {
                                    ActivityManagerWrapper.getInstance().removeTask(task.key.id);
                                }
                            }
                        });
    }

    protected void onDismissAnimationEnds() {
        AccessibilityManagerCompat.sendTestProtocolEventToTest(getContext(),
                DISMISS_ANIMATION_ENDS_MESSAGE);
    }

    protected boolean snapToPageRelative(int delta, boolean cycle,
            TaskGridNavHelper.TaskNavDirection direction) {
        // Set next page if scroll animation is still running, otherwise cannot snap to the
        // next page on successive key presses. Setting the current page aborts the scroll.
        if (!mScroller.isFinished()) {
            setCurrentPage(getNextPage());
        }
        int pageCount = getPageCount();
        if (pageCount == 0) {
            return false;
        }
        final int newPageUnbound = getNextPageInternal(delta, direction, cycle);
        if (!cycle && (newPageUnbound < 0 || newPageUnbound > pageCount)) {
            return false;
        }
        final int newPage = (newPageUnbound + pageCount) % pageCount;
        snapToPage(newPage);
        View child = getChildAt(newPage);
        if (child != null) {
            // TAB focuses first focusable (or last if direction reversed), including task itself.
            if (direction == TaskGridNavHelper.TaskNavDirection.TAB) {
                List<View> visibleFocusables = mUtils.getVisibleFocusables(child,
                        delta > 0 ? FOCUS_FORWARD : FOCUS_BACKWARD);
                if (!visibleFocusables.isEmpty()) {
                    (delta > 0 ? visibleFocusables.getFirst()
                            : visibleFocusables.getLast()).requestFocus();
                    return true;
                }
            }
            child.requestFocus();
        }
        return true;
    }

    @Override
    protected boolean snapToPage(int whichPage, int delta, int duration, boolean immediate) {
        debugLog(PAGE_SCROLL_TAG,
                "snapToPage, whichPage: " + whichPage + ", delta: " + delta + ", duration: "
                        + duration + ", immediate: " + immediate);
        return super.snapToPage(whichPage, delta, duration, immediate);
    }

    private int getNextPageInternal(int delta, TaskGridNavHelper.TaskNavDirection direction,
            boolean cycle) {
        if (!showAsGrid()) {
            return getNextPage() + delta;
        }

        // Init task grid nav helper with top/bottom id arrays.
        TaskGridNavHelper taskGridNavHelper = new TaskGridNavHelper(mUtils.getTopRowIdArray(),
                mUtils.getBottomRowIdArray(), mUtils.getLargeTaskViewIds(),
                mAddDesktopButton != null);

        // Get current page's task view ID.
        TaskView currentPageTaskView = getCurrentPageTaskView();
        int currentPageTaskViewId;
        final int clearAllButtonIndex = indexOfChild(mClearAllButton);
        final int addDesktopButtonIndex = indexOfChild(mAddDesktopButton);
        if (currentPageTaskView != null) {
            currentPageTaskViewId = currentPageTaskView.getTaskViewId();
        } else if (mCurrentPage == clearAllButtonIndex) {
            currentPageTaskViewId = TaskGridNavHelper.CLEAR_ALL_PLACEHOLDER_ID;
        } else if (mCurrentPage == addDesktopButtonIndex) {
            currentPageTaskViewId = TaskGridNavHelper.ADD_DESK_PLACEHOLDER_ID;
        } else {
            return INVALID_PAGE;
        }

        final int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);
        if (nextGridPage == TaskGridNavHelper.CLEAR_ALL_PLACEHOLDER_ID) {
            return clearAllButtonIndex;
        }
        if (nextGridPage == TaskGridNavHelper.ADD_DESK_PLACEHOLDER_ID) {
            return addDesktopButtonIndex;
        }
        return indexOfChild(getTaskViewFromTaskViewId(nextGridPage));
    }

    @UiThread
    public void dismissTask(int taskId, boolean removeTask) {
        mDismissUtils.dismissTask(taskId, removeTask);
    }

    /** Dismisses the entire [taskView]. */
    public void dismissTaskView(TaskView taskView, boolean removeTask) {
        RecentsDismissUtils.SpringSet dismissSpringSet =
                mDismissUtils.createTaskDismissSpringAnimation(taskView, removeTask,
                        false /* isSplitSelection */);
        if (dismissSpringSet != null) {
            dismissSpringSet.start();
        }
    }

    @SuppressWarnings("unused")
    private void dismissAllTasks(View view) {
        InteractionJankMonitorWrapper.begin(this, Cuj.CUJ_LAUNCHER_OVERVIEW_CLEAR_ALL);
        mDismissUtils.dismissAllTasks();
        mContainer.getStatsLogManager().logger().log(LAUNCHER_TASK_CLEAR_ALL);
    }

    private void createDesk() {
        SystemUiProxy.INSTANCE
                .get(getContext())
                .createDesk(mContainer.getDisplay().getDisplayId());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isHandlingTouch() || event.getAction() != KeyEvent.ACTION_DOWN
                || getStateManager().isInTransition()) {
            return super.dispatchKeyEvent(event);
        }

        if (mUtils.taskMenuIsOpen()) {
            return super.dispatchKeyEvent(event);
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_TAB: {
                return mUtils.handleTabKeyEvent(event, super::dispatchKeyEvent);
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return snapToPageRelative(mIsRtl ? -1 : 1, true /* cycle */,
                        TaskGridNavHelper.TaskNavDirection.RIGHT);
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return snapToPageRelative(mIsRtl ? 1 : -1, true /* cycle */,
                        TaskGridNavHelper.TaskNavDirection.LEFT);
            case KeyEvent.KEYCODE_DPAD_UP:
                return snapToPageRelative(1, false /* cycle */,
                        TaskGridNavHelper.TaskNavDirection.UP);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return snapToPageRelative(1, false /* cycle */,
                        TaskGridNavHelper.TaskNavDirection.DOWN);
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL:
                mUtils.onDeleteKeyPressed();
                return true;
            case KeyEvent.KEYCODE_NUMPAD_DOT:
                if (event.isAltPressed()) {
                    // Numpad DEL pressed while holding Alt.
                    mUtils.onDeleteKeyPressed();
                    return true;
                }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction,
            @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus && getChildCount() > 0) {
            switch (direction) {
                case FOCUS_FORWARD:
                    setCurrentPage(0);
                    break;
                case FOCUS_BACKWARD:
                case FOCUS_RIGHT:
                case FOCUS_LEFT:
                    setCurrentPage(getChildCount() - 1);
                    break;
            }
        }
    }

    public float getContentAlpha() {
        return mContentAlpha;
    }

    public void setContentAlpha(float alpha) {
        if (alpha == mContentAlpha) {
            return;
        }

        traceBegin(Trace.TRACE_TAG_APP, "RecentsView.setContentAlpha");
        alpha = Utilities.boundToRange(alpha, 0, 1);
        mContentAlpha = alpha;

        for (TaskView taskView : getTaskViews()) {
            taskView.setStableAlpha(alpha);
        }
        mClearAllButton.setContentAlpha(mContentAlpha);

        if (mAddDesktopButton != null) {
            mAddDesktopButton.setContentAlpha(mContentAlpha);
        }
        if (mEmptyRecentsMessageView != null) {
            mEmptyRecentsMessageView.setAlpha(alpha);
        } else {
            int alphaInt = Math.round(alpha * 255);
            mEmptyMessagePaint.setAlpha(alphaInt);
            mEmptyIcon.setAlpha(alphaInt);
        }
        mActionsView.getContentAlpha().updateValue(mContentAlpha);

        if (alpha > 0) {
            setVisibility(VISIBLE);
        } else if (!mFreezeViewVisibility) {
            setVisibility(INVISIBLE);
        }
        traceEnd(Trace.TRACE_TAG_APP);
    }

    /**
     * Freezes the view visibility change. When frozen, the view will not change its visibility
     * to gone due to alpha changes.
     */
    public void setFreezeViewVisibility(boolean freezeViewVisibility) {
        if (mFreezeViewVisibility != freezeViewVisibility) {
            mFreezeViewVisibility = freezeViewVisibility;
            if (!mFreezeViewVisibility) {
                setVisibility(mContentAlpha > 0 ? VISIBLE : INVISIBLE);
            }
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mActionsView != null) {
            mActionsView.updateHiddenFlags(HIDDEN_NO_RECENTS, visibility != VISIBLE);
            if (visibility != VISIBLE) {
                mActionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING, false);
            }
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRecentsRotation();
        onOrientationChanged();
        updateEmptyMessageConfiguration();
        if (enableLowResThumbnailPreloading()) {
            mHelper.updateCacheSizeAndPreload(/* shouldPreloadIfNeeded= */ !mOverviewStateEnabled);
        }
    }

    /**
     * Updates {@link RecentsOrientedState}'s cached RecentsView rotation.
     */
    public void updateRecentsRotation() {
        final int rotation = TraceHelper.allowIpcs(
                "RecentsView.updateRecentsRotation", () -> mContainer.getDisplay().getRotation());
        // Log real orientation change.
        if (mOrientationState.setRecentsRotation(rotation)) {
            logOrientationChanged();
        }
    }

    public void reapplyActiveRotation() {
        mUtils.reapplyActiveRotation();
    }

    public void setLayoutRotation(int touchRotation, int displayRotation) {
        if (mOrientationState.update(touchRotation, displayRotation)) {
            updateOrientationHandler();
        }
    }

    public RecentsOrientedState getPagedViewOrientedState() {
        return mOrientationState;
    }

    public RecentsPagedOrientationHandler getPagedOrientationHandler() {
        return (RecentsPagedOrientationHandler) super.getPagedOrientationHandler();
    }

    @Nullable
    public TaskView getNextTaskView() {
        return getTaskViewAt(getRunningTaskIndex() + 1);
    }

    @Nullable
    public TaskView getPreviousTaskView() {
        return getTaskViewAt(getRunningTaskIndex() - 1);
    }

    @Nullable
    public TaskView getFirstNonDesktopTaskView() {
        return mUtils.getFirstNonDesktopTaskView();
    }

    @Nullable
    public TaskView getLastDesktopTaskView() {
        return mUtils.getLastDesktopTaskView();
    }

    @Nullable
    public TaskView getCurrentPageTaskView() {
        return getTaskViewAt(getCurrentPage());
    }

    @Nullable
    public TaskView getNextPageTaskView() {
        return getTaskViewAt(getNextPage());
    }

    @Nullable
    public TaskView getFirstTaskView() {
        return mUtils.getFirstTaskView();
    }

    public int getFirstTaskViewIndex() {
        return indexOfChild(getFirstTaskView());
    }

    /**
     * Returns false if it is the last desktop on desktop-first when multi-desk enabled. Otherwise,
     * returns true.
     */
    public boolean canRemoveTaskView(TaskView taskView) {
        return mUtils.canRemoveTaskView(taskView);
    }

    @Override
    public int getNextPage() {
        int nextPage = super.getNextPage();
        if (getPageAt(nextPage) instanceof AddDesktopButton) {
            nextPage = mUtils.getAlternatePageWithSameScroll(nextPage);
        }
        return nextPage;
    }

    protected int getCurrentPageScrollDiff() {
        return mCurrentPageScrollDiff;
    }

    protected void setCurrentPageScrollDiff(int currentPageScrollDiff) {
        mCurrentPageScrollDiff = currentPageScrollDiff;
    }

    @Nullable
    public TaskView getTaskViewNearestToCenterOfScreen() {
        return getTaskViewAt(getPageNearestToCenterOfScreen());
    }

    /**
     * Returns null instead of indexOutOfBoundsError when index is not in range
     */
    @Nullable
    public TaskView getTaskViewAt(int index) {
        View child = getChildAt(index);
        return child instanceof TaskView ? (TaskView) child : null;
    }

    /**
     * Returns iterable [TaskView] children.
     */
    public RecentsViewUtils.TaskViewsIterable getTaskViews() {
        return mUtils.getTaskViews();
    }

    public void setOnEmptyMessageUpdatedListener(@Nullable OnEmptyMessageUpdatedListener listener) {
        mOnEmptyMessageUpdatedListener = listener;
    }

    public void updateEmptyMessage() {
        boolean isEmpty = !hasTaskViews() && !isSplitSelectionActive();
        boolean hasSizeChanged = mLastMeasureSize.x != getWidth()
                || mLastMeasureSize.y != getHeight();
        if (isEmpty == mShowEmptyMessage && (mEmptyRecentsMessageView != null || !hasSizeChanged)) {
            return;
        }
        mShowEmptyMessage = isEmpty;
        if (mEmptyRecentsMessageView != null) {
            ViewKt.setVisible(mEmptyRecentsMessageView, isEmpty);
        } else {
            setContentDescription(isEmpty ? mEmptyMessage : "");
            setFocusable(isEmpty);
            updateEmptyStateUi(hasSizeChanged);
            invalidate();
        }

        if (mOnEmptyMessageUpdatedListener != null) {
            mOnEmptyMessageUpdatedListener.onEmptyMessageUpdated(mShowEmptyMessage);
        }
    }

    private void updateEmptyMessageConfiguration() {
        if (mEmptyRecentsMessageView == null) {
            return;
        }
        ViewGroup.MarginLayoutParams layoutParams =
                (MarginLayoutParams) mEmptyRecentsMessageView.getLayoutParams();
        layoutParams.bottomMargin =
                mContainer.getDeviceProfile().getOverviewActionsClaimedSpaceBelow();
        mEmptyRecentsMessageView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // If we're going to a state without overview panel, avoid unnecessary onLayout that
        // cause TaskViews to re-arrange during animation to that state.
        if (!mOverviewStateEnabled && !mFirstLayout) {
            return;
        }

        mShowAsGridLastOnLayout = showAsGrid();

        super.onLayout(changed, left, top, right, bottom);

        updateEmptyStateUi(changed);

        setTaskModalness(mTaskModalness);
        mLastComputedTaskStartPushOutDistance = null;
        mLastComputedTaskEndPushOutDistance = null;
        updatePageOffsets();
        runActionOnRemoteHandles(
                remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                        .setScroll(getScrollOffset()));
        setImportantForAccessibility(isModal() ? IMPORTANT_FOR_ACCESSIBILITY_NO
                : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        recalculateTaskViewScreenEdgeIntersections();
    }

    private void updatePivots() {
        mTempRect.set(mLastComputedTaskSize);
        getPagedViewOrientedState().getFullScreenScaleAndPivot(mTempRect,
                mContainer.getDeviceProfile(), mTempPointF);
        setPivotX(mTempPointF.x);
        setPivotY(mTempPointF.y);
        runActionOnRemoteHandles(remoteTargetHandle ->
                remoteTargetHandle.getTaskViewSimulator().setPivotOverride(mTempPointF));
    }

    /**
     * Sets whether we should force-override the page offset mid-point to the current task, rather
     * than the running task, when updating page offsets.
     */
    public void setOffsetMidpointIndexOverride(int offsetMidpointIndexOverride) {
        mOffsetMidpointIndexOverride = offsetMidpointIndexOverride;
        updatePageOffsets();
    }

    private void updatePageOffsets() {
        float offset = mAdjacentPageHorizontalOffset;
        float modalOffset = ACCELERATE_0_75.getInterpolation(mTaskModalness);
        int count = getChildCount();
        boolean showAsGrid = showAsGrid();

        TaskView runningTask = mRunningTaskViewId == INVALID_PAGE || !mRunningTaskTileHidden
                ? null : getRunningTaskView();
        int midpoint = mOffsetMidpointIndexOverride == INVALID_PAGE
                ? (runningTask == null ? INVALID_PAGE : indexOfChild(runningTask))
                : mOffsetMidpointIndexOverride;
        int modalMidpoint = getCurrentPage();
        TaskView carouselHiddenMidpointTask = runningTask != null ? runningTask
                : mUtils.getFirstTaskViewInCarousel(/*nonRunningTaskCarouselHidden=*/true,
                        /*runningTaskView=*/null);
        int carouselHiddenMidpoint = indexOfChild(carouselHiddenMidpointTask);
        boolean shouldCalculateOffsetForAllTasks = showAsGrid && mTaskModalness > 0;
        if (shouldCalculateOffsetForAllTasks) {
            modalMidpoint = indexOfChild(getSelectedTaskView());
        }

        float midpointOffsetSize = 0;
        float leftOffsetSize = midpoint - 1 >= 0
                ? getHorizontalOffsetSize(midpoint - 1, midpoint, offset)
                : 0;
        int rightOffsetReferenceIndex;
        if (midpoint == INVALID_PAGE) {
            rightOffsetReferenceIndex = getFirstViewIndex();
        } else {
            rightOffsetReferenceIndex = midpoint + 1;
        }
        float rightOffsetSize = rightOffsetReferenceIndex >= 0 && rightOffsetReferenceIndex < count
                ? getHorizontalOffsetSize(rightOffsetReferenceIndex, midpoint, offset)
                : 0;

        float modalMidpointOffsetSize = 0;
        float modalLeftOffsetSize = 0;
        float modalRightOffsetSize = 0;
        float gridOffsetSize = 0;
        float carouselHiddenOffsetSize;

        if (showAsGrid) {
            // In grid, we only focus the task on the side. The reference index used for offset
            // calculation is the task directly next to the focus task in the grid.
            int referenceIndex = modalMidpoint == 0 ? 1 : 0;
            gridOffsetSize = referenceIndex < count
                    ? getHorizontalOffsetSize(referenceIndex, modalMidpoint, modalOffset)
                    : 0;
        } else {
            modalLeftOffsetSize = modalMidpoint - 1 >= 0
                    ? getHorizontalOffsetSize(modalMidpoint - 1, modalMidpoint, modalOffset)
                    : 0;
            modalRightOffsetSize = modalMidpoint + 1 < count
                    ? getHorizontalOffsetSize(modalMidpoint + 1, modalMidpoint, modalOffset)
                    : 0;
        }

        int primarySize = getPagedOrientationHandler().getPrimaryValue(getWidth(), getHeight());
        float maxOverscroll = primarySize * OverScroll.OVERSCROLL_DAMP_FACTOR;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            float translation = i == midpoint
                    ? midpointOffsetSize
                    : i < midpoint
                            ? leftOffsetSize
                            : rightOffsetSize;
            if (shouldCalculateOffsetForAllTasks) {
                gridOffsetSize = getHorizontalOffsetSize(i, modalMidpoint, modalOffset);
                gridOffsetSize = Math.abs(gridOffsetSize) * (i <= modalMidpoint ? 1 : -1);
            }
            if (child instanceof TaskView
                    && !mUtils.isVisibleInCarousel((TaskView) child,
                    runningTask, /*nonRunningTaskCarouselHidden=*/true)) {
                // Increment carouselHiddenOffsetSize by maxOverscroll so it won't be on screen
                // even when user overscroll.
                carouselHiddenOffsetSize = (Math.abs(getMaxHorizontalOffsetSize(i,
                        carouselHiddenMidpoint)) + maxOverscroll)
                        * mDesktopCarouselDetachProgress;
                carouselHiddenOffsetSize = carouselHiddenOffsetSize * (
                        i <= carouselHiddenMidpoint ? 1 : -1);
            } else {
                carouselHiddenOffsetSize = 0;
            }
            float modalTranslation = i == modalMidpoint
                    ? modalMidpointOffsetSize
                    : showAsGrid
                            ? mIsRtl ? gridOffsetSize : -gridOffsetSize
                            : i < modalMidpoint ? modalLeftOffsetSize : modalRightOffsetSize;
            boolean skipTranslationOffset =
                    i == getRunningTaskIndex() && child instanceof DesktopTaskView;
            float totalTranslationX = (skipTranslationOffset ? 0f : translation) + modalTranslation
                    + carouselHiddenOffsetSize;
            if (child instanceof TaskView taskView) {
                taskView.getPrimaryTaskOffsetTranslationProperty().set(taskView, totalTranslationX);
            } else if (child instanceof ClearAllButton) {
                getPagedOrientationHandler().getPrimaryViewTranslate().set(child,
                        totalTranslationX);
            } else if (child instanceof AddDesktopButton addDesktopButton) {
                addDesktopButton.setOffsetTranslationX(totalTranslationX);
            }
            if (mEnableDrawingLiveTile && i == getRunningTaskIndex()) {
                runActionOnRemoteHandles(
                        remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                                .taskPrimaryTranslation.value = totalTranslationX);
                redrawLiveTile();
            }

            if (showAsGrid && child instanceof TaskView taskView) {
                float totalTranslationY = getVerticalOffsetSize(taskView, modalOffset);
                FloatProperty<TaskView> translationPropertyY =
                        taskView.getSecondaryTaskOffsetTranslationProperty();
                translationPropertyY.set(taskView, totalTranslationY);
            }
        }
        updateCurveProperties();
    }

    /**
     * Computes the child position with persistent translation considered (see
     * {@link TaskView#getPersistentTranslationX()}.
     */
    private void getPersistentChildPosition(int childIndex, int midPointScroll, RectF outRect) {
        View child = getChildAt(childIndex);
        outRect.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
        if (child instanceof TaskView taskView) {
            outRect.offset(taskView.getPersistentTranslationX(),
                    taskView.getPersistentTranslationY());

            mTempMatrix.reset();
            float persistentScale = taskView.getPersistentScale();
            mTempMatrix.postScale(persistentScale, persistentScale,
                    mIsRtl ? outRect.right : outRect.left, outRect.top);
            mTempMatrix.mapRect(outRect);
        }
        outRect.offset(getPagedOrientationHandler().getPrimaryValue(-midPointScroll, 0),
                getPagedOrientationHandler().getSecondaryValue(-midPointScroll, 0));
    }

    /**
     * Computes the distance to offset the given child such that it is completely offscreen when
     * translating away from the given midpoint.
     *
     * @param offsetProgress From 0 to 1 where 0 means no offset and 1 means offset offscreen.
     */
    protected float getHorizontalOffsetSize(int childIndex, int midpointIndex,
            float offsetProgress) {
        if (offsetProgress == 0) {
            // Don't bother calculating everything below if we won't offset anyway.
            return 0;
        }

        return getMaxHorizontalOffsetSize(childIndex, midpointIndex) * offsetProgress;
    }

    /**
     * Computes the distance to offset the given child such that it is completely offscreen when
     * translating away from the given midpoint.
     */
    private float getMaxHorizontalOffsetSize(int childIndex, int midpointIndex) {
        // First, get the position of the task relative to the midpoint. If there is no midpoint
        // then we just use the normal (centered) task position.
        RectF taskPosition = mTempRectF;
        // Whether the task should be shifted to start direction (i.e. left edge for portrait, top
        // edge for landscape/seascape).
        boolean isStartShift;
        if (midpointIndex > -1 && midpointIndex < getChildCount()) {
            // When there is a midpoint reference task, adjacent tasks have less distance to travel
            // to reach offscreen. Offset the task position to the task's starting point, and offset
            // by current page's scroll diff.
            int midpointScroll = getScrollForPage(midpointIndex)
                    + getPagedOrientationHandler().getPrimaryScroll(this)
                    - getScrollForPage(mCurrentPage);

            getPersistentChildPosition(midpointIndex, midpointScroll, taskPosition);
            float midpointStart = getPagedOrientationHandler().getStart(taskPosition);

            getPersistentChildPosition(childIndex, midpointScroll, taskPosition);
            // Assume child does not overlap with midPointChild.
            isStartShift = getPagedOrientationHandler().getStart(taskPosition) < midpointStart;
        } else {
            // Position the task at scroll position.
            getPersistentChildPosition(childIndex, getScrollForPage(childIndex), taskPosition);
            isStartShift = mIsRtl;
        }

        // Next, calculate the distance to move the task off screen. We also need to account for
        // RecentsView scale, because it moves tasks based on its pivot. To do this, we move the
        // task position to where it would be offscreen at scale = 1 (computed above), then we
        // apply the scale via getMatrix() to determine how much that moves the task from its
        // desired position, and adjust the computed distance accordingly.
        float distanceToOffscreen;
        if (isStartShift) {
            float desiredStart = -getPagedOrientationHandler().getPrimarySize(taskPosition);
            distanceToOffscreen = -getPagedOrientationHandler().getEnd(taskPosition);
            if (mLastComputedTaskStartPushOutDistance == null) {
                taskPosition.offsetTo(
                        getPagedOrientationHandler().getPrimaryValue(desiredStart, 0f),
                        getPagedOrientationHandler().getSecondaryValue(desiredStart, 0f));
                getMatrix().mapRect(taskPosition);
                mLastComputedTaskStartPushOutDistance = getPagedOrientationHandler().getEnd(
                        taskPosition) / getPagedOrientationHandler().getPrimaryScale(this);
            }
            distanceToOffscreen -= mLastComputedTaskStartPushOutDistance;
        } else {
            float desiredStart = getPagedOrientationHandler().getPrimarySize(this);
            distanceToOffscreen = desiredStart - getPagedOrientationHandler().getStart(
                    taskPosition);
            if (mLastComputedTaskEndPushOutDistance == null) {
                taskPosition.offsetTo(
                        getPagedOrientationHandler().getPrimaryValue(desiredStart, 0f),
                        getPagedOrientationHandler().getSecondaryValue(desiredStart, 0f));
                getMatrix().mapRect(taskPosition);
                mLastComputedTaskEndPushOutDistance = (getPagedOrientationHandler().getStart(
                        taskPosition) - desiredStart)
                        / getPagedOrientationHandler().getPrimaryScale(this);
            }
            distanceToOffscreen -= mLastComputedTaskEndPushOutDistance;
        }
        return distanceToOffscreen;
    }

    /**
     * Computes the vertical distance to offset a given child such that it is completely offscreen.
     *
     * @param offsetProgress From 0 to 1 where 0 means no offset and 1 means offset offscreen.
     */
    private float getVerticalOffsetSize(TaskView taskView, float offsetProgress) {
        if (offsetProgress == 0 || !showAsGrid() || getSelectedTaskView() == null) {
            // Don't bother calculating everything below if we won't offset vertically.
            return 0;
        }

        // First, get the position of the task relative to the top row.
        Rect taskPosition = getTaskBounds(taskView);

        boolean isSelectedTaskTopRow = mTopRowIdSet.contains(getSelectedTaskView().getTaskViewId());
        boolean isChildTopRow = mTopRowIdSet.contains(taskView.getTaskViewId());
        // Whether the task should be shifted to the top.
        boolean isTopShift = !isSelectedTaskTopRow && isChildTopRow;
        boolean isBottomShift = isSelectedTaskTopRow && !isChildTopRow;

        // Next, calculate the distance to move the task off screen at scale = 1.
        float distanceToOffscreen = 0;
        if (isTopShift) {
            distanceToOffscreen = -taskPosition.bottom;
        } else if (isBottomShift) {
            distanceToOffscreen = mContainer.getDeviceProfile().getDeviceProperties().getHeightPx() - taskPosition.top;
        }
        return distanceToOffscreen * offsetProgress;
    }

    protected void setTaskViewsResistanceTranslation(float translation) {
        mTaskViewsSecondaryTranslation = translation;
        for (TaskView taskView : getTaskViews()) {
            taskView.getTaskResistanceTranslationProperty().set(taskView,
                    translation / getScaleY());
        }
        runActionOnRemoteHandles(
                remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                        .recentsViewSecondaryTranslation.value = translation);
    }

    private void updateTaskViewsSnapshotRadius() {
        for (TaskView taskView : getTaskViews()) {
            taskView.updateFullscreenParams();
        }
    }

    protected void setTaskViewsPrimarySplitTranslation(float translation) {
        mTaskViewsPrimarySplitTranslation = translation;
        for (TaskView taskView : getTaskViews()) {
            taskView.getPrimarySplitTranslationProperty().set(taskView, translation);
        }
    }

    protected void setTaskViewsSecondarySplitTranslation(float translation) {
        mTaskViewsSecondarySplitTranslation = translation;
        for (TaskView taskView : getTaskViews()) {
            if (taskView == mSplitHiddenTaskView && !taskView.containsMultipleTasks()) {
                continue;
            }
            taskView.getSecondarySplitTranslationProperty().set(taskView, translation);
        }
    }

    /**
     * Resets the visuals when exit modal state.
     */
    public void resetModalVisuals() {
        if (getSelectedTaskView() != null) {
            getSelectedTaskView().taskContainers.forEach(
                    taskContainer -> taskContainer.getOverlay().resetModalVisuals());
        }
    }

    protected void resetShareUIState() {
        mUtils.resetShareUIState();
    }

    /**
     * Primarily used by overview actions to initiate split from TaskView, logs the source
     * of split invocation as such.
     */
    public void initiateSplitSelect(TaskContainer taskContainer) {
        int defaultSplitPosition = getPagedOrientationHandler()
                .getDefaultSplitPosition(mContainer.getDeviceProfile());
        initiateSplitSelect(taskContainer, defaultSplitPosition, LAUNCHER_OVERVIEW_ACTIONS_SPLIT);
    }

    public void initiateSplitSelect(TaskContainer taskContainer,
            @StagePosition int stagePosition,
            StatsLogManager.EventEnum splitEvent) {
        TaskView taskView = taskContainer.getTaskView();
        mSplitHiddenTaskView = taskView;
        mSplitSelectStateController.setInitialTaskSelect(null /*intent*/, stagePosition,
                taskContainer.getItemInfo(), splitEvent, taskContainer.getTask().key.id);
        mSplitSelectStateController.setAnimateCurrentTaskDismissal(
                true /*animateCurrentTaskDismissal*/);
        mSplitHiddenTaskViewIndex = indexOfChild(taskView);
    }

    /**
     * Called when staging a split from Home/AllApps/Overview (Taskbar),
     * using the icon long-press menu.
     * Attempts to initiate split with an existing taskView, if one exists
     */
    public void initiateSplitSelect(SplitSelectSource splitSelectSource) {
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "enterSplitSelect");
        mSplitSelectSource = splitSelectSource;
        mSplitHiddenTaskView = getTaskViewByTaskId(splitSelectSource.alreadyRunningTaskId);
        mSplitHiddenTaskViewIndex = indexOfChild(mSplitHiddenTaskView);
        mSplitSelectStateController
                .setAnimateCurrentTaskDismissal(splitSelectSource.animateCurrentTaskDismissal
                        && mSplitHiddenTaskView != null
                        && !(mSplitHiddenTaskView instanceof DesktopTaskView));

        // Prevent dismissing whole task if we're only initiating from one of 2 tasks in split pair
        mSplitSelectStateController.setDismissingFromSplitPair(mSplitHiddenTaskView != null
                && mSplitHiddenTaskView instanceof GroupedTaskView);
        mSplitSelectStateController.setInitialTaskSelect(splitSelectSource.intent,
                splitSelectSource.position.stagePosition, splitSelectSource.getItemInfo(),
                splitSelectSource.splitEvent, splitSelectSource.alreadyRunningTaskId);
    }

    /**
     * Animate DesktopTaskView(s) to hide in split select
     */
    public void handleDesktopTaskInSplitSelectState(PendingAnimation builder,
            Interpolator deskTopFadeInterPolator) {
        if (mAppliedTaskListChangeId == -1) {
            // If applyLoadPlan hasn't been called, skip animating the DesktopTaskViews as they
            // won't be added in applyLoadPlan during Split Select.
            return;
        }
        SplitAnimationTimings timings = AnimUtils.getDeviceOverviewToSplitTimings(
                mContainer.getDeviceProfile().getDeviceProperties().isLargeScreen());
        getTaskViews().forEachWithIndexInParent((index, taskView) -> {
            if (taskView instanceof DesktopTaskView) {
                // Setting pivot to scale down from screen centre.
                if (isTaskViewVisible(taskView)) {
                    float pivotX;
                    if (index < mCurrentPage) {
                        pivotX = mIsRtl ? taskView.getWidth() / 2f - mPageSpacing
                                - taskView.getWidth()
                                : taskView.getWidth() / 2f + mPageSpacing + taskView.getWidth();
                    } else if (index == mCurrentPage) {
                        pivotX = taskView.getWidth() / 2f;
                    } else {
                        pivotX = mIsRtl ? taskView.getWidth() + mPageSpacing
                                + taskView.getWidth()
                                : taskView.getWidth() - mPageSpacing - taskView.getWidth();
                    }
                    taskView.setPivotX(pivotX);
                    taskView.setPivotY(taskView.getHeight() / 2f);
                    builder.add(ObjectAnimator
                                    .ofFloat(taskView, TaskView.DISMISS_SCALE, 0.95f),
                            clampToProgress(timings.getDesktopTaskScaleInterpolator(), 0f,
                                    timings.getDesktopFadeSplitAnimationEndOffset()));
                }
                builder.addFloat(taskView, SPLIT_ALPHA, 1f, 0f,
                        clampToProgress(deskTopFadeInterPolator, 0f,
                                timings.getDesktopFadeSplitAnimationEndOffset()));
            }
        });
    }

    /**
     * While exiting from split mode, show all existing DesktopTaskViews.
     */
    public void resetDesktopTaskFromSplitSelectState() {
        for (TaskView taskView : getTaskViews()) {
            if (taskView instanceof DesktopTaskView) {
                taskView.setSplitAlpha(1f);
            }
        }
    }

    /**
     * Modifies a PendingAnimation with the animations for entering split staging
     */
    public void createSplitSelectInitAnimation(PendingAnimation builder) {
        boolean isInitiatingSplitFromTaskView =
                mSplitSelectStateController.isAnimateCurrentTaskDismissal();
        boolean isInitiatingTaskViewSplitPair =
                mSplitSelectStateController.isDismissingFromSplitPair();
        if (isInitiatingSplitFromTaskView && isInitiatingTaskViewSplitPair
                && mSplitHiddenTaskView instanceof GroupedTaskView groupedTaskView) {
            // Splitting from Overview for split pair task
            createInitialSplitSelectAnimation(builder);

            // Animate pair thumbnail into full thumbnail
            boolean primaryTaskSelected = groupedTaskView.getLeftTopTaskContainer().getTask().key.id
                    == mSplitSelectStateController.getInitialTaskId();
            TaskContainer taskContainer =
                    primaryTaskSelected ? groupedTaskView.getRightBottomTaskContainer()
                            : groupedTaskView.getLeftTopTaskContainer();
            mSplitSelectStateController.getSplitAnimationController()
                    .addInitialSplitFromPair(taskContainer, builder,
                            mContainer.getDeviceProfile(),
                            mSplitHiddenTaskView.getLayoutParams().width,
                            mSplitHiddenTaskView.getLayoutParams().height,
                            primaryTaskSelected);
            builder.addOnFrameCallback(() -> mSplitHiddenTaskView.updateFullscreenParams());
        } else if (isInitiatingSplitFromTaskView) {
            mSplitHiddenTaskView.setBorderEnabled(false);
            // Splitting from Overview for fullscreen task
            runExpressiveSplit(builder, mSplitHiddenTaskView);
        } else {
            // Splitting from Home
            TaskView currentPageTaskView = getTaskViewAt(mCurrentPage);
            // When current page is a Desktop task it needs special handling to
            // display correct animation in split mode
            if (currentPageTaskView instanceof DesktopTaskView) {
                runExpressiveSplit(builder, /* taskView= */ null);
            } else {
                createInitialSplitSelectAnimation(builder);
            }
        }
    }

    private void runExpressiveSplit(PendingAnimation builder, @Nullable TaskView taskView) {
        createInitialSplitSelectAnimation(builder);
        AtomicBoolean hasRunDismiss = new AtomicBoolean(false);
        builder.addOnFrameListener((animator) -> {
            SplitAnimationTimings splitTimings =
                    AnimUtils.getDeviceOverviewToSplitTimings(
                            mContainer.getDeviceProfile().getDeviceProperties().isLargeScreen());
            if (animator.getAnimatedFraction() > splitTimings.getGridSlideStartOffset()
                    && !hasRunDismiss.get()) {
                RecentsDismissUtils.SpringSet dismissSpringSet =
                        mDismissUtils.createTaskDismissSpringAnimation(taskView,
                                false /* shouldRemoveTaskView */, true /* isSplitSelection */);
                if (dismissSpringSet != null) {
                    dismissSpringSet.start();
                }
                hasRunDismiss.set(true);
            }
        });
    }

    /**
     * Confirms the selection of the next split task. The extra data is passed through because the
     * user may be selecting a subtask in a group.
     *
     * @param containerTaskView If our second selected app is currently running in Recents, this is
     *                          the "container" TaskView from Recents. If we are starting a fresh
     *                          instance of the app from an Intent, this will be null.
     * @param task              The Task corresponding to our second selected app. If we are
     *                          starting a fresh
     *                          instance of the app from an Intent, this will be null.
     * @param drawable          The Drawable corresponding to our second selected app's icon.
     * @param secondView        The View representing the current space on the screen where the
     *                          second app
     *                          is (either the ThumbnailView or the tapped icon).
     * @param intent            If we are launching a fresh instance of the app, this is the Intent
     *                          for it. If
     *                          the second app is already running in Recents, this will be null.
     * @param user              If we are launching a fresh instance of the app, this is the
     *                          UserHandle for it.
     *                          If the second app is already running in Recents, this will be null.
     * @return true if waiting for confirmation of second app or if split animations are running,
     * false otherwise
     */
    public boolean confirmSplitSelect(
            @Nullable TaskView containerTaskView, @Nullable Task task, Drawable drawable,
            View secondView, @Nullable Bitmap thumbnail, @Nullable Intent intent,
            @Nullable UserHandle user, ItemInfo itemInfo) {
        if (canLaunchFullscreenTask()) {
            return false;
        }
        if (mSplitSelectStateController.isBothSplitAppsConfirmed()) {
            Log.w(TAG, splitFailureMessage(
                    "confirmSplitSelect", "both apps have already been set"));
            return true;
        }
        // Second task is selected either as an already-running Task or an Intent
        if (task != null) {
            if (!task.isDockable) {
                // Task does not support split screen
                mSplitUnsupportedToast.show();
                Log.w(TAG, splitFailureMessage("confirmSplitSelect",
                        "selected Task (" + task.key.getPackageName()
                                + ") is not dockable / does not support splitscreen"));
                return true;
            }
            mSplitSelectStateController.setSecondTask(task, itemInfo);
        } else {
            // Param intent and user cannot be null if task is null.
            mSplitSelectStateController.setSecondTask(
                    requireNonNull(intent), requireNonNull(user), itemInfo);
        }

        RectF secondTaskStartingBounds = new RectF();
        Rect secondTaskEndingBounds = new Rect();
        // TODO(194414938) starting bounds seem slightly off, investigate
        Rect firstTaskStartingBounds = new Rect();
        Rect firstTaskEndingBounds = mTempRect;

        boolean isLargeScreen = mContainer.getDeviceProfile().getDeviceProperties().isLargeScreen();
        SplitAnimationTimings timings = AnimUtils.getDeviceSplitToConfirmTimings(isLargeScreen);
        PendingAnimation pendingAnimation = new PendingAnimation(timings.getDuration());

        int halfDividerSize = getResources()
                .getDimensionPixelSize(R.dimen.multi_window_task_divider_size) / 2;
        getPagedOrientationHandler().getFinalSplitPlaceholderBounds(halfDividerSize,
                mContainer.getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), firstTaskEndingBounds,
                secondTaskEndingBounds);

        mSplitScrim = mSplitSelectStateController.getSplitAnimationController()
                .addScrimBehindAnim(pendingAnimation, mContainer, getContext());
        FloatingTaskView firstFloatingTaskView =
                mSplitSelectStateController.getFirstFloatingTaskView();

        if (DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX.isTrue()
                && firstFloatingTaskView == null) {
            Log.d(TAG, "confirmSplitSelect: First floating task view was null, aborting split.");
            mSplitSelectStateController.resetState();
            return false;
        }

        firstFloatingTaskView.getBoundsOnScreen(firstTaskStartingBounds);
        firstFloatingTaskView.addConfirmAnimation(pendingAnimation,
                new RectF(firstTaskStartingBounds), firstTaskEndingBounds,
                false /* fadeWithThumbnail */, true /* isStagedTask */);

        safeRemoveDragLayerView(mSecondFloatingTaskView);

        mSecondFloatingTaskView = FloatingTaskView.getFloatingTaskView(mContainer, secondView,
                thumbnail, drawable, secondTaskStartingBounds);
        mSecondFloatingTaskView.setAlpha(1);
        mSecondFloatingTaskView.addConfirmAnimation(pendingAnimation, secondTaskStartingBounds,
                secondTaskEndingBounds, true /* fadeWithThumbnail */, false /* isStagedTask */);

        pendingAnimation.setViewAlpha(mSplitSelectStateController.getSplitInstructionsView(), 0,
                clampToProgress(LINEAR, timings.getInstructionsFadeStartOffset(),
                        timings.getInstructionsFadeEndOffset()));

        pendingAnimation.addEndListener(aBoolean -> {
            mSplitSelectStateController.launchSplitTasks(
                    aBoolean1 -> {
                        InteractionJankMonitorWrapper.end(Cuj.CUJ_SPLIT_SCREEN_ENTER);
                        mSplitSelectStateController.resetState();
                    });
        });

        mSecondSplitHiddenView = containerTaskView;
        if (mSecondSplitHiddenView != null) {
            mSecondSplitHiddenView.setThumbnailVisibility(false,
                    mSplitSelectStateController.getSecondTaskId());
        }

        InteractionJankMonitorWrapper.begin(this, Cuj.CUJ_SPLIT_SCREEN_ENTER,
                "Second tile selected");

        // Fade out all other views underneath placeholders
        ObjectAnimator tvFade = ObjectAnimator.ofFloat(this, RecentsView.CONTENT_ALPHA, 1, 0);
        pendingAnimation.add(tvFade, DECELERATE_2, SpringProperty.DEFAULT);
        pendingAnimation.buildAnim().start();
        return true;
    }

    @SuppressLint("WrongCall")
    private void resetFromSplitSelectionState() {
        safeRemoveDragLayerView(mSplitSelectStateController.getFirstFloatingTaskView());
        safeRemoveDragLayerView(mSecondFloatingTaskView);
        safeRemoveDragLayerView(mSplitSelectStateController.getSplitInstructionsView());
        safeRemoveDragLayerView(mSplitScrim);
        mSecondFloatingTaskView = null;
        mSplitSelectSource = null;
        mSplitSelectStateController.getSplitAnimationController()
                .removeSplitInstructionsView(mContainer);

        if (mSecondSplitHiddenView != null) {
            mSecondSplitHiddenView.setThumbnailVisibility(true, INVALID_TASK_ID);
            mSecondSplitHiddenView = null;
        }

        // We are leaving split selection state, so it is safe to reset thumbnail translations for
        // the next time split is invoked.
        setTaskViewsPrimarySplitTranslation(0);
        setTaskViewsSecondarySplitTranslation(0);

        if (mSplitHiddenTaskViewIndex == -1) {
            return;
        }
        if (!mContainer.getDeviceProfile().getDeviceProperties().isLargeScreen()) {
            int pageToSnapTo = mCurrentPage;
            if (mSplitHiddenTaskViewIndex <= pageToSnapTo) {
                pageToSnapTo += 1;
            } else {
                pageToSnapTo = mSplitHiddenTaskViewIndex;
            }
            snapToPageImmediately(pageToSnapTo);
        }
        onLayout(false /*  changed */, getLeft(), getTop(), getRight(), getBottom());

        resetTaskVisuals();
        mSplitHiddenTaskViewIndex = -1;
        if (mSplitHiddenTaskView != null) {
            mSplitHiddenTaskView.setThumbnailVisibility(true, INVALID_TASK_ID);
            // mSplitHiddenTaskView is set when split select animation starts. The TaskView is only
            // removed when when the animation finishes. So in the case of overview being dismissed
            // during the animation, we should not call clearAndRecycleTaskView() because it has
            // not been removed yet.
            if (mSplitHiddenTaskView.getParent() == null) {
                clearAndRecycleTaskView(mSplitHiddenTaskView);
            }
            mSplitHiddenTaskView = null;
        }

        // Recents doesn't receive activity callback, so we cleanup manually
        if (mContainer instanceof RecentsWindowManager manager) {
            manager.getStateManager().moveToRestState();
        }
    }

    private void safeRemoveDragLayerView(@Nullable View viewToRemove) {
        if (viewToRemove != null) {
            mContainer.getDragLayer().removeView(viewToRemove);
        }
    }

    /**
     * Returns how much additional translation there should be for each of the child TaskViews.
     * Note that the translation can be its primary or secondary dimension.
     */
    public float getSplitSelectTranslation() {
        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
        RecentsPagedOrientationHandler orientationHandler = getPagedOrientationHandler();
        int splitPosition = getSplitSelectController().getActiveSplitStagePosition();
        int splitPlaceholderSize =
                mContainer.getResources().getDimensionPixelSize(R.dimen.split_placeholder_size);
        int direction = orientationHandler.getSplitTranslationDirectionFactor(
                splitPosition, deviceProfile);

        if (deviceProfile.getDeviceProperties().isLargeScreen()
                && deviceProfile.getSysuiProfile().isLeftRightSplit()) {
            // Only shift TaskViews if there is not enough space on the side of
            // mLastComputedTaskSize to minimize motion.
            int sideSpace = mIsRtl
                    ? deviceProfile.getDeviceProperties().getWidthPx() - mLastComputedTaskSize.right
                    : mLastComputedTaskSize.left;
            int extraSpace = splitPlaceholderSize + mPageSpacing - sideSpace;
            if (extraSpace <= 0f) {
                return 0f;
            }

            return extraSpace * direction;
        }

        return splitPlaceholderSize * direction;
    }

    protected void onRotateInSplitSelectionState() {
        getPagedOrientationHandler().getInitialSplitPlaceholderBounds(mSplitPlaceholderSize,
                mSplitPlaceholderInset, mContainer.getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), mTempRect);
        mTempRectF.set(mTempRect);
        FloatingTaskView firstFloatingTaskView =
                mSplitSelectStateController.getFirstFloatingTaskView();
        firstFloatingTaskView.updateOrientationHandler(getPagedOrientationHandler());
        firstFloatingTaskView.update(mTempRectF, /*progress=*/1f);

        RecentsPagedOrientationHandler orientationHandler = getPagedOrientationHandler();
        Pair<FloatProperty<RecentsView<?, ?>>, FloatProperty<RecentsView<?, ?>>> taskViewsFloat =
                orientationHandler.getSplitSelectTaskOffset(
                        TASK_PRIMARY_SPLIT_TRANSLATION, TASK_SECONDARY_SPLIT_TRANSLATION,
                        mContainer.getDeviceProfile());
        taskViewsFloat.first.set(this, getSplitSelectTranslation());
        taskViewsFloat.second.set(this, 0f);

        if (mSplitSelectStateController.getSplitInstructionsView() != null) {
            mSplitSelectStateController.getSplitInstructionsView().ensureProperRotation();
        }
    }

    private void updateDeadZoneRects() {
        // Get the deadzone rect surrounding the clear all button to not dismiss overview to home
        mClearAllButtonDeadZoneRect.setEmpty();
        if (mClearAllButton.getWidth() > 0) {
            int verticalMargin = getResources()
                    .getDimensionPixelSize(R.dimen.recents_clear_all_deadzone_vertical_margin);
            mClearAllButton.getHitRect(mClearAllButtonDeadZoneRect);
            mClearAllButtonDeadZoneRect.inset(-getPaddingRight() / 2, -verticalMargin);
        }

        mUtils.updateTaskViewDeadZoneRect(mTaskViewDeadZoneRect, mTopRowDeadZoneRect,
                mBottomRowDeadZoneRect);
    }

    private void updateEmptyStateUi(boolean sizeChanged) {
        if (mEmptyRecentsMessageView != null) {
            return;
        }
        boolean hasValidSize = getWidth() > 0 && getHeight() > 0;
        if (sizeChanged && hasValidSize) {
            mEmptyTextLayout = null;
            mLastMeasureSize.set(getWidth(), getHeight());
        }

        if (mShowEmptyMessage && hasValidSize && mEmptyTextLayout == null) {
            int availableWidth = mLastMeasureSize.x - mEmptyMessagePadding - mEmptyMessagePadding;
            mEmptyTextLayout = StaticLayout.Builder.obtain(mEmptyMessage, 0, mEmptyMessage.length(),
                            mEmptyMessagePaint, availableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build();
            int totalHeight = mEmptyTextLayout.getHeight()
                    + mEmptyMessagePadding + mEmptyIcon.getIntrinsicHeight();

            int top = (mLastMeasureSize.y - totalHeight) / 2;
            int left = (mLastMeasureSize.x - mEmptyIcon.getIntrinsicWidth()) / 2;
            mEmptyIcon.setBounds(left, top, left + mEmptyIcon.getIntrinsicWidth(),
                    top + mEmptyIcon.getIntrinsicHeight());
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || (mEmptyRecentsMessageView == null && (
                mShowEmptyMessage && who == mEmptyIcon));
    }

    protected void maybeDrawEmptyMessage(Canvas canvas) {
        if (mEmptyRecentsMessageView != null) {
            return;
        }
        if (mShowEmptyMessage && mEmptyTextLayout != null) {
            // Offsets icon and text up so that the vertical center of screen (accounting for
            // insets) is between icon and text.
            int offset = (mEmptyIcon.getIntrinsicHeight() + mEmptyMessagePadding) / 2;

            canvas.save();
            canvas.translate(getScrollX() + (mInsets.left - mInsets.right) / 2f,
                    (mInsets.top - mInsets.bottom) / 2f - offset);
            mEmptyIcon.draw(canvas);
            canvas.translate(mEmptyMessagePadding,
                    mEmptyIcon.getBounds().bottom + mEmptyMessagePadding);
            mEmptyTextLayout.draw(canvas);
            canvas.restore();
        }
    }

    /**
     * Animate adjacent tasks off screen while scaling up.
     *
     * If launching one of the adjacent tasks, parallax the center task and other adjacent task
     * to the right.
     */
    @SuppressLint("Recycle")
    public AnimatorSet createAdjacentPageAnimForTaskLaunch(TaskView taskView) {
        AnimatorSet anim = new AnimatorSet();

        int taskIndex = indexOfChild(taskView);
        int centerTaskIndex = getCurrentPage();

        float toScale = getMaxScaleForFullScreen();
        boolean showAsGrid = showAsGrid();
        boolean zoomInTaskView = showAsGrid ? taskView.isLargeTile() : taskIndex == centerTaskIndex;
        if (zoomInTaskView) {
            anim.play(ObjectAnimator.ofFloat(this, RECENTS_SCALE_PROPERTY, toScale));
            anim.play(ObjectAnimator.ofFloat(this, FULLSCREEN_PROGRESS, 1));
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {
                    taskView.getThumbnailBounds(mTempRect, /*relativeToDragLayer=*/true);
                    getTaskDimension(mContainer.getDeviceProfile(), mTempPointF);
                    Rect fullscreenBounds = new Rect(0, 0, (int) mTempPointF.x,
                            (int) mTempPointF.y);
                    Utilities.getPivotsForScalingRectToRect(mTempRect, fullscreenBounds,
                            mTempPointF);
                    setPivotX(mTempPointF.x);
                    setPivotY(mTempPointF.y);

                    // If live tile is not launching, apply pivot to live tile as well and bring it
                    // above RecentsView to avoid wallpaper blur from being applied to it.
                    if (!taskView.isRunningTask()) {
                        runActionOnRemoteHandles(
                                remoteTargetHandle ->
                                        remoteTargetHandle.getTaskViewSimulator()
                                                .setPivotOverride(mTempPointF));
                        mBlurUtils.setDrawLiveTileBelowRecents(false);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    // If live tile is not launching, reset the pivot applied above.
                    if (!taskView.isRunningTask()) {
                        runActionOnRemoteHandles(
                                remoteTargetHandle -> {
                                    remoteTargetHandle.getTaskViewSimulator().setPivotOverride(
                                            null);
                                });
                    }
                }
            });
        } else if (!showAsGrid) {
            // We are launching an adjacent task, so parallax the center and other adjacent task.
            float displacementX = taskView.getWidth() * (toScale - 1f);
            float primaryTranslation = mIsRtl ? -displacementX : displacementX;
            anim.play(ObjectAnimator.ofFloat(getPageAt(centerTaskIndex),
                    getPagedOrientationHandler().getPrimaryViewTranslate(), primaryTranslation));
            int runningTaskIndex = getRunningTaskIndex();
            if (runningTaskIndex != -1 && runningTaskIndex != taskIndex
                    && getRemoteTargetHandles() != null) {
                for (RemoteTargetHandle remoteHandle : getRemoteTargetHandles()) {
                    anim.play(ObjectAnimator.ofFloat(
                            remoteHandle.getTaskViewSimulator().taskPrimaryTranslation,
                            AnimatedFloat.VALUE,
                            primaryTranslation));
                }
            }

            int otherAdjacentTaskIndex = centerTaskIndex + (centerTaskIndex - taskIndex);
            if (otherAdjacentTaskIndex >= 0 && otherAdjacentTaskIndex < getPageCount()) {
                PropertyValuesHolder[] properties = new PropertyValuesHolder[3];
                properties[0] = PropertyValuesHolder.ofFloat(
                        getPagedOrientationHandler().getPrimaryViewTranslate(), primaryTranslation);
                properties[1] = PropertyValuesHolder.ofFloat(View.SCALE_X, 1);
                properties[2] = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1);

                anim.play(ObjectAnimator.ofPropertyValuesHolder(getPageAt(otherAdjacentTaskIndex),
                        properties));
            }
        }
        anim.play(ObjectAnimator.ofFloat(this, TASK_THUMBNAIL_SPLASH_ALPHA, 0, 1));
        if (taskView instanceof DesktopTaskView) {
            anim.play(ObjectAnimator.ofArgb(mContainer.getScrimView(), VIEW_BACKGROUND_COLOR,
                    Color.TRANSPARENT));
            anim.play(ObjectAnimator.ofFloat(this, DESK_EXPLODE_PROGRESS, 1f, 0f));
        }
        DepthController<?, ?> depthController = getDepthController();
        if (depthController != null) {
            float targetDepth = taskView instanceof DesktopTaskView ? 0
                    : mContainer.getBackgroundAppState().getDepth(mContainer);
            anim.play(ObjectAnimator.ofFloat(depthController.stateDepth, MULTI_PROPERTY_VALUE,
                    targetDepth));
        }
        return anim;
    }

    /**
     * Returns the scale up required on the view, so that it coves the screen completely
     */
    public float getMaxScaleForFullScreen() {
        if (mLastComputedTaskSize.isEmpty()) {
            getTaskSize(mLastComputedTaskSize);
        }
        mTempRect.set(mLastComputedTaskSize);
        return getPagedViewOrientedState().getFullScreenScaleAndPivot(
                mTempRect, mContainer.getDeviceProfile(), mTempPointF);
    }

    /**
     * Clears the existing PendingAnimation.
     */
    public void clearPendingAnimation() {
        mPendingAnimation = null;
    }

    public PendingAnimation createTaskLaunchAnimation(
            TaskView taskView, long duration, Interpolator interpolator) {
        if (FeatureFlags.IS_STUDIO_BUILD && mPendingAnimation != null) {
            throw new IllegalStateException("Another pending animation is still running");
        }

        if (!hasTaskViews()) {
            return new PendingAnimation(duration);
        }

        // When swiping down from overview to tasks, ensures the snapped page's scroll maintain
        // invariant between quick switch and overview, to ensure a smooth animation transition.
        updateGridProperties();
        updateScrollSynchronously();

        int targetSysUiFlags = taskView.getSysUiStatusNavFlags();
        final boolean[] passedOverviewThreshold = new boolean[]{false};
        AnimatorSet anim = createAdjacentPageAnimForTaskLaunch(taskView);
        anim.play(new AnimatedFloat(v -> {
            // Once we pass a certain threshold, update the sysui flags to match the target
            // tasks' flags
            if (v > UPDATE_SYSUI_FLAGS_THRESHOLD) {
                mContainer.getSystemUiController().updateUiState(
                        UI_STATE_FULLSCREEN_TASK, targetSysUiFlags);
            } else {
                mContainer.getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, 0);
            }

            // Passing the threshold from taskview to fullscreen app will vibrate
            final boolean passed = v >= SUCCESS_TRANSITION_PROGRESS;
            if (passed != passedOverviewThreshold[0]) {
                passedOverviewThreshold[0] = passed;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                // Also update recents animation controller state if it is ongoing.
                if (mRecentsAnimationController != null) {
                    mRecentsAnimationController.setWillFinishToHome(!passed);
                }
            }
        }).animateToValue(0f, 1f));
        anim.setInterpolator(interpolator);

        mPendingAnimation = new PendingAnimation(duration);
        mPendingAnimation.add(anim);
        if (taskView.isRunningTask()) {
            runActionOnRemoteHandles(
                    remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                            .addOverviewToAppAnim(mPendingAnimation, interpolator));
            mPendingAnimation.addOnFrameCallback(this::redrawLiveTile);
        }
        mPendingAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mBlurUtils.setDrawLiveTileBelowRecents(false);
            }
        });
        mPendingAnimation.addEndListener(isSuccess -> {
            if (isSuccess) {
                if (taskView instanceof GroupedTaskView && hasAllValidTaskIds(taskView.getTaskIds())
                        && mRemoteTargetHandles != null) {
                    // TODO(b/194414938): make this part of the animations instead.
                    SplitRecentsAnimUtils splitRecentsAnimUtils = new SplitRecentsAnimUtils(
                            mRemoteTargetHandles[0].getTransformParams().getTargetSet().nonApps);
                    splitRecentsAnimUtils.fadeInDimLayer(/* immediate= */ true);
                    splitRecentsAnimUtils.fadeInDivider(/* immediate= */ true);
                }
                if (taskView.isRunningTask()) {
                    finishRecentsAnimation(false /* toHome */, null);
                    onTaskLaunchAnimationEnd(true /* success */);
                } else {
                    finishRecentsAnimation(true /* toHome */,
                            () -> taskView.launchWithoutAnimation(this::onTaskLaunchAnimationEnd));
                }
                mContainer.getStatsLogManager().logger().withItemInfo(taskView.getItemInfo())
                        .log(LAUNCHER_TASK_LAUNCH_SWIPE_DOWN);
            } else {
                onTaskLaunchAnimationEnd(false);
            }
            mPendingAnimation = null;
        });
        return mPendingAnimation;
    }

    protected Unit onTaskLaunchAnimationEnd(boolean success) {
        if (success) {
            resetTaskVisuals();
        } else {
            // If launch animation didn't complete i.e. user dragged live tile down and then
            // back up and returned to Overview, then we need to ensure we reset the
            // view to draw below recents so that it can't be interacted with.
            mBlurUtils.setDrawLiveTileBelowRecents(true);
            redrawLiveTile();
        }
        return Unit.INSTANCE;
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        updateCurrentTaskActionsVisibility();
        loadVisibleTaskData();
        updateEnabledOverlays();
        mUtils.updateCentralTask();
    }

    @Override
    protected void pageBeginTransition() {
        super.pageBeginTransition();
    }

    @Override
    protected String getCurrentPageDescription() {
        return "";
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> outChildren) {
        outChildren.addAll(mUtils.getAccessibilityChildren());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        // We only care about TaskView's for the `CollectionInfo` that Talkback uses to read out.
        final AccessibilityNodeInfo.CollectionInfo
                collectionInfo = new AccessibilityNodeInfo.CollectionInfo(
                1, getTaskViewCount(), false);
        info.setCollectionInfo(collectionInfo);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        // To hear position-in-list related feedback from Talkback.
        return ListView.class.getName();
    }

    @Override
    protected boolean isPageOrderFlipped() {
        return true;
    }

    public void setEnableDrawingLiveTile(boolean enableDrawingLiveTile) {
        mEnableDrawingLiveTile = enableDrawingLiveTile;
    }

    public boolean getEnableDrawingLiveTile() {
        return mEnableDrawingLiveTile;
    }

    public void redrawLiveTile() {
        runActionOnRemoteHandles(remoteTargetHandle -> {
            TransformParams params = remoteTargetHandle.getTransformParams();
            if (params.getTargetSet() != null) {
                remoteTargetHandle.getTaskViewSimulator().apply(params);
            }
        });
    }

    @Nullable
    public RemoteTargetHandle[] getRemoteTargetHandles() {
        return mRemoteTargetHandles;
    }

    public void setRecentsAnimationTargets(RecentsAnimationController recentsAnimationController,
            RecentsAnimationTargets recentsAnimationTargets) {
        Log.d(TAG, "setRecentsAnimationTargets "
                + "- recentsAnimationController: " + recentsAnimationController
                + ", recentsAnimationTargets: " + recentsAnimationTargets);
        mRecentsAnimationController = recentsAnimationController;
        mSplitSelectStateController.setRecentsAnimationRunning(true);
        if (recentsAnimationTargets == null || recentsAnimationTargets.apps.length == 0) {
            return;
        }

        boolean forDesktop = mActiveGestureGroupedTaskInfo != null
                && mActiveGestureGroupedTaskInfo.isBaseType(GroupedTaskInfo.TYPE_DESK);
        RemoteTargetGluer gluer = new RemoteTargetGluer(getContext(), getContainerInterface(),
                recentsAnimationTargets, forDesktop);
        if (forDesktop) {
            mRemoteTargetHandles = gluer.assignTargetsForDesktop(
                    recentsAnimationTargets, /* transitionInfo = */ null);
        } else {
            mRemoteTargetHandles = gluer.assignTargetsForSplitScreen(recentsAnimationTargets);
        }
        mSplitBoundsConfig = gluer.getSplitBounds();
        // Add release check to the targets from the RemoteTargetGluer and not the targets
        // passed in because in the event we're in split screen, we use the passed in targets
        // to create new RemoteAnimationTargets in assignTargetsForSplitScreen(), and the
        // mSyncTransactionApplier doesn't get transferred over
        runActionOnRemoteHandles(remoteTargetHandle -> {
            final TransformParams params = remoteTargetHandle.getTransformParams();
            if (mContainer instanceof RecentsWindowManager) {
                params.setHomeBuilderProxy((builder, app, transformParams) -> {
                    mTmpMatrix.setScale(
                            1f, 1f, app.localBounds.exactCenterX(), app.localBounds.exactCenterY());
                    builder.setMatrix(mTmpMatrix).setAlpha(1f).setShow();
                });
            }

            if (mSyncTransactionApplier != null) {
                params.setSyncTransactionApplier(mSyncTransactionApplier);
                params.getTargetSet().addReleaseCheck(mSyncTransactionApplier);
            }

            TaskViewSimulator tvs = remoteTargetHandle.getTaskViewSimulator();
            tvs.setOrientationState(mOrientationState);
            tvs.setDp(mContainer.getDeviceProfile());
            tvs.recentsViewScale.value = 1;
        });

        TaskView runningTaskView = getRunningTaskView();
        if (runningTaskView instanceof GroupedTaskView) {
            // We initially create a GroupedTaskView in showCurrentTask() before launcher even
            // receives the leashes for the remote apps, so the mSplitBoundsConfig that gets passed
            // in there is either null or outdated, so we need to update here as soon as we're
            // notified.
            ((GroupedTaskView) runningTaskView).updateSplitBoundsConfig(mSplitBoundsConfig);
        }
    }

    /** Helper to avoid writing some for-loops to iterate over {@link #mRemoteTargetHandles} */
    public void runActionOnRemoteHandles(Consumer<RemoteTargetHandle> consumer) {
        if (mRemoteTargetHandles == null) {
            return;
        }

        for (RemoteTargetHandle handle : mRemoteTargetHandles) {
            consumer.accept(handle);
        }
    }

    /**
     * Finish recents animation.
     */
    public void finishRecentsAnimation(boolean toHome, @Nullable Runnable onFinishComplete) {
        finishRecentsAnimation(toHome, true /* shouldPip */, onFinishComplete);
    }

    /**
     * NOTE: Whatever value gets passed through to the toHome param may need to also be set on
     * {@link #mRecentsAnimationController#setWillFinishToHome}.
     */
    public void finishRecentsAnimation(
            boolean toHome,
            boolean shouldPip,
            @Nullable Runnable onFinishComplete) {
        Log.d(TAG, "finishRecentsAnimation - mRecentsAnimationController: "
                + mRecentsAnimationController + ", toHome: " + toHome + ", shouldPip: " + shouldPip
                + ", partial trace:\n"
                + getTrimmedStackTrace("RecentsView.finishRecentsAnimation"));
        // TODO(b/197232424#comment#10) Move this back into onRecentsAnimationComplete(). Maybe?
        cleanupRemoteTargets();

        if (mRecentsAnimationController == null) {
            if (onFinishComplete != null) {
                onFinishComplete.run();
            }
            return;
        }

        final boolean sendUserLeaveHint = toHome && shouldPip;
        if (sendUserLeaveHint && !PipFlags.isPip2ExperimentEnabled()) {
            // Notify the SysUI to use fade-in animation when entering PiP from live tile.
            // Note: PiP2 handles entering differently, so skip if enable_pip2=true.
            mSystemUiProxy.setPipAnimationTypeToAlpha();
            mSystemUiProxy.setShelfHeight(true,
                    mContainer.getDeviceProfile().getHotseatProfile().getBarSizePx());
            // Transaction to hide the task to avoid flicker for entering PiP from split-screen.
            // See also {@link AbsSwipeUpHandler#maybeFinishSwipeToHome}.
            PictureInPictureSurfaceTransaction tx =
                    new PictureInPictureSurfaceTransaction.Builder()
                            .setAlpha(0f)
                            .build();
            tx.setShouldDisableCanAffectSystemUiFlags(false);
            int[] taskIds = mTopTaskTracker.getRunningSplitTaskIds();
            for (int taskId : taskIds) {
                mRecentsAnimationController.setFinishTaskTransaction(taskId,
                        tx, null /* overlay */);
            }
        }
        mBlurUtils.setDrawLiveTileBelowRecents(false);
        mRecentsAnimationController.finish(
                toHome,
                /* onFinishComplete= */ () -> {
                    if (onFinishComplete != null) {
                        onFinishComplete.run();
                    }
                    onRecentsAnimationComplete();
                },
                sendUserLeaveHint,
                /* reason= */ new ActiveGestureLog.CompoundString(
                        "RecentsView.finishRecentsAnimation"));
    }

    /**
     * Called when a running recents animation has finished or canceled.
     */
    public void onRecentsAnimationComplete() {
        Log.d(TAG, "onRecentsAnimationComplete "
                + "- mRecentsAnimationController: " + mRecentsAnimationController
                + ", mSideTaskLaunchCallback: " + mSideTaskLaunchCallback);
        // At this point, the recents animation is not running and if the animation was canceled
        // by a display rotation then reset this state to show the screenshot
        setRunningTaskViewShowScreenshot(true);
        // After we finish the recents animation, the current task id should be correctly
        // reset so that when the task is launched from Overview later, it goes through the
        // flow of starting a new task instead of finishing recents animation to app. A
        // typical example of this is (1) user swipes up from app to Overview (2) user
        // taps on QSB (3) user goes back to Overview and launch the most recent task.
        setCurrentTask(-1);
        mRecentsAnimationController = null;
        mSplitSelectStateController.setRecentsAnimationRunning(false);
        mBlurUtils.setDrawLiveTileBelowRecents(false);
    }

    public void setDisallowScrollToClearAll(boolean disallowScrollToClearAll) {
        if (mDisallowScrollToClearAll != disallowScrollToClearAll) {
            mDisallowScrollToClearAll = disallowScrollToClearAll;
            updateMinAndMaxScrollX();
        }
    }

    /**
     * Updates page scroll synchronously after measure and layout child views.
     */
    @SuppressLint("WrongCall")
    public void updateScrollSynchronously() {
        // onMeasure is needed to update child's measured width which is used in scroll calculation,
        // in case TaskView sizes has changed when being small/large.
        onMeasure(makeMeasureSpec(getMeasuredWidth(), EXACTLY),
                makeMeasureSpec(getMeasuredHeight(), EXACTLY));
        onLayout(false /*  changed */, getLeft(), getTop(), getRight(), getBottom());
        updateMinAndMaxScrollX();
    }

    @Override
    protected int getChildGap(int fromIndex, int toIndex) {
        int clearAllIndex = indexOfChild(mClearAllButton);
        return fromIndex == clearAllIndex || toIndex == clearAllIndex
                ? getClearAllExtraPageSpacing() : 0;
    }

    protected int getClearAllExtraPageSpacing() {
        OverviewProfile overviewProfile = mContainer.getDeviceProfile().getOverviewProfile();
        return showAsGrid()
                ? Math.max(overviewProfile.getGridSideMargin() - mPageSpacing, 0) : 0;
    }

    @Override
    protected void updateMinAndMaxScrollX() {
        super.updateMinAndMaxScrollX();
        debugLog(PAGE_SCROLL_TAG, "updateMinAndMaxScrollX - mMinScroll: " + mMinScroll);
        debugLog(PAGE_SCROLL_TAG, "updateMinAndMaxScrollX - mMaxScroll: " + mMaxScroll);
    }

    @Override
    protected int computeMinScroll() {
        if (!hasTaskViews()) {
            return super.computeMinScroll();
        }

        return getScrollForPage(mIsRtl ? getLastViewIndex() : getFirstViewIndex());
    }

    @Override
    protected int computeMaxScroll() {
        if (!hasTaskViews()) {
            return super.computeMaxScroll();
        }

        return getScrollForPage(mIsRtl ? getFirstViewIndex() : getLastViewIndex());
    }

    private int getFirstViewIndex() {
        final View firstView;
        if (mShowAsGridLastOnLayout) {
            // For grid Overview, it always start if a large tile if they exist, otherwise it start
            // with the first task.
            TaskView firstLargeTaskView = mUtils.getFirstLargeTaskView();
            if (firstLargeTaskView != null) {
                firstView = firstLargeTaskView;
            } else {
                firstView = mUtils.getFirstSmallTaskView();
            }
        } else {
            firstView = mUtils.getFirstTaskViewInCarousel(
                    /*nonRunningTaskCarouselHidden=*/mDesktopCarouselDetachProgress > 0);
        }
        return indexOfChild(firstView);
    }

    private int getLastViewIndex() {
        final View lastView;
        if (!mDisallowScrollToClearAll) {
            // When ClearAllButton is present, it always end with ClearAllButton.
            lastView = mClearAllButton;
        } else if (mShowAsGridLastOnLayout) {
            // When ClearAllButton is absent, for the grid Overview, it always end with a grid task
            // if they exist, otherwise it ends with a large tile.
            TaskView lastGridTaskView = getLastGridTaskView();
            if (lastGridTaskView != null) {
                lastView = lastGridTaskView;
            } else {
                lastView = mUtils.getLastLargeTaskView();
            }
        } else {
            lastView = mUtils.getLastTaskViewInCarousel(
                    /*nonRunningTaskCarouselHidden=*/mDesktopCarouselDetachProgress > 0);
        }
        return indexOfChild(lastView);
    }

    /**
     * Returns page scroll of ClearAllButton.
     */
    public int getClearAllScroll() {
        return getScrollForPage(indexOfChild(mClearAllButton));
    }

    @Override
    protected boolean getPageScrolls(int[] outPageScrolls, boolean layoutChildren,
            ComputePageScrollsLogic scrollLogic) {
        int[] newPageScrolls = new int[outPageScrolls.length];
        super.getPageScrolls(newPageScrolls, layoutChildren, scrollLogic);
        boolean showAsFullscreen = showAsFullscreen();
        boolean showAsGrid = showAsGrid();

        // Align ClearAllButton to the left (RTL) or right (non-RTL), which is different from other
        // TaskViews. This must be called after laying out ClearAllButton.
        if (layoutChildren) {
            int clearAllWidthDiff = getPagedOrientationHandler().getPrimaryValue(mTaskWidth,
                    mTaskHeight) - getPagedOrientationHandler().getPrimarySize(mClearAllButton);
            mClearAllButton.setScrollOffsetPrimary(mIsRtl ? clearAllWidthDiff : -clearAllWidthDiff);
        }

        int[] oldPageScrolls = Arrays.copyOf(outPageScrolls, outPageScrolls.length);
        int clearAllIndex = indexOfChild(mClearAllButton);
        int clearAllScroll = 0;
        int clearAllWidth = getPagedOrientationHandler().getPrimarySize(mClearAllButton);
        if (clearAllIndex != -1 && clearAllIndex < outPageScrolls.length) {
            float scrollDiff = mClearAllButton.getScrollAdjustment(showAsFullscreen, showAsGrid);
            clearAllScroll = newPageScrolls[clearAllIndex] + Math.round(scrollDiff);
            outPageScrolls[clearAllIndex] = clearAllScroll;
        }

        int lastTaskScroll = getLastTaskScroll(clearAllScroll, clearAllWidth);
        getTaskViews().forEachWithIndexInParent((index, taskView) -> {
            float scrollDiff = taskView.getScrollAdjustment(showAsGrid);
            int pageScroll = newPageScrolls[index] + Math.round(scrollDiff);
            if ((mIsRtl && pageScroll < lastTaskScroll)
                    || (!mIsRtl && pageScroll > lastTaskScroll)) {
                pageScroll = lastTaskScroll;
            }
            outPageScrolls[index] = pageScroll;
            debugLog(PAGE_SCROLL_TAG,
                    "getPageScrolls - outPageScrolls[" + index + "]: " + outPageScrolls[index]);
        });

        int addDesktopButtonIndex = indexOfChild(mAddDesktopButton);
        if (addDesktopButtonIndex >= 0 && addDesktopButtonIndex < outPageScrolls.length) {
            int firstViewIndex = getFirstViewIndex();
            if (firstViewIndex >= 0 && firstViewIndex < outPageScrolls.length) {
                outPageScrolls[addDesktopButtonIndex] = outPageScrolls[firstViewIndex];
            }
            debugLog(PAGE_SCROLL_TAG, "getPageScrolls - addDesktopButtonScroll: "
                    + outPageScrolls[addDesktopButtonIndex]);
        }
        debugLog(PAGE_SCROLL_TAG, "getPageScrolls - clearAllScroll: " + clearAllScroll);
        return !Arrays.equals(oldPageScrolls, outPageScrolls);
    }

    @Override
    protected int getChildOffset(int index) {
        int childOffset = super.getChildOffset(index);
        View child = getChildAt(index);
        if (child instanceof TaskView) {
            childOffset += ((TaskView) child).getOffsetAdjustment(showAsGrid());
        } else if (child instanceof ClearAllButton) {
            childOffset += ((ClearAllButton) child).getOffsetAdjustment(mOverviewFullscreenEnabled,
                    showAsGrid());
        }
        return childOffset;
    }

    @Override
    protected int getChildVisibleSize(int childIndex) {
        final TaskView taskView = getTaskViewAt(childIndex);
        if (taskView == null) {
            return super.getChildVisibleSize(childIndex);
        }
        return (int) (super.getChildVisibleSize(childIndex) * taskView.getSizeAdjustment(
                showAsFullscreen()));
    }

    public ClearAllButton getClearAllButton() {
        return mClearAllButton;
    }

    @Nullable
    public AddDesktopButton getAddDeskButton() {
        return mAddDesktopButton;
    }

    /**
     * @return How many pixels the running task is offset on the currently laid out dominant axis.
     */
    public int getScrollOffset() {
        return getScrollOffset(getRunningTaskIndex());
    }

    /**
     * Returns how many pixels the running task is offset on the currently laid out dominant axis
     * specifically during a Keyboard task focus.
     */
    public int getScrollOffsetForKeyboardTaskFocus() {
        if (!isKeyboardTaskFocusPending()) {
            return getScrollOffset(getRunningTaskIndex());
        }
        return getPagedOrientationHandler().getPrimaryScroll(this)
                - getScrollForPage(indexOfChild(getKeyboardFocusTaskView()))
                + getScrollOffset(getRunningTaskIndex());
    }

    /**
     * Sets whether or not we should clamp the scroll offset.
     * This is used to avoid x-axis movement when swiping up transient taskbar.
     * Should only be set at the beginning and end of the gesture, otherwise a jump may occur.
     *
     * @param clampScrollOffset When true, we clamp the scroll to 0 before the clamp threshold is
     *                          met.
     */
    public void setClampScrollOffset(boolean clampScrollOffset) {
        mShouldClampScrollOffset = clampScrollOffset;
    }

    /**
     * Returns how many pixels the page is offset on the currently laid out dominant axis.
     */
    public int getScrollOffset(int pageIndex) {
        int unclampedOffset = getUnclampedScrollOffset(pageIndex);
        if (!mShouldClampScrollOffset) {
            return unclampedOffset;
        }
        if (Math.abs(unclampedOffset) < mClampedScrollOffsetBound) {
            return 0;
        }
        return unclampedOffset
                - Math.round(Math.signum(unclampedOffset) * mClampedScrollOffsetBound);
    }

    /**
     * Returns how many pixels the page is offset on the currently laid out dominant axis.
     */
    private int getUnclampedScrollOffset(int pageIndex) {
        if (pageIndex == INVALID_PAGE) {
            return 0;
        }
        // Don't dampen the scroll (due to overscroll) if the adjacent tasks are offscreen, so that
        // the page can move freely given there's no visual indication why it shouldn't.
        int overScrollShift = mAdjacentPageHorizontalOffset > 0
                ? (int) Utilities.mapRange(
                mAdjacentPageHorizontalOffset,
                getOverScrollShift(),
                getUndampedOverScrollShift())
                : getOverScrollShift();
        return getScrollForPage(pageIndex) - getPagedOrientationHandler().getPrimaryScroll(this)
                + overScrollShift + getOffsetFromScrollPosition(pageIndex);
    }

    /**
     * Returns how many pixels the page is offset from its scroll position.
     */
    private int getOffsetFromScrollPosition(int pageIndex) {
        return getOffsetFromScrollPosition(pageIndex, mUtils.getTopRowIdArray(),
                mUtils.getBottomRowIdArray());
    }

    private int getOffsetFromScrollPosition(
            int pageIndex, IntArray topRowIdArray, IntArray bottomRowIdArray) {
        return getOffsetFromScrollPosition(pageIndex, topRowIdArray, bottomRowIdArray,
                /* removedPageIndex= */ INVALID_PAGE);
    }

    protected int getOffsetFromScrollPosition(int pageIndex, IntArray topRowIdArray,
            IntArray bottomRowIdArray, int removedPageIndex) {
        if (!showAsGrid()) {
            return 0;
        }

        TaskView taskView = getTaskViewAt(pageIndex);
        if (taskView == null) {
            return 0;
        }

        TaskView lastGridTaskView = getLastGridTaskView(topRowIdArray, bottomRowIdArray);
        if (lastGridTaskView == null) {
            return 0;
        }

        int lastGridTaskViewIndex = indexOfChild(lastGridTaskView);
        // When computing offset, account for a removed page if provided.
        if (removedPageIndex != INVALID_PAGE && removedPageIndex < lastGridTaskViewIndex) {
            lastGridTaskViewIndex--;
        }
        if (getScrollForPage(pageIndex) != getScrollForPage(lastGridTaskViewIndex)) {
            return 0;
        }

        // Check distance from lastGridTaskView to taskView.
        int lastGridTaskViewPosition =
                getPositionInRow(lastGridTaskView, topRowIdArray, bottomRowIdArray);
        int taskViewPosition = getPositionInRow(taskView, topRowIdArray, bottomRowIdArray);
        int gridTaskSizeAndSpacing = mLastComputedGridTaskSize.width() + mPageSpacing;
        int positionDiff = gridTaskSizeAndSpacing * (lastGridTaskViewPosition - taskViewPosition);

        int taskEnd = getLastTaskEnd() + (mIsRtl ? positionDiff : -positionDiff);
        int normalTaskEnd = mIsRtl
                ? mLastComputedGridTaskSize.left
                : mLastComputedGridTaskSize.right;
        return taskEnd - normalTaskEnd;
    }

    private int getLastTaskEnd() {
        return mIsRtl
                ? mLastComputedGridSize.left + mPageSpacing + mClearAllShortTotalWidthTranslation
                : mLastComputedGridSize.right - mPageSpacing - mClearAllShortTotalWidthTranslation;
    }

    private int getPositionInRow(
            TaskView taskView, IntArray topRowIdArray, IntArray bottomRowIdArray) {
        int position = topRowIdArray.indexOf(taskView.getTaskViewId());
        return position != -1 ? position : bottomRowIdArray.indexOf(taskView.getTaskViewId());
    }

    /**
     * @return true if the task in on the bottom of the grid
     */
    public boolean isOnGridBottomRow(TaskView taskView) {
        return showAsGrid()
                && !mTopRowIdSet.contains(taskView.getTaskViewId())
                && !taskView.isLargeTile();
    }

    /**
     * @return true if the task in on the top of the grid
     */
    public boolean isOnGridTopRow(TaskView taskView) {
        return showAsGrid() && mTopRowIdSet.contains(taskView.getTaskViewId());
    }

    public Consumer<MotionEvent> getEventDispatcher(float navbarRotation) {
        float degreesRotated;
        if (navbarRotation == 0) {
            degreesRotated = getPagedOrientationHandler().getDegreesRotated();
        } else {
            degreesRotated = -navbarRotation;
        }
        if (degreesRotated == 0) {
            return super::onTouchEvent;
        }

        // At this point the event coordinates have already been transformed, so we need to
        // undo that transformation since PagedView also accommodates for the transformation via
        // PagedOrientationHandler
        return e -> {
            if (navbarRotation != 0
                    && mOrientationState.isMultipleOrientationSupportedByDevice()
                    && !mOrientationState.getOrientationHandler().isLayoutNaturalToLauncher()) {
                mOrientationState.flipVertical(e);
                super.onTouchEvent(e);
                mOrientationState.flipVertical(e);
                return;
            }
            mOrientationState.transformEvent(-degreesRotated, e, true);
            super.onTouchEvent(e);
            mOrientationState.transformEvent(-degreesRotated, e, false);
        };
    }

    private void updateEnabledOverlays() {
        Set<Integer> fullyVisibleTaskIds = new HashSet<>();
        for (TaskView taskView : getTaskViews()) {
            if (isTaskViewFullyVisible(taskView)) {
                fullyVisibleTaskIds.addAll(taskView.getTaskIdSet());
            }
        }
        mRecentsViewModel.updateTasksFullyVisible(fullyVisibleTaskIds);
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        if (mOverlayEnabled != overlayEnabled) {
            mOverlayEnabled = overlayEnabled;
            updateEnabledOverlays();

            mRecentsViewModel.setOverlayEnabled(overlayEnabled);
        }
    }

    public void setOverviewGridEnabled(boolean overviewGridEnabled) {
        if (mOverviewGridEnabled != overviewGridEnabled) {
            mOverviewGridEnabled = overviewGridEnabled;
            // Request layout to ensure scroll position is recalculated with updated mGridProgress.
            requestLayout();
        }
    }

    public void setOverviewFullscreenEnabled(boolean overviewFullscreenEnabled) {
        if (mOverviewFullscreenEnabled != overviewFullscreenEnabled) {
            mOverviewFullscreenEnabled = overviewFullscreenEnabled;
            // Request layout to ensure scroll position is recalculated with updated
            // mFullscreenProgress.
            requestLayout();
        }
    }

    /**
     * Update whether RecentsView is in select mode. Should be enabled before transitioning to
     * select mode, and only disabled after transitioning from select mode.
     */
    public void setOverviewSelectEnabled(boolean overviewSelectEnabled) {
        if (mOverviewSelectEnabled != overviewSelectEnabled) {
            mOverviewSelectEnabled = overviewSelectEnabled;
            updatePivots();
            if (!mOverviewSelectEnabled) {
                setSelectedTask(INVALID_TASK_ID);
            }
        }
    }

    /**
     * Switch the current running task view to static snapshot mode,
     * capturing the snapshot at the same time.
     */
    public void switchToScreenshot(Runnable onFinishRunnable) {
        if (mRecentsAnimationController == null) {
            if (onFinishRunnable != null) {
                onFinishRunnable.run();
            }
            return;
        }

        TaskView taskView = getRunningTaskView();
        if (taskView == null) {
            onFinishRunnable.run();
            return;
        }

        Map<Integer, ThumbnailData> updatedThumbnails = mUtils.screenshotTasks(taskView);
        mHelper.switchToScreenshot(taskView, updatedThumbnails, onFinishRunnable);
    }

    /**
     * Switch the current running task view to static snapshot mode, using the
     * provided thumbnail data as the snapshot.
     * TODO(b/195609063) Consolidate this method w/ the one above, except this thumbnail data comes
     *  from gesture state, which is a larger change of it having to keep track of multiple tasks.
     *  OR. Maybe it doesn't need to pass in a thumbnail and we can use the exact same flow as above
     */
    public void switchToScreenshot(@Nullable HashMap<Integer, ThumbnailData> thumbnailDatas,
            Runnable onFinishRunnable) {
        final TaskView taskView = getRunningTaskView();
        if (taskView != null) {
            mHelper.switchToScreenshot(taskView, thumbnailDatas, onFinishRunnable);
        } else {
            onFinishRunnable.run();
        }
    }

    /**
     * The current task is fully modal (modalness = 1) when it is shown on its own in a modal
     * way. Modalness 0 means the task is shown in context with all the other tasks.
     */
    private void setTaskModalness(float modalness) {
        mTaskModalness = modalness;
        updatePageOffsets();
        if (getSelectedTaskView() != null) {
            for (TaskView taskView : getTaskViews()) {
                taskView.setModalness(modalness);
            }
        } else if (getCurrentPageTaskView() != null) {
            getCurrentPageTaskView().setModalness(modalness);
        }
        // Only show actions view when it's modal for in-place landscape mode.
        mActionsView.updateHiddenFlags(
                HIDDEN_NON_ZERO_ROTATION, modalness < 1
                        && mOrientationState.shouldHideActionButtons()
        );
    }

    @Nullable
    protected DepthController<?, ?> getDepthController() {
        return null;
    }

    @Nullable
    protected DesktopRecentsTransitionController getDesktopRecentsController() {
        return mDesktopRecentsTransitionController;
    }

    /** Enables or disables modal state for RecentsView */
    public abstract void setModalStateEnabled(int taskId, boolean animate);

    public TaskOverlayFactory getTaskOverlayFactory() {
        return mTaskOverlayFactory;
    }

    public BaseContainerInterface<STATE_TYPE, ?> getContainerInterface() {
        return mContainerInterface;
    }

    public RecentsViewContainer getContainer() {
        return mContainer;
    }

    /**
     * Set all the task views to color tint scrim mode, dimming or tinting them all. Allows the
     * tasks to be dimmed while other elements in the recents view are left alone.
     */
    public void showForegroundScrim(boolean show) {
        if (!show && mColorTint == 0) {
            if (mTintingAnimator != null) {
                mTintingAnimator.cancel();
                mTintingAnimator = null;
            }
            return;
        }

        mTintingAnimator = ObjectAnimator.ofFloat(this, COLOR_TINT,
                show ? FOREGROUND_SCRIM_TINT : 0f);
        mTintingAnimator.setAutoCancel(true);
        mTintingAnimator.start();
    }

    /** Tint the RecentsView and TaskViews in to simulate a scrim. */
    private void setColorTint(float tintAmount) {
        mColorTint = tintAmount;

        for (TaskView taskView : getTaskViews()) {
            taskView.setColorTint(mColorTint, mTintingColor);
        }

        Drawable scrimBg = mContainer.getScrimView().getBackground();
        if (scrimBg != null) {
            if (tintAmount == 0f) {
                scrimBg.setTintList(null);
            } else {
                scrimBg.setTintBlendMode(BlendMode.SRC_OVER);
                scrimBg.setTint(
                        ColorUtils.setAlphaComponent(mTintingColor, (int) (255 * tintAmount)));
            }
        }
    }

    private float getColorTint() {
        return mColorTint;
    }

    /** Returns {@code true} if the overview tasks are displayed as a grid. */
    public boolean showAsGrid() {
        return mOverviewGridEnabled || (mCurrentGestureEndTarget != null
                && mContainerInterface.stateFromGestureEndTarget(mCurrentGestureEndTarget)
                .displayOverviewTasksAsGrid(mContainer.getDeviceProfile()));
    }

    protected boolean showAsFullscreen() {
        return mOverviewFullscreenEnabled
                && mCurrentGestureEndTarget != GestureState.GestureEndTarget.RECENTS;
    }

    public void cleanupRemoteTargets() {
        if (mRemoteTargetHandles == null) {
            return;
        }
        Log.d(TAG, "cleanupRemoteTargets - mRemoteTargetHandles: " + Arrays.toString(
                mRemoteTargetHandles));
        mRemoteTargetHandles = null;
    }

    /**
     * Used to register callbacks for when our empty message state changes.
     *
     * @see #setOnEmptyMessageUpdatedListener(OnEmptyMessageUpdatedListener)
     * @see #updateEmptyMessage()
     */
    public interface OnEmptyMessageUpdatedListener {
        /** @param isEmpty Whether RecentsView is empty (i.e. has no children) */
        void onEmptyMessageUpdated(boolean isEmpty);
    }

    /**
     * Adds a listener for scroll changes
     */
    public void addOnScrollChangedListener(OnScrollChangedListener listener) {
        if (mScrollListeners.contains(listener)) {
            if (BuildConfig.IS_STUDIO_BUILD) {
                throw new IllegalStateException(
                        "Should not add duplicated OnScrollChangedListener");
            } else {
                mScrollListeners.remove(listener);
            }
        }
        mScrollListeners.add(listener);
    }

    /**
     * Removes a previously added scroll change listener
     */
    public void removeOnScrollChangedListener(OnScrollChangedListener listener) {
        mScrollListeners.remove(listener);
    }

    /**
     * @return PiP resources for PiP window, which is updated via
     * {@link #mIPipAnimationListener}
     */
    public PipResources getPipResources() {
        return mPipResources;
    }

    @Override
    public boolean scrollLeft() {
        if (!showAsGrid()) {
            return super.scrollLeft();
        }

        int targetPage = getNextPage();
        if (targetPage >= 0) {
            // Find the next page that is not fully visible.
            TaskView taskView = getTaskViewAt(targetPage);
            while ((taskView == null || isTaskViewFullyVisible(taskView)) && targetPage - 1 >= 0) {
                taskView = getTaskViewAt(--targetPage);
            }
            // Target a scroll where targetPage is on left of screen but still fully visible.
            int normalTaskEnd = mIsRtl
                    ? mLastComputedGridTaskSize.left
                    : mLastComputedGridTaskSize.right;
            int targetScroll = getScrollForPage(targetPage) + normalTaskEnd - getLastTaskEnd();
            // Find a page that is close to targetScroll while not over it.
            while (targetPage - 1 >= 0
                    && (mIsRtl
                    ? getScrollForPage(targetPage - 1) < targetScroll
                    : getScrollForPage(targetPage - 1) > targetScroll)) {
                targetPage--;
            }
            snapToPage(targetPage);
            return true;
        }

        return mAllowOverScroll;
    }

    @Override
    public boolean scrollRight() {
        if (!showAsGrid()) {
            return super.scrollRight();
        }

        int targetPage = getNextPage();
        if (targetPage < getChildCount()) {
            // Find the next page that is not fully visible.
            TaskView taskView = getTaskViewAt(targetPage);
            while ((taskView != null && isTaskViewFullyVisible(taskView))
                    && targetPage + 1 < getChildCount()) {
                taskView = getTaskViewAt(++targetPage);
            }
            snapToPage(targetPage);
            return true;
        }
        return mAllowOverScroll;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        dispatchScrollChanged();
    }

    /**
     * Prepares this RecentsView to scroll properly for an upcoming child view focus request from
     * keyboard quick switching
     */
    public void setKeyboardFocusTask(@NonNull KeyboardFocusTask keyboardFocusTask) {
        mUtils.setKeyboardFocusTask(keyboardFocusTask);
    }

    /**
     * Returns the TaskView that will be focused for an upcoming child view focus request from
     * keyboard quick switching
     */
    @Nullable
    public TaskView getKeyboardFocusTaskView() {
        return mUtils.getKeyboardFocusTaskView();
    }

    /** Returns whether this RecentsView will be scrolling to a child view for a focus request */
    public boolean isKeyboardTaskFocusPending() {
        return mUtils.isKeyboardTaskFocusPending();
    }

    private boolean isKeyboardTaskFocusPendingForChild(View child) {
        return isKeyboardTaskFocusPending() && getKeyboardFocusTaskView() == child;
    }

    @Override
    protected int getSnapAnimationDuration() {
        return isKeyboardTaskFocusPending()
                ? mKeyboardTaskFocusSnapAnimationDuration : super.getSnapAnimationDuration();
    }

    @Override
    protected void onVelocityValuesUpdated() {
        super.onVelocityValuesUpdated();
        mKeyboardTaskFocusSnapAnimationDuration =
                getResources().getInteger(R.integer.config_keyboardTaskFocusSnapAnimationDuration);
    }

    @Override
    protected boolean shouldHandleRequestChildFocus(View child) {
        // If we are already scrolling to a task view and we aren't focusing to this child from
        // keyboard quick switch, then the focus request has already been handled
        return mScroller.isFinished() || isKeyboardTaskFocusPendingForChild(child);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (isKeyboardTaskFocusPendingForChild(child)) {
            updateGridProperties();
            updateScrollSynchronously();
        }
        super.requestChildFocus(child, focused);
    }

    protected void dispatchScrollChanged() {
        runActionOnRemoteHandles(remoteTargetHandle ->
                remoteTargetHandle.getTaskViewSimulator().setScroll(getScrollOffset()));
        for (int i = mScrollListeners.size() - 1; i >= 0; i--) {
            mScrollListeners.valueAt(i).onScrollChanged();
        }
    }

    private static class PinnedStackAnimationListener<T extends RecentsViewContainer> extends
            IPipAnimationListener.Stub {
        @Nullable
        private WeakReference<T> mActivity;
        @Nullable
        private WeakReference<RecentsView> mRecentsView;

        public void setActivityAndRecentsView(@Nullable T activity,
                @Nullable RecentsView recentsView) {
            mActivity = new WeakReference<>(activity);
            mRecentsView = new WeakReference<>(recentsView);
        }

        @Override
        public void onPipAnimationStarted() {
            MAIN_EXECUTOR.execute(() -> {
                final T activity = mActivity.get();
                // Needed for activities that auto-enter PiP, which will not trigger a remote
                // animation to be created
                if (activity != null) {
                    activity.clearForceInvisibleFlag(STATE_HANDLER_INVISIBILITY_FLAGS);
                }
            });
        }

        @Override
        public void onPipResourceDimensionsChanged(PipResources res) {
            final RecentsView recentsView = mRecentsView.get();
            if (recentsView != null) {
                recentsView.mPipResources = res;
            }
        }

        @Override
        public void onExpandPip() {
            MAIN_EXECUTOR.execute(() -> {
                final RecentsView recentsView = mRecentsView.get();
                if (recentsView == null
                        || recentsView.mContainerInterface.getTaskbarInteractor() == null) {
                    return;
                }
                // Hide the task bar when leaving PiP to prevent it from flickering once
                // the app settles in full-screen mode.
                recentsView.mContainerInterface.getTaskbarInteractor().onExpandPip();
            });
        }
    }

    /** Get the color used for foreground scrimming the RecentsView for sharing. */
    public static int getForegroundScrimDimColor(Context context) {
        return context.getColor(R.color.overview_foreground_scrim_color);
    }

    /** Get the RecentsAnimationController */
    @Nullable
    public RecentsAnimationController getRecentsAnimationController() {
        return mRecentsAnimationController;
    }

    /** Update the current activity locus id to show the enabled state of Overview */
    public void updateLocusId() {
        String locusId = "Overview";

        if (mOverviewStateEnabled && mContainer.isStarted()) {
            locusId += "|ENABLED";
        } else {
            locusId += "|DISABLED";
        }

        final LocusId id = new LocusId(locusId);
        // Set locus context is a binder call, don't want it to happen during a transition
        UI_HELPER_EXECUTOR.post(() -> mContainer.setLocusContext(id, Bundle.EMPTY));
    }

    /**
     * Moves the provided task into desktop mode, and invoke {@code successCallback} if succeeded.
     */
    public void moveTaskToDesktop(TaskContainer taskContainer,
            DesktopModeTransitionSource transitionSource,
            Runnable successCallback) {
        if (!mDesktopState.canEnterDesktopMode()) {
            return;
        }
        switchToScreenshot(() -> finishRecentsAnimation(/* toHome= */true, /* shouldPip= */false,
                () -> moveTaskToDesktopInternal(taskContainer, successCallback, transitionSource)));
    }

    private void moveTaskToDesktopInternal(TaskContainer taskContainer,
            Runnable successCallback, DesktopModeTransitionSource transitionSource) {
        if (mDesktopRecentsTransitionController == null) {
            return;
        }

        mDesktopRecentsTransitionController.moveToDesktop(taskContainer, transitionSource, () -> {
            successCallback.run();
            if (mContainer instanceof RecentsWindowManager recentsWindowManager) {
                post(() -> recentsWindowManager.getStateManager().moveToRestState());
            }
        });
    }

    /**
     * Move the provided task into external display and invoke {@code successCallback} if succeeded.
     */
    public void moveTaskToExternalDisplay(TaskContainer taskContainer,
            DesktopModeTransitionSource transitionSource, Runnable successCallback) {
        if (!mDesktopState.canEnterDesktopMode()) {
            return;
        }
        switchToScreenshot(() -> finishRecentsAnimation(/* toHome= */true, /* shouldPip= */false,
                () -> moveTaskToExternalDisplayInternal(taskContainer, successCallback,
                        transitionSource)));
    }

    private void moveTaskToExternalDisplayInternal(TaskContainer taskContainer,
            Runnable successCallback,
            DesktopModeTransitionSource transitionSource) {
        if (mDesktopRecentsTransitionController == null) {
            return;
        }
        mDesktopRecentsTransitionController.moveToExternalDisplay(taskContainer.getTask().key.id,
                transitionSource);
        dismissTaskView(taskContainer.getTaskView(), /*removeTask*/ false);
        successCallback.run();
    }


    // Logs when the orientation of Overview changes. We log both real and fake orientation changes.
    private void logOrientationChanged() {
        // Only log when Overview is showing.
        if (mOverviewStateEnabled) {
            mContainer.getStatsLogManager()
                    .logger()
                    .withContainerInfo(
                            LauncherAtom.ContainerInfo.newBuilder()
                                    .setTaskSwitcherContainer(
                                            LauncherAtom.TaskSwitcherContainer.newBuilder()
                                                    .setOrientationHandler(
                                                            getPagedOrientationHandler()
                                                                    .getHandlerTypeForLogging()))
                                    .build())
                    .log(LAUNCHER_OVERVIEW_ORIENTATION_CHANGED);
        }
    }

    /**
     * Runs the spring animations as a task dismisses or settles back into its place in overview.
     *
     * <p>When a task dismiss is cancelled, the task will return to its original position via a
     * spring animation. As it passes the threshold of its settling state, its neighbors will
     * spring in response to the perceived impact of the settling task.
     */
    public RecentsDismissUtils.SpringSet runTaskDismissSettlingSpringAnimation(
            TaskView draggedTaskView, boolean isDismissing,
            RecentsDismissUtils.DismissedTaskData dismissedTaskData, boolean shouldRemoveTaskView,
            boolean isSplitSelection) {
        RecentsDismissUtils.SpringSet dismissSpringSet =
                mDismissUtils.createTaskDismissSpringAnimation(draggedTaskView, isDismissing,
                        dismissedTaskData, shouldRemoveTaskView, isSplitSelection);
        if (dismissSpringSet != null) {
            dismissSpringSet.start();
        }
        return dismissSpringSet;
    }

    /**
     * Animates RecentsView's scale to the provided value, using spring animations.
     */
    public SpringAnimation animateRecentsScale(float scale) {
        return mDismissUtils.animateRecentsScale(scale);
    }

    public interface TaskLaunchListener {
        void onTaskLaunched();
    }

    /**
     * Draws the remote animation targets above the recents view.
     *
     * @param remoteTargetHandles collection of remoteTargetHandles in Recents.
     */
    public void setDrawAboveRecents(RemoteTargetHandle[] remoteTargetHandles) {
        mBlurUtils.setDrawAboveRecents(remoteTargetHandles);
    }

    public Map<TaskView, Integer> getTaskViewsDismissPrimaryTranslations() {
        return mTaskViewsDismissPrimaryTranslations;
    }
}
