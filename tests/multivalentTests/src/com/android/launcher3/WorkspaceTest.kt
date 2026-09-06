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

import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.util.Size
import android.view.DragAndDropPermissions
import android.view.View
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Flags.FLAG_ENABLE_CURSOR_DRIVEN_WORKFLOWS
import com.android.launcher3.Flags.FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS
import com.android.launcher3.Flags.enableFileSystemFoldersAsDropTargets
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SYSTEM_DRAG
import com.android.launcher3.LauncherState.DESKTOP_DRAG_MODE
import com.android.launcher3.LauncherState.SPRING_LOADED
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.SystemDragItemInfo
import com.android.launcher3.homescreenfiles.HomeScreenFile
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUpdate
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemCoordinates
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.RoboApiWrapper.convertToSpy
import com.android.launcher3.util.SandboxApplication
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@LargeTest
@RunWith(AndroidJUnit4::class)
class WorkspaceTest {

    @get:Rule val flags = SetFlagsRule()
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    private val nextUniqueId = AtomicInteger(1)

    @Test
    @EnableFlags(FLAG_ENABLE_CURSOR_DRIVEN_WORKFLOWS)
    fun testTouchDrag_RemainsInSpringLoaded() {
        val dragObject = createDragObject(ITEM_TYPE_APPLICATION, createUniqueMediaStoreUri())
        val options = DragOptions().apply { isMouseDrag = false }
        launcherActivity.executeOnLauncher { launcher ->
            launcher.dragController.mOptions = options
            launcher.workspace.onDragStart(dragObject, options)
        }
        // Ensure launcher enters SPRING_LOADED after starting touch drag.
        launcherActivity.waitUntil("Launcher didn't switch to SPRING_LOADED") {
            it.stateManager.state == SPRING_LOADED
        }

        launcherActivity.executeOnLauncher { launcher ->
            dragObject.x = 10 // near left edge
            launcher.workspace.onDragOver(dragObject)
        }
        // Ensure launcher remains in SPRING_LOADED after touch drag over screen edge.
        launcherActivity.waitUntil("Launcher didn't switch to SPRING_LOADED") {
            it.stateManager.state == SPRING_LOADED
        }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CURSOR_DRIVEN_WORKFLOWS)
    fun testMouseDrag_TransitionsToDesktopDragMode() {
        val dragObject = createDragObject(ITEM_TYPE_APPLICATION, createUniqueMediaStoreUri())
        val options = DragOptions().apply { isMouseDrag = true }

        launcherActivity.executeOnLauncher { launcher ->
            launcher.dragController.mOptions = options
            launcher.workspace.onDragStart(dragObject, options)
        }
        // Ensure launcher enters DESKTOP_DRAG_MODE after starting mouse drag.
        launcherActivity.waitUntil("Launcher didn't switch to DESKTOP_DRAG_MODE") {
            it.stateManager.state == DESKTOP_DRAG_MODE
        }

        launcherActivity.executeOnLauncher { launcher ->
            dragObject.x = 10 // near left edge
            launcher.workspace.onDragOver(dragObject)
        }
        // Ensure launcher changes to SPRING_LOADED after dragging to screen edge.
        launcherActivity.waitUntil("Launcher didn't switch to SPRING_LOADED") {
            it.stateManager.state == SPRING_LOADED
        }

        launcherActivity.executeOnLauncher { launcher ->
            dragObject.x = launcher.workspace.width / 2 // center of workspace
            launcher.workspace.onDragOver(dragObject)
        }

        // Ensure launcher changes back to DESKTOP_DRAG_MODE when dragging away from the screen
        // edge.
        launcherActivity.waitUntil("Launcher didn't switch to SPRING_LOADED") {
            it.stateManager.state == DESKTOP_DRAG_MODE
        }
    }

    @Test
    @DisableFlags(FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS)
    fun testAddToExistingFileSystemFolderWithFeatureDisabled() {
        testAddToExistingFileSystemFolder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS)
    fun testAddToExistingFileSystemFolderWithFeatureEnabled() {
        testAddToExistingFileSystemFolder()
    }

