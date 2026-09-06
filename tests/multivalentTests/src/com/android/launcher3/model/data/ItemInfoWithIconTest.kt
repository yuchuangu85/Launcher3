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

package com.android.launcher3.model.data

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.IconShape
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_AUTOMATED
import com.android.launcher3.pm.PackageInstallInfo
import com.android.launcher3.util.SandboxApplication
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@MutatedComponent(target = LauncherAppComponent::class)
class ItemInfoWithIconTest {

    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @BindValue @Mock lateinit var themeManager: ThemeManager
    private lateinit var itemInfoWithIcon: TestItemInfoWithIcon

    @Before
    fun setup() {
        app.initDaggerComponent(mutatedComponentBuilder())
        itemInfoWithIcon = TestItemInfoWithIcon()
    }

    @Test
    fun itemInfoWithIconDefaultParamsTest() {
        Truth.assertThat(itemInfoWithIcon.isDisabled).isFalse()
        Truth.assertThat(itemInfoWithIcon.isPendingDownload).isFalse()
        Truth.assertThat(itemInfoWithIcon.isArchived).isFalse()
    }

    @Test
    fun isDisabledOrPendingTest() {
        itemInfoWithIcon.setProgressLevel(0, PackageInstallInfo.STATUS_INSTALLING)
        Truth.assertThat(itemInfoWithIcon.isDisabled).isFalse()
        Truth.assertThat(itemInfoWithIcon.isPendingDownload).isTrue()

        itemInfoWithIcon.setProgressLevel(1, PackageInstallInfo.STATUS_INSTALLING)
        Truth.assertThat(itemInfoWithIcon.isDisabled).isFalse()
        Truth.assertThat(itemInfoWithIcon.isPendingDownload).isFalse()
    }

    @Test
    fun isAppLockSupported() {
        itemInfoWithIcon.runtimeStatusFlags =
            itemInfoWithIcon.runtimeStatusFlags or ItemInfoWithIcon.FLAG_APP_LOCK_SUPPORTED
        Truth.assertThat(itemInfoWithIcon.isAppLockSupported).isTrue()

        itemInfoWithIcon.runtimeStatusFlags =
            itemInfoWithIcon.runtimeStatusFlags and ItemInfoWithIcon.FLAG_APP_LOCK_SUPPORTED.inv()
        Truth.assertThat(itemInfoWithIcon.isAppLockSupported).isFalse()
    }

    @Test
    fun isAppLockEnabled() {
        itemInfoWithIcon.runtimeStatusFlags =
            itemInfoWithIcon.runtimeStatusFlags or ItemInfoWithIcon.FLAG_APP_LOCK_ENABLED
        Truth.assertThat(itemInfoWithIcon.isAppLockEnabled).isTrue()

        itemInfoWithIcon.runtimeStatusFlags =
            itemInfoWithIcon.runtimeStatusFlags and ItemInfoWithIcon.FLAG_APP_LOCK_ENABLED.inv()
        Truth.assertThat(itemInfoWithIcon.isAppLockEnabled).isFalse()
    }

    @Test
    fun checkAndApplyAutomationFlag_setsFlagWhenPackageIsAutomated() {
        val mockRepository = mock<AutomationRepository>()
        val testPackage = "com.android.test"
        itemInfoWithIcon.testPackage = testPackage
        itemInfoWithIcon.user = Process.myUserHandle()
        itemInfoWithIcon.runtimeStatusFlags =
            itemInfoWithIcon.runtimeStatusFlags and FLAG_AUTOMATED.inv()
        whenever(mockRepository.isPackageAutomated(itemInfoWithIcon.user, testPackage))
            .thenReturn(true)

        itemInfoWithIcon.checkAndApplyAutomationFlag(mockRepository)

        Truth.assertThat(itemInfoWithIcon.runtimeStatusFlags and FLAG_AUTOMATED)
            .isEqualTo(FLAG_AUTOMATED)
    }

    @Test
    fun checkAndApplyAutomationFlag_unsetsFlagWhenPackageIsNotAutomated() {
        val mockRepository = mock<AutomationRepository>()
        val testPackage = "com.android.test"
        itemInfoWithIcon.testPackage = testPackage
        itemInfoWithIcon.runtimeStatusFlags = itemInfoWithIcon.runtimeStatusFlags or FLAG_AUTOMATED
        itemInfoWithIcon.user = Process.myUserHandle()
        whenever(mockRepository.isPackageAutomated(itemInfoWithIcon.user, testPackage))
            .thenReturn(false)

        itemInfoWithIcon.checkAndApplyAutomationFlag(mockRepository)

        Truth.assertThat(itemInfoWithIcon.runtimeStatusFlags and FLAG_AUTOMATED).isEqualTo(0)
    }

    @Test
    fun newIcon_usesFileShapeDataForFileSystemFiles() {
        val fileBitmapInfo = mock<BitmapInfo>()
        val fileShapeData = mock<IconShape>()
        val fileIcon = mock<FastBitmapDrawable>()

        whenever(fileBitmapInfo.newIcon(any(), any(), eq(fileShapeData))).thenReturn(fileIcon)
        whenever(themeManager.fileShapeData).thenReturn(fileShapeData)

        itemInfoWithIcon.bitmap = fileBitmapInfo
        itemInfoWithIcon.itemType = ITEM_TYPE_FILE_SYSTEM_FILE
        Truth.assertThat(itemInfoWithIcon.newIcon(app)).isSameInstanceAs(fileIcon)
    }

    private class TestItemInfoWithIcon : ItemInfoWithIcon() {
        var testPackage: String? = null

        override fun getTargetPackage(): String? = testPackage

        override fun clone(): ItemInfoWithIcon? = null
    }
}
