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

package com.android.launcher3.homescreenfiles

import android.content.Context
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.ContextThemeWrapper
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.BitmapInfo.Companion.LOW_RES_INFO
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.FastBitmapDrawableDelegate
import com.android.launcher3.icons.FastBitmapDrawableDelegate.Companion.drawShaderInBounds
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.icons.GraphicsUtils.resize
import com.android.launcher3.icons.IconShape
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Themes
import javax.inject.Inject

/** Icon provider interface for home screen files/folders. */
interface HomeScreenFilesIconProvider {
    val folderIcon: BitmapInfo
}

/** No-op icon provider for home screen files/folders. */
class HomeScreenFilesNoOpIconProvider : HomeScreenFilesIconProvider {
    override val folderIcon = LOW_RES_INFO
}

/** Icon provider for home screen files/folders. */
@LauncherAppSingleton
class HomeScreenFilesIconProviderImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @LauncherAppSingleton private val idp: InvariantDeviceProfile,
    @LauncherAppSingleton private val lifecycle: DaggerSingletonTracker,
) : HomeScreenFilesIconProvider {

    // Cached icon to be used for folders.
    override var folderIcon: BitmapInfo = createFolderIcon()
        private set

    init {
        val listener = OnIDPChangeListener { folderIcon = createFolderIcon() }
        idp.addOnChangeListener(listener)
        lifecycle.addCloseable { idp.removeOnChangeListener(listener) }
    }

    // NOTE: We need to provide an [icon] other than [BitmapInfo.LOW_RES_ICON] in order to use our
    // custom delegate factory. Otherwise [PlaceholderDelegateFactory] will be used when creating
    // new drawables.
    private fun createFolderIcon() =
        BitmapInfo(
            icon = ALPHA_BITMAP,
            color = Color.TRANSPARENT,
            defaultIconShape = createFolderIconShape(),
            delegateFactory = FolderIconDelegateFactory(),
        )

    private fun createFolderIconShape(): IconShape {
        val res = context.resources
        val data = res.getString(R.string.home_screen_files_folder_icon_path_data)
        val size = res.getInteger(R.integer.home_screen_files_folder_icon_path_intrinsic_size)
        val path = PathParser.createPathFromPathData(data).resize(size, idp.iconBitmapSize)
        return GraphicsUtils.generateIconShape(idp.iconBitmapSize, path)
    }

    /** Delegate factory for drawing folder icons. */
    private inner class FolderIconDelegateFactory : FastBitmapDrawableDelegate.DelegateFactory {

        override fun newDelegate(
            bitmapInfo: BitmapInfo,
            iconShape: IconShape,
            paint: Paint,
            host: FastBitmapDrawable,
        ): FastBitmapDrawableDelegate = FolderIconDelegate()
    }

    /** Delegate for drawing folder icons. */
    @VisibleForTesting
    inner class FolderIconDelegate : FastBitmapDrawableDelegate {

        // The themed color to use when drawing folder icons. Note that we need to wrap the
        // application context in the appropriate activity theme in order to successfully resolve
        // the color resource.
        private val color =
            ContextThemeWrapper(context, Themes.getActivityThemeRes(context))
                .getColor(R.color.home_screen_files_folder_icon_color)

        override fun drawContent(
            info: BitmapInfo,
            iconShape: IconShape,
            canvas: Canvas,
            bounds: Rect,
            paint: Paint,
        ) {
            paint.color =
                paint.color.let { oldColor ->
                    paint.color = color
                    canvas.drawShaderInBounds(bounds, info.defaultIconShape, paint, null)
                    oldColor
                }
        }
    }

    companion object {
        private val ALPHA_BITMAP = createBitmap(1, 1, Config.ALPHA_8)
    }
}
