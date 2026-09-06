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

package com.android.launcher3.popup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Process
import android.util.Log
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.DropTargetHandler
import com.android.launcher3.Flags.enableHomeScreenFilesCopyPaste
import com.android.launcher3.Flags.enableHomeScreenFilesRenaming
import com.android.launcher3.LauncherConstants
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.R
import com.android.launcher3.SecondaryDropTarget
import com.android.launcher3.Utilities
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.homescreenfiles.HomeScreenFilesRenameDialogFactory
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.homescreenfiles.homeScreenFile
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.SystemShortcut.BubbleActivityStarter
import com.android.launcher3.popup.SystemShortcut.TaskbarBubbleActivityStarter
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PendingRequestArgs
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.Snackbar
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.wm.shell.shared.bubbles.logging.EntryPoint
import javax.inject.Inject

object PopupDataSource {

    // Popup data for remove shortcut.
    val removePopupData =
        PopupData(
            iconResId = R.drawable.ic_remove_no_shadow,
            labelResId = R.string.remove_system_shortcut_label,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        ) { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            AbstractFloatingView.closeAllOpenViews(activityContext)
            val dropTargetHandler: DropTargetHandler = activityContext.dropTargetHandler
            dropTargetHandler.prepareToUndoDelete(itemInfo)
            dropTargetHandler.onDeleteComplete(itemInfo, view)
        }
}

object FolderSystemShortcuts : PopupDataMapper {

    override fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>? =
        if (itemInfo.itemType == ITEM_TYPE_FOLDER) listOf(PopupDataSource.removePopupData) else null
}

object AppPairSystemShortcuts : PopupDataMapper {

    override fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>? =
        if (itemInfo.itemType == ITEM_TYPE_APP_GROUP) listOf(PopupDataSource.removePopupData)
        else null
}

object AppWidgetSystemShortcuts : PopupDataMapper {
    private const val TAG = "AppWidgetSystemShortcuts"

    // Popup data for widget settings shortcut.
    private val widgetSettingsPopupData =
        PopupData(
            iconResId = R.drawable.ic_setting,
            labelResId = R.string.widget_settings,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        ) { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            if (view is LauncherAppWidgetHostView) {
                activityContext.setWaitingForResult(
                    PendingRequestArgs.forWidgetInfo(
                        view.appWidgetId,
                        // Widget add handler is null since we're reconfiguring an existing widget.
                        /* widgetHandler= */ null,
                        itemInfo,
                    )
                )

                activityContext.appWidgetHolder?.also {
                    it.startConfigActivity(
                        ActivityContext.lookupContext(view.context),
                        view.appWidgetId,
                        LauncherConstants.ActivityCodes.REQUEST_RECONFIGURE_APPWIDGET,
                    )
                } ?: Log.e(TAG, "appWidgetHolder is null, cannot start config activity.")
            }
        }

    override fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>? {
        return if (itemInfo.itemType != ITEM_TYPE_APPWIDGET) null
        else if (itemInfo is LauncherAppWidgetInfo && itemInfo.isReconfigurable) {
            listOf(PopupDataSource.removePopupData, widgetSettingsPopupData)
        } else {
            listOf(PopupDataSource.removePopupData)
        }
    }
}

