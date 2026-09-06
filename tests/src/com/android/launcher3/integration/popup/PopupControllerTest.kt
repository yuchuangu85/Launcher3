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

package com.android.launcher3.integration.popup

import android.content.ComponentName
import android.content.Intent
import android.os.Process.myUserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.core.view.size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.DefaultPopupResizeStrategy
import com.android.launcher3.popup.Popup
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.popup.PopupController.PopupControllerFactory.createPopupController
import com.android.launcher3.popup.PopupData
import com.android.launcher3.popup.PopupDataRepository
import com.android.launcher3.popup.PopupDataSource
import com.android.launcher3.popup.UnusedShortcuts
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.widget.LauncherAppWidgetHostView
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/** Tests for the [PopupControllerForHomeScreenItems, PopupControllerForAppIcon] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PopupControllerTest {

    private val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    private val popupDataMapper = FakePopupDataMapper()
    private val popupDataRepository = PopupDataRepository(setOf(popupDataMapper))

    private val launcherDragController = launcherActivity.getFromLauncher { it.dragController }!!

    private val floatingTextViewAlpha = mock<MultiPropertyFactory<AnimatedFloat>.MultiProperty>()

    private val userHandle = myUserHandle()
    private val appPackage = "appPackage"
    private val componentName = ComponentName(appPackage, "class")
    private val appIntent =
        Intent().apply {
            component = componentName
            setPackage(appPackage)
        }

    private val appPairItemInfo =
        ItemInfo().apply {
            itemType = Favorites.ITEM_TYPE_APP_GROUP
            container = Favorites.CONTAINER_DESKTOP
            user = userHandle
        }

    private val appItemInfo =
        WorkspaceItemInfo().apply {
            itemType = Favorites.ITEM_TYPE_APPLICATION
            container = Favorites.CONTAINER_DESKTOP
            user = userHandle
            screenId = 0
            intent = appIntent
        }

    private val folderItemInfo =
        ItemInfo().apply {
            itemType = Favorites.ITEM_TYPE_FOLDER
            container = Favorites.CONTAINER_DESKTOP
            user = userHandle
        }

    private val widgetItemInfo =
        ItemInfo().apply {
            itemType = Favorites.ITEM_TYPE_APPWIDGET
            container = Favorites.CONTAINER_DESKTOP
            user = userHandle
            screenId = 0
        }

    @Test
    @EnableFlags(Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS, Flags.FLAG_MODEL_REPOSITORY)
    fun popupForAppPair_showsWithCorrectNumberOfSystemShortcuts() {
        val popupData: List<PopupData> =
            listOf(PopupDataSource.removePopupData, UnusedShortcuts.appInfoPopupData)
        val popupControllerForHomeScreenItems =
            createPopupController<Launcher>(popupDataRepository, launcherDragController)
        var popup: PopupContainer<Launcher>? = null
        launcherActivity.executeOnLauncher { l: Launcher ->
            val appPairView = spy(AppPairIcon(l))
            doReturn(floatingTextViewAlpha).whenever(appPairView).getFloatingViewTextAlpha()
            doReturn(0).whenever(appPairView).getIconHeight()
            appPairView.tag = appPairItemInfo
            popupDataMapper.addPopupData(appPairView.id, popupData)
            popup = popupControllerForHomeScreenItems.show(appPairView) as PopupContainer<Launcher>?
        }

        assert(popup?.systemShortcutContainer?.size == 2)
        assert(popup?.createPreDragCondition() != null)
        assert(hasPopupMenu())

        cleanUp()
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS, Flags.FLAG_MODEL_REPOSITORY)
    fun popupForFolder_showsWithCorrectNumberOfSystemShortcuts() {
        val popupData: List<PopupData> = listOf(PopupDataSource.removePopupData)
        val popupControllerForExtraHomeScreenItems =
            createPopupController<Launcher>(popupDataRepository, launcherDragController)
        var popup: PopupContainer<Launcher>? = null
        launcherActivity.executeOnLauncher { l: Launcher ->
            val folderIconView = spy(FolderIcon(l))
            doReturn(floatingTextViewAlpha).whenever(folderIconView).getFloatingViewTextAlpha()
            doReturn(0).whenever(folderIconView).getIconHeight()
            folderIconView.tag = folderItemInfo
            popupDataMapper.addPopupData(folderIconView.id, popupData)
            popup =
                popupControllerForExtraHomeScreenItems.show(folderIconView)
                    as PopupContainer<Launcher>
        }

        assert(popup?.systemShortcutContainer?.size == 1)
        assert(popup?.createPreDragCondition() != null)
        assert(hasPopupMenu())

        cleanUp()
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS, Flags.FLAG_MODEL_REPOSITORY)
    fun popupForWidget_showsWithCorrectNumberOfSystemShortcuts() {
        val popupData: List<PopupData> = listOf(PopupDataSource.removePopupData)
        val popupControllerForHomeScreenItems =
            createPopupController<Launcher>(popupDataRepository, launcherDragController)
        var popup: PopupContainer<Launcher>? = null
        launcherActivity.executeOnLauncher { l: Launcher ->
            val widgetView = LauncherAppWidgetHostView(l)
            widgetView.tag = widgetItemInfo
            popupDataMapper.addPopupData(widgetView.id, popupData)
            popup = popupControllerForHomeScreenItems.show(widgetView) as PopupContainer<Launcher>?
        }

        assert(popup?.systemShortcutContainer?.size == 1)
        assert(popup?.createPreDragCondition() != null)
        assert(hasPopupMenu())

        cleanUp()
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS, Flags.FLAG_MODEL_REPOSITORY)
    fun popupForAppIcon_shows_flagEnabled() {
        val popupControllerForAppIcons = createPopupController<Launcher>()
        var popup: Popup? = null
        launcherActivity.executeOnLauncher { l: Launcher ->
            val appView = BubbleTextView(l)
            appView.tag = appItemInfo
            popup = popupControllerForAppIcons.show(appView)
        }

        assert(hasPopupMenu())
        assert(popup?.createPreDragCondition() != null)

        cleanUp()
    }

    @Test
    @DisableFlags(Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS)
    fun popupForAppIcon_shows_flagDisabled() {
        var preDragCondition: DragOptions.PreDragCondition? = null
        launcherActivity.executeOnLauncher { l: Launcher ->
            val appView = BubbleTextView(l)
            appView.tag = appItemInfo
            preDragCondition = appView.startLongPressAction(l.popupControllerForAppIcons)
        }

        assert(hasPopupMenu())
        assert(preDragCondition != null)

        cleanUp()
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS, Flags.FLAG_MODEL_REPOSITORY)
    fun shouldShowWidgetResizeFrame_shouldReturnTrue() {
        launcherActivity.executeOnLauncher { l: Launcher ->
            val widgetView = LauncherAppWidgetHostView(l)
            val popupResizeStrategy = DefaultPopupResizeStrategy()
            widgetView.tag = widgetItemInfo
            assert(
                popupResizeStrategy.shouldShowResizeFrame(
                    widgetItemInfo,
                    widgetView,
                    l.getCellLayout(Favorites.CONTAINER_DESKTOP, 0),
                )
            )
        }

        cleanUp()
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS, Flags.FLAG_MODEL_REPOSITORY)
    fun shouldShowWidgetResizeFrame_shouldReturnFalse() {
        launcherActivity.executeOnLauncher { l: Launcher ->
            val appPairIconView = spy(AppPairIcon(l))
            doReturn(floatingTextViewAlpha).whenever(appPairIconView).getFloatingViewTextAlpha()
            doReturn(0).whenever(appPairIconView).getIconHeight()
            appPairIconView.tag = appPairItemInfo
            val popupResizeStrategy = DefaultPopupResizeStrategy()
            assert(
                !popupResizeStrategy.shouldShowResizeFrame(
                    appItemInfo,
                    appPairIconView,
                    l.getCellLayout(Favorites.CONTAINER_DESKTOP, 0),
                )
            )
        }

        cleanUp()
    }

    private fun hasPopupMenu(): Boolean {
        return launcherActivity.getFromLauncher {
            AbstractFloatingView.hasOpenView(it, AbstractFloatingView.TYPE_ACTION_POPUP)
        }!!
    }

    private fun cleanUp() {
        popupDataMapper.clearPopupData()
        launcherActivity.executeOnLauncher { AbstractFloatingView.closeAllOpenViews(it) }
    }
}
