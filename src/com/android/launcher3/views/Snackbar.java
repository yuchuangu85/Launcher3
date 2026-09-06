/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.views;

import static android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS;
import static android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.dragndrop.DragLayer;

/**
 * A toast-like UI at the bottom of the screen with a label, button action, and dismiss action.
 */
public class Snackbar extends AbstractFloatingView {

    static final long SHOW_DURATION_MS = 180;
    static final long HIDE_DURATION_MS = 180;
    static final long TIMEOUT_DURATION_LARGE_SCREEN_MS = 6000;
    static final long TIMEOUT_DURATION_DEFAULT_MS = 4000;

    private static SnackbarDismissTimer sSnackbarTestDismissTimer = null;

    private final ActivityContext mActivity;
    private Runnable mOnDismissed;

    // Hover count to track whether snackbar and its child views are being hovered. When the count
    // is positive, it means we should pause the dismiss timeout; when the count is set back to 0,
    // we can resume the timeout.
    private int mHoveredCount = 0;

    /** Interface for scheduling and cancelling dismiss actions for testing. */
    @VisibleForTesting
    public interface SnackbarDismissTimer {
        /** Schedules a runnable to be executed after a timeout. */
        void post(View snackbar, Runnable runnable, long timeout);

        /** Cancels a previously scheduled runnable. */
        void cancel(View snackbar, Runnable runnable);
    }

    @VisibleForTesting
    static void setSnackbarTestDismissTimer(SnackbarDismissTimer timer) {
        sSnackbarTestDismissTimer = timer;
    }

    private static void scheduleDismissAction(View snackbar, Runnable action, long timeout) {
        if (sSnackbarTestDismissTimer != null) {
            sSnackbarTestDismissTimer.post(snackbar, action, timeout);
        } else {
            snackbar.postDelayed(action, timeout);
        }
    }

    private static void cancelDismissAction(View snackbar, Runnable action) {
        if (sSnackbarTestDismissTimer != null) {
            sSnackbarTestDismissTimer.cancel(snackbar, action);
        } else {
            snackbar.removeCallbacks(action);
        }
    }

