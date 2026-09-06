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

package com.android.launcher3.integration.dragndrop

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.os.PersistableBundle
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
import android.provider.MediaStore.Files.FileColumns.MIME_TYPE
import android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH
import android.provider.MediaStore.Files.FileColumns._ID
import android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.view.View.DRAG_FLAG_GLOBAL
import android.view.View.DRAG_FLAG_GLOBAL_URI_READ
import android.view.View.DRAG_FLAG_GLOBAL_URI_WRITE
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams
import androidx.core.graphics.toPointF
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.dragndrop.SystemDragController
import com.android.launcher3.dragndrop.SystemDragController.Companion.DOCS_UI_EXTRA_PREFIX
import com.android.launcher3.dragndrop.SystemDragControllerStub
import com.android.launcher3.homescreenfiles.HomeScreenFilesNoOpProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.Companion.HOME_SCREEN_FOLDER_RELATIVE_PATH
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.testutil.Wait.atMost
import com.android.launcher3.util.BaseLauncherActivityTest
import com.android.launcher3.util.ModelTestExtensions.setEmptyModelLayout
import com.android.launcher3.util.RoboApiWrapper
import com.android.launcher3.util.rule.ScreenRecordRule
import java.util.LinkedList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Integration tests for system-level drag-and-drop. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SystemDragIntegrationTest : BaseLauncherActivityTest<Launcher>() {

    @get:Rule val screenRecord = RoboApiWrapper.screenRecordRule() // b/462494467

    private val context: Context = targetContext()
    private lateinit var draggableView: View

    @Before
    fun setUp() {
        // Initialize file system and workspace.
        deleteAllHomeScreenFiles()
        context.setEmptyModelLayout()
        loadLauncherSync()

        // Initialize draggable view.
        launcherActivity.executeOnLauncher { launcher ->
            launcher.dragLayer.addView(
                View(context).also { draggableView = it },
                LayoutParams(DRAGGABLE_VIEW_SIZE, DRAGGABLE_VIEW_SIZE),
            )
        }

        // Wait for draggable view to finish initializing.
        atMost("Draggable view not laid out") { draggableView.isLaidOut }
    }

    @After
    fun tearDown() {
        // Clean up draggable view.
        launcherActivity.executeOnLauncher { launcher ->
            launcher.dragLayer.removeView(draggableView)
        }

        // Clean up file system.
        deleteAllHomeScreenFiles()
    }

    @Test
    @ScreenRecordRule.ScreenRecord // b/462494467
    fun testDragAndDropWhenPayloadContainsImmovableUri() {
        testDragAndDrop(
            ClipDescription(/* label= */ "", /* mimeTypes= */ arrayOf(MIMETYPE_TEXT_PLAIN)).apply {
                extras = PersistableBundle().apply { putString("$DOCS_UI_EXTRA_PREFIX...", null) }
            },
            listOf(ClipData.Item("content://test/path/id".toUri())),
        )
    }

    @Test
    @ScreenRecordRule.ScreenRecord // b/462494467
    fun testDragAndDropWhenPayloadContainsMovableUris() {
        val uniqueDisplayName = "${System.currentTimeMillis()}"

        val mediaStoreUris =
            listOf("$uniqueDisplayName (1).txt", "$uniqueDisplayName (2).txt").map { displayName ->
                context.contentResolver
                    .insert(
                        MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY),
                        ContentValues().apply {
                            put(DISPLAY_NAME, displayName)
                            put(MIME_TYPE, MIMETYPE_TEXT_PLAIN)
                            put(RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                        },
                    )
                    ?.also { mediaStoreUri ->
                        context.contentResolver.openOutputStream(mediaStoreUri)?.use { stream ->
                            stream.write(displayName.toByteArray())
                        }
                    }
            }

        assertTrue(mediaStoreUris.all(this::isExternalPrimaryMediaStoreUri))

        testDragAndDrop(
            ClipDescription(/* label= */ "", /* mimeTypes= */ arrayOf(MIMETYPE_TEXT_PLAIN)).apply {
                extras = PersistableBundle().apply { putString("$DOCS_UI_EXTRA_PREFIX...", null) }
            },
            mediaStoreUris.map(ClipData::Item),
        )
    }

    @Test
    @ScreenRecordRule.ScreenRecord // b/462494467
    fun testDragAndDropWhenPayloadContainsText() {
        testDragAndDrop(
            ClipDescription(/* label= */ "", /* mimeTypes= */ arrayOf(MIMETYPE_TEXT_PLAIN)).apply {
                extras = PersistableBundle().apply { putString("$DOCS_UI_EXTRA_PREFIX...", null) }
            },
            listOf(ClipData.Item("text")),
        )
    }

    private fun testDragAndDrop(description: ClipDescription, itemList: List<ClipData.Item>) {
        // Perform a system-level drag-and-drop sequence.
        var endPoint = launcherActivity.getFromLauncher { it.dragLayer.getExactCenterOnScreen() }!!
        draggableView.performDragAndDropSequenceTo(endPoint, description, itemList)
        waitForLauncherCondition("Workspace item animation not completed") {
            it.dragLayer.animatedView == null
        }

        // Expect a workspace item to be created on system-level drag-and-drop if and only if:
        // (a) the home screen files provider is implemented,
        // (b) the system-level drag controller is implemented, and
        // (c) the dropped payload solely contains external primary media store URIs.
        val expectWorkspaceItemCreated =
            HomeScreenFilesProvider.INSTANCE[context] !is HomeScreenFilesNoOpProvider &&
                getSystemDragController("Controller not created") !is SystemDragControllerStub &&
                itemList.map(ClipData.Item::getUri).all(this::isExternalPrimaryMediaStoreUri)

        // Verify workspace item creation (or lack thereof).
        val workspaceItemView =
            launcherActivity.getFromLauncher {
                assertThrowsIf("Workspace item created", !expectWorkspaceItemCreated) {
                    findWorkspaceItem("Workspace item not created", itemList.firstIfNotEmpty()?.uri)
                }
            }
        val workspaceItemCreated = workspaceItemView != null
        assertEquals(expectWorkspaceItemCreated, workspaceItemCreated)

        // If a workspace item was not created, there's nothing left to verify.
        if (!workspaceItemCreated) {
            return
        }

        // If external storage permissions are not held, verify workspace item removal.
        if (!Environment.isExternalStorageManager()) {
            atMost("Workspace item not removed") { isRemovedFromLayout(workspaceItemView) }
            return
        }

        // If external storage permissions are held, verify expected file system changes.
        itemList.forEach { item ->
            atMost("'${item.uri}' not moved to '$HOME_SCREEN_FOLDER_RELATIVE_PATH'") {
                hasFileSystemItem(item.uri)
            }
        }

        // Find a vacant cell nearest the created workspace item.
        val itemInfo = workspaceItemView?.tag as? ItemInfo
        assertNotNull("Workspace item not tagged", itemInfo)
        val cellLayout = findCellLayout("Workspace cell layout not found", itemInfo!!)
        val cell = findNearestVacantCell(cellLayout, itemInfo.cellX, itemInfo.cellY)
        assertNotNull("Workspace cell vacancy not found", cell)
        assertNotEquals(cell!!.x, itemInfo.cellX)
        assertNotEquals(cell.y, itemInfo.cellY)

        // Perform a drag-and-drop sequence and verify the workspace item can be moved.
        endPoint = findExactCenterOnScreen(cellLayout, cell.x, cell.y).toPointF()
        workspaceItemView.performDragAndDropSequenceTo(endPoint)
        atMost("Workspace item not moved") { itemInfo.cellX == cell.x && itemInfo.cellY == cell.y }
    }

    private fun assertThrows(message: String, block: () -> Unit) {
        assertThrows(message, AssertionError::class.java, block)
    }

    private fun <T> assertThrowsIf(message: String, condition: Boolean, block: () -> T): T? {
        if (condition) {
            assertThrows(message) { block() }
            return null
        }
        return block()
    }

    private fun deleteAllHomeScreenFiles() {
        try {
            context.contentResolver.delete(
                MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY),
                "$RELATIVE_PATH = ?",
                arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to delete all home screen files", e)
        }
    }

    private fun findCellLayout(message: String, itemInfo: ItemInfo): CellLayout =
        launcherActivity.getOnceNotNull(message) { launcher ->
            launcher.getCellLayout(itemInfo.container, itemInfo.screenId)
        }!!

    private fun findExactCenterOnScreen(cellLayout: CellLayout, cellX: Int, cellY: Int): Point =
        IntArray(2)
            .apply {
                cellLayout.regionToCenterPoint(
                    cellX,
                    cellY,
                    /*spanX=*/ 1,
                    /*spanY=*/ 1,
                    /*result=*/ this,
                )
            }
            .let { centerPoint ->
                val boundsOnScreen = Rect().apply(cellLayout::getBoundsOnScreen)
                Point(boundsOnScreen.left + centerPoint[0], boundsOnScreen.top + centerPoint[1])
            }

    private fun findNearestVacantCell(cellLayout: CellLayout, cellX: Int, cellY: Int): Point? {
        val offsetsToNeighboringCells =
            listOf(
                Point(-1, -1), // Top-left.
                Point(0, -1), // Top.
                Point(1, -1), // Top-right.
                Point(1, 0), // Right.
                Point(1, 1), // Bottom-right.
                Point(0, 1), // Bottom.
                Point(-1, 1), // Bottom-left.
                Point(-1, 0), // Left.
            )

        val queuedCells = LinkedList<Point>().apply { add(Point(cellX, cellY)) }
        val visitedCells = mutableSetOf<Point>()

        while (queuedCells.isNotEmpty()) {
            val cell = queuedCells.removeFirst()
            visitedCells.add(cell)

            if (cellLayout.isRegionVacant(cell.x, cell.y, /* spanX= */ 1, /* spanY= */ 1)) {
                return cell
            }

            offsetsToNeighboringCells
                .map { offset -> Point(cell).apply { offset(offset.x, offset.y) } }
                .filter { neighbor -> neighbor.x in 0..<cellLayout.countX }
                .filter { neighbor -> neighbor.y in 0..<cellLayout.countY }
                .filterNot(visitedCells::contains)
                .forEach(queuedCells::addLast)
        }

        return null
    }

    private fun findWorkspaceItem(message: String, uri: Uri?) =
        launcherActivity.getOnceNotNull(message) { launcher ->
            launcher.workspace.mapOverItems { itemInfo, _ ->
                itemInfo?.itemType == ITEM_TYPE_FILE_SYSTEM_FILE && itemInfo.intent?.data == uri
            }
        }

    private fun getSystemDragController(message: String): SystemDragController =
        launcherActivity.getOnceNotNull(message) { launcher ->
            launcher.activityComponent.systemDragController
        }!!

    private fun hasFileSystemItem(uri: Uri): Boolean =
        context.contentResolver
            .query(
                uri,
                /*projection=*/ arrayOf(_ID),
                /*selection=*/ "$RELATIVE_PATH = ?",
                /*selectionArgs=*/ arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH),
                /*sortOrder=*/ null,
            )
            .use { cursor -> cursor?.count == 1 }

    private fun isExternalPrimaryMediaStoreUri(uri: Uri?) =
        uri?.scheme == ContentResolver.SCHEME_CONTENT &&
            uri.authority == MediaStore.AUTHORITY &&
            kotlin
                .runCatching { MediaStore.getVolumeName(uri) == VOLUME_EXTERNAL_PRIMARY }
                .getOrDefault(false)

    private fun isRemovedFromLayout(view: View?) =
        launcherActivity.getFromLauncher { view?.parent } == null

    private fun obtainMotionEvent(action: Int, point: PointF, downTime: Long): MotionEvent =
        MotionEvent.obtain(
            downTime,
            /*eventTime=*/ SystemClock.uptimeMillis(),
            action,
            point.x,
            point.y,
            /*metaState=*/ 0,
        )

    private fun <T> List<T>?.firstIfNotEmpty(): T? =
        if (this?.isNotEmpty() == true) first() else null

    private fun PointF.getMidPointTo(endPoint: PointF): PointF =
        PointF((this.x + endPoint.x) / 2.0f, (this.y + endPoint.y) / 2.0f)

    private fun View.getExactCenterOnScreen(): PointF =
        Rect().apply(this::getBoundsOnScreen).let { PointF(it.exactCenterX(), it.exactCenterY()) }

    private fun View.performDragAndDropSequenceTo(
        endPt: PointF,
        description: ClipDescription? = null,
        items: List<ClipData.Item>? = null,
    ) {
        InstrumentationRegistry.getInstrumentation().run {
            val downTime = SystemClock.uptimeMillis()
            val startPt = getExactCenterOnScreen()
            val midPt = startPt.getMidPointTo(endPt)
            val sync = true
            uiAutomation.injectInputEvent(obtainMotionEvent(ACTION_DOWN, startPt, downTime), sync)
            Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong())
            if (description != null && items != null) {
                runOnMainSync {
                    startDragAndDrop(
                        ClipData(description, items.firstIfNotEmpty()).apply {
                            items.drop(1).forEach(this::addItem)
                        },
                        View.DragShadowBuilder(this@performDragAndDropSequenceTo),
                        /*localState=*/ null,
                        DRAG_FLAG_GLOBAL or DRAG_FLAG_GLOBAL_URI_READ or DRAG_FLAG_GLOBAL_URI_WRITE,
                    )
                }
            }
            uiAutomation.injectInputEvent(obtainMotionEvent(ACTION_MOVE, midPt, downTime), sync)
            uiAutomation.injectInputEvent(obtainMotionEvent(ACTION_MOVE, endPt, downTime), sync)
            uiAutomation.injectInputEvent(obtainMotionEvent(ACTION_UP, endPt, downTime), sync)
        }
    }

    companion object {
        private const val TAG = "SystemDragIntegrationTest"
        private const val DRAGGABLE_VIEW_SIZE = 24
    }
}
