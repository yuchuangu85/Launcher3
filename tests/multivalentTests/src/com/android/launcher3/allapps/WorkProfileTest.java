/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.launcher3.LauncherPrefs.WORK_EDU_STEP;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD;
import static com.android.launcher3.allapps.UserProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_ENABLED;
import static com.android.launcher3.model.data.AppsListData.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.TestActivityContext;
import com.android.launcher3.util.UserIconInfo;
import com.android.launcher3.util.rule.MockUsersRule;
import com.android.launcher3.util.rule.MockUsersRule.MockUser;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.users.UserType;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@MockUser(userType = UserType.MAIN)
@MockUser(userType = UserType.WORK)
public class WorkProfileTest {

    @Rule public TestRule testStabilityRule = new TestStabilityRule();
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule public SandboxApplication app = new SandboxApplication().withModelDependency();
    @Rule public MockUsersRule mockUserRule = new MockUsersRule(app);
    @Rule public TestActivityContext context = new TestActivityContext(app);

    private UserHandle mWorkUser;
    private WorkProfileManager mWorkProfileManager;
    private AllAppsStore mAllAppsStore;
    private UserManager mUserManager;
    private LauncherPrefs mLauncherPrefs;

    @Mock private StatsLogManager mStatsLogManager;

    @Before
    public void setUp() throws Exception {
        mWorkUser = mockUserRule.findUser(UserIconInfo::isWork);
        ActivityAllAppsContainerView<?> activityAllAppsContainerView =
                new ActivityAllAppsContainerView<>(context);
        mAllAppsStore = context.getActivityComponent().getAppsStore();
        mLauncherPrefs = LauncherPrefs.get(context);
        // Ensure a clean state for edu preference
        mLauncherPrefs.remove(WORK_EDU_STEP);

        mUserManager = app.spyService(UserManager.class);
        doReturn(true).when(mUserManager).requestQuietModeEnabled(anyBoolean(), eq(mWorkUser));

        mWorkProfileManager = spy(new WorkProfileManager(
                activityAllAppsContainerView,
                mStatsLogManager,
                UserCache.getInstance(app)));
    }

    private void setWorkProfileQuietMode(boolean quietMode) {
        int flags = quietMode ? FLAG_WORK_PROFILE_QUIET_MODE_ENABLED : 0;
        // Set apps to trigger flag update, an empty array is fine.
        mAllAppsStore.setApps(AppInfo.EMPTY_ARRAY, flags, null);
        doReturn(quietMode).when(mUserManager).isQuietModeEnabled(eq(mWorkUser));
        mWorkProfileManager.reset();
    }

    @Test
    public void initialState_workEnabled() {
        setWorkProfileQuietMode(false);

        assertEquals(STATE_ENABLED, mWorkProfileManager.getCurrentState());

        assertTrue(mWorkProfileManager.shouldShowWorkApps());
    }

    @Test
    public void initialState_workDisabled() {
        setWorkProfileQuietMode(true);

        assertEquals(STATE_DISABLED, mWorkProfileManager.getCurrentState());

        assertFalse(mWorkProfileManager.shouldShowWorkApps());
    }

    @Test
    public void setWorkProfileEnabled_false_invokesSetQuietModeTrue() {
        setWorkProfileQuietMode(false); // Start enabled

        mWorkProfileManager.setWorkProfileEnabled(false);

        verify(mWorkProfileManager).setQuietMode(eq(true), any(Context.class));
    }

    @Test
    public void setWorkProfileEnabled_true_invokesSetQuietModeFalse() {
        setWorkProfileQuietMode(true); // Start disabled

        mWorkProfileManager.setWorkProfileEnabled(true);

        verify(mWorkProfileManager).setQuietMode(eq(false), any(Context.class));
    }

    @Test
    public void addWorkItems_whenDisabled_addsDisabledCard() {
        setWorkProfileQuietMode(true);
        ArrayList<AdapterItem> items = new ArrayList<>();

        mWorkProfileManager.addWorkItems(items);

        assertEquals(1, items.size());
        assertEquals(VIEW_TYPE_WORK_DISABLED_CARD, items.get(0).viewType);
    }

    @Test
    public void addWorkItems_whenEnabled_eduNotSeen_addsEduCard() {
        setWorkProfileQuietMode(false);
        mLauncherPrefs.put(WORK_EDU_STEP.to(0)); // Edu not seen
        ArrayList<AdapterItem> items = new ArrayList<>();

        mWorkProfileManager.addWorkItems(items);

        assertEquals(1, items.size());
        assertEquals(VIEW_TYPE_WORK_EDU_CARD, items.get(0).viewType);
    }

    @Test
    public void addWorkItems_whenEnabled_eduSeen_addsNoSpecialCard() {
        setWorkProfileQuietMode(false);
        mLauncherPrefs.put(WORK_EDU_STEP.to(1)); // Edu seen
        ArrayList<AdapterItem> items = new ArrayList<>();

        mWorkProfileManager.addWorkItems(items);

        assertTrue(items.isEmpty());
    }

    @Test
    public void hasWorkApps_trueWhenWorkAppsPresent() {
        AppInfo workApp = new AppInfo();
        workApp.user = mWorkUser;

        mAllAppsStore.setApps(new AppInfo[]{workApp}, 0, null);

        assertTrue(mWorkProfileManager.hasWorkApps());
    }

    @Test
    public void hasWorkApps_falseWhenNoWorkAppsPresent() {
        AppInfo personalApp = new AppInfo();
        personalApp.user = mockUserRule.findUser(UserIconInfo::isMain);

        mAllAppsStore.setApps(new AppInfo[]{personalApp}, 0, null);

        assertFalse(mWorkProfileManager.hasWorkApps());
    }
}
