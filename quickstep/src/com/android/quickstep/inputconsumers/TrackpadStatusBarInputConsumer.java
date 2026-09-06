/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep.inputconsumers;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;

import static com.android.launcher3.MotionEventsUtils.isTrackpadThreeFingerSwipe;

import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Flags;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.InputMonitorCompat;

/** Allows the status bar to be pull down for notification shade using the trackpad. */
public class TrackpadStatusBarInputConsumer extends DelegateInputConsumer {

    private final Context mContext;
    private final SystemUiProxy mSystemUiProxy;
    private final RecentsAnimationDeviceState mDeviceState;
    private final float mTouchSlop;
    private final PointF mDown = new PointF();
    private boolean mHasPassedTouchSlop;
    private boolean mConsumeGesture;

    public TrackpadStatusBarInputConsumer(
            Context context,
            int displayId,
            InputConsumer delegate,
            InputMonitorCompat inputMonitor,
            RecentsAnimationDeviceState deviceState) {
        super(displayId, delegate, inputMonitor);

        mContext = context;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(context);
        mTouchSlop = deviceState.getTouchSlop();
        mDeviceState = deviceState;
    }

    @Override
    public int getType() {
        return TYPE_STATUS_BAR | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (mState != STATE_ACTIVE) {
            switch (ev.getActionMasked()) {
                case ACTION_DOWN -> {
                    mDown.set(ev.getX(), ev.getY());
                    mHasPassedTouchSlop = false;
                    mConsumeGesture = false;
                }
                case ACTION_MOVE -> {
                    if (!mHasPassedTouchSlop) {
                        float displacementY = ev.getY() - mDown.y;
                        if (Math.abs(displacementY) > mTouchSlop) {
                            mHasPassedTouchSlop = true;
                            if (displacementY > 0 || mDeviceState.isNotificationPanelVisible()) {
                                if (displacementY > 0 && Flags.enableNewTouchpadGestures()
                                        && isThreeFingerTrackpadSwipe(ev)) {
                                    tryLaunchCurrentTaskIfInOverview();
                                }
                                if (!mConsumeGesture) {
                                    setActive(ev);
                                    ev.setAction(ACTION_DOWN);
                                    dispatchTouchEvent(ev);
                                }
                            }
                        }
                    }
                }
            }

            // Don't forward the event to the delegate if we just became active above.
            if (mState != STATE_ACTIVE) {
                mDelegate.onMotionEvent(ev);
            }
        } else {
            dispatchTouchEvent(ev);
        }
    }

    @VisibleForTesting
    protected boolean isThreeFingerTrackpadSwipe(MotionEvent ev) {
        return isTrackpadThreeFingerSwipe(ev);
    }

    private void dispatchTouchEvent(MotionEvent ev) {
        if (mSystemUiProxy.isActive()) {
            mSystemUiProxy.onStatusBarTrackpadEvent(ev);
        }
    }

    private void tryLaunchCurrentTaskIfInOverview() {
        BaseContainerInterface<?, ?> containerInterface =
                OverviewComponentObserver.INSTANCE.get(mContext)
                        .getContainerInterface(getDisplayId());
        if (containerInterface == null) {
            return;
        }
        RecentsViewContainer container = containerInterface.getCreatedContainer();
        if (container == null || !container.isRecentsViewVisible()) {
            return;
        }
        RecentsView<?, ?> recentsView = container.getOverviewPanel();
        if (recentsView == null) {
            return;
        }
        TaskView taskView = recentsView.getCurrentPageTaskView();
        if (recentsView.shouldSwipeDownLaunchTaskView(taskView)) {
            taskView.launchWithAnimation();
        }
        mConsumeGesture = true;

    }

    @Override
    protected String getDelegatorName() {
        return "TrackpadStatusBarInputConsumer";
    }
}