    private fun testAddToExistingFileSystemFolder() {
        launcherActivity.executeOnLauncher { launcher ->
            val displayName = "Folder"
            val dropOverView = createDropOverView(createFolder(displayName))
            val dropOverInfo = dropOverView.tag as ItemInfo
            val expected = enableFileSystemFoldersAsDropTargets()
            val provider = HomeScreenFilesProvider.INSTANCE[launcher].apply { convertToSpy() }
            val times = if (expected) times(1) else times(0)

            // Case: Dropping internal file system file on file system folder.
            var uri = createUniqueMediaStoreUri()
            assertEquals(
                expected,
                launcher.workspace.addToExistingFolder(
                    dropOverView,
                    createDragObject(ITEM_TYPE_FILE_SYSTEM_FILE, uri),
                    false,
                ),
            )
            verify(provider, times)
                .moveToHomeScreen(
                    listOf(uri),
                    HomeScreenFilesUpdate.Extras.builder()
                        .findSpaceStartingFrom(
                            WorkspaceItemCoordinates(
                                dropOverInfo.screenId,
                                dropOverInfo.cellX,
                                dropOverInfo.cellY,
                                dropOverInfo.container,
                            )
                        )
                        .build(),
                    displayName,
                )

            // Case: Dropping internal file system folder on file system folder.
            uri = createUniqueMediaStoreUri()
            assertEquals(
                expected,
                launcher.workspace.addToExistingFolder(
                    dropOverView,
                    createDragObject(ITEM_TYPE_FILE_SYSTEM_FOLDER, uri),
                    false,
                ),
            )
            verify(provider, times)
                .moveToHomeScreen(
                    listOf(uri),
                    HomeScreenFilesUpdate.Extras.builder()
                        .findSpaceStartingFrom(
                            WorkspaceItemCoordinates(
                                dropOverInfo.screenId,
                                dropOverInfo.cellX,
                                dropOverInfo.cellY,
                                dropOverInfo.container,
                            )
                        )
                        .build(),
                    displayName,
                )

            // Case: Dropping external file system file/folder on file system folder.
            uri = createUniqueMediaStoreUri()
            assertEquals(
                expected,
                launcher.workspace.addToExistingFolder(
                    dropOverView,
                    createDragObject(ITEM_TYPE_SYSTEM_DRAG, uri),
                    true,
                ),
            )
            verify(provider, times)
                .moveToHomeScreen(
                    listOf(uri),
                    HomeScreenFilesUpdate.Extras.builder()
                        .findSpaceStartingFrom(
                            WorkspaceItemCoordinates(
                                dropOverInfo.screenId,
                                dropOverInfo.cellX,
                                dropOverInfo.cellY,
                                dropOverInfo.container,
                            )
                        )
                        .build(),
                    displayName,
                )

            // Case: Dropping application on file system folder.
            uri = createUniqueMediaStoreUri()
            assertFalse(
                launcher.workspace.addToExistingFolder(
                    dropOverView,
                    createDragObject(ITEM_TYPE_APPLICATION, uri),
                    false,
                )
            )
            verifyNoMoreInteractions(provider)

            reset(provider)
        }
    }

    @Test
    @DisableFlags(FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS)
    fun testWillAddToExistingFileSystemFolderWithFeatureDisabled() {
        testWillAddToExistingFileSystemFolder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_FILE_SYSTEM_FOLDERS_AS_DROP_TARGETS)
    fun testWillAddToExistingFileSystemFolderWithFeatureEnabled() {
        testWillAddToExistingFileSystemFolder()
    }

    private fun testWillAddToExistingFileSystemFolder() {
        launcherActivity.executeOnLauncher { launcher ->
            val dropOverView = createDropOverView(createFolder("Folder"))
            val expected = enableFileSystemFoldersAsDropTargets()

            // Case: Dropping internal file system file on file system folder.
            assertEquals(
                expected,
                launcher.workspace.willAddToExistingUserFolder(
                    createWorkspaceItemInfo(ITEM_TYPE_FILE_SYSTEM_FILE),
                    dropOverView,
                ),
            )

            // Case: Dropping internal file system folder on file system folder.
            assertEquals(
                expected,
                launcher.workspace.willAddToExistingUserFolder(
                    createWorkspaceItemInfo(ITEM_TYPE_FILE_SYSTEM_FOLDER),
                    dropOverView,
                ),
            )

            // Case: Dropping external file system file/folder on file system folder.
            assertEquals(
                expected,
                launcher.workspace.willAddToExistingUserFolder(
                    createWorkspaceItemInfo(ITEM_TYPE_SYSTEM_DRAG),
                    dropOverView,
                ),
            )

            // Case: Dropping application on file system folder.
            assertFalse(
                launcher.workspace.willAddToExistingUserFolder(
                    createWorkspaceItemInfo(ITEM_TYPE_APPLICATION),
                    dropOverView,
                )
            )
        }
    }

    private fun createDragObject(itemType: Int, uri: Uri) =
        mock<DragObject>().apply {
            dragInfo = createWorkspaceItemInfo(itemType, uri)
            originalDragInfo = dragInfo.makeShallowCopy()
            whenever(getVisualCenter(any())).thenAnswer {
                val args = it.arguments
                val res = (args[0] as? FloatArray) ?: FloatArray(2)
                res[0] = x.toFloat()
                res[1] = y.toFloat()
                res
            }
        }

    private fun createDropOverView(folder: HomeScreenFile) =
        mock<View>().apply {
            val cell = Point(1, 2)
            val span = Size(3, 4)

            doReturn(CellLayoutLayoutParams(cell.x, cell.y, span.width, span.height))
                .whenever(this@apply)
                .layoutParams

            doReturn(
                    WorkspaceItemInfo().apply {
                        cellX = cell.x
                        cellY = cell.y
                        intent = HomeScreenFilesUtils.buildLaunchIntent(folder.uri, folder)
                        itemType = HomeScreenFilesUtils.buildItemType(folder)
                        spanX = span.width
                        spanY = span.height
                        title = folder.displayName
                    }
                )
                .whenever(this@apply)
                .tag
        }

    private fun createFolder(displayName: String) =
        HomeScreenFile(
            displayName = displayName,
            isDirectory = true,
            mimeType = MIME_TYPE_DIR,
            uri = createUniqueMediaStoreUri(),
            user = Process.myUserHandle(),
        )

    private fun createWorkspaceItemInfo(itemType: Int, uri: Uri? = null): WorkspaceItemInfo {
        return when (itemType) {
            ITEM_TYPE_SYSTEM_DRAG ->
                SystemDragItemInfo().apply {
                    if (uri != null) {
                        payload =
                            SystemDragItemInfo.UriListPayload(
                                permissions = mock<DragAndDropPermissions>(),
                                uriList = listOf(uri),
                            )
                    }
                }
            else ->
                WorkspaceItemInfo().apply {
                    if (uri != null) intent = Intent().apply { data = uri }
                    this.itemType = itemType
                }
        }
    }

    private fun createUniqueMediaStoreUri(): Uri =
        "content://media/external_primary/file/${nextUniqueId.getAndIncrement()}".toUri()
}
