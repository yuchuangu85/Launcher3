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

package com.android.launcher3.popup

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.util.Log
import android.view.DragAndDropPermissions
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.LauncherState
import com.android.launcher3.homescreenfiles.HomeScreenFilesNoOpProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.integration.util.events.ActivityTestEvents.createStateWaiter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever

@SkipOnDeviceless
@MediumTest
@RunWith(AndroidJUnit4::class)
class WorkspaceLongPressOptionsTest {

    companion object {
        const val APPS_OPTION_LABEL = "Apps list"
        const val CREATE_NEW_FOLDER_OPTION_LABEL = "New folder"
        const val TAG = "WorkspaceLongPressOptionsTest"
    }

    @Rule @JvmField val limitDevicesRule = LimitDevicesRule()

    var targetContext: Context = getInstrumentation().targetContext

    var launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @Test
    fun `test long press Apps option`() {
        val allAppsStateWaiter = launcherActivity.createStateWaiter(LauncherState.ALL_APPS)
        launcherActivity.executeOnLauncher {
            val appsOption = findOption(it, APPS_OPTION_LABEL)
            assertWithMessage("No option for apps found").that(appsOption).isNotNull()
            appsOption!!.popupAction.invoke(it, ItemInfo(), it.rootView)
        }
        allAppsStateWaiter.waitForSignal(TimeUnit.SECONDS.toMillis(10))
    }

    @Test
    fun `test long press create new folder option`() {
        val expectCreateNewFolderOption =
            isHomeScreenFilesEnabled() && isExternalStorageDirectoryMounted()

        // Verify option (in-)existence and long press if applicable.
        val currentScreenId = launcherActivity.getFromLauncher { launcher ->
            val createNewFolderOption = findOption(launcher, CREATE_NEW_FOLDER_OPTION_LABEL)
            when (expectCreateNewFolderOption) {
                false -> {
                    assertWithMessage("Option for create new folder found")
                        .that(createNewFolderOption)
                        .isNull()
                }
                true -> {
                    assertWithMessage("No option for create new folder found")
                        .that(createNewFolderOption)
                        .isNotNull()

                    createNewFolderOption!!
                        .popupAction
                        .invoke(launcher, ItemInfo(), launcher.rootView)
                }
            }
            launcher.workspace.run { getScreenIdForPageIndex(currentPage) }
        }

        // If the option does not exist, there's nothing left to verify.
        if (!expectCreateNewFolderOption) {
            return
        }

        // Verify new folder creation.
        val view =
            launcherActivity.getOnceNotNull("No new folder created") { launcher ->
                launcher.workspace.mapOverItems { itemInfo, _ ->
                    itemInfo?.itemType == ITEM_TYPE_FILE_SYSTEM_FOLDER &&
                        itemInfo.screenId == currentScreenId &&
                        itemInfo.title?.matches(Regex("^(New folder)( \\(\\d+\\))?$")) == true
                }
            }!!

        // Clean up newly created folder on a best-effort basis.
        try {
            val itemInfo = view.tag as WorkspaceItemInfo
            targetContext.contentResolver.delete(itemInfo.intent.data!!, null)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to delete new folder", e)
        }
    }

    @Test
    fun openWidgetPicker_safeMode_returnsFalse() {
        val mockContext = spy(targetContext)

        val pm = mock(PackageManager::class.java)
        whenever(pm.isSafeMode()).thenReturn(true)
        whenever(mockContext.getPackageManager()).thenReturn(pm)

        var result = true
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) {
            result = WorkspaceLongPressOptions.openWidgetPicker(mockContext)
        }

        assertFalse(result)
        verify(mockContext, never()).startActivity(any())
    }

    @Test
    fun openWidgetPicker_notSafeMode_notActivity_startsActivityWithNewTaskFlag() {
        val mockContext = spy(targetContext)

        val pm = mock(PackageManager::class.java)
        whenever(pm.isSafeMode()).thenReturn(false)
        whenever(mockContext.getPackageManager()).thenReturn(pm)
        whenever(mockContext.getPackageName()).thenReturn("com.test")

        doNothing().`when`(mockContext).startActivity(any())

        assertTrue(WorkspaceLongPressOptions.openWidgetPicker(mockContext))

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(mockContext).startActivity(intentCaptor.capture())

        val intent = intentCaptor.value
        assertEquals(Intent.ACTION_PICK, intent.action)
        assertEquals("com.test", intent.`package`)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun openWidgetPicker_notSafeMode_isActivity_startsActivityWithoutNewTaskFlag() {
        abstract class MockActivity : Activity() {
            override fun getWindow(): Window? = super<Activity>.getWindow()

            override fun getLayoutInflater(): LayoutInflater = super<Activity>.getLayoutInflater()

            override fun getComponentName(): ComponentName? = super<Activity>.getComponentName()

            override fun requestDragAndDropPermissions(event: DragEvent?): DragAndDropPermissions? =
                super<Activity>.requestDragAndDropPermissions(event)
        }
        val mockActivity = mock(MockActivity::class.java)

        val pm = mock(PackageManager::class.java)
        whenever(pm.isSafeMode()).thenReturn(false)
        whenever(mockActivity.getPackageManager()).thenReturn(pm)
        whenever(mockActivity.getPackageName()).thenReturn("com.test")

        assertTrue(WorkspaceLongPressOptions.openWidgetPicker(mockActivity))

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(mockActivity).startActivity(intentCaptor.capture())

        val intent = intentCaptor.value
        assertEquals(Intent.ACTION_PICK, intent.action)
        assertEquals("com.test", intent.`package`)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) == 0)
    }

    private fun findOption(launcher: Launcher, label: String): PopupData? =
        WorkspaceLongPressOptions.getAll(launcher).find {
            launcher.getString(it.labelResId) == label
        }

    private fun isExternalStorageDirectoryMounted(): Boolean =
        Environment.getExternalStorageState(Environment.getExternalStorageDirectory()) ==
            Environment.MEDIA_MOUNTED

    private fun isHomeScreenFilesEnabled(): Boolean =
        HomeScreenFilesProvider.INSTANCE[targetContext] !is HomeScreenFilesNoOpProvider
}
