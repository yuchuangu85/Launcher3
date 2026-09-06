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

package com.android.launcher3.accessibility

import android.os.Process
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.view.View
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS
import com.android.launcher3.R
import com.android.launcher3.homescreenfiles.HomeScreenFile
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.TestActivityContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WorkspaceAccessibilityHelperTest {

    @get:Rule val context = TestActivityContext()
    @get:Rule val flags = SetFlagsRule()

    @Test
    @EnableFlags(FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS)
    fun testGetDescriptionForDropOverFileSystemFolder() {
        val folder =
            HomeScreenFile(
                displayName = "Folder",
                isDirectory = true,
                mimeType = MIME_TYPE_DIR,
                uri = "content://media/external_primary/file/1".toUri(),
                user = Process.myUserHandle(),
            )

        val overChild =
            mock<View>().apply {
                doReturn(
                        WorkspaceItemInfo().apply {
                            intent = HomeScreenFilesUtils.buildLaunchIntent(folder.uri, folder)
                            itemType = HomeScreenFilesUtils.buildItemType(folder)
                            title = folder.displayName
                            cellX = 1
                            cellY = 2
                            screenId = 0
                        }
                    )
                    .whenever(this@apply)
                    .tag
            }

        val actual = WorkspaceAccessibilityHelper.getDescriptionForDropOver(overChild, context, "Page 1")
        val expected =
            context.getString(
                R.string.add_to_folder_with_position,
                folder.displayName,
                3,
                2,
                "Page 1",
            )
        assertEquals(expected, actual)
    }

    @Test
    fun testGetDescriptionForDropOverWorkspaceItem() {
        val info = WorkspaceItemInfo().apply {
            title = "Gmail"
            cellX = 1
            cellY = 2
            screenId = 0
        }
        val overChild = mock<View>().apply { whenever(tag).thenReturn(info) }

        val actual = WorkspaceAccessibilityHelper.getDescriptionForDropOver(overChild, context, "Page 1")
        val expected =
            context.getString(
                R.string.create_folder_with_position,
                "Gmail",
                3,
                2,
                "Page 1",
            )
        assertEquals(expected, actual)
    }

    @Test
    fun testGetDescriptionForDropOverFolder() {
        val info = FolderInfo().apply {
            title = "Games"
            cellX = 1
            cellY = 2
            screenId = 0
        }
        val overChild = mock<View>().apply { whenever(tag).thenReturn(info) }

        val actual = WorkspaceAccessibilityHelper.getDescriptionForDropOver(overChild, context, "Page 1")
        val expected =
            context.getString(
                R.string.add_to_folder_with_position,
                "Games",
                3,
                2,
                "Page 1",
            )
        assertEquals(expected, actual)
    }

    @Test
    fun testGetDescriptionForDropOverFolder_unnamed_withContent() {
        val info = FolderInfo().apply {
            title = ""
            cellX = 1
            cellY = 2
            screenId = 0
            add(WorkspaceItemInfo().apply { title = "Gmail"; rank = 0 })
        }
        val overChild = mock<View>().apply { whenever(tag).thenReturn(info) }

        val actual = WorkspaceAccessibilityHelper.getDescriptionForDropOver(overChild, context, "Page 1")
        val expected =
            context.getString(
                R.string.add_to_folder_with_app_with_position,
                "Gmail",
                3,
                2,
                "Page 1",
            )
        assertEquals(expected, actual)
    }
}
