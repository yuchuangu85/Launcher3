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

package com.android.launcher3.homescreenfiles

import android.content.Context
import android.net.Uri
import android.os.UserHandle
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.Workspace
import com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconCache
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_FILE_SYSTEM_NOT_READY
import com.android.launcher3.model.data.WorkspaceItemCoordinates
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(LauncherMultivalentJUnit::class)
class HomeScreenFilesUpdateTaskTest {

    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var context: Context
    @Mock private lateinit var apps: AllAppsList
    @Mock private lateinit var dataModel: BgDataModel
    @Mock private lateinit var dataModelCallback: BgDataModel.Callbacks
    @Mock private lateinit var iconCache: IconCache
    @Mock private lateinit var idp: InvariantDeviceProfile
    @Mock private lateinit var modelWriter: ModelWriter
    @Mock private lateinit var taskController: ModelTaskController
    @Mock private lateinit var user: UserHandle
    @Mock private lateinit var workspaceItemSpaceFinder: WorkspaceItemSpaceFinder
    @Mock private lateinit var statsLogManagerFactory: StatsLogManager.StatsLogManagerFactory
    @Mock private lateinit var statsLogManager: StatsLogManager
    @Mock(answer = Answers.RETURNS_SELF)
    private lateinit var statsLogger: StatsLogManager.StatsLogger

    private val iconsByUri = mutableMapOf<Uri, BitmapInfo>()
    private val items = mutableListOf<ItemInfo>()
    private val nextCellX = AtomicInteger(1)
    private val nextCellY = AtomicInteger(1)
    private val nextId = AtomicInteger(1)

    @Before
    fun setUp() {
        // Mock data model updates.
        whenever(dataModel.updateAndCollectWorkspaceItemInfos(eq(user), any(), anyOrNull()))
            .thenAnswer {
                val predicate = it.getArgument<(WorkspaceItemInfo) -> Boolean>(1)
                items.filterIsInstance<WorkspaceItemInfo>().filter(predicate)
            }

        // Mock data model additions.
        whenever(dataModelCallback.bindItemsAdded(any())).thenAnswer {
            items.addAll(it.getArgument<List<ItemInfo>>(0))
        }

        // Mock icon caching.
        val userSerial = System.currentTimeMillis()
        whenever(iconCache.getSerialNumberForUser(eq(user))).thenReturn(userSerial)
        whenever(
                iconCache.addIconToDBAndMemCache(
                    any(),
                    eq(HomeScreenFilesCachingLogic),
                    eq(userSerial),
                )
            )
            .thenAnswer { iconsByUri.put(it.getArgument<HomeScreenFile>(0).uri, mock()) }

        // Mock icon lookup.
        whenever(iconCache.getTitleAndIcon(any(), any())).thenAnswer {
            with(it.getArgument<ItemInfoWithIcon>(0)) {
                bitmap = iconsByUri[intent?.data] ?: BitmapInfo.LOW_RES_INFO
            }
        }

        // Mock data model deletions.
        whenever(
                taskController.deleteAndBindComponentsRemoved(
                    any(),
                    eq("The file system item no longer exists"),
                )
            )
            .thenAnswer { items.removeIf(it.getArgument<Predicate<ItemInfo?>>(0)) }

        // Mock data model writer.
        whenever(taskController.getModelWriter()).thenReturn(modelWriter)

        // Mock task scheduling.
        whenever(taskController.scheduleCallbackTask(any())).thenAnswer {
            it.getArgument<LauncherModel.CallbackTask>(0).execute(dataModelCallback)
        }

        // Mock user event logging.
        whenever(statsLogManagerFactory.create(context)).thenReturn(statsLogManager)
        whenever(statsLogManager.logger()).thenReturn(statsLogger)
    }

