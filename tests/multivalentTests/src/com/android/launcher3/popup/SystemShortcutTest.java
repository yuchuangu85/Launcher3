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

package com.android.launcher3.popup;

import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;

import static com.android.launcher3.AbstractFloatingView.TYPE_SNACKBAR;
import static com.android.launcher3.Flags.FLAG_ENABLE_PRIVATE_SPACE;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DISMISS_PREDICTION_UNDO;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DISABLE_APP_LOCK_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_ENABLE_APP_LOCK_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TAP_TO_ADD_TO_HOME_SCREEN_FROM_ALL_APPS;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_APP_LOCK_ENABLED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_APP_LOCK_SUPPORTED;
import static com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI;
import static com.android.launcher3.testutil.rule.LazyInitRule.lazyP;
import static com.android.launcher3.testutil.rule.LazyInitRule.lazyRule;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.ORDERED_BG_EXECUTOR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.Flags;
import android.view.View;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.PrivateProfileManager;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.testutil.rule.LazyInitRule;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LauncherMultivalentJUnit;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.TestActivityContext;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.UserIconInfo;
import com.android.launcher3.util.rule.MockUsersRule;
import com.android.launcher3.util.rule.MockUsersRule.MockUser;
import com.android.launcher3.views.Snackbar;
import com.android.launcher3.widget.picker.model.WidgetPickerDataProvider;
import com.android.launcher3.widget.picker.model.data.WidgetPickerData;
import com.android.users.UserType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.Consumer;

