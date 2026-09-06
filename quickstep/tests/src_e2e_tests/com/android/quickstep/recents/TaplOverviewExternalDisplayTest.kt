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

package com.android.quickstep.recents

import android.platform.test.annotations.LargeTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability
import com.android.launcher3.util.rule.TestStabilityRule.LOCAL
import com.android.launcher3.util.ui.BaseLauncherTaplTest.AllowInRecentsWindowTests
import com.android.quickstep.AbstractQuickStepTest
import com.android.quickstep.util.MultiDisplayTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@AllowInRecentsWindowTests
class TaplOverviewExternalDisplayTest : AbstractQuickStepTest() {

    @Before
    override fun setUp() {
        super.setUp()
        clearAllRecentTasks()
        startTestActivity(2)
        startTestActivity(3)
    }

    @Test
    @MultiDisplayTest
    @DesktopStability(flavors = LOCAL, bug = 489810466)
    fun testOpeningCurrentTaskFromOverview() {
        val task = baseContainer.switchToOverview().currentTask
        assertThat(task).isNotNull()
        assertThat(task.open()).isNotNull()
        assertTestActivityIsRunning(3, "Test Activity didn't open from Overview")
    }

    @Test
    @MultiDisplayTest
    @DesktopStability(flavors = LOCAL, bug = 488078155)
    fun testStartAppsAndGoToOverview() {
        mLauncher.launchedAppState.switchToOverview()
        assertThat(mLauncher.recentTasks.size).isEqualTo(2)
    }

    @Test
    @MultiDisplayTest
    @DesktopStability(flavors = LOCAL, bug = 489810588)
    fun testDismissAllTasksFromOverview() {
        baseContainer.switchToOverview().dismissAllTasks()
        assertThat(mLauncher.recentTasks).isEmpty()
    }

    /**
     * This is a workaround to avoid memory leak causing our test to fail.
     *
     * TODO(b/492105996): Remove this after this bug is fixed.
     */
    @After
    fun tearDown() {
        clearAllRecentTasks()
        startTestActivity(2)
    }
}
