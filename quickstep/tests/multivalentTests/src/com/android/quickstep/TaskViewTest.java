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

package com.android.quickstep;

import static com.android.quickstep.TaskViewTestDIHelpers.mockRecentsModel;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Rect;
import android.view.MotionEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.util.SandboxContext;
import com.android.quickstep.util.BorderAnimator;
import com.android.quickstep.views.TaskView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TaskViewTest {
    private final SandboxContext mApp =
            new SandboxContext(InstrumentationRegistry.getInstrumentation().getTargetContext());

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    private BorderAnimator mHoverAnimator;
    @Mock
    private BorderAnimator mFocusAnimator;
    private TaskView mTaskView;

    @Before
    public void setup() {
        mApp.initDaggerComponent(
                DaggerTaskViewTestComponent.builder().bindRecentsModel(mockRecentsModel()));
        mTaskView = new TaskView(mApp, null, 0, 0, mFocusAnimator, mHoverAnimator);
    }

    @Test
    public void notShowBorderOnBorderDisabled() {
        presetBorderStatus(/* enabled= */ true);
        mTaskView.setBorderEnabled(/* enabled= */ false);
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_ENTER, 0.0f, 0.0f, 0);
        mTaskView.onHoverEvent(MotionEvent.obtain(event));
        verify(mHoverAnimator, never()).setBorderVisibility(/* visible= */ true, /* animated= */
                true);

        mTaskView.onFocusChanged(false, 0, new Rect());
        verify(mFocusAnimator, never()).setBorderVisibility(/* visible= */ true, /* animated= */
                true);
    }

    @Test
    public void showBorderOnBorderEnabled() {
        presetBorderStatus(/* enabled= */ false);
        mTaskView.setBorderEnabled(/* enabled= */ true);
        verify(mHoverAnimator, times(1)).setBorderVisibility(/* visible= */ true, /* animated= */
                true);
        verify(mFocusAnimator, times(1)).setBorderVisibility(/* visible= */ true, /* animated= */
                true);
    }

    @Test
    public void hideBorderOnBorderDisabled() {
        presetBorderStatus(/* enabled= */ true);
        mTaskView.setBorderEnabled(/* enabled= */ false);
        verify(mHoverAnimator, times(1)).setBorderVisibility(/* visible= */ false, /* animated= */
                true);
        verify(mFocusAnimator, times(1)).setBorderVisibility(/* visible= */ false, /* animated= */
                true);
    }

    @Test
    public void notTriggerAnimatorWhenEnableStatusUnchanged() {
        presetBorderStatus(/* enabled= */ false);
        // Border is disabled by default, no animator is triggered after it is disabled again
        mTaskView.setBorderEnabled(/* enabled= */ false);
        verify(mHoverAnimator, never()).setBorderVisibility(/* visible= */
                anyBoolean(), /* animated= */ anyBoolean());
        verify(mFocusAnimator, never()).setBorderVisibility(/* visible= */
                anyBoolean(), /* animated= */ anyBoolean());
    }

    private void presetBorderStatus(boolean enabled) {
        // Make the task view focused and hovered
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_ENTER, 0.0f, 0.0f, 0);
        mTaskView.onHoverEvent(MotionEvent.obtain(event));
        mTaskView.setFocusableInTouchMode(true);
        mTaskView.requestFocus();
        mTaskView.setBorderEnabled(/* enabled= */ enabled);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // Reset invocation count after presetting status
        reset(mHoverAnimator);
        reset(mFocusAnimator);
    }

    @Test
    public void notShowBorderByDefault() {
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_ENTER, 0.0f, 0.0f, 0);
        mTaskView.onHoverEvent(MotionEvent.obtain(event));
        verify(mHoverAnimator, never()).setBorderVisibility(/* visible= */
                anyBoolean(), /* animated= */ anyBoolean());
        mTaskView.onFocusChanged(true, 0, new Rect());
        verify(mHoverAnimator, never()).setBorderVisibility(/* visible= */
                anyBoolean(), /* animated= */ anyBoolean());
    }
}