class FileSystemShortcuts
@Inject
constructor(private val renameDialogFactory: HomeScreenFilesRenameDialogFactory) : PopupDataMapper {

    private val openHomeScreenFile =
        PopupData(
            iconResId = R.drawable.ic_home_screen_files_context_menu_open_in_app,
            labelResId = R.string.home_screen_files_context_menu_open_in_app_label,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
            eventId = LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_OPEN_VIA_CONTEXT_MENU,
        ) { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            activityContext.startActivitySafely(view, itemInfo.intent, itemInfo)
        }

    private val copyFileSystemItem =
        PopupData(
            iconResId = R.drawable.ic_home_screen_files_context_menu_copy,
            labelResId = R.string.home_screen_files_context_menu_copy_label,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
            eventId = LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_COPY_VIA_CONTEXT_MENU,
        ) { activityContext: ActivityContext, itemInfo: ItemInfo, _: View ->
            val file = itemInfo.homeScreenFile ?: return@PopupData
            activityContext
                .asContext()
                .getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(
                    ClipData(/* label= */ "", arrayOf(file.mimeType), ClipData.Item(file.uri))
                )
        }

    private val renameFileSystemItem =
        PopupData(
            iconResId = R.drawable.ic_home_screen_files_context_menu_rename,
            labelResId = R.string.home_screen_files_context_menu_rename_label,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
            eventId = LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_RENAME_VIA_CONTEXT_MENU,
        ) { activityContext: ActivityContext, itemInfo: ItemInfo, _: View ->
            val file = itemInfo.homeScreenFile ?: return@PopupData
            renameDialogFactory.create(activityContext, file).show()
        }

    private val deleteFileSystemItem =
        PopupData(
            iconResId = R.drawable.ic_home_screen_files_context_menu_move_to_trash,
            labelResId =
                if (HomeScreenFilesUtils.isTrashingEnabled())
                    R.string.home_screen_files_context_menu_move_to_trash_label
                else R.string.home_screen_files_context_menu_delete_permanently_label,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
            eventId = LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_DELETE_VIA_CONTEXT_MENU,
            popupAction = PopupDataSource.removePopupData.popupAction,
        )

    override fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>? {
        return if (
            itemInfo.itemType != ITEM_TYPE_FILE_SYSTEM_FILE &&
                itemInfo.itemType != ITEM_TYPE_FILE_SYSTEM_FOLDER
        )
            null
        else
            buildList {
                add(openHomeScreenFile)
                if (enableHomeScreenFilesCopyPaste()) {
                    add(copyFileSystemItem)
                }
                if (enableHomeScreenFilesRenaming()) {
                    add(renameFileSystemItem)
                }
                add(deleteFileSystemItem)
            }
    }
}

object CustomWidgetSystemShortcuts : PopupDataMapper {

    override fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>? =
        if (itemInfo.itemType == ITEM_TYPE_CUSTOM_APPWIDGET) listOf(PopupDataSource.removePopupData)
        else null
}

object UnusedShortcuts {

