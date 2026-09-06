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

import android.content.ClipDescription.MIMETYPE_UNKNOWN
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentResolver.NOTIFY_INSERT
import android.content.ContentResolver.NOTIFY_UPDATE
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.provider.DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY
import android.provider.DocumentsContract.EXTRA_URI
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.DATA
import android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
import android.provider.MediaStore.Files.FileColumns.IS_TRASHED
import android.provider.MediaStore.Files.FileColumns.MIME_TYPE
import android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH
import android.provider.MediaStore.Files.FileColumns._ID
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.R
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.Companion.HOME_SCREEN_FOLDER_RELATIVE_PATH
import com.android.launcher3.testutil.rule.LazyInitRule.Companion.lazyRule
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ForwardingExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class HomeScreenFilesProviderTest {

    @get:Rule val contextSpy = lazyRule { spy(SandboxApplication()) }
    @get:Rule var mockitoRule: MockitoRule = MockitoJUnit.rule()

    private val context: SandboxApplication by contextSpy
    @Mock private lateinit var contentResolver: ContentResolver
    @Mock private lateinit var contentProviderClient: ContentProviderClient
    @Mock private lateinit var fileFactory: (path: String) -> File
    @Mock private lateinit var environmentWrapper: EnvironmentWrapper
    @Mock private lateinit var iconProvider: HomeScreenFilesIconProvider

    private lateinit var provider: HomeScreenFilesProvider

    @Before
    fun setUp() {
        doReturn(contentResolver).whenever(context).contentResolver
        whenever(fileFactory.invoke(any())).thenAnswer { i -> File(i.getArgument<String>(0)) }
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(true)

        provider = createProvider()
    }

    @Test
    fun testOnReadyWhenExternalStorageProviderIsMountedAfterCall() {
        // Unmount external storage directory.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(false)

        // Recreate [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Invoke [#onReady()].
        val onReady = provider.onReady()
        assertFalse(onReady.isDone)

        // Mount external storage directory.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(true)

        // Notify external storage directory mounted.
        captureContentObserver()
            .dispatchChange(
                /*selfChange=*/ false,
                Uri.parse("content://media/external_primary"),
                ContentResolver.NOTIFY_SYNC_TO_NETWORK,
            )

        // Wait.
        assertNull(onReady.get())
    }

    @Test
    fun testOnReadyWhenExternalStorageProviderIsMountedBeforeCall() {
        // Unmount external storage directory.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(false)

        // Recreate [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Mount external storage directory.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(true)

        // Notify external storage directory mounted.
        captureContentObserver()
            .dispatchChange(
                /*selfChange=*/ false,
                Uri.parse("content://media/external_primary"),
                ContentResolver.NOTIFY_SYNC_TO_NETWORK,
            )

        // Invoke [#onReady()].
        assertNull(provider.onReady().get())
    }

    @Test
    fun testOnReadyWhenExternalStorageProviderIsMountedBeforeInit() {
        // Mount external storage directory.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(true)

        // Recreate [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Invoke [#onReady()].
        assertNull(provider.onReady().get())
    }

    @Test
    fun testCanCreateNewFolderWhenExternalStorageProviderIsMounted() {
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        assertTrue(provider.canCreateNewFolder())
    }

    @Test
    fun testCanCreateNewFolderWhenExternalStorageProviderIsUnmounted() {
        whenever(environmentWrapper.isExternalStorageDirectoryMounted()).thenReturn(false)

        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        assertFalse(provider.canCreateNewFolder())
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsMounted() {
        testCreateNewFolder(externalStorageDirectoryIsMounted = true, expectSuccess = true)
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsMountedButFolderCreationFails() {
        testCreateNewFolder(
            externalStorageDirectoryIsMounted = true,
            folderCreationSucceeds = false,
            expectSuccess = false,
        )
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsMountedButMediaStoreInsertionFails() {
        testCreateNewFolder(
            externalStorageDirectoryIsMounted = true,
            mediaStoreInsertionSucceeds = false,
            expectSuccess = false,
        )
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsMountedButMediaStoreQueryFails() {
        testCreateNewFolder(
            externalStorageDirectoryIsMounted = true,
            mediaStoreQuerySucceeds = false,
            expectSuccess = false,
        )
    }

    @Test
    fun testCreateNewFolderWhenExternalStorageProviderIsUnmounted() {
        testCreateNewFolder(externalStorageDirectoryIsMounted = false, expectSuccess = false)
    }

    private fun testCreateNewFolder(
        externalStorageDirectoryIsMounted: Boolean = true,
        mediaStoreInsertionSucceeds: Boolean = true,
        mediaStoreQuerySucceeds: Boolean = true,
        folderCreationSucceeds: Boolean = true,
        expectSuccess: Boolean = true,
    ) {
        val defaultNewFolderName = context.getString(R.string.default_new_folder_name)
        val data = "$HOME_SCREEN_FOLDER_RELATIVE_PATH/$defaultNewFolderName"
        val folder = mock<File>()
        val uri = createExternalPrimaryMediaStoreUri(1L)

        // Mock external storage directory state.
        whenever(environmentWrapper.isExternalStorageDirectoryMounted())
            .thenReturn(externalStorageDirectoryIsMounted)

        // Mock media store insertion success/failure.
        whenever(
                contentResolver.insert(
                    eq(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)),
                    argThat {
                        get(DISPLAY_NAME) == defaultNewFolderName &&
                            get(MIME_TYPE) == DocumentsContract.Document.MIME_TYPE_DIR &&
                            get(RELATIVE_PATH) == HOME_SCREEN_FOLDER_RELATIVE_PATH
                    },
                )
            )
            .thenReturn(if (mediaStoreInsertionSucceeds) uri else null)

        // Mock media store query success/failure.
        whenever(contentResolver.query(uri, arrayOf(DATA), null, null)).thenAnswer {
            if (mediaStoreQuerySucceeds) MatrixCursor(arrayOf(DATA)).apply { addRow(arrayOf(data)) }
            else null
        }

        // Mock folder creation success/failure.
        whenever(folder.exists()).thenReturn(false)
        whenever(folder.mkdirs()).thenReturn(folderCreationSucceeds)
        whenever(fileFactory.invoke(data)).thenReturn(folder)

        // Init [provider].
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider()

        // Cache updates.
        val updates = mutableListOf<HomeScreenFilesUpdate>()
        val updatesStream = provider.updates.forEach(Runnable::run, updates::add)

        // Invoke [#createNewFolder()].
        val extras = HomeScreenFilesUpdate.Extras.builder().findSpaceStartingFrom(mock()).build()
        assertEquals(expectSuccess, provider.createNewFolder(extras).get())

        // Verify [extras] propagation.
        if (expectSuccess) {
            captureContentObserver().dispatchChange(false, uri, NOTIFY_INSERT)
            assertEquals(1, updates.size)
            assertEquals(extras, updates[0].extras)
        }
    }

    @Test
    fun testCanMoveToHomeScreen() {
        val espUri = createExternalStorageProviderUri("externalRelativePath", "externalDisplayName")
        val mediaStoreUri = createExternalPrimaryMediaStoreUri(1L)
        val testUri = createTestUri("testId")

        assertFalse(provider.canMoveToHomeScreen(null))
        assertFalse(provider.canMoveToHomeScreen(emptyList()))
        assertTrue(provider.canMoveToHomeScreen(listOf(espUri)))
        assertTrue(provider.canMoveToHomeScreen(listOf(espUri, mediaStoreUri)))
        assertFalse(provider.canMoveToHomeScreen(listOf(espUri, mediaStoreUri, testUri)))
    }

    @Test
    fun testMoveToHomeScreen() {
        testMoveToHomeScreen(relativeFolderPath = null)
    }

    @Test
    fun testMoveToHomeScreenFolder() {
        testMoveToHomeScreen(relativeFolderPath = "Folder")
    }

    private fun testMoveToHomeScreen(relativeFolderPath: String?) {
        val relativePath = "$HOME_SCREEN_FOLDER_RELATIVE_PATH${relativeFolderPath ?: ""}"

        val espUri = createExternalStorageProviderUri("externalRelativePath", "externalDisplayName")
        val mediaStoreUri = createExternalPrimaryMediaStoreUri(1L)
        val mediaStoreUriResolvedFromEsp = createExternalPrimaryMediaStoreUri(2L)
        val mediaStoreFolderUri = createExternalPrimaryMediaStoreUri(3L)
        val testUri = createTestUri("testId")

        // Mock attempts to resolve media store URIs.
        whenever(contentProviderClient.call(eq(GET_MEDIA_URI_CALL), anyOrNull(), any()))
            .thenAnswer { invocation ->
                Bundle().apply {
                    putParcelable(
                        EXTRA_URI,
                        when (
                            invocation
                                .getArgument<Bundle>(2)
                                .getParcelable(EXTRA_URI, Uri::class.java)
                        ) {
                            espUri -> mediaStoreUriResolvedFromEsp
                            mediaStoreUri -> mediaStoreUri
                            mediaStoreUriResolvedFromEsp -> mediaStoreUriResolvedFromEsp
                            mediaStoreFolderUri -> mediaStoreFolderUri
                            else -> null
                        },
                    )
                }
            }

        // Associate media store content provider client with content resolver.
        whenever(contentResolver.acquireContentProviderClient(MediaStore.AUTHORITY))
            .thenReturn(contentProviderClient)

        // Mock attempts to resolve a media store URI.
        val path = "/storage/emulated/0/Download"
        whenever(contentResolver.query(any(), any(), isNull(), isNull(), isNull(), isNull()))
            .thenAnswer { invocation ->
                val answer = MatrixCursor(arrayOf(DISPLAY_NAME, MIME_TYPE, DATA))
                val uri = invocation.getArgument<Uri>(0)
                val id = ContentUris.parseId(uri)
                when (uri) {
                    mediaStoreFolderUri -> answer.addRow(arrayOf("Folder", null, "$path/Folder"))
                    else -> answer.addRow(arrayOf("File\\ $id.png", "image/png", "$path/File/$id"))
                }
                return@thenAnswer answer
            }
        whenever(fileFactory.invoke(any())).thenAnswer { invocation ->
            val file = mock<File>()
            val isDirectory = invocation.getArgument<String>(0) == "$path/Folder"
            whenever(file.exists()).thenReturn(true)
            whenever(file.isDirectory).thenReturn(isDirectory)
            file
        }

        // Mock attempts to update media store.
        whenever(
                contentResolver.update(
                    /*uri=*/ any(),
                    /*contentValues=*/ any(),
                    /*where=*/ eq("$RELATIVE_PATH != ?"),
                    /*selectionArgs=*/ eq(arrayOf(relativePath)),
                )
            )
            .thenAnswer { invocation ->
                when (invocation.getArgument<Uri>(0)) {
                    mediaStoreUri,
                    mediaStoreUriResolvedFromEsp -> {
                        assertThat(invocation.getArgument<ContentValues>(1))
                            .isEqualTo(ContentValues().apply { put(RELATIVE_PATH, relativePath) })
                        1
                    }
                    mediaStoreFolderUri -> {
                        assertThat(invocation.getArgument<ContentValues>(1))
                            .isEqualTo(
                                ContentValues().apply {
                                    put(MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                                    put(RELATIVE_PATH, relativePath)
                                }
                            )
                        1
                    }
                    else -> throw RuntimeException()
                }
            }

        // Init [provider].
        val executorService = CountingExecutorService(MoreExecutors.newDirectExecutorService())
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider(executorService)

        // Cache updates.
        val updates = mutableListOf<HomeScreenFilesUpdate>()
        val updatesStream = provider.updates.forEach(Runnable::run, updates::add)

        // NOTE: Overlapping move attempts for a given URI are disallowed.
        val expectSuccessByUri =
            listOf(
                Pair(espUri, true),
                Pair(mediaStoreUri, true),
                Pair(mediaStoreUri, false),
                Pair(mediaStoreFolderUri, true),
                Pair(testUri, false),
            )

        // Attempt to move URIs to home screen.
        val extras = HomeScreenFilesUpdate.Extras.builder().findSpaceStartingFrom(mock()).build()
        assertEquals(
            expectSuccessByUri.map(Pair<Uri, Boolean>::second),
            provider
                .moveToHomeScreen(
                    expectSuccessByUri.map(Pair<Uri, Boolean>::first),
                    extras,
                    relativeFolderPath,
                )
                .map(CompletableFuture<Boolean>::get),
        )

        // Verify expected number of [executorService] interactions. If the count is greater than
        // expected, that implies there is a nested execution which could result in deadlock.
        assertEquals(expectSuccessByUri.size, executorService.executionCount)

        // Verify [extras] propagation.
        val contentObserver = captureContentObserver()
        expectSuccessByUri
            .filter(Pair<Uri, Boolean>::second)
            .map(Pair<Uri, Boolean>::first)
            .map { MediaStore.getMediaUri(context, it) }
            .forEachIndexed { index, uri ->
                contentObserver.dispatchChange(false, uri, NOTIFY_UPDATE)
                assertEquals(extras, updates[index].extras)
            }
    }

    @Test
    fun testMoveToTrash() {
        whenever(environmentWrapper.getExternalStorageDirectory()).thenReturn(File("/test/"))
        whenever(
                contentProviderClient.call(
                    eq(MediaStore.AUTHORITY),
                    eq("mark_file_as_trashed"),
                    anyOrNull(),
                    argThat { x -> x.getString("file_path") == "/test/Home screen/file.png" },
                )
            )
            .thenReturn(Bundle().apply { putString("file_path", "/new/path/in/trash") })
        whenever(contentResolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY))
            .thenReturn(contentProviderClient)

        val future = provider.moveToTrash("file.png")
        assertThat(future.get()).isEqualTo("/new/path/in/trash")
    }

    @Test
    fun testMoveToTrashHandlesException() {
        whenever(environmentWrapper.getExternalStorageDirectory()).thenReturn(File("/test/"))
        whenever(
                contentProviderClient.call(
                    eq(MediaStore.AUTHORITY),
                    eq("mark_file_as_trashed"),
                    anyOrNull(),
                    argThat { x -> x.getString("file_path") == "/test/Home screen/file.png" },
                )
            )
            .thenThrow(UnsupportedOperationException())
        whenever(contentResolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY))
            .thenReturn(contentProviderClient)

        val future = provider.moveToTrash("file.png")
        assertThat(future.get()).isNull()
    }

    @Test
    fun testRestoreFromTrash() {
        whenever(
                contentProviderClient.call(
                    eq(MediaStore.AUTHORITY),
                    eq("mark_file_as_restored"),
                    anyOrNull(),
                    argThat { x -> x.getString("file_path") == "/path/in/trash" },
                )
            )
            .thenAnswer { invocation ->
                Bundle().apply {
                    putString("file_path", invocation.getArgument<Bundle>(3).getString("file_path"))
                }
            }
        whenever(contentResolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY))
            .thenReturn(contentProviderClient)

        val future = provider.restoreFromTrash("/path/in/trash")
        assertThat(future.get()).isTrue()
    }

    @Test
    fun testRestoreFromTrashHandlesException() {
        whenever(
                contentProviderClient.call(
                    eq(MediaStore.AUTHORITY),
                    eq("mark_file_as_restored"),
                    anyOrNull(),
                    argThat { x -> x.getString("file_path") == "/path/in/trash" },
                )
            )
            .thenThrow(UnsupportedOperationException())
        whenever(contentResolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY))
            .thenReturn(contentProviderClient)

        val future = provider.restoreFromTrash("/path/in/trash")
        assertThat(future.get()).isFalse()
    }

    @Test
    fun testDeletePermanently() {
        val uri = Uri.parse("content://media/external_primary/file/1")
        provider.deletePermanently(uri)

        verify(contentResolver, times(1))
            .delete(
                eq(uri),
                eq("$RELATIVE_PATH = ? AND $IS_TRASHED = ?"),
                eq(arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH, "0")),
            )
    }

    @Test
    fun testQuery() {
        val expectedUri = Uri.parse("content://media/external_primary/file")
        val expectedProjection = arrayOf(_ID, DISPLAY_NAME, MIME_TYPE, DATA)

        whenever(
                contentResolver.query(
                    eq(expectedUri),
                    eq(expectedProjection),
                    any(),
                    any(),
                    isNull(),
                    isNull(),
                )
            )
            .thenAnswer {
                val answer = MatrixCursor(expectedProjection)
                answer.addRow(
                    arrayOf("1", "test.png", "image/png", "/storage/emulated/0/Desktop/test.png")
                )
                answer.addRow(
                    arrayOf("2", "subfolder", null, "/storage/emulated/0/Desktop/subfolder")
                )
                return@thenAnswer answer
            }

        val result = provider.query().get()
        assertThat(result.size).isEqualTo(2)

        val uri1 = Uri.parse("content://media/external_primary/file/1")
        assertThat(result.containsKey(uri1)).isTrue()
        assertThat(result[uri1]!!.uri).isEqualTo(uri1)
        assertThat(result[uri1]!!.displayName).isEqualTo("test.png")
        assertThat(result[uri1]!!.mimeType).isEqualTo("image/png")

        val uri2 = Uri.parse("content://media/external_primary/file/2")
        assertThat(result.containsKey(uri2)).isTrue()
        assertThat(result[uri2]!!.uri).isEqualTo(uri2)
        assertThat(result[uri2]!!.displayName).isEqualTo("subfolder")
        assertThat(result[uri2]!!.mimeType).isNull()

        verify(contentResolver, times(1))
            .query(
                eq(expectedUri),
                eq(expectedProjection),
                eq("$RELATIVE_PATH = ? AND $IS_TRASHED = ?"),
                eq(arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH, "0")),
                isNull(),
                isNull(),
            )
    }

    @Test
    fun testRenameWhenUriIsNotSupported() {
        createTestUri("id").also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original.png",
                        mimeType = "image/png",
                        isDirectory = false,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed.png",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = false,
            )
        }
    }

    @Test
    fun testRenameWhenQueryingBackingFileFails() {
        testRename(
            usingBackingFile = null,
            usingName = "Renamed.png",
            usingUpdateResult = true,
            usingUri = createExternalPrimaryMediaStoreUri(1L),
            expectSuccess = false,
        )
    }

    @Test
    fun testRenameWhenUpdateFails() {
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original.png",
                        mimeType = "image/png",
                        isDirectory = false,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed.png",
                usingUpdateResult = false,
                usingUri = uri,
                expectSuccess = false,
            )
        }
    }

    @Test
    fun testRenameWhenUpdateSucceeds() {
        // Case: Rename file.
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original.png",
                        mimeType = "image/png",
                        isDirectory = false,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed.png",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = true,
            )
        }

        // Case: Rename file, adding extension.
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original",
                        mimeType = MIMETYPE_UNKNOWN,
                        isDirectory = false,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed.txt",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = true,
            )
        }

        // Case: Rename file, changing extension.
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original.png",
                        mimeType = "image/png",
                        isDirectory = false,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed.txt",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = true,
            )
        }

        // Case: Rename file, removing extension.
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original.png",
                        mimeType = "image/png",
                        isDirectory = false,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = true,
            )
        }

        // Case: Rename folder.
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original",
                        mimeType = MIME_TYPE_DIR,
                        isDirectory = true,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = true,
            )
        }

        // Case: Rename folder, adding extension.
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original",
                        mimeType = MIME_TYPE_DIR,
                        isDirectory = true,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed.dir",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = true,
            )
        }

        // Case: Rename folder, changing extension.
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original.dir",
                        mimeType = MIME_TYPE_DIR,
                        isDirectory = true,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed.folder",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = true,
            )
        }

        // Case: Rename folder, keeping extension.
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original.dir",
                        mimeType = MIME_TYPE_DIR,
                        isDirectory = true,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed.dir",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = true,
            )
        }

        // Case: Rename folder, removing extension.
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original.dir",
                        mimeType = MIME_TYPE_DIR,
                        isDirectory = true,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed",
                usingUpdateResult = true,
                usingUri = uri,
                expectSuccess = true,
            )
        }
    }

    @Test
    fun testRenameWhenUpdateThrows() {
        createExternalPrimaryMediaStoreUri(1L).also { uri ->
            testRename(
                usingBackingFile =
                    HomeScreenFile(
                        uri = uri,
                        displayName = "Original.png",
                        mimeType = "image/png",
                        isDirectory = false,
                        user = Process.myUserHandle(),
                    ),
                usingName = "Renamed.png",
                usingUpdateResult = null,
                usingUri = uri,
                expectSuccess = false,
            )
        }
    }

    private fun testRename(
        usingBackingFile: HomeScreenFile?,
        usingName: String,
        usingUpdateResult: Boolean?,
        usingUri: Uri,
        expectSuccess: Boolean,
    ) {
        // Mock query result.
        whenever(
                context.contentResolver.query(
                    eq(usingUri),
                    /*projection=*/ eq(arrayOf(DISPLAY_NAME, MIME_TYPE, DATA)),
                    /*selection=*/ isNull(),
                    /*selectionArgs=*/ isNull(),
                    /*sortOrder=*/ isNull(),
                    /*cancellationSignal=*/ isNull(),
                )
            )
            .thenAnswer {
                MatrixCursor(arrayOf(DISPLAY_NAME, MIME_TYPE, DATA)).apply {
                    if (usingBackingFile != null) {
                        val displayName = usingBackingFile.displayName
                        val mimeType = usingBackingFile.mimeType
                        val data = "$HOME_SCREEN_FOLDER_RELATIVE_PATH/$displayName"
                        addRow(arrayOf(displayName, mimeType, data))
                    }
                }
            }

        // Mock update result.
        whenever(
                context.contentResolver.update(
                    usingUri,
                    ContentValues().apply {
                        put(DISPLAY_NAME, usingName)
                        if (usingBackingFile?.isDirectory == true) {
                            put(MIME_TYPE, MIME_TYPE_DIR)
                        } else {
                            MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(File(usingName).extension)
                                .run { put(MIME_TYPE, this ?: MIMETYPE_UNKNOWN) }
                        }
                    },
                    /*where=*/ "$RELATIVE_PATH == ?",
                    /*selectionArgs=*/ arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH),
                )
            )
            .apply {
                when (usingUpdateResult) {
                    true -> thenReturn(1)
                    false -> thenReturn(0)
                    else -> thenThrow(RuntimeException())
                }
            }

        // Init [provider].
        val executorService = CountingExecutorService(MoreExecutors.newDirectExecutorService())
        clearInvocations(context)
        clearInvocations(contentResolver)
        provider = createProvider(executorService)

        // Perform rename.
        assertEquals(expectSuccess, provider.rename(usingUri, usingName).get())

        // Verify expected number of [executorService] interactions. If the count is greater than
        // expected, that implies there is a nested execution which could result in deadlock.
        assertEquals(1, executorService.executionCount)
    }

    @Test
    fun testNotifiesUpdateCallback() {
        val expectedData = "/storage/emulated/0/Desktop/test.png"
        val expectedDisplayName = "NEW_test.png"
        val expectedMimeType = "image/png"
        val expectedUri = Uri.parse("content://media/external_primary/file/1")

        whenever(contentResolver.query(eq(expectedUri), any(), any(), any(), isNull(), isNull()))
            .thenAnswer {
                val answer = MatrixCursor(arrayOf(DISPLAY_NAME, MIME_TYPE, DATA))
                answer.addRow(arrayOf(expectedDisplayName, expectedMimeType, expectedData))
                return@thenAnswer answer
            }

        val callback = mock<(HomeScreenFilesUpdate) -> Unit>()
        val immediateExecutor = Executor { r -> r.run() }
        val unregisterCallback = provider.updates.forEach(immediateExecutor) { callback(it) }
        val underlyingContentObserver = captureContentObserver()

        underlyingContentObserver.dispatchChange(false, expectedUri, NOTIFY_INSERT)

        val expectedIsDirectory = false
        val expectedUser = Process.myUserHandle()
        val expectedFile =
            HomeScreenFile(
                expectedUri,
                expectedDisplayName,
                expectedMimeType,
                expectedIsDirectory,
                expectedUser,
            )

        verify(callback, times(1))
            .invoke(
                argThat {
                    filesByUri.get() == mapOf(expectedUri to expectedFile) &&
                        !extras.isDelayedInit &&
                        user == expectedUser
                }
            )

        context.appComponent.daggerSingletonTracker.close()
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        verify(contentResolver, times(1)).unregisterContentObserver(underlyingContentObserver)

        unregisterCallback.close()
    }

    private fun captureContentObserver() =
        argumentCaptor<ContentObserver>().let { contentObserver ->
            verify(contentResolver, times(1))
                .registerContentObserver(
                    eq(Uri.parse("content://media/external_primary/file")),
                    eq(true),
                    contentObserver.capture(),
                )
            contentObserver.firstValue
        }

    private fun createExternalPrimaryMediaStoreUri(id: Long) =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, id)

    private fun createExternalStorageProviderUri(relativePath: String, displayName: String) =
        DocumentsContract.buildDocumentUri(
            /*authority=*/ EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
            /*documentId=*/ "primary:$relativePath%3F$displayName",
        )

    private fun createProvider(executorService: ExecutorService? = null) =
        HomeScreenFilesMediaStoreProvider(
            context,
            executorService ?: MoreExecutors.newDirectExecutorService(),
            fileFactory,
            environmentWrapper,
            iconProvider,
            context.appComponent.daggerSingletonTracker,
        )

    private fun createTestUri(id: String) = "content://test/path/$id".toUri()

    private class CountingExecutorService(private val delegate: ExecutorService) :
        ForwardingExecutorService() {
        var executionCount = 0

        override fun delegate(): ExecutorService = delegate

        override fun execute(command: Runnable) {
            super.execute(command)
            executionCount++
        }
    }

    companion object {
        private const val GET_MEDIA_URI_CALL = "get_media_uri"
    }
}