    public Snackbar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Snackbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivity = ActivityContext.lookupContext(context);
        inflate(context, R.layout.snackbar, this);
    }

    /** Show a snackbar with just a label. */
    public static <T extends Context & ActivityContext> void show(T activity, int labelStringRedId,
            Runnable onDismissed) {
        show(activity, labelStringRedId, NO_ID, onDismissed, null);
    }

    /** Show a snackbar with just a label. */
    public static void show(
            ActivityContext activity, CharSequence labelString, Runnable onDismissed) {
        show(activity, labelString, NO_ID, onDismissed, null);
    }

    /** Show a snackbar with a label and action. */
    public static <T extends Context & ActivityContext> void show(T activity, int labelStringResId,
            int actionStringResId, Runnable onDismissed, @Nullable Runnable onActionClicked) {
        show(
                activity,
                activity.getResources().getText(labelStringResId),
                actionStringResId,
                onDismissed,
                onActionClicked);
    }

    /** Show a snackbar with a label and action. */
    public static void show(ActivityContext activity, CharSequence labelString,
            int actionStringResId, Runnable onDismissed, @Nullable Runnable onActionClicked) {
        closeOpenViews(activity, true, TYPE_SNACKBAR);
        Snackbar snackbar = new Snackbar((Context) activity, null);
        // Set some properties here since inflated xml only contains the children.
        snackbar.setOrientation(HORIZONTAL);
        snackbar.setGravity(Gravity.CENTER_VERTICAL);
        Resources res = snackbar.getResources();
        snackbar.setElevation(res.getDimension(R.dimen.snackbar_elevation));
        int padding = res.getDimensionPixelSize(R.dimen.snackbar_padding);
        snackbar.setPadding(padding, padding, padding, padding);
        snackbar.setBackgroundResource(R.drawable.round_rect_primary);

        snackbar.mIsOpen = true;
        BaseDragLayer dragLayer = activity.getDragLayer();
        dragLayer.addView(snackbar);

        DragLayer.LayoutParams params = (DragLayer.LayoutParams) snackbar.getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        params.height = res.getDimensionPixelSize(R.dimen.snackbar_height);
        int maxMarginLeftRight = res.getDimensionPixelSize(R.dimen.snackbar_max_margin_left_right);
        int minMarginLeftRight = res.getDimensionPixelSize(R.dimen.snackbar_min_margin_left_right);
        int marginBottom = res.getDimensionPixelSize(R.dimen.snackbar_margin_bottom);
        int absoluteMaxWidth = res.getDimensionPixelSize(R.dimen.snackbar_max_width);
        Rect insets = activity.getDeviceProfile().getInsets();
        int maxWidth = Math.min(
                dragLayer.getWidth() - minMarginLeftRight * 2 - insets.left - insets.right,
                absoluteMaxWidth);
        int minWidth = Math.min(
                dragLayer.getWidth() - maxMarginLeftRight * 2 - insets.left - insets.right,
                absoluteMaxWidth);
        params.width = minWidth;
        DeviceProfile deviceProfile = activity.getDeviceProfile();
        params.setMargins(0, 0, 0, marginBottom
                + (deviceProfile.getDeviceProperties().getTaskbarConfiguration().isTaskbarPresent()
                ? deviceProfile.getTaskbarProfile().getHeight() + deviceProfile.getTaskbarOffsetY()
                : insets.bottom));

        TextView labelView = snackbar.findViewById(R.id.label);
        labelView.setText(labelString);

        Button actionView = snackbar.findViewById(R.id.action);
        float actionWidth;

        // When the user hovers over the snackbar, we want to pause the dismiss timeout so that it
        // doesn't disappear. Once the user stops hovering, we want to resume the timeout. This
        // hover listener should be added to both snackbar and action view.
        // A typical flow is as follows:
        //   - cursor moved into snackbar
        //     - hover enters snackbar
        //     - remove dismiss callback
        //   - cursor moved into action view
        //     - hover enters action view
        //     - hover exits snackbar
        //   - cursor moved out of action view
        //     - hover exits action view
        //     - hover enters snackbar
        //   - cursor moved out of snackbar
        //     - hover exits snackbar
        //     - resume dismiss callback
        long dismissTimeout = getDismissTimeout(activity);
        final Runnable dismissRunnable = () -> snackbar.close(true);
        final OnHoverListener hoverListener = (v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_HOVER_ENTER
                    && event.getAction() != MotionEvent.ACTION_HOVER_EXIT) {
                return false;
            }

            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                snackbar.mHoveredCount++;
            } else {
                snackbar.mHoveredCount--;
            }

            if (snackbar.mHoveredCount == 0) {
                scheduleDismissAction(snackbar, dismissRunnable, dismissTimeout);
            } else if (snackbar.mHoveredCount > 0) {
                cancelDismissAction(snackbar, dismissRunnable);
            }

            return false;
        };

        if (actionStringResId != NO_ID) {
            String actionText = res.getString(actionStringResId);
            actionWidth = actionView.getPaint().measureText(actionText)
                    + actionView.getPaddingRight() + actionView.getPaddingLeft();
            actionView.setText(actionText);
            actionView.setOnClickListener(v -> {
                if (onActionClicked != null) {
                    onActionClicked.run();
                }
                snackbar.mOnDismissed = null;
                snackbar.close(true);
            });
            actionView.setOnHoverListener(hoverListener);
        } else {
            actionWidth = 0;
            actionView.setVisibility(GONE);
        }

        int totalContentWidth = (int) (labelView.getPaint().measureText(labelString.toString())
                    + actionWidth)
                + labelView.getPaddingRight() + labelView.getPaddingLeft()
                + padding * 2;
        if (totalContentWidth > params.width) {
            // The text doesn't fit in our standard width so update width to accommodate.
            if (totalContentWidth <= maxWidth) {
                params.width = totalContentWidth;
            } else {
                // One line will be cut off, fallback to 2 lines and smaller font. (This should only
                // happen in some languages if system display and font size are set to largest.)
                int textHeight = res.getDimensionPixelSize(R.dimen.snackbar_content_height);
                float textSizePx = res.getDimension(R.dimen.snackbar_min_text_size);
                labelView.setLines(2);
                labelView.getLayoutParams().height = textHeight * 2;
                actionView.getLayoutParams().height = textHeight * 2;
                labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
                actionView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
                params.height += textHeight;
                params.width = maxWidth;
            }
        }

        snackbar.mOnDismissed = onDismissed;
        snackbar.setAlpha(0);
        snackbar.setScaleX(0.8f);
        snackbar.setScaleY(0.8f);
        snackbar.animate()
                .alpha(1f)
                .withLayer()
                .scaleX(1)
                .scaleY(1)
                .setDuration(SHOW_DURATION_MS)
                .setInterpolator(Interpolators.ACCELERATE_DECELERATE)
                .withEndAction(() -> {
                    if (actionView.getVisibility() == VISIBLE) {
                        actionView.performAccessibilityAction(ACTION_ACCESSIBILITY_FOCUS, null);
                    }
                })
                .start();
        scheduleDismissAction(snackbar, dismissRunnable, dismissTimeout);
        snackbar.setOnHoverListener(hoverListener);
    }

    /**
     * Returns the recommended timeout for dismissing the snackbar, taking into account device
     * properties. The timeout is longer for large screen devices to give users more time to
     * interact.
     *
     * @param activity the activity context used to access resources and device properties
     * @return the recommended timeout in milliseconds
     */
    @VisibleForTesting
    static long getDismissTimeout(ActivityContext activity) {
        DeviceProfile deviceProfile = activity.getDeviceProfile();
        return AccessibilityManagerCompat.getRecommendedTimeoutMillis(
                (Context) activity,
                (int) (deviceProfile.getDeviceProperties().isLargeScreen()
                    ? TIMEOUT_DURATION_LARGE_SCREEN_MS : TIMEOUT_DURATION_DEFAULT_MS),
                FLAG_CONTENT_TEXT | FLAG_CONTENT_CONTROLS);
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mIsOpen) {
            if (animate) {
                animate().alpha(0f)
                        .withLayer()
                        .setStartDelay(0)
                        .setDuration(HIDE_DURATION_MS)
                        .setInterpolator(Interpolators.ACCELERATE)
                        .withEndAction(this::onClosed)
                        .start();
            } else {
                animate().cancel();
                onClosed();
            }
            mIsOpen = false;
        }
    }

    private void onClosed() {
        mActivity.getDragLayer().removeView(this);
        if (mOnDismissed != null) {
            mOnDismissed.run();
        }
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_SNACKBAR) != 0;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BaseDragLayer dl = mActivity.getDragLayer();
            if (!dl.isEventOverView(this, ev)) {
                close(true);
            }
        }
        return false;
    }
}