    @Test
    fun testExecuteDelayedInit() {
        // Create file system backed items.
        val fileToAdd = HomeScreenFile(mock(), "add.txt", "text/plain", false, user)
        val fileToDelete = HomeScreenFile(mock(), "delete.txt", "text/plain", false, user)
        val fileToIgnore = HomeScreenFile(mock(), "ignore.txt", "text/plain", false, user)
        var fileToUpdate = HomeScreenFile(mock(), "update.txt", "text/plain", false, user)
        val itemToAdd = createItem(fileToAdd, /* isInitialized= */ false)
        val itemToDelete = createItem(fileToDelete, /* isInitialized= */ false)
        val itemToIgnore = createItem(fileToIgnore, /* isInitialized= */ true)
        val itemToUpdate = createItem(fileToUpdate, /* isInitialized= */ false)

        // Mock space finding for [itemToAdd].
        whenever(
                workspaceItemSpaceFinder.findSpaceForItem(
                    argThat { knownItems -> knownItems.isEmpty() },
                    eq(itemToAdd.spanX),
                    eq(itemToAdd.spanY),
                    eq(IntSet()),
                    eq(
                        WorkspaceItemCoordinates(
                            Workspace.FIRST_SCREEN_ID,
                            /* cellX=*/ 0,
                            /* cellY=*/ 0,
                        )
                    ),
                )
            )
            .thenReturn(
                WorkspaceItemCoordinates(itemToAdd.screenId, itemToAdd.cellX, itemToAdd.cellY)
            )

        // Mock data model.
        items.addAll(listOf(itemToDelete, itemToIgnore, itemToUpdate))

        // Execute delayed initialization.
        fileToUpdate = fileToUpdate.copy(displayName = "updated.txt")
        val filesByUri = listOf(fileToAdd, fileToIgnore, fileToUpdate).associateByUri()
        val delayedInit = createDelayedInit(filesByUri)
        createTask(delayedInit).execute()

        // Verify expected data model modifications.
        with(items) {
            assertThat(size).isEqualTo(3)
            with(get(0) as WorkspaceItemInfo) {
                assertThat(this).isEqualTo(itemToIgnore)
                assertThat(bitmap).isEqualTo(BitmapInfo.LOW_RES_INFO)
                assertThat(hasFlagDisabledFileSystemNotReady()).isFalse()
                assertThat(title).isEqualTo(fileToIgnore.displayName)
                verify(modelWriter, times(0)).updateItemInDatabase(this)
            }
            with(get(1) as WorkspaceItemInfo) {
                assertThat(this).isEqualTo(itemToUpdate)
                assertThat(bitmap).isEqualTo(iconsByUri[itemToUpdate.intent?.data])
                assertThat(hasFlagDisabledFileSystemNotReady()).isFalse()
                assertThat(title).isEqualTo(fileToUpdate.displayName)
                verify(modelWriter).updateItemInDatabase(this)
            }
            with(get(2) as WorkspaceItemInfo) {
                assertThat(bitmap).isEqualTo(iconsByUri[itemToAdd.intent?.data])
                assertThat(cellX).isEqualTo(itemToAdd.cellX)
                assertThat(cellY).isEqualTo(itemToAdd.cellY)
                assertThat(container).isEqualTo(itemToAdd.container)
                assertThat(hasFlagDisabledFileSystemNotReady()).isFalse()
                assertThat(intent?.data).isEqualTo(itemToAdd.intent?.data)
                assertThat(intent?.flags).isEqualTo(itemToAdd.intent?.flags)
                assertThat(intent?.scheme).isEqualTo(itemToAdd.intent?.scheme)
                assertThat(itemType).isEqualTo(itemToAdd.itemType)
                assertThat(screenId).isEqualTo(itemToAdd.screenId)
                assertThat(spanX).isEqualTo(itemToAdd.spanX)
                assertThat(spanY).isEqualTo(itemToAdd.spanY)
                assertThat(title).isEqualTo(itemToAdd.title)
                assertThat(user).isEqualTo(itemToAdd.user)
                verify(modelWriter).addItemsToDatabase(listOf(this))
            }
            verifyNoMoreInteractions(modelWriter)
        }

        verify(statsLogger, times(1)).withCardinality(3)
        verify(statsLogger, times(1))
            .log(StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_COUNT)
    }

    @Test
    fun testExecuteUpdateWhichAddsAnItem() {
        // Create file system backed items.
        val fileToAdd = HomeScreenFile(mock(), "add.txt", "text/plain", false, user)
        val fileToIgnore = HomeScreenFile(mock(), "ignore.txt", "text/plain", false, user)
        val itemToAdd = createItem(fileToAdd)
        val itemToIgnore = createItem(fileToIgnore)

        // Mock space finding for [itemToAdd].
        whenever(
                workspaceItemSpaceFinder.findSpaceForItem(
                    argThat { knownItems -> knownItems.isEmpty() },
                    eq(itemToAdd.spanX),
                    eq(itemToAdd.spanY),
                    eq(IntSet()),
                    eq(
                        WorkspaceItemCoordinates(
                            itemToAdd.screenId,
                            itemToAdd.cellX,
                            itemToAdd.cellY,
                        )
                    ),
                )
            )
            .thenAnswer { it.getArgument(4) }

        // Mock data model.
        items.add(itemToIgnore)

        // Execute update.
        val filesByUri = listOf(fileToAdd).associateByUri()
        val update =
            createUpdate(
                filesByUri,
                HomeScreenFilesUpdate.Extras.builder()
                    .findSpaceStartingFrom(
                        WorkspaceItemCoordinates(
                            itemToAdd.screenId,
                            itemToAdd.cellX,
                            itemToAdd.cellY,
                        )
                    )
                    .build(),
            )
        createTask(update).execute()

        // Verify expected data model modifications.
        with(items) {
            assertThat(size).isEqualTo(2)
            assertThat(get(0)).isEqualTo(itemToIgnore)
            with(get(1) as WorkspaceItemInfo) {
                assertThat(bitmap).isEqualTo(iconsByUri[itemToAdd.intent?.data])
                assertThat(cellX).isEqualTo(itemToAdd.cellX)
                assertThat(cellY).isEqualTo(itemToAdd.cellY)
                assertThat(container).isEqualTo(itemToAdd.container)
                assertThat(hasFlagDisabledFileSystemNotReady()).isFalse()
                assertThat(intent?.data).isEqualTo(itemToAdd.intent?.data)
                assertThat(intent?.flags).isEqualTo(itemToAdd.intent?.flags)
                assertThat(intent?.scheme).isEqualTo(itemToAdd.intent?.scheme)
                assertThat(itemType).isEqualTo(itemToAdd.itemType)
                assertThat(screenId).isEqualTo(itemToAdd.screenId)
                assertThat(spanX).isEqualTo(itemToAdd.spanX)
                assertThat(spanY).isEqualTo(itemToAdd.spanY)
                assertThat(title).isEqualTo(itemToAdd.title)
                assertThat(user).isEqualTo(itemToAdd.user)
                verify(modelWriter).addItemsToDatabase(listOf(this))
            }
            verifyNoMoreInteractions(modelWriter)
        }

        verifyNoInteractions(statsLogger)
    }

