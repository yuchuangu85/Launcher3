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
package com.android.launcher3.views;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.platform.test.flag.junit.SetFlagsRule;
import android.platform.test.rule.LimitDevicesRule;
import android.platform.test.rule.SkipOnDeviceless;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.testcomponent.AppWidgetNoConfig;
import com.android.launcher3.testcomponent.AppWidgetWithConfig;
import com.android.launcher3.testcomponent.RequestPinItemActivity;
import com.android.launcher3.util.BaseLauncherActivityTest;
import com.android.launcher3.util.BlockingBroadcastReceiver;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.ModelTestExtensions;
import com.android.launcher3.util.RoboApiWrapper;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Test to verify pin item request flow.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class RequestPinItemTest extends BaseLauncherActivityTest<Launcher> {
    @Rule
    public final SetFlagsRule mSetFlagsRule =
            new SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);

    @Rule
    public TestRule mGrantWidgetRule = RoboApiWrapper.INSTANCE.grantWidgetBindPermissionRule();

    @Rule
    public TestRule mDefaultLauncherRule = RoboApiWrapper.INSTANCE.grantShortcutsPermissionRule();

    @Rule
    public LimitDevicesRule mlimitDevicesRule = new LimitDevicesRule();

    private String mCallbackAction;
    private String mShortcutId;
    private int mAppWidgetId;

    @Before
    public void setUp() throws Exception {
        mCallbackAction = UUID.randomUUID().toString();
        mShortcutId = UUID.randomUUID().toString();
    }

    @Test
    /*
     * Test is is hanging on Robolectric, possibly an issue with threading.
     */
    @SkipOnDeviceless
    @DesktopStability(flavors = LOCAL, bug = 486280528)
    public void testPinWidgetNoConfig() throws Throwable {
        runTest("pinWidgetNoConfig", true, (info, view) -> info instanceof LauncherAppWidgetInfo
                && ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId
                && ((LauncherAppWidgetInfo) info).providerName.getClassName()
                        .equals(AppWidgetNoConfig.class.getName()));
    }

    @Test
    /*
     * Test is is hanging on Robolectric, possibly an issue with threading.
     */
    @SkipOnDeviceless
    @DesktopStability(flavors = LOCAL, bug = 486280528)
    public void testPinWidgetNoConfig_customPreview() throws Throwable {
        // Command to set custom preview
        Intent command = RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setRemoteViewColor").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", Color.RED);

        runTest("pinWidgetNoConfig", true, (info, view) -> info instanceof LauncherAppWidgetInfo
                && ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId
                && ((LauncherAppWidgetInfo) info).providerName.getClassName()
                        .equals(AppWidgetNoConfig.class.getName()), command);
    }

    @Test
    /*
     * Test is is hanging on Robolectric, possibly an issue with threading.
     */
    @SkipOnDeviceless
    @DesktopStability(flavors = LOCAL, bug = 486280528)
    public void testPinWidgetWithConfig() throws Throwable {
        runTest("pinWidgetWithConfig", true,
                (info, view) -> info instanceof LauncherAppWidgetInfo
                        && ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId
                        && ((LauncherAppWidgetInfo) info).providerName.getClassName()
                                .equals(AppWidgetWithConfig.class.getName()));
    }

    @Test
    /*
     * Test is is hanging on Robolectric, possibly an issue with threading.
     */
    @SkipOnDeviceless
    public void testPinShortcut() throws Throwable {
        // Command to set the shortcut id
        Intent command = RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setShortcutId").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", mShortcutId);

        runTest("pinShortcut", false, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof WorkspaceItemInfo
                        && info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT
                        && ShortcutKey.fromItemInfo(info).getId().equals(mShortcutId);
            }
        }, command);
    }

    @Test
    @SkipOnDeviceless
    @DesktopStability(flavors = LOCAL, bug = 486280528)
    public void testPinCancel_canClick() throws Throwable {
        // Command to set the shortcut id
        initPinActivity("pinWidgetNoConfig");

        BySelector selector = By.text(Pattern.compile("^Cancel$", CASE_INSENSITIVE))
                .pkg(targetContext().getPackageName());
        uiDevice.wait(device -> device.findObject(selector), TestUtil.DEFAULT_UI_TIMEOUT).click();
    }

    private void initPinActivity(String activityMethod, Intent... commandIntents)
            throws InterruptedException {
        ModelTestExtensions.setEmptyModelLayout(targetContext());
        loadLauncherSync();

        // Open Pin item activity
        BlockingBroadcastReceiver openMonitor = new BlockingBroadcastReceiver(
                RequestPinItemActivity.class.getName());
        Context testContext = getInstrumentation().getContext();
        getLauncherTestInteractions().startAppFast(
                testContext.getPackageName(),
                new Intent(testContext, RequestPinItemActivity.class));
        assertNotNull(openMonitor.blockingGetExtraIntent());

        // Set callback
        PendingIntent callback = PendingIntent.getBroadcast(targetContext(), 0,
                new Intent(mCallbackAction).setPackage(targetContext().getPackageName()),
                FLAG_ONE_SHOT | FLAG_MUTABLE);
        targetContext().sendBroadcast(RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setCallback").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", callback));

        for (Intent command : commandIntents) {
            targetContext().sendBroadcast(command);
        }

        // call the requested method to start the flow
        targetContext().sendBroadcast(RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, activityMethod));
    }

    private void runTest(String activityMethod, boolean isWidget, ItemOperator itemMatcher,
            Intent... commandIntents) throws Throwable {
        initPinActivity(activityMethod, commandIntents);

        // Accept confirmation:
        BlockingBroadcastReceiver resultReceiver = new BlockingBroadcastReceiver(mCallbackAction);
        BySelector selector = By.text(Pattern.compile("^Add to home screen$", CASE_INSENSITIVE))
                .pkg(targetContext().getPackageName());
        uiDevice.wait(device -> device.findObject(selector), TestUtil.DEFAULT_UI_TIMEOUT).click();
        Intent result = resultReceiver.blockingGetIntent();
        assertNotNull(result);
        mAppWidgetId = result.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (isWidget) {
            assertNotSame(-1, mAppWidgetId);
        }

        // Reload activity, so that the activity is focused
        getLauncherActivity().close();
        getLauncherActivity().initializeActivity();
        getLauncherActivity().getOnceNotNull(
                "",
                l -> l.getWorkspace().getFirstMatch(itemMatcher)
        );
    }
}
