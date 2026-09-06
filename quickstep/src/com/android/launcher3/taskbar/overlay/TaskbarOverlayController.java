/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar.overlay;

import static android.os.Trace.TRACE_TAG_APP;
import static android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME;
import static android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_CONSUME_IME_INSETS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.window.DesktopModeFlags.ENABLE_TASKBAR_OVERFLOW;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.util.Executors.getTaskbarUiThread;
import static com.android.systemui.shared.Flags.cueBarAceMigration;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.gui.EarlyWakeupInfo;
import android.os.Binder;
import android.os.Trace;
import android.util.Log;
import android.view.AttachedSurfaceControl;
import android.view.CrossWindowBlurListeners;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarBootAppContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.bubbles.BubbleActivityStarter;
import com.android.launcher3.taskbar.bubbles.BubbleActivityStarter.Listener;
import com.android.systemui.shared.system.BlurUtils;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.io.PrintWriter;
import java.util.Optional;

/**
 * Handles the Taskbar overlay window lifecycle.
 * <p>
 * Overlays need to be inflated in a separate window so that have the correct hierarchy. For
 * instance, they need to be below the notification tray. If there are multiple overlays open, the
 * same window is used.
 */
public final class TaskbarOverlayController
        implements TaskbarControllers.LoggableTaskbarController {

    private static final String TAG = "TaskbarOverlayController";
    private static final String WINDOW_TITLE = "Taskbar Overlay";
    private static final boolean DEBUG = true; // b/446041145

    private final TaskbarActivityContext mTaskbarContext;
    private final Context mWindowContext;
    private final TaskbarOverlayProxyView mProxyView;
    private final LayoutParams mLayoutParams;
    private final int mMaxBlurRadius;
    private final BubbleActivityStarter mBubbleBarActivityStarter;
    private String mDebugTouchableReason = "";
    private final Rect mDebugTouchableBounds = new Rect();

    private final Listener mBubbleShowListener = new Listener() {
        @Override
        public void onBubbleLaunchRequested() {
            mBubbleShowRequested = true;
        }
    };

    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onTaskMovedToFront(int taskId) {
            // New front task will be below existing overlay, so move out of the way.
            getTaskbarUiThread().execute(this::hideWindowOnTaskStackChange);
        }

        @Override
        public void onTaskStackChanged() {
            getTaskbarUiThread().execute(() -> {
                // The other callbacks are insufficient for All Apps, because there are many cases
                // where it can relaunch the same task already behind it. However, this callback
                // needs to be a no-op when only EDU is shown, because going between the EDU steps
                // invokes this callback.
                if (mControllers.getSharedState() != null
                        && mControllers.getSharedState().allAppsVisible) {
                    hideWindowOnTaskStackChange();
                }
            });
        }

        private void hideWindowOnTaskStackChange() {
            getTaskbarUiThread().execute(() -> {
                // A task was launched while overlay window was open, so stash Taskbar.
                mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(
                        /* stash = */ true,
                        /* shouldBubblesFollow = */ !mBubbleShowRequested
                );
                boolean cueBarVisible = cueBarAceMigration()
                        && (mControllers.getSharedState() != null
                        && mControllers.getSharedState().cueBarVisible);
                // Don't hide the window when cueBar is visible. This method can be invoked when
                // cueBar is clicked due to onTaskMovedToFront() and hide the cueBar unexpectedly.
                if (!cueBarVisible) {
                    hideWindow();
                }
            });
        }
    };

    private DeviceProfile mLauncherDeviceProfile;
    private @Nullable TaskbarOverlayContext mOverlayContext;
    private TaskbarControllers mControllers; // Initialized in init.
    // True if we have alerted surface flinger of an expensive call for blur.
    private boolean mInEarlyWakeUp;
    private boolean mBubbleShowRequested;
    /**
     * Token for early wakeup requests to SurfaceFlinger.
     */
    private EarlyWakeupInfo mEarlyWakeupInfo = new EarlyWakeupInfo();

    public TaskbarOverlayController(
            TaskbarActivityContext taskbarContext, DeviceProfile launcherDeviceProfile) {
        mTaskbarContext = taskbarContext;
        mWindowContext = mTaskbarContext.createWindowContext(TYPE_APPLICATION_OVERLAY, null);
        mProxyView = new TaskbarOverlayProxyView();
        mLayoutParams = createLayoutParams();
        mLauncherDeviceProfile = launcherDeviceProfile;
        mMaxBlurRadius = mTaskbarContext.getResources().getDimensionPixelSize(
                R.dimen.max_depth_blur_radius_enhanced);
        mEarlyWakeupInfo.token = new Binder();
        mEarlyWakeupInfo.trace = TaskbarOverlayController.class.getName();
        mBubbleBarActivityStarter = BubbleActivityStarter.INSTANCE.get(taskbarContext);
    }

    /** Initialize the controller. */
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;

        if (supportsTaskbarBehindShade()) {
            // To avoid jank caused by creating the window, we request it early but keep it hidden.
            requestWindow();
            mOverlayContext.getDragLayer().setVisibility(View.GONE);
        }
    }

    /**
     * Creates a window for Taskbar overlays, if it does not already exist. Returns the window
     * context for the current overlay window.
     */
    public TaskbarOverlayContext requestWindow() {
        return requestWindowInternal(mLayoutParams, /* shouldListenForBubbles= */ true);
    }

    @SuppressLint("WrongConstant")
    public TaskbarOverlayContext requestCueBarWindow() {
        LayoutParams cueBarParams = createLayoutParams();
        cueBarParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        cueBarParams.privateFlags &= ~PRIVATE_FLAG_CONSUME_IME_INSETS;
        return requestWindowInternal(cueBarParams, /* shouldListenForBubbles= */ false);
    }

    private TaskbarOverlayContext requestWindowInternal(LayoutParams layoutParams,
            boolean shouldListenForBubbles) {
        if (DEBUG) {
            Log.d(TAG, "requestWindow: " + Utilities.getTrimmedStackTrace("requestWindow"));
            Log.d(TAG, "requestWindow: Was window already present? " + (mOverlayContext != null));
        }
        if (mOverlayContext == null) {
            mOverlayContext =
                    new TaskbarOverlayContext(mWindowContext, mTaskbarContext, mControllers);
            if (shouldListenForBubbles) {
                mBubbleBarActivityStarter.addListener(mBubbleShowListener);
            }
        }

        WindowManager wm = mOverlayContext.getSystemService(WindowManager.class);
        if (!mProxyView.isOpen()) {
            mProxyView.show();
            if (wm != null) {
                if (mOverlayContext.getDragLayer().getParent() == null) {
                    wm.addView(mOverlayContext.getDragLayer(), layoutParams);
                } else {
                    wm.updateViewLayout(mOverlayContext.getDragLayer(), layoutParams);
                }
            }
            TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
        } else if (wm != null) {
            wm.updateViewLayout(mOverlayContext.getDragLayer(), layoutParams);
        }

        mOverlayContext.getDragLayer().setVisibility(View.VISIBLE);
        return mOverlayContext;
    }

    /** Hides the current overlay window with animation. */
    public void hideWindow() {
        mProxyView.close(true);
    }

    /**
     * Removes the overlay window from the hierarchy, if all floating views are closed and there is
     * no system drag operation in progress.
     * <p>
     * This method should be called after an exit animation finishes, if applicable.
     */
    void maybeCloseWindow() {
        if (!canCloseWindow()) return;
        mProxyView.close(false);
        if (supportsTaskbarBehindShade()) {
            reset();
        } else {
            onDestroy();
        }
    }

    private void reset() {
        if (cueBarAceMigration()) {
            mControllers.cueBarController.cleanUpOverlay();
        }
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
        if (mOverlayContext != null) {
            mOverlayContext.getDragLayer().setVisibility(View.GONE);
        }
        mBubbleBarActivityStarter.removeListener(mBubbleShowListener);
        mBubbleShowRequested = false;
    }

    @SuppressLint("WrongConstant")
    private boolean canCloseWindow() {
        if (mOverlayContext == null) return true;
        if (AbstractFloatingView.hasOpenView(mOverlayContext, TYPE_ALL)) return false;
        return !mOverlayContext.getDragController().isSystemDragInProgress();
    }

    /** Destroys the controller and any overlay window if present. */
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "onDestroy: " + Utilities.getTrimmedStackTrace("onDestroy"));
            Log.d(TAG, "onDestroy: Was window already present? " + (mOverlayContext != null));
        }
        reset();
        Optional.ofNullable(mOverlayContext).ifPresent(c -> {
            c.onDestroy();
            WindowManager wm = c.getSystemService(WindowManager.class);
            if (wm != null) {
                wm.removeViewImmediate(mOverlayContext.getDragLayer());
            }
        });
        mOverlayContext = null;
    }

    /** The current device profile for the overlay window. */
    public DeviceProfile getLauncherDeviceProfile() {
        return mLauncherDeviceProfile;
    }

    /** Updates {@link deviceprofile} instance for Taskbar's overlay window. */
    public void updateLauncherDeviceProfile(DeviceProfile dp) {
        mLauncherDeviceProfile = dp;
        Optional.ofNullable(mOverlayContext).ifPresent(c -> {
            AbstractFloatingView.closeAllOpenViewsExcept(c, false, TYPE_REBIND_SAFE);
            c.dispatchDeviceProfileChanged();
        });
    }

    /** The default open duration for overlays. */
    public int getOpenDuration() {
        return ALL_APPS.getTransitionDuration(mTaskbarContext, true);
    }

    /** The default close duration for overlays. */
    public int getCloseDuration() {
        return ALL_APPS.getTransitionDuration(mTaskbarContext, false);
    }

    @SuppressLint("WrongConstant")
    private LayoutParams createLayoutParams() {
        LayoutParams layoutParams = new LayoutParams(
                TYPE_APPLICATION_OVERLAY,
                /* flags = */ 0,
                PixelFormat.TRANSLUCENT);
        layoutParams.setTitle(WINDOW_TITLE);
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.packageName = mTaskbarContext.getPackageName();
        layoutParams.setFitInsetsTypes(0); // Handled by container view.
        layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        layoutParams.setSystemApplicationOverlay(true);
        layoutParams.privateFlags |= PRIVATE_FLAG_CONSUME_IME_INSETS;
        return layoutParams;
    }

    /**
     * Sets the blur radius for the overlay window.
     *
     * @param radius the blur radius in pixels. This will automatically change to {@code 0} if blurs
     *               are unsupported on the device.
     */
    public void setBackgroundBlurRadius(int radius) {

        if (!BlurUtils.supportsBlursOnWindows()) {
            Log.d(TAG, "setBackgroundBlurRadius: not supported, setting to 0");
            radius = 0;
            // intentionally falling through in case a non-0 blur was previously set.
        }
        if (!CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled()) {
            Log.d(TAG, "setBackgroundBlurRadius: disabled, setting to 0");
            radius = 0;
            // intentionally falling through in case a non-0 blur was previously set.
        }
        if (mOverlayContext == null) {
            Log.w(TAG, "setBackgroundBlurRadius: no overlay context");
            return;
        }
        TaskbarOverlayDragLayer dragLayer = mOverlayContext.getDragLayer();
        if (dragLayer == null) {
            Log.w(TAG, "setBackgroundBlurRadius: no drag layer");
            return;
        }
        ViewRootImpl dragLayerViewRoot = dragLayer.getViewRootImpl();
        if (dragLayerViewRoot == null) {
            Log.w(TAG, "setBackgroundBlurRadius: dragLayerViewRoot is null");
            return;
        }
        AttachedSurfaceControl rootSurfaceControl = dragLayer.getRootSurfaceControl();
        if (rootSurfaceControl == null) {
            Log.w(TAG, "setBackgroundBlurRadius: rootSurfaceControl is null");
            return;
        }
        SurfaceControl surfaceControl = dragLayerViewRoot.getSurfaceControl();
        if (surfaceControl == null || !surfaceControl.isValid()) {
            Log.w(TAG, "setBackgroundBlurRadius: surfaceControl is null or invalid");
            return;
        }
        Log.v(TAG, "setBackgroundBlurRadius: " + radius);
        final SurfaceControl.Transaction transaction =
                new SurfaceControl.Transaction().setBackgroundBlurRadius(surfaceControl, radius);

        try (transaction) {
            // Set early wake-up flags when we know we're executing an expensive operation, this way
            // SurfaceFlinger will adjust its internal offsets to avoid jank.
            boolean wantsEarlyWakeUp = radius > 0 && radius < mMaxBlurRadius;
            if (wantsEarlyWakeUp && !mInEarlyWakeUp) {
               Log.d(TAG, "setBackgroundBlurRadius: setting early wakeup with token "
                                                    + mEarlyWakeupInfo);
                Trace.instantForTrack(TRACE_TAG_APP, TAG, "notifyRendererForGpuLoadUp");
                dragLayerViewRoot.notifyRendererForGpuLoadUp("setBackgroundBlurRadius");
                transaction.setEarlyWakeupStart(mEarlyWakeupInfo);
                mInEarlyWakeUp = true;
            } else if (!wantsEarlyWakeUp && mInEarlyWakeUp) {
                Log.d(TAG, "setBackgroundBlurRadius: clearing early wakeup with token "
                                                    + mEarlyWakeupInfo);
                transaction.setEarlyWakeupEnd(mEarlyWakeupInfo);
                mInEarlyWakeUp = false;
            }

            rootSurfaceControl.applyTransactionOnDraw(transaction);
        }
    }

    /** Returns {@code true} if overlay or Taskbar windows are handling a system drag. */
    public boolean isAnySystemDragInProgress() {
        boolean overlaySystemDragInProgress = mOverlayContext != null
                && mOverlayContext.getDragController().isSystemDragInProgress();
        return overlaySystemDragInProgress
                || mTaskbarContext.getDragController().isSystemDragInProgress();
    }

    /**
     * Returns {@code true} if either overlay or Taskbar windows are handlng a system drag for
     * which Taskbar is a viable drop target.
     */
    public boolean taskbarIsViableTargetForSystemDrag() {
        return (mOverlayContext != null
                && mOverlayContext.getDragController().getTaskbarIsViableTargetForSystemDrag())
                || mTaskbarContext.getDragController().getTaskbarIsViableTargetForSystemDrag();
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarOverlayController:");
        pw.println(prefix + "\tlast touchable reason=" + mDebugTouchableReason);
        pw.println(prefix + "\tlast touchable bounds=" + mDebugTouchableBounds);
    }

    /**
     * Proxy view connecting taskbar drag layer to the overlay window.
     *
     * Overlays are in a separate window and has its own drag layer, but this proxy lets its views
     * behave as though they are in the taskbar drag layer. For instance, when the taskbar closes
     * all {@link AbstractFloatingView} instances, the overlay window will also close.
     */
    private class TaskbarOverlayProxyView extends AbstractFloatingView {

        private TaskbarOverlayProxyView() {
            super(mTaskbarContext, null);
        }

        private void show() {
            mIsOpen = true;
            mTaskbarContext.getDragLayer().addView(this);
        }

        @Override
        protected void handleClose(boolean animate) {
            if (!mIsOpen) return;
            if (ENABLE_TASKBAR_OVERFLOW.isTrue()) {
                // Mark the view closed before attempting to remove it, so the drag layer does not
                // schedule another call to close. Needed for taskbar overflow in case the KQS
                // view shown for taskbar overflow needs to be reshown - delayed close call would
                // would result in reshown KQS view getting hidden.
                mIsOpen = false;
            }
            mTaskbarContext.getDragLayer().removeView(this);
            Optional.ofNullable(mOverlayContext).ifPresent(c -> {
                if (canCloseWindow()) {
                    if (supportsTaskbarBehindShade()) {
                        reset(); // Window is already ready to be reset.
                    } else {
                        onDestroy(); // Window is already ready to be destroyed.
                    }
                } else {
                    // Close window's AFVs before destroying it. Its drag layer will attempt to
                    // close the proxy view again once its children are removed.
                    closeAllOpenViews(c, animate);
                }
            });
        }

        @Override
        protected boolean isOfType(int type) {
            return (type & TYPE_TASKBAR_OVERLAY_PROXY) != 0;
        }

        @Override
        public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
            return false;
        }
    }

    public void updateInsetsTouchability(ViewTreeObserver.InternalInsetsInfo insetsInfo) {
        if (mControllers == null || mControllers.getSharedState() == null) {
            insetsInfo.touchableRegion.setEmpty();
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
            mDebugTouchableReason = "Controllers not initialized";
            mDebugTouchableBounds.setEmpty();
            return;
        }
        // Start with an empty region
        insetsInfo.touchableRegion.setEmpty();
        final String reason;
        final int touchableInsets;
        final boolean cueBarVisible = cueBarAceMigration()
                && mControllers.getSharedState().cueBarVisible;
        final boolean hasOpenFloatingViews = mOverlayContext != null
                && AbstractFloatingView.hasOpenView(mOverlayContext, TYPE_ALL);
        final boolean isKeyboardQuickSwitchOpen =
                mControllers.keyboardQuickSwitchController.isShown();
        final boolean isManageWindowsPopupOpen =
                mControllers.taskbarPopupController.isManageWindowsViewOpen();
        if (isAnySystemDragInProgress()) {
            touchableInsets = TOUCHABLE_INSETS_REGION;
            reason = "System drag in progress (empty region)";
        } else if (hasOpenFloatingViews) {
            // If all apps or another floating view is open, be modal (intercept all touches within
            // the frame).
            touchableInsets = TOUCHABLE_INSETS_FRAME;
            reason = "Floating view open (modal FRAME)";
        } else if (isKeyboardQuickSwitchOpen) {
            // If keyboard quick switch is open, be modal (intercept all touches within
            // the frame).
            touchableInsets = TOUCHABLE_INSETS_FRAME;
            reason = "Keyboard Quick Switch view open (modal FRAME)";
        } else if (isManageWindowsPopupOpen) {
            // If taskbar manage (multi-instance) app windows view is open, be modal (intercept all
            // touches within the frame).
            touchableInsets = TOUCHABLE_INSETS_FRAME;
            reason = "Manage multi-instance app windows view open (modal FRAME)";
        } else if (cueBarVisible) {
            mControllers.cueBarController.addTouchableRegion(insetsInfo.touchableRegion);
            if (mControllers.cueBarController.isExpanded()) {
                touchableInsets = TOUCHABLE_INSETS_FRAME;
                reason = "Cue bar is visible (expanded, modal FRAME)";
            } else {
                touchableInsets = TOUCHABLE_INSETS_REGION;
                reason = "Cue bar is visible (pill region)";
            }
        } else {
            // Default: Let touches pass through us (empty region).
            touchableInsets = TOUCHABLE_INSETS_REGION;
            reason = "Default (empty pass-through region)";
        }
        insetsInfo.setTouchableInsets(touchableInsets);
        mDebugTouchableReason = reason;
        mDebugTouchableBounds.set(insetsInfo.touchableRegion.getBounds());
    }

    private boolean supportsTaskbarBehindShade() {
        return Flags.enableTaskbarBehindShade()
                && !(mTaskbarContext.getApplicationContext() instanceof TaskbarBootAppContext);
    }
}