    @Test
    fun testExecuteUpdateWhichDeletesAnItem() {
        // Create file system backed items.
        val fileToDelete = HomeScreenFile(mock(), "delete.txt", "text/plain", false, user)
        val fileToIgnore = HomeScreenFile(mock(), "ignore.txt", "text/plain", false, user)
        val itemToDelete = createItem(fileToDelete)
        val itemToIgnore = createItem(fileToIgnore)

        // Mock data model.
        items.addAll(listOf(itemToDelete, itemToIgnore))

        // Execute update.
        val filesByUri = mapOf(fileToDelete.uri to null)
        val update = createUpdate(filesByUri)
        createTask(update).execute()

        // Verify expected data model modifications.
        assertThat(items).containsExactly(itemToIgnore)
        verifyNoMoreInteractions(modelWriter)

        verifyNoInteractions(statsLogger)
    }

    @Test
    fun testExecuteUpdateWhichUpdatesAnItem() {
        // Create file system backed items.
        val fileToIgnore = HomeScreenFile(mock(), "ignore.txt", "text/plain", false, user)
        var fileToUpdate = HomeScreenFile(mock(), "update.txt", "text/plain", false, user)
        val itemToIgnore = createItem(fileToIgnore)
        val itemToUpdate = createItem(fileToUpdate)

        // Mock data model.
        items.addAll(listOf(itemToIgnore, itemToUpdate))

        // Execute update.
        fileToUpdate = fileToUpdate.copy(displayName = "updated.txt")
        val filesByUri = listOf(fileToUpdate).associateByUri()
        val update = createUpdate(filesByUri)
        createTask(update).execute()

        // Verify expected data model modifications.
        assertThat(items).containsExactly(itemToIgnore, itemToUpdate)
        with(itemToUpdate) {
            assertThat(bitmap).isEqualTo(iconsByUri[intent?.data])
            assertThat(hasFlagDisabledFileSystemNotReady()).isFalse()
            assertThat(title).isEqualTo(fileToUpdate.displayName)
            verify(modelWriter).updateItemInDatabase(this)
        }
        verifyNoMoreInteractions(modelWriter)

        verifyNoInteractions(statsLogger)
    }

    private fun createDelayedInit(filesByUri: Map<Uri, HomeScreenFile>) =
        createUpdate(filesByUri, HomeScreenFilesUpdate.Extras.builder().isDelayedInit(true).build())

    private fun createItem(file: HomeScreenFile, isInitialized: Boolean = true) =
        WorkspaceItemInfo().apply {
            setFlagDisabledFileSystemNotReady(!isInitialized)
            bitmap = BitmapInfo.LOW_RES_INFO
            cellX = nextCellX.getAndIncrement()
            cellY = nextCellY.getAndIncrement()
            container = CONTAINER_DESKTOP
            id = nextId.getAndIncrement()
            intent = HomeScreenFilesUtils.buildLaunchIntent(file.uri, file)
            itemType = HomeScreenFilesUtils.buildItemType(file)
            screenId = FIRST_SCREEN_ID
            spanX = 1
            spanY = 1
            title = file.displayName
            user = file.user
        }

    private fun createTask(update: HomeScreenFilesUpdate) =
        HomeScreenFilesUpdateTask(
            context,
            iconCache,
            idp,
            update,
            workspaceItemSpaceFinder,
            statsLogManagerFactory,
        )

    private fun createUpdate(
        filesByUri: Map<Uri, HomeScreenFile?>,
        extras: HomeScreenFilesUpdate.Extras = HomeScreenFilesUpdate.Extras.builder().build(),
    ) = HomeScreenFilesUpdate(supplyAsync { filesByUri }, user, extras)

    private fun HomeScreenFilesUpdateTask.execute() = execute(taskController, dataModel, apps)

    private fun List<HomeScreenFile>.associateByUri() = associateBy(HomeScreenFile::uri)

    private fun WorkspaceItemInfo.hasFlagDisabledFileSystemNotReady() =
        (runtimeStatusFlags and FLAG_DISABLED_FILE_SYSTEM_NOT_READY) != 0

    private fun WorkspaceItemInfo.setFlagDisabledFileSystemNotReady(enabled: Boolean) {
        runtimeStatusFlags =
            FlagOp.NO_OP.setFlag(FLAG_DISABLED_FILE_SYSTEM_NOT_READY, enabled)
                .apply(runtimeStatusFlags)
    }
}
