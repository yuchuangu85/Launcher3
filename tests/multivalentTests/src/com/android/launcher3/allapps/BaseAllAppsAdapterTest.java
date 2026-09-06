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
package com.android.launcher3.allapps;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_ICON;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_APP_ICON;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_ALL_APPS_DIVIDER;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BaseAllAppsAdapterTest {

    @Test
    public void testAdapterItemIsSameAs_DifferentApps() {
        UserHandle user = Process.myUserHandle();
        AppInfo app1 =
                new AppInfo(new ComponentName("com.pkg1", "Cls1"), "App1", user, new Intent());
        AppInfo app2 =
                new AppInfo(new ComponentName("com.pkg2", "Cls2"), "App2", user, new Intent());

        AdapterItem item1 = AdapterItem.asApp(app1);
        AdapterItem item2 = AdapterItem.asApp(app2);

        assertFalse("Different apps should not be same", item1.isSameAs(item2));
    }

    @Test
    public void testAdapterItemIsSameAs_SameApp() {
        UserHandle user = Process.myUserHandle();
        AppInfo app1 =
                new AppInfo(new ComponentName("com.pkg1", "Cls1"), "App1", user, new Intent());

        AdapterItem item1 = AdapterItem.asApp(app1);
        AdapterItem item2 = AdapterItem.asApp(app1);

        assertTrue("Same app should be same", item1.isSameAs(item2));
    }

    @Test
    public void testAdapterItemIsSameAs_DifferentViewTypes() {
        UserHandle user = Process.myUserHandle();
        AppInfo app =
                new AppInfo(new ComponentName("com.pkg1", "Cls1"), "App1", user, new Intent());

        AdapterItem item1 = AdapterItem.asApp(app);
        AdapterItem item2 = new AdapterItem(VIEW_TYPE_ALL_APPS_DIVIDER);

        assertFalse("Different view types should not be same", item1.isSameAs(item2));
    }

    @Test
    public void testAdapterItemIsSameAs_PrivateSpaceAppIcon() {
        UserHandle user = Process.myUserHandle();
        AppInfo app1 =
                new AppInfo(new ComponentName("com.pkg1", "Cls1"), "App1", user, new Intent());

        AdapterItem item1 = new AdapterItem(VIEW_TYPE_PRIVATE_SPACE_APP_ICON);
        item1.itemInfo = app1;

        AdapterItem item2 = new AdapterItem(VIEW_TYPE_PRIVATE_SPACE_APP_ICON);
        item2.itemInfo = app1;

        assertTrue("Same private space app should be same", item1.isSameAs(item2));

        AppInfo app2 =
                new AppInfo(new ComponentName("com.pkg2", "Cls2"), "App2", user, new Intent());
        AdapterItem item3 = new AdapterItem(VIEW_TYPE_PRIVATE_SPACE_APP_ICON);
        item3.itemInfo = app2;

        assertFalse("Different private space apps should not be same", item1.isSameAs(item3));
    }
}