@SmallTest
@RunWith(LauncherMultivalentJUnit.class)
public class SystemShortcutTest {
    @Rule(order = 0) public SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);
    @Rule(order = 1) public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule(order = 2) public LazyInitRule lazyInitRule = lazyRule(
            lazyP(SandboxApplication.class, l -> spy(new SandboxApplication())),
            lazyP(TestActivityContext.class,
                    l -> new TestActivityContext(l.get(SandboxApplication.class))),
            lazyP(MockUsersRule.class, l -> new MockUsersRule(l.get(SandboxApplication.class)))
    );

    private View mView;
    private ItemInfo mItemInfo;
    private AppInfo mAppInfo;

    private SandboxApplication mSandboxContext;
    private MockUsersRule mMockUsers;

    private TestActivityContext mTestContext;
    private PrivateProfileManager mPrivateProfileManager;
    private WidgetPickerDataProvider mWidgetPickerDataProvider;

    @Mock LauncherActivityInfo mLauncherActivityInfo;
    @Mock ApplicationInfo mApplicationInfo;
    @Mock Intent mIntent;
    @Mock StatsLogManager mStatsLogManager;
    @Mock(answer = Answers.RETURNS_SELF) StatsLogger mStatsLogger;
    @Mock LauncherAccessibilityDelegate mLauncherAccessibilityDelegate;
    @Mock PackageManager mPackageManager;
    @Mock PendingIntent mPendingIntent;

    @Before
    public void setUp() {
        mSandboxContext = lazyInitRule.get(SandboxApplication.class);
        mTestContext = spy(lazyInitRule.get(TestActivityContext.class));
        mMockUsers = lazyInitRule.get(MockUsersRule.class);
        doReturn(mLauncherAccessibilityDelegate).when(mTestContext).getAccessibilityDelegate();

        doReturn(mStatsLogManager).when(mTestContext).getStatsLogManager();

        doReturn(mStatsLogger).when(mStatsLogManager).logger();

        mView = new View(mTestContext);
        mItemInfo = new ItemInfo();

        LauncherApps mLauncherApps = mSandboxContext.spyService(LauncherApps.class);
        doReturn(mLauncherActivityInfo).when(mLauncherApps).resolveActivity(any(), any());
        when(mLauncherActivityInfo.getApplicationInfo()).thenReturn(mApplicationInfo);

        mPrivateProfileManager = spy(mTestContext.getAppsView().getPrivateProfileManager());
        mWidgetPickerDataProvider = spy(mTestContext.getWidgetPickerDataProvider());

        doReturn(mPackageManager).when(mTestContext).getPackageManager();
    }

    @Test
    public void testWidgetsForNullComponentName() {
        assertNull(mItemInfo.getTargetComponent());
        SystemShortcut systemShortcut = SystemShortcut.WIDGETS
                .getShortcut(mTestContext, mItemInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testWidgetsForEmptyWidgetList() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        assertNotNull(mAppInfo.getTargetComponent());
        doReturn(new WidgetPickerData()).when(mWidgetPickerDataProvider).get();
        AppInfo appInfo = spy(mAppInfo);
        SystemShortcut systemShortcut = SystemShortcut.WIDGETS
                .getShortcut(mTestContext, appInfo, mView);
        verify(appInfo, times(2)).getTargetComponent();
        assertNull(systemShortcut);
    }

    @Test
    public void testAppInfoShortcut() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        SystemShortcut systemShortcut = SystemShortcut.APP_INFO
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNotNull(systemShortcut);
    }


    @Test
    public void testDontSuggestAppForNonPredictedItem() {
        assertFalse(mItemInfo.isPredictedItem());
        SystemShortcut systemShortcut = SystemShortcut.DONT_SUGGEST_APP
                .getShortcut(mTestContext, mItemInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testDontSuggestAppForPredictedItemWithUndo() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_HOTSEAT_PREDICTION;
        assertTrue(mAppInfo.isPredictedItem());
        SystemShortcut systemShortcut = SystemShortcut.DONT_SUGGEST_APP
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNotNull(systemShortcut);

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR, () -> systemShortcut.onClick(mView));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mStatsLogger).log(eq(LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP));

        // Undo bar shown
        Snackbar snackbar = AbstractFloatingView.getOpenView(mTestContext, TYPE_SNACKBAR);
        assertNotNull(snackbar);
        reset(mStatsLogger);
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR, snackbar.findViewById(
                R.id.action)::performClick);
        verify(mStatsLogger).log(eq(LAUNCHER_DISMISS_PREDICTION_UNDO));
    }

    @Test
    public void testPrivateProfileInstallwithTargetComponentNull() {
        assertNull(mItemInfo.getTargetComponent());
        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mItemInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testPrivateProfileInstallNotAllAppsContainer() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_HOTSEAT_PREDICTION;

        assertNotNull(mAppInfo.getTargetComponent());
        assertFalse(mAppInfo.getContainerInfo().hasAllAppsContainer());

        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testPrivateProfileInstallNullPrivateProfileManager() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_ALL_APPS;
        mPrivateProfileManager = null;

        assertNotNull(mAppInfo.getTargetComponent());
        assertTrue(mAppInfo.getContainerInfo().hasAllAppsContainer());
        assertNull(mPrivateProfileManager);

        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testPrivateProfileInstallPrivateProfileManagerDisabled() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_ALL_APPS;

        assertNotNull(mPrivateProfileManager);
        assertNotNull(mAppInfo.getTargetComponent());
        assertTrue(mAppInfo.getContainerInfo().hasAllAppsContainer());

        when(mPrivateProfileManager.isEnabled()).thenReturn(false);
        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testPrivateProfileInstallNullPrivateProfileUser() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_ALL_APPS;
        when(mPrivateProfileManager.getProfileUser()).thenReturn(null);

        assertNotNull(mPrivateProfileManager);
        assertNotNull(mAppInfo.getTargetComponent());
        assertTrue(mAppInfo.getContainerInfo().hasAllAppsContainer());
        assertNull(mPrivateProfileManager.getProfileUser());

        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);

        assertNull(systemShortcut);
    }

    @Test
    @MockUser(userType = UserType.MAIN)
    @MockUser(userType = UserType.PRIVATE)
    public void testPrivateProfileInstallNonNullPrivateProfileUser() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_ALL_APPS;

        assertNotNull(mAppInfo.getTargetComponent());
        assertTrue(mAppInfo.getContainerInfo().hasAllAppsContainer());

        enablePrivateProfileManager();

        assertNotNull(mPrivateProfileManager);
        assertNotNull(mPrivateProfileManager.getProfileUser());
        assertNull(mTestContext.getAppsView().getAppsStore().getApp(new ComponentKey(
                mAppInfo.getTargetComponent(), mMockUsers.findUser(UserIconInfo::isPrivate))));

        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);

        verify(mPrivateProfileManager, atLeast(1)).isEnabled();
        assertNotNull(systemShortcut);
    }

    private void enablePrivateProfileManager() {
        ActivityAllAppsContainerView<TestActivityContext>
                allAppsView = spy(mTestContext.getAppsView());
        when(mTestContext.getAppsView()).thenReturn(allAppsView);
        when(allAppsView.getPrivateProfileManager()).thenReturn(mPrivateProfileManager);
        when(mPrivateProfileManager.isEnabled()).thenReturn(true);
    }

    @Test
    public void testInstallGetShortcutWithNonWorkSpaceItemInfo() {
        SystemShortcut systemShortcut = SystemShortcut.INSTALL.getShortcut(
                mTestContext, mItemInfo, mView);
        Assert.assertNull(systemShortcut);
    }

    @Test
    @UiThreadTest
    public void testInstallGetShortcutWithWorkSpaceItemInfo() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.intent = mIntent;
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo(mAppInfo);
        workspaceItemInfo.status = FLAG_SUPPORTS_WEB_UI;
        SystemShortcut systemShortcut = SystemShortcut.INSTALL.getShortcut(
                mTestContext, workspaceItemInfo, mView);
        Assert.assertNotNull(systemShortcut);
    }


    @Test
    @DisableFlags(FLAG_ENABLE_PRIVATE_SPACE)
    public void testUninstallGetShortcutWithPrivateSpaceOff() {
        SystemShortcut systemShortcut = SystemShortcut.UNINSTALL_APP.getShortcut(
                mTestContext, null, mView);
        Assert.assertNull(systemShortcut);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE)
    public void testUninstallGetShortcutWithNonPrivateItemInfo() {
        mAppInfo = new AppInfo();
        Assert.assertNull(SystemShortcut.UNINSTALL_APP.getShortcut(
                mTestContext, mAppInfo, mView));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE)
    @MockUser(userType = UserType.MAIN)
    @MockUser(userType = UserType.PRIVATE)
    public void testUninstallGetShortcutWithSystemItemInfo() {
        mAppInfo = new AppInfo();
        mAppInfo.user = mMockUsers.findUser(UserIconInfo::isPrivate);
        mAppInfo.itemType = ITEM_TYPE_APPLICATION;
        mAppInfo.intent = mIntent;
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        when(mLauncherActivityInfo.getComponentName()).thenReturn(mAppInfo.componentName);
        // System App
        mApplicationInfo.flags = 1;

        SystemShortcut systemShortcut = SystemShortcut.UNINSTALL_APP.getShortcut(
                mTestContext, mAppInfo, mView);
        verify(mLauncherActivityInfo, times(0)).getComponentName();
        Assert.assertNull(systemShortcut);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE)
    @MockUser(userType = UserType.MAIN)
    @MockUser(userType = UserType.PRIVATE)
    public void testUninstallGetShortcutWithPrivateItemInfo() {
        mAppInfo = new AppInfo();
        mAppInfo.user = mMockUsers.findUser(UserIconInfo::isPrivate);
        mAppInfo.itemType = ITEM_TYPE_APPLICATION;
        mAppInfo.intent = mIntent;
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        when(mLauncherActivityInfo.getComponentName()).thenReturn(mAppInfo.componentName);
        // 3rd party app, not system app.
        mApplicationInfo.flags = 0;

        SystemShortcut systemShortcut = SystemShortcut.UNINSTALL_APP.getShortcut(
                mTestContext, mAppInfo, mView);

        verify(mLauncherActivityInfo).getComponentName();
        Assert.assertNotNull(systemShortcut);

        systemShortcut.onClick(mView);
        verify(mSandboxContext).startActivity(any());
    }

    @Test
    public void testAddToHomeScreenShortcutFromAllApps() {
        mAppInfo = new AppInfo();
        mAppInfo.itemType = ITEM_TYPE_APPLICATION;
        mAppInfo.container = CONTAINER_ALL_APPS;
        SystemShortcut systemShortcut = SystemShortcut.ADD_TO_HOME_SCREEN.getShortcut(
                mTestContext, mAppInfo, mView);

        assertNotNull(systemShortcut);

        // Mock the addToWorkspace method to execute the callback immediately
        doAnswer(invocation -> {
            // The callback is the third argument to the method
            Consumer<Boolean> callback = invocation.getArgument(2);
            // Execute the callback with a 'success' value of true
            callback.accept(true);
            return null; // The method returns void
        }).when(mLauncherAccessibilityDelegate).addToWorkspace(any(), eq(false), any());

        systemShortcut.onClick(mView);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mStatsLogger).log(eq(LAUNCHER_TAP_TO_ADD_TO_HOME_SCREEN_FROM_ALL_APPS));
    }

    @Test
    public void testAddToHomeScreenShortcutFromWorkspaceShouldBeNull() {
        mAppInfo = new AppInfo();
        mAppInfo.itemType = ITEM_TYPE_APPLICATION;
        mAppInfo.container = CONTAINER_DESKTOP;
        SystemShortcut systemShortcut = SystemShortcut.ADD_TO_HOME_SCREEN.getShortcut(
                mTestContext, mAppInfo, mView);

        assertNull(systemShortcut);
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_LOCK_APIS)
    public void testAppLockShortcut_flagOff_isNull() {
        // Not supported, not enabled
        mAppInfo = createAppInfoWithAppLock(false, false);
        assertNull(SystemShortcut.APP_LOCK.getShortcut(mTestContext, mAppInfo, mView));

        // Not supported, enabled
        mAppInfo = createAppInfoWithAppLock(false, true);
        assertNull(SystemShortcut.APP_LOCK.getShortcut(mTestContext, mAppInfo, mView));

        // Supported, not enabled
        mAppInfo = createAppInfoWithAppLock(true, false);
        assertNull(SystemShortcut.APP_LOCK.getShortcut(mTestContext, mAppInfo, mView));

        // Supported, enabled
        mAppInfo = createAppInfoWithAppLock(true, true);
        assertNull(SystemShortcut.APP_LOCK.getShortcut(mTestContext, mAppInfo, mView));
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_LOCK_APIS)
    public void testAppLockShortcut_appLockNotSupported_isNull() {
        // Not supported, not enabled
        mAppInfo = createAppInfoWithAppLock(false, false);
        assertNull(SystemShortcut.APP_LOCK.getShortcut(mTestContext, mAppInfo, mView));

        // Not supported, enabled
        mAppInfo = createAppInfoWithAppLock(false, true);
        assertNull(SystemShortcut.APP_LOCK.getShortcut(mTestContext, mAppInfo, mView));
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_LOCK_APIS)
    public void testEnableAppLockShortcut_clickSucceeds_defersMenuDismiss() {
        final AppLockTestStateBuilder.Result result = new AppLockTestStateBuilder()
                .withAppLockDisabled()
                .setupAndGetResult();
        final SystemShortcut spyShortcut = result.mSpyShortcut;

        assertNotNull(spyShortcut);
        assertEquals(R.string.enable_app_lock, spyShortcut.mLabelResId);

        TestUtil.runOnExecutorSync(ORDERED_BG_EXECUTOR, () -> spyShortcut.onClick(mView));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mStatsLogger).log(eq(LAUNCHER_SYSTEM_SHORTCUT_ENABLE_APP_LOCK_TAP));
        verify(mTestContext).sendPendingIntentWithAnimation(eq(mView), eq(mPendingIntent),
                eq(mAppInfo));
        verify(spyShortcut, never()).dismissTaskMenuView();

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(result.mSpyAnimationCallback).add(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(spyShortcut).dismissTaskMenuView();
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_LOCK_APIS)
    public void testDisableAppLockShortcut_clickSucceeds_defersMenuDismiss() {
        final AppLockTestStateBuilder.Result result = new AppLockTestStateBuilder()
                .withAppLockEnabled()
                .setupAndGetResult();
        final SystemShortcut spyShortcut = result.mSpyShortcut;

        assertNotNull(spyShortcut);
        assertEquals(R.string.disable_app_lock, spyShortcut.mLabelResId);

        TestUtil.runOnExecutorSync(ORDERED_BG_EXECUTOR, () -> spyShortcut.onClick(mView));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mStatsLogger).log(eq(LAUNCHER_SYSTEM_SHORTCUT_DISABLE_APP_LOCK_TAP));
        verify(mTestContext).sendPendingIntentWithAnimation(eq(mView), eq(mPendingIntent),
                eq(mAppInfo));
        verify(spyShortcut, never()).dismissTaskMenuView();

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(result.mSpyAnimationCallback).add(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(spyShortcut).dismissTaskMenuView();
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_LOCK_APIS)
    public void testAppLockShortcut_intentRetrievalFails_clickDismissesMenuAndDoesNotSendIntent() {
        final SystemShortcut spyShortcut = new AppLockTestStateBuilder()
                .withAppLockDisabled()
                .withIntentRetrievalFailing()
                .setupAndGetResult()
                .mSpyShortcut;
        assertNotNull(spyShortcut);

        TestUtil.runOnExecutorSync(ORDERED_BG_EXECUTOR, () -> spyShortcut.onClick(mView));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mStatsLogger).log(eq(LAUNCHER_SYSTEM_SHORTCUT_ENABLE_APP_LOCK_TAP));
        verify(spyShortcut).dismissTaskMenuView();
        verify(mTestContext, never()).sendPendingIntentWithAnimation(any(), any(), any());
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_LOCK_APIS)
    public void testAppLockShortcut_intentSendFails_clickDismissesMenu() {
        final SystemShortcut spyShortcut = new AppLockTestStateBuilder()
                .withAppLockDisabled()
                .withIntentSendFailing()
                .setupAndGetResult()
                .mSpyShortcut;
        assertNotNull(spyShortcut);

        TestUtil.runOnExecutorSync(ORDERED_BG_EXECUTOR, () -> spyShortcut.onClick(mView));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mStatsLogger).log(eq(LAUNCHER_SYSTEM_SHORTCUT_ENABLE_APP_LOCK_TAP));
        verify(mTestContext).sendPendingIntentWithAnimation(eq(mView), eq(mPendingIntent),
                eq(mAppInfo));
        verify(spyShortcut).dismissTaskMenuView();
    }

    private AppInfo createAppInfoWithAppLock(boolean isAppLockSupported, boolean isAppLockEnabled) {
        AppInfo appInfo = new AppInfo();
        if (isAppLockSupported) {
            appInfo.runtimeStatusFlags |= FLAG_APP_LOCK_SUPPORTED;
        } else {
            appInfo.runtimeStatusFlags &= ~FLAG_APP_LOCK_SUPPORTED;
        }
        if (isAppLockEnabled) {
            appInfo.runtimeStatusFlags |= FLAG_APP_LOCK_ENABLED;
        } else {
            appInfo.runtimeStatusFlags &= ~FLAG_APP_LOCK_ENABLED;
        }
        appInfo.componentName = new ComponentName(mTestContext, getClass());
        return appInfo;
    }

    /** A builder to set up the environment for testing the App Lock shortcut for supported apps. */
    private class AppLockTestStateBuilder {
        private boolean mIsAppLockEnabled = false;
        private boolean mGetIntentReturnsNull = false;
        private boolean mSendIntentReturnsNull = false;

        // A simple data class to hold the results of the builder setup.
        private static class Result {
            final SystemShortcut mSpyShortcut;
            final RunnableList mSpyAnimationCallback;

            Result(SystemShortcut spyShortcut, RunnableList spyAnimationCallback) {
                this.mSpyShortcut = spyShortcut;
                this.mSpyAnimationCallback = spyAnimationCallback;
            }
        }

        AppLockTestStateBuilder withAppLockDisabled() {
            mIsAppLockEnabled = false;
            return this;
        }

        AppLockTestStateBuilder withAppLockEnabled() {
            mIsAppLockEnabled = true;
            return this;
        }

        AppLockTestStateBuilder withIntentRetrievalFailing() {
            mGetIntentReturnsNull = true;
            return this;
        }

        AppLockTestStateBuilder withIntentSendFailing() {
            mSendIntentReturnsNull = true;
            return this;
        }

        /**
         * Sets up all mocks based on the configured state and returns the results.
         */
        Result setupAndGetResult() {
            mAppInfo = createAppInfoWithAppLock(/* isAppLockSupported= */ true, mIsAppLockEnabled);

            PendingIntent intent = mGetIntentReturnsNull ? null : mPendingIntent;
            when(mPackageManager.getEnableAppLockIntentForPackage(eq(mTestContext.getPackageName()),
                    eq(!mIsAppLockEnabled))).thenReturn(intent);

            RunnableList spyRunnableList = spy(new RunnableList());
            if (!mGetIntentReturnsNull) {
                doReturn(mSendIntentReturnsNull ? null : spyRunnableList)
                        .when(mTestContext).sendPendingIntentWithAnimation(
                                eq(mView), eq(mPendingIntent), eq(mAppInfo));
            }

            SystemShortcut shortcut = SystemShortcut.APP_LOCK.getShortcut(
                    mTestContext, mAppInfo, mView);

            SystemShortcut spyShortcut = spy(shortcut);
            doNothing().when(spyShortcut).dismissTaskMenuView();
            return new Result(spyShortcut, spyRunnableList);
        }
    }
}
