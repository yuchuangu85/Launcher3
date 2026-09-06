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

import android.content.ContentResolver
import android.content.ContentResolver.MimeTypeInfo
import android.content.Context
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Process
import android.provider.MediaStore.Files.FileColumns.HEIGHT
import android.provider.MediaStore.Files.FileColumns.WIDTH
import android.util.DisplayMetrics
import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.Launcher
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.cache.BaseIconCache
import com.android.launcher3.icons.cache.IconLoadRequest
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.SandboxApplication
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@LargeTest
@RunWith(AndroidJUnit4::class)
@MutatedComponent(target = LauncherAppComponent::class)
class HomeScreenFilesCachingLogicTest {
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @BindValue @Mock lateinit var provider: HomeScreenFilesProvider

    @Mock private lateinit var context: Context
    @Mock private lateinit var contentResolver: ContentResolver
    @Mock private lateinit var baseIconCache: BaseIconCache
    @Mock private lateinit var baseIconFactory: BaseIconFactory
    @Mock private lateinit var bitmap: Bitmap
    @Mock private lateinit var bitmapInfo: BitmapInfo
    @Mock private lateinit var drawable: Drawable
    @Mock private lateinit var icon: Icon
    @Mock private lateinit var iconProvider: HomeScreenFilesIconProvider

    @Before
    fun setUp() {
        whenever(context.contentResolver).thenReturn(contentResolver)
        whenever(baseIconCache.iconFactory).thenAnswer { baseIconFactory }
        whenever(provider.iconProvider).thenReturn(iconProvider)
        whenever(provider.onReady()).thenReturn(CompletableFuture())
        app.initDaggerComponent(mutatedComponentBuilder())
    }