    private val handleAddToHomeScreenFromAllApps =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            AbstractFloatingView.closeAllOpenViews(activityContext)
            val launcherAccessibilityDelegate =
                activityContext.accessibilityDelegate as LauncherAccessibilityDelegate
            launcherAccessibilityDelegate.addToWorkspace(itemInfo, /* accessibility= */ false)
            /*finishCallback=*/ {
                activityContext.statsLogManager
                    .logger()
                    .withItemInfo(itemInfo)
                    .log(LauncherEvent.LAUNCHER_TAP_TO_ADD_TO_HOME_SCREEN_FROM_ALL_APPS)
            }
            Unit
        }

    // Popup data for add to home screen from all apps shortcut.
    val addToHomeScreenFromAllAppsPopupData =
        PopupData(
            iconResId = R.drawable.ic_plus,
            labelResId = R.string.action_add_to_workspace,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
            popupAction = handleAddToHomeScreenFromAllApps,
        )

    // Handle action from tapping app info shortcut.
    private val handleAppInfo =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            val sourceBounds = Utilities.getViewBounds(view)
            val options: ActivityOptionsWrapper =
                activityContext.getActivityLaunchOptions(view, itemInfo)

            // Dismiss the taskMenu when the app launch animation is complete
            options.onEndCallback.add { dismissTaskMenuView(activityContext) }
            PackageManagerHelper.startDetailsActivityForInfo(
                view.context,
                itemInfo,
                sourceBounds,
                options.toBundle(),
            )
        }

    // Popup data for app info shortcut.
    val appInfoPopupData =
        PopupData(
            iconResId = R.drawable.info_24px,
            labelResId = R.string.app_info_drop_target_label,
            category = PopupCategory.SYSTEM_SHORTCUT,
            eventId = LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP,
            popupAction = handleAppInfo,
        )

    // Handle action from tapping private profile install shortcut.
    private val handlePrivateProfileInstall =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            val privateProfileManager: PrivateProfileManager =
                activityContext.appsView.privateProfileManager
            val intent =
                ApiWrapper.INSTANCE[view.context].getAppMarketActivityIntent(
                    itemInfo.targetComponent?.packageName,
                    privateProfileManager.profileUser,
                )
            activityContext.startActivitySafely(view, intent, itemInfo)
            AbstractFloatingView.closeAllOpenViews(activityContext)
        }

    // Popup data for private profile install shortcut.
    val privateProfileInstallPopupData =
        PopupData(
            iconResId = R.drawable.ic_remove_no_shadow,
            labelResId = R.string.remove_drop_target_label,
            category = PopupCategory.SYSTEM_SHORTCUT,
            eventId = LauncherEvent.LAUNCHER_PRIVATE_SPACE_INSTALL_SYSTEM_SHORTCUT_TAP,
            popupAction = handlePrivateProfileInstall,
        )

    // Handles action from tapping install shortcut.
    private val handleInstall =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            val intent =
                ApiWrapper.INSTANCE[view.context].getAppMarketActivityIntent(
                    itemInfo.targetComponent?.packageName,
                    Process.myUserHandle(),
                )
            activityContext.startActivitySafely(view, intent, itemInfo)
            AbstractFloatingView.closeAllOpenViews(activityContext)
        }

    // Popup data for install shortcut.
    val installPopupData =
        PopupData(
            iconResId = R.drawable.ic_install_no_shadow,
            labelResId = R.string.install_drop_target_label,
            category = PopupCategory.SYSTEM_SHORTCUT,
            popupAction = handleInstall,
        )

    // Handles action from tapping "don't suggest app" shortcut.
    private val handleDontSuggestApp =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            dismissTaskMenuView(activityContext)
            Snackbar.show(
                activityContext,
                view.context.getString(R.string.item_removed),
                R.string.undo,
                {},
                {
                    activityContext.statsLogManager
                        .logger()
                        .withItemInfo(itemInfo)
                        .log(LauncherEvent.LAUNCHER_DISMISS_PREDICTION_UNDO)
                },
            )
        }

    // Popup data the "don't suggest app" shortcut.
    val dontSuggestAppPopupData =
        PopupData(
            iconResId = R.drawable.ic_block_no_shadow,
            labelResId = R.string.dismiss_prediction_label,
            category = PopupCategory.SYSTEM_SHORTCUT,
            eventId = LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP,
            popupAction = handleDontSuggestApp,
        )

    // Handles action when tapping uninstall app shortcut.
    private val handleUninstallApp =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            dismissTaskMenuView(activityContext)
            val componentName = SecondaryDropTarget.getUninstallTarget(view.context, itemInfo)
            SecondaryDropTarget.performUninstall(view.context, componentName, itemInfo)
            Unit
        }

    // Popup data for uninstall app shortcut.
    val uninstallAppPopupData =
        PopupData(
            iconResId = R.drawable.ic_uninstall_no_shadow,
            labelResId = R.string.uninstall_private_system_shortcut_label,
            category = PopupCategory.SYSTEM_SHORTCUT,
            eventId = LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNINSTALL_SYSTEM_SHORTCUT_TAP,
            popupAction = handleUninstallApp,
        )

    // Handles action when tapping bubble shortcut.
    private val handleBubbleShortcut =
        { activityContext: ActivityContext, itemInfo: ItemInfo, _: View ->
            val starter: BubbleActivityStarter = activityContext as BubbleActivityStarter

            dismissTaskMenuView(activityContext)
            showBubbleShortcut(starter, itemInfo)
        }

    private fun showBubbleShortcut(starter: BubbleActivityStarter, itemInfo: ItemInfo) {
        fun ItemInfo.getEntryPoint() =
            when {
                isInAllApps -> EntryPoint.ALL_APPS_ICON_MENU
                isInHotseat ->
                    if (starter is TaskbarBubbleActivityStarter) {
                        EntryPoint.TASKBAR_ICON_MENU
                    } else {
                        EntryPoint.HOTSEAT_ICON_MENU
                    }
                else -> EntryPoint.LAUNCHER_ICON_MENU
            }

        // TODO: handle GroupTask (single) items so that recent items in taskbar work
        if (itemInfo is WorkspaceItemInfo) {
            val shortcutInfo = itemInfo.deepShortcutInfo
            if (shortcutInfo != null) {
                starter.showShortcutBubble(shortcutInfo, itemInfo.getEntryPoint())
                return
            }
        }

        // If we're here check for an intent
        if (itemInfo.intent != null) {
            val intent = Intent(itemInfo.intent)
            if (intent.getPackage() == null) {
                intent.setPackage(itemInfo.getTargetPackage())
            }
            starter.showAppBubble(intent, itemInfo.user, itemInfo.getEntryPoint())
        } else {
            Log.w(TAG, "unable to bubble, no intent: $itemInfo")
        }
    }

    // Popup data for bubble shortcut
    val bubblePopupData =
        PopupData(
            iconResId = R.drawable.ic_bubble_button,
            labelResId = R.string.bubble,
            category = PopupCategory.SYSTEM_SHORTCUT,
            popupAction = handleBubbleShortcut,
        )

    private fun dismissTaskMenuView(activityContext: ActivityContext) {
        AbstractFloatingViewHelper.closeOpenViews(
            activityContext,
            true,
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv(),
        )
    }

    private const val TAG = "FileSystemShortcuts"
}
