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

package com.android.launcher3

import android.content.Context
import android.net.Uri
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.homescreenfiles.HomeScreenFile
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.views.Snackbar
import com.android.providers.media.flags.Flags.FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API
import com.google.common.truth.StringSubject
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DropTargetHandlerTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule var mockitoRule: MockitoRule = MockitoJUnit.rule()
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @Mock private lateinit var homeScreenFilesProvider: HomeScreenFilesProvider
    @Mock private lateinit var undoDeleteController: UndoDeleteController

    private val immediateExecutor = Executor { it.run() }
    private val file =
        HomeScreenFile(
            uri = Uri.parse("content://media/external_primary/file/1"),
            displayName = "file.png",
            mimeType = "image/png",
            isDirectory = false,
            user = Process.myUserHandle(),
        )
    private val item =
        WorkspaceItemInfo().apply {
            itemType = Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
            title = "abc.png"
            intent = HomeScreenFilesUtils.buildLaunchIntent(file.uri, file)
        }

    @Test
    @DisableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun onDeletePermanentlyCompleteForHomeScreenFile() {
        launcherActivity.executeOnLauncher { launcher ->
            val dropTargetHandler =
                DropTargetHandler(
                    launcher,
                    undoDeleteController,
                    homeScreenFilesProvider,
                    immediateExecutor,
                )
            dropTargetHandler.onDeleteComplete(item, null)

            val snackbar = launcher.snackbar
            assertThat(snackbar.labelView.text.toString()).isEqualToString(R.string.item_removed)
            verifyNoInteractions(homeScreenFilesProvider)

            // The provider call happens only when the snackbar gets dismissed.
            snackbar.close(false)
            verify(homeScreenFilesProvider, times(1)).deletePermanently(file.uri)
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun onDeleteCompleteForHomeScreenFile() {
        whenever(homeScreenFilesProvider.moveToTrash(any()))
            .thenReturn(CompletableFuture.completedFuture("/new/path/in/trash"))
        whenever(homeScreenFilesProvider.restoreFromTrash(any()))
            .thenReturn(CompletableFuture.completedFuture(true))

        launcherActivity.executeOnLauncher { launcher ->
            val dropTargetHandler =
                DropTargetHandler(
                    launcher,
                    undoDeleteController,
                    homeScreenFilesProvider,
                    immediateExecutor,
                )
            dropTargetHandler.onDeleteComplete(item, null)

            val snackbar = launcher.snackbar

            // Move to trash.
            assertThat(snackbar.labelView.text.toString())
                .isEqualToString(R.string.home_screen_files_moved_to_trash_message)
            verify(homeScreenFilesProvider, times(1)).moveToTrash(eq("abc.png"))
            verifyNoMoreInteractions(homeScreenFilesProvider)

            // Undo.
            assertThat(snackbar.actionView.text.toString()).isEqualToString(R.string.undo)
            snackbar.actionView.performClick()
            verify(homeScreenFilesProvider, times(1)).restoreFromTrash(eq("/new/path/in/trash"))
            verifyNoMoreInteractions(homeScreenFilesProvider)
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun onDeleteCompleteForHomeScreenFileOnError() {
        whenever(homeScreenFilesProvider.moveToTrash(any()))
            .thenReturn(CompletableFuture.completedFuture(null))

        launcherActivity.executeOnLauncher { launcher ->
            val dropTargetHandler =
                DropTargetHandler(
                    launcher,
                    undoDeleteController,
                    homeScreenFilesProvider,
                    immediateExecutor,
                )
            dropTargetHandler.onDeleteComplete(item, null)

            // Error toast for failed move to trash.
            assertThat(launcher.snackbar.labelView.text.toString())
                .isEqualToString(R.string.home_screen_files_move_to_trash_error_message)
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun onDeleteCompleteForHomeScreenFileOnUndoError() {
        whenever(homeScreenFilesProvider.moveToTrash(any()))
            .thenReturn(CompletableFuture.completedFuture("/new/path/in/trash"))
        whenever(homeScreenFilesProvider.restoreFromTrash(any()))
            .thenReturn(CompletableFuture.completedFuture(false))

        launcherActivity.executeOnLauncher { launcher ->
            val dropTargetHandler =
                DropTargetHandler(
                    launcher,
                    undoDeleteController,
                    homeScreenFilesProvider,
                    immediateExecutor,
                )
            dropTargetHandler.onDeleteComplete(item, null)

            // Click "Undo".
            launcher.snackbar.actionView.performClick()

            // Error toast for failed undo.
            assertThat(launcher.snackbar.labelView.text.toString())
                .isEqualToString(R.string.home_screen_files_restore_from_trash_error_message)
        }
    }

    @Test
    fun onDeleteComplete_standardItem_commitsOnDismiss() {
        launcherActivity.executeOnLauncher { launcher ->
            val item =
                WorkspaceItemInfo().apply {
                    itemType = Favorites.ITEM_TYPE_APPLICATION
                    title = "Test App"
                    container = Favorites.CONTAINER_DESKTOP
                    screenId = 0
                }

            val dropTargetHandler =
                DropTargetHandler(
                    launcher,
                    undoDeleteController,
                    homeScreenFilesProvider,
                    immediateExecutor,
                )
            dropTargetHandler.onDeleteComplete(item, null)

            launcher.snackbar.close(false)
            verify(undoDeleteController).commit()
            verifyNoMoreInteractions(undoDeleteController)
        }
    }

    @Test
    fun onDeleteComplete_standardItem_abortsOnUndo() {
        launcherActivity.executeOnLauncher { launcher ->
            val item =
                WorkspaceItemInfo().apply {
                    itemType = Favorites.ITEM_TYPE_APPLICATION
                    title = "Test App"
                    container = Favorites.CONTAINER_DESKTOP
                    screenId = 0
                }

            val dropTargetHandler =
                DropTargetHandler(
                    launcher,
                    undoDeleteController,
                    homeScreenFilesProvider,
                    immediateExecutor,
                )
            dropTargetHandler.onDeleteComplete(item, null)

            launcher.snackbar.actionView.performClick()
            verify(undoDeleteController).abort()
            verifyNoMoreInteractions(undoDeleteController)
        }
    }

    private val Launcher.snackbar
        get() = AbstractFloatingView.getOpenView<Snackbar>(this, AbstractFloatingView.TYPE_SNACKBAR)

    private val Snackbar.labelView
        get() = findViewById<TextView>(R.id.label)

    private val Snackbar.actionView
        get() = findViewById<TextView>(R.id.action)

    private fun StringSubject.isEqualToString(@StringRes id: Int, vararg args: String) =
        isEqualTo(ApplicationProvider.getApplicationContext<Context>().getString(id, *args))
}
