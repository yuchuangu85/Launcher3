/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppModule;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.util.SandboxApplication;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.InputMonitorCompat;

import dagger.BindsInstance;
import dagger.Component;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TrackpadStatusBarInputConsumerTest {

    private static final int DISPLAY_ID = Display.DEFAULT_DISPLAY;
    private static final float TOUCH_SLOP = 10f;

    @Rule public final SandboxApplication mContext = new SandboxApplication();
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private InputConsumer mDelegate;
    @Mock private InputMonitorCompat mInputMonitor;
    @Mock private RecentsAnimationDeviceState mDeviceState;
    @Mock private RecentsViewContainer mRecentsViewContainer;
    @Mock private RecentsView mRecentsView;
    @Mock private TaskView mTaskView;
    @Mock private BaseContainerInterface mBaseContainerInterface;

    @Mock public OverviewComponentObserver mOverviewComponentObserver;
    @Mock public SystemUiProxy mSystemUiProxy;

    private TrackpadStatusBarInputConsumer mUnderTest;

    private boolean mIsThreeFingerTrackpadSwipe;
    @Before
    public void setup() {
        when(mDeviceState.getTouchSlop()).thenReturn(TOUCH_SLOP);
        when(mOverviewComponentObserver.getContainerInterface(anyInt()))
                .thenReturn(mBaseContainerInterface);
        when(mBaseContainerInterface.getCreatedContainer()).thenReturn(mRecentsViewContainer);
        when(mRecentsViewContainer.getOverviewPanel()).thenReturn(mRecentsView);
        when(mRecentsView.getCurrentPageTaskView()).thenReturn(mTaskView);
        when(mRecentsView.shouldSwipeDownLaunchTaskView(mTaskView)).thenReturn(true);

        mContext.initDaggerComponent(
                DaggerTrackpadStatusBarInputConsumerTest_TestComponent.builder()
                .bindOverviewComponentObserver(mOverviewComponentObserver)
                .bindSystemUiProxy(mSystemUiProxy)
        );
        mUnderTest = new TrackpadStatusBarInputConsumer(
                mContext, DISPLAY_ID, mDelegate, mInputMonitor, mDeviceState) {
            @Override
            protected boolean isThreeFingerTrackpadSwipe(MotionEvent ev) {
                return mIsThreeFingerTrackpadSwipe;
            }
        };
    }

    @Test
    public void testOnMotionEvent_threeFingerSwipeDown_inOverview_launchesTask() {
        when(mRecentsViewContainer.isRecentsViewVisible()).thenReturn(true);
        mIsThreeFingerTrackpadSwipe = true;

        // ACTION_DOWN
        mUnderTest.onMotionEvent(createMotionEvent(ACTION_DOWN, 0, 0));

        // ACTION_MOVE passing touch slop downwards
        MotionEvent moveEvent = createMotionEvent(ACTION_MOVE, 0, TOUCH_SLOP + 1);
        mUnderTest.onMotionEvent(moveEvent);

        verify(mTaskView).launchWithAnimation();
        verify(mSystemUiProxy, never()).onStatusBarTrackpadEvent(any());
    }

    @Test
    public void testOnMotionEvent_threeFingerSwipeDown_inOverview_cannotLaunchTask() {
        when(mRecentsViewContainer.isRecentsViewVisible()).thenReturn(true);
        when(mRecentsView.shouldSwipeDownLaunchTaskView(mTaskView)).thenReturn(false);
        when(mSystemUiProxy.isActive()).thenReturn(true);
        mIsThreeFingerTrackpadSwipe = true;

        // ACTION_DOWN
        mUnderTest.onMotionEvent(createMotionEvent(ACTION_DOWN, 0, 0));

        // ACTION_MOVE passing touch slop downwards
        MotionEvent moveEvent = createMotionEvent(ACTION_MOVE, 0, TOUCH_SLOP + 1);
        mUnderTest.onMotionEvent(moveEvent);

        verify(mTaskView, never()).launchWithAnimation();
    }

    @Test
    public void testOnMotionEvent_threeFingerSwipeDown_notInOverview_dispatchesToStatusBar() {
        when(mRecentsViewContainer.isRecentsViewVisible()).thenReturn(false);
        when(mSystemUiProxy.isActive()).thenReturn(true);
        mIsThreeFingerTrackpadSwipe = true;

        // ACTION_DOWN
        mUnderTest.onMotionEvent(createMotionEvent(ACTION_DOWN, 0, 0));

        // ACTION_MOVE passing touch slop downwards
        MotionEvent moveEvent = createMotionEvent(ACTION_MOVE, 0, TOUCH_SLOP + 1);
        mUnderTest.onMotionEvent(moveEvent);

        verify(mTaskView, never()).launchWithAnimation();
        verify(mSystemUiProxy).onStatusBarTrackpadEvent(any());
    }

    @Test
    public void testOnMotionEvent_subsequentMoveEventsIgnoredAfterLaunch() {
        when(mRecentsViewContainer.isRecentsViewVisible()).thenReturn(true);
        mIsThreeFingerTrackpadSwipe = true;

        // Trigger launch
        mUnderTest.onMotionEvent(createMotionEvent(ACTION_DOWN, 0, 0));
        mUnderTest.onMotionEvent(createMotionEvent(ACTION_MOVE, 0, TOUCH_SLOP + 1));

        verify(mTaskView).launchWithAnimation();

        // Subsequent MOVE event while state is ACTIVE
        mUnderTest.onMotionEvent(createMotionEvent(ACTION_MOVE, 0, TOUCH_SLOP + 10));

        // Should NOT dispatch to status bar
        verify(mSystemUiProxy, never()).onStatusBarTrackpadEvent(any());
    }

    private MotionEvent createMotionEvent(int action, float x, float y) {
        MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[1];
        pp[0] = new MotionEvent.PointerProperties();
        pp[0].id = 0;
        MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[1];
        pc[0] = new MotionEvent.PointerCoords();
        pc[0].x = x;
        pc[0].y = y;

        return MotionEvent.obtain(0, 0, action, 1, pp, pc, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHPAD, 0);
    }
    @LauncherAppSingleton
    @Component(modules = {LauncherAppModule.class})
    interface TestComponent extends LauncherAppComponent {
        @Component.Builder
        interface Builder extends LauncherAppComponent.Builder {
            @BindsInstance Builder bindOverviewComponentObserver(OverviewComponentObserver o);
            @BindsInstance Builder bindSystemUiProxy(SystemUiProxy proxy);

            @Override
            TestComponent build();
        }
    }
}