    @Test
    fun getComponent() {
        val hsf =
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "image/png",
                isDirectory = false,
                user = Process.myUserHandle(),
            )
        val component = HomeScreenFilesCachingLogic.getComponent(hsf)
        assertThat(component.packageName).isEqualTo("com.android.launcher3.homescreenfiles")
        assertThat(component.className).isEqualTo("content://media/external_primary/file/1")
    }

    @Test
    fun testGetFolderIcon() {
        val folderIcon = mock<BitmapInfo>()
        val hsf = mock<HomeScreenFile>()

        whenever(hsf.isDirectory).thenReturn(true)
        whenever(iconProvider.folderIcon).thenReturn(folderIcon)

        launcherActivity.executeOnLauncher { launcher ->
            val icon = hsf.loadIcon(launcher)
            assertThat(icon).isEqualTo(folderIcon)
        }
    }

    @Test
    fun testGetImageThumbnailIcon() {
        testGetThumbnailIcon("image/png")
    }

    @Test
    fun testGetVideoThumbnailIcon() {
        testGetThumbnailIcon("video/mp4")
    }

    private fun testGetThumbnailIcon(mimeType: String) {
        val hsf =
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = mimeType,
                isDirectory = false,
                user = Process.myUserHandle(),
            )

        whenever(contentResolver.loadThumbnail(eq(hsf.uri), any(), isNull())).thenReturn(bitmap)
        whenever(baseIconFactory.createIconBitmap(any(), eq(true))).thenReturn(bitmapInfo)

        val icon = hsf.loadIcon()
        assertThat(icon).isEqualTo(bitmapInfo)
    }

    @Test
    fun testGetGenericMimeTypeIcon() {
        val hsf =
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "application/pdf",
                isDirectory = false,
                user = Process.myUserHandle(),
            )

        whenever(contentResolver.getTypeInfo(eq("application/pdf")))
            .thenReturn(MimeTypeInfo(icon, "label", "contentDescription"))
        whenever(icon.loadDrawable(any())).thenReturn(drawable)
        whenever(baseIconFactory.createBadgedIconBitmap(eq(drawable), any())).thenReturn(bitmapInfo)

        val icon = hsf.loadIcon()
        assertThat(icon).isEqualTo(bitmapInfo)
    }

    @Test
    fun testGetGenericMimeTypeIconWhenThumbnailFails() {
        val hsf =
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "image/png",
                isDirectory = false,
                user = Process.myUserHandle(),
            )

        whenever(contentResolver.loadThumbnail(eq(hsf.uri), any(), isNull()))
            .thenThrow(IOException("test"))
        whenever(contentResolver.getTypeInfo(eq("image/png")))
            .thenReturn(MimeTypeInfo(icon, "label", "contentDescription"))
        whenever(icon.loadDrawable(any())).thenReturn(drawable)
        whenever(baseIconFactory.createBadgedIconBitmap(eq(drawable), any())).thenReturn(bitmapInfo)

        val icon = hsf.loadIcon()
        assertThat(icon).isEqualTo(bitmapInfo)
    }

    @Test
    fun testLoadThumbnailSizeBeforeCropToSquare() {
        baseIconFactory =
            spy(BaseIconFactory(context, /* fullResIconDpi= */ 0, /* iconBitmapSize= */ 24))

        // Case: Unexpected dimensions.
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = null, expected = Size(24, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(0, 0), expected = Size(24, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(0, 1), expected = Size(24, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(1, 0), expected = Size(24, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(-1, -1), expected = Size(24, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(-1, 1), expected = Size(24, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(1, -1), expected = Size(24, 24))

        // Case: Square dimensions.
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(12, 12), expected = Size(24, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(24, 24), expected = Size(24, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(48, 48), expected = Size(24, 24))

        // Case: Non-square dimensions.
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(12, 16), expected = Size(24, 32))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(16, 12), expected = Size(32, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(12, 24), expected = Size(24, 48))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(24, 12), expected = Size(48, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(12, 32), expected = Size(24, 64))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(32, 12), expected = Size(64, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(24, 48), expected = Size(24, 48))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(48, 24), expected = Size(48, 24))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(48, 64), expected = Size(24, 32))
        testLoadThumbnailSizeBeforeCropToSquare(dimensions = Size(64, 48), expected = Size(32, 24))
    }

    private fun testLoadThumbnailSizeBeforeCropToSquare(dimensions: Size?, expected: Size) {
        val hsf =
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "image/png",
                isDirectory = false,
                user = Process.myUserHandle(),
            )

        // Mock dimensions.
        if (dimensions != null) {
            whenever(
                    contentResolver.query(
                        eq(hsf.uri),
                        eq(arrayOf(WIDTH, HEIGHT)),
                        /* queryArgs= */ isNull(),
                        /* cancellationSignal= */ isNull(),
                    )
                )
                .thenAnswer {
                    MatrixCursor(arrayOf(WIDTH, HEIGHT)).apply {
                        addRow(arrayOf(dimensions.width, dimensions.height))
                    }
                }
        }

        // Mock thumbnail.
        // NOTE: This verifies expected thumbnail size before cropping to square.
        whenever(contentResolver.loadThumbnail(eq(hsf.uri), eq(expected), isNull()))
            .thenReturn(Bitmap.createBitmap(expected.width, expected.height, ARGB_8888))

        // Load thumbnail.
        hsf.loadIcon()

        // Verify expected thumbnail size after cropping to square.
        val thumbnail = argumentCaptor<Bitmap>()
        verify(baseIconFactory).createIconBitmap(thumbnail.capture(), eq(true))
        assertEquals(baseIconFactory.iconBitmapSize, thumbnail.firstValue.width)
        assertEquals(baseIconFactory.iconBitmapSize, thumbnail.firstValue.height)

        // Reset.
        clearInvocations(baseIconFactory)
    }

    @Test
    fun testGetPlaceholderIconWhenLoadMimeTypeDrawableFails() {
        val hsf = mock<HomeScreenFile>()
        val mimeType = "application/foo"

        whenever(hsf.mimeType).thenReturn(mimeType)
        whenever(contentResolver.getTypeInfo(mimeType))
            .thenReturn(MimeTypeInfo(icon, "label", "description"))
        whenever(icon.loadDrawable(any())).thenReturn(null)

        val icon = hsf.loadIcon()
        assertThat(icon).isEqualTo(BitmapInfo.LOW_RES_INFO)
    }

    @Test
    fun testGetPlaceholderIconWhenMimeTypeIsEmpty() {
        val hsf = mock<HomeScreenFile>()
        whenever(hsf.mimeType).thenReturn("")

        val icon = hsf.loadIcon()
        assertThat(icon).isEqualTo(BitmapInfo.LOW_RES_INFO)
    }

    @Test
    fun testGetPlaceholderIconWhenMimeTypeIsNull() {
        val hsf = mock<HomeScreenFile>()
        whenever(hsf.mimeType).thenReturn(null)

        val icon = hsf.loadIcon()
        assertThat(icon).isEqualTo(BitmapInfo.LOW_RES_INFO)
    }

    private fun HomeScreenFile.loadIcon(ctx: Context = context) =
        IconLoadRequest(
                context = ctx,
                item = this,
                logic = HomeScreenFilesCachingLogic,
                cache = baseIconCache,
                iconDpi = DisplayMetrics.DENSITY_DEFAULT,
            )
            .evaluate()
}
