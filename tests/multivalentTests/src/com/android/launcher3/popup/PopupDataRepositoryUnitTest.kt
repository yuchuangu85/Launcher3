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

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.DropTargetHandler
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_QSB
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.homescreenfiles.HomeScreenFile
import com.android.launcher3.homescreenfiles.HomeScreenFilesRenameDialog
import com.android.launcher3.homescreenfiles.HomeScreenFilesRenameDialogFactory
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.homescreenfiles.homeScreenFile
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.android.providers.media.flags.Flags.FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for the [PopupDataRepository] */
@SmallTest
@RunWith(AndroidJUnit4::class)
@MutatedComponent(target = LauncherAppComponent::class)
class PopupDataRepositoryUnitTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication()
    @get:Rule val setFlagsRule = SetFlagsRule()

    @BindValue
    @Mock
    lateinit var homeScreenFilesRenameDialogFactory: HomeScreenFilesRenameDialogFactory

    private lateinit var popupDataMapper: PopupDataRepository

    @Before
    fun setup() {
        // Late initialization of `PopupDataSource` is required because some of the created
        // `PopupData` use feature flags.
        app.initDaggerComponent(mutatedComponentBuilder())
        popupDataMapper = app.appComponent.popupDataRepository
    }

    @Test
    fun getPopupDataByItemInfoWithInvalidItemInfoShouldReturnNull() {
        val itemInfo =
            ItemInfo().apply {
                itemType = ITEM_TYPE_QSB
                id = 1
            }
        val popupData = popupDataMapper.getAllSupportedPopupActions(itemInfo)

        assert(popupData == null)
    }

    @Test
    fun getPopupDataByItemInfoWithEmptyItemInfoShouldReturnNull() {
        val itemInfo = ItemInfo().apply { id = 1 }
        val popupData = popupDataMapper.getAllSupportedPopupActions(itemInfo)

        assert(popupData == null)
    }

    @Test
    fun getPopupDataByItemInfoWithFolderShouldReturnPopupData() {
        val itemInfo =
            ItemInfo().apply {
                itemType = ITEM_TYPE_FOLDER
                id = 1
            }
        val popupData = popupDataMapper.getAllSupportedPopupActions(itemInfo)

        assert(popupData != null)
        assert(popupData?.size == 1)
        assert(popupData?.contains(PopupDataSource.removePopupData) == true)
    }

    @Test
    fun getPopupDataByItemInfoWithWidgetShouldReturnPopupData() {
        val widgetItemInfo =
            ItemInfo().apply {
                itemType = ITEM_TYPE_APPWIDGET
                id = 2
            }
        val popupData = popupDataMapper.getAllSupportedPopupActions(widgetItemInfo)

        assert(popupData != null)
        assert(popupData?.size == 1)
        assert(popupData?.contains(PopupDataSource.removePopupData) == true)
    }

    @Test
    fun popupDataShouldHaveAllTheDataFilledIn() {
        val folderItemInfo =
            ItemInfo().apply {
                id = 1
                itemType = ITEM_TYPE_FOLDER
            }
        val popupData = popupDataMapper.getAllSupportedPopupActions(folderItemInfo)

        assert(popupData?.size == 1)
        assert(popupData?.get(0)?.category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
        assert(popupData?.get(0)?.iconResId == R.drawable.ic_remove_no_shadow)
        assert(popupData?.get(0)?.labelResId == R.string.remove_system_shortcut_label)
        assert(popupData?.get(0)?.popupAction != null)
    }

    @Test
    @DisableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_COPY_PASTE,
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_RENAMING,
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun getPopupDataForFileSystemItemsWhenFlagsDisabled() {
        testPopupDataForFileSystemItems(
            supportsCopyPaste = false,
            supportsRenaming = false,
            supportsTrashing = false,
        )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_COPY_PASTE,
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_RENAMING,
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun getPopupDataForFileSystemItemsWhenFlagsEnabled() {
        testPopupDataForFileSystemItems(
            supportsCopyPaste = true,
            supportsRenaming = true,
            supportsTrashing = true,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_FILES_COPY_PASTE)
    @DisableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_RENAMING,
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun getPopupDataForFileSystemItemsWhenCopyPasteEnabled() {
        testPopupDataForFileSystemItems(
            supportsCopyPaste = true,
            supportsRenaming = false,
            supportsTrashing = false,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_FILES_RENAMING)
    @DisableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_COPY_PASTE,
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun getPopupDataForFileSystemItemsWhenRenamingEnabled() {
        testPopupDataForFileSystemItems(
            supportsCopyPaste = false,
            supportsRenaming = true,
            supportsTrashing = false,
        )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    @DisableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_COPY_PASTE,
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_RENAMING,
    )
    fun getPopupDataForFileSystemItemsWhenTrashingEnabled() {
        testPopupDataForFileSystemItems(
            supportsCopyPaste = false,
            supportsRenaming = false,
            supportsTrashing = true,
        )
    }

    private fun testPopupDataForFileSystemItems(
        supportsCopyPaste: Boolean,
        supportsRenaming: Boolean,
        supportsTrashing: Boolean,
    ) {
        testPopupDataForFileSystemItem(
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "image/png",
                isDirectory = false,
                user = Process.myUserHandle(),
            ),
            supportsCopyPaste,
            supportsRenaming,
            supportsTrashing,
        )
        testPopupDataForFileSystemItem(
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/2"),
                displayName = "folder",
                mimeType = MIME_TYPE_DIR,
                isDirectory = true,
                user = Process.myUserHandle(),
            ),
            supportsCopyPaste,
            supportsRenaming,
            supportsTrashing,
        )
    }

    private fun testPopupDataForFileSystemItem(
        file: HomeScreenFile,
        supportsCopyPaste: Boolean,
        supportsRenaming: Boolean,
        supportsTrashing: Boolean,
    ) {
        val activityContext = mock<ActivityContext>()
        val view = mock<View>()

        val item =
            WorkspaceItemInfo().apply {
                id = 1
                itemType = HomeScreenFilesUtils.buildItemType(file)
                intent = HomeScreenFilesUtils.buildLaunchIntent(file.uri, file)
                title = file.displayName
            }

        val popupData = popupDataMapper.getAllSupportedPopupActions(item)
        var popupDataIndex = 0
        val popupDataSize = run {
            var size = 2
            if (supportsCopyPaste) size += 1
            if (supportsRenaming) size += 1
            size
        }

        assert(popupData!!.size == popupDataSize)
        with(popupData[popupDataIndex++]) {
            assert(category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
            assert(iconResId == R.drawable.ic_home_screen_files_context_menu_open_in_app)
            assert(labelResId == R.string.home_screen_files_context_menu_open_in_app_label)
            assert(eventId == LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_OPEN_VIA_CONTEXT_MENU)

            popupAction.invoke(activityContext, item, view)
            verify(activityContext, times(1)).startActivitySafely(view, item.intent, item)
        }
        if (supportsCopyPaste) {
            with(popupData[popupDataIndex++]) {
                assert(category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
                assert(iconResId == R.drawable.ic_home_screen_files_context_menu_copy)
                assert(labelResId == R.string.home_screen_files_context_menu_copy_label)
                assert(eventId == LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_COPY_VIA_CONTEXT_MENU)

                val context = mock<Context>()
                val clipboardManager = mock<ClipboardManager>()
                whenever(activityContext.asContext()).thenReturn(context)
                whenever(context.getSystemService(ClipboardManager::class.java))
                    .thenReturn(clipboardManager)

                popupAction.invoke(activityContext, item, view)

                verify(clipboardManager)
                    .setPrimaryClip(
                        argThat {
                            description.mimeTypeCount == 1 &&
                                description.getMimeType(0) == file.mimeType &&
                                itemCount == 1 &&
                                getItemAt(0).uri == file.uri
                        }
                    )
            }
        }
        if (supportsRenaming) {
            with(popupData[popupDataIndex++]) {
                assert(category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
                assert(iconResId == R.drawable.ic_home_screen_files_context_menu_rename)
                assert(labelResId == R.string.home_screen_files_context_menu_rename_label)
                assert(eventId == LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_RENAME_VIA_CONTEXT_MENU)

                val homeScreenFilesRenameDialog = mock<HomeScreenFilesRenameDialog>()

                whenever(
                        homeScreenFilesRenameDialogFactory.create(
                            activityContext,
                            item.homeScreenFile!!,
                        )
                    )
                    .thenReturn(homeScreenFilesRenameDialog)

                popupAction.invoke(activityContext, item, view)
                verify(homeScreenFilesRenameDialog).show()
            }
        }
        with(popupData[popupDataIndex++]) {
            assert(category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
            assert(iconResId == R.drawable.ic_home_screen_files_context_menu_move_to_trash)
            assertEquals(
                if (supportsTrashing) R.string.home_screen_files_context_menu_move_to_trash_label
                else R.string.home_screen_files_context_menu_delete_permanently_label,
                labelResId,
            )
            assert(eventId == LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_DELETE_VIA_CONTEXT_MENU)

            val dropTargetHandler = mock<DropTargetHandler>()
            whenever(activityContext.dragLayer).thenReturn(mock<BaseDragLayer<*>>())
            whenever(activityContext.dropTargetHandler).thenReturn(dropTargetHandler)
            popupAction.invoke(activityContext, item, view)
            verify(dropTargetHandler, times(1)).prepareToUndoDelete(item)
            verify(dropTargetHandler, times(1)).onDeleteComplete(item, view)
        }
    }
}
