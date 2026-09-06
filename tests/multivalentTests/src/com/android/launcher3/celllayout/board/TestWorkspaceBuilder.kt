/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.celllayout.board

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.WidgetUtils

class TestWorkspaceBuilder(private val mContext: Context) {

    private var appComponentName =
        ComponentName("com.google.android.calculator", "com.android.calculator2.Calculator")

    /**
     * Helper to set the app to use for the test workspace, using activity-alias from
     * AndroidManifest-common.
     *
     * @param testAppName the android:name field of the test app activity-alias to use
     */
    fun setTestAppActivityAlias(testAppName: String) {
        appComponentName =
            ComponentName(
                InstrumentationRegistry.getInstrumentation().context.packageName,
                TEST_ACTIVITY_PACKAGE_PREFIX + testAppName,
            )
    }

    /**
     * Sets the test app for app icons to the specified Component
     *
     * @param testAppComponent ComponentName to use for app icons
     */
    fun setTestAppComponent(testAppComponent: ComponentName) {
        appComponentName = testAppComponent
    }

    private fun addCorrespondingWidgetRect(
        widgetRect: WidgetRect,
        builder: LauncherLayoutBuilder,
        screenId: Int,
    ) {
        if (widgetRect.type == 'x') {
            // Fills the given rect in WidgetRect with 1x1 widgets. This is useful to equalize
            // cases.
            val initX = widgetRect.cellX
            val initY = widgetRect.cellY
            for (x in initX until initX + widgetRect.spanX) {
                for (y in initY until initY + widgetRect.spanY) {
                    builder.addWidget(WidgetRect(CellType.IGNORE, Rect(x, y, x, y)), screenId)
                }
            }
        } else {
            builder.addWidget(widgetRect, screenId)
        }
    }

    private fun LauncherLayoutBuilder.addWidget(widgetRect: WidgetRect, screenId: Int) {
        val widget = WidgetUtils.findWidgetProvider(false)
        atWorkspace(widgetRect.cellX, widgetRect.cellY, screenId)
            .putWidget(
                widget.provider.packageName,
                widget.provider.className,
                widgetRect.spanX,
                widgetRect.spanY,
            )
    }

    /** Builds the given board into the transaction */
    fun buildFromBoard(board: CellLayoutBoard, builder: LauncherLayoutBuilder, screenId: Int) {
        board.widgets.forEach { addCorrespondingWidgetRect(it, builder, screenId) }
        board.icons.forEach {
            builder
                .atWorkspace(it.coord.x, it.coord.y, screenId)
                .putApp(appComponentName.packageName, appComponentName.className)
        }
        board.folders.forEach {
            builder.atWorkspace(it.coord.x, it.coord.y, screenId).putFolder(null).apply {
                for (i in 0 until it.numberIconsInside) {
                    addApp(appComponentName.packageName, appComponentName.className)
                }
            }
        }
    }

    /**
     * Fills the hotseat row with apps instead of suggestions, for this to work the workspace should
     * be clean otherwise this doesn't overrides the existing icons.
     */
    fun fillHotseatIcons(builder: LauncherLayoutBuilder) {
        for (i in 0..<InvariantDeviceProfile.INSTANCE[mContext].numDatabaseHotseatIcons) {
            builder.atHotseat(i).putApp(appComponentName.packageName, appComponentName.className)
        }
    }

    companion object {
        private const val TEST_ACTIVITY_PACKAGE_PREFIX = "com.android.launcher3.tests."
    }
}
