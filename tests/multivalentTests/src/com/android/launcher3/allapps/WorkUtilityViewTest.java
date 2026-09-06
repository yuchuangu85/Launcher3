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

package com.android.launcher3.allapps;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.util.TestActivityContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WorkUtilityViewTest {

    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public TestActivityContext context = new TestActivityContext(getApplicationContext(),
            com.android.launcher3.R.style.DynamicColorsBaseLauncherTheme);

    private WorkUtilityView mVut;

    @Before
    public void setUp() {
        mVut = (WorkUtilityView) ViewGroup.inflate(context,
                com.android.launcher3.R.layout.work_mode_utility_view, null);
    }

    @Test
    public void testScheduler_visible() {
        WorkUtilityView workUtilityView = Mockito.spy(mVut);
        doReturn(true).when(workUtilityView).shouldUseScheduler();

        workUtilityView.onFinishInflate();

        assertThat(workUtilityView.getSchedulerButton().getVisibility()).isEqualTo(VISIBLE);
        assertThat(workUtilityView.getSchedulerButton().hasOnClickListeners()).isEqualTo(true);
    }
}
