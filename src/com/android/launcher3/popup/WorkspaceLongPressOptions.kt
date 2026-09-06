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

package com.android.launcher3.popup

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import com.android.launcher3.BuildConfig
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUpdate.Extras.Companion.builder
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALL_APPS_TAP_OR_LONGPRESS
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_CREATE_NEW_FOLDER_BUTTON_TAP_OR_LONGPRESS
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemCoordinates
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.organizer.creation.screen.ui.OrganizerActivity
import com.android.launcher3.organizer.creation.screen.ui.foldercreator.FolderCreatorActivity
import com.android.launcher3.popup.PopupCategory.SYSTEM_SHORTCUT
import com.android.launcher3.testing.TestLogging
import com.android.launcher3.testing.shared.TestProtocol
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView.OptionItem

/** Class to create default set of long-press options. */
object WorkspaceLongPressOptions {

    @JvmStatic
    fun getAll(ctx: Context): List<PopupData> = buildList {
        add(
            PopupData(
                R.drawable.ic_palette,
                R.string.styles_wallpaper_button_text,
                SYSTEM_SHORTCUT,
                IGNORE,
            ) { ac, _, v ->
                startWallpaperPicker(ac, v)
            }
        )
        if (BuildConfig.WIDGETS_ENABLED) {
            add(
                PopupData(
                    R.drawable.ic_widget,
                    R.string.widget_button_text,
                    SYSTEM_SHORTCUT,
                    LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS,
                ) { ac, _, _ ->
                    openWidgetPicker(ac.asContext())
                }
            )
        }
        if (FeatureFlags.MULTI_SELECT_EDIT_MODE.get()) {
            add(
                PopupData(
                    R.drawable.enter_home_gardening_icon,
                    R.string.edit_home_screen,
                    SYSTEM_SHORTCUT,
                    LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
                ) { ac, _, _ ->
                    (ac as? Launcher)?.stateManager?.goToState(LauncherState.EDIT_MODE)
                }
            )
        }

        add(
            PopupData(
                R.drawable.ic_apps,
                R.string.all_apps_button_label,
                SYSTEM_SHORTCUT,
                LAUNCHER_ALL_APPS_TAP_OR_LONGPRESS,
            ) { ac, _, _ ->
                (ac as? Launcher)?.apply {
                    activityComponent.keyboardStateManager.launchedFromA11y = true
                    stateManager.goToState(LauncherState.ALL_APPS)
                }
            }
        )

        if (Flags.condoPlanner()) {
            add(
                PopupData(
                    R.drawable.ic_create_new_folder,
                    R.string.settings_folder_creation,
                    SYSTEM_SHORTCUT,
                    LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
                ) { ac, _, _ ->
                    ac.asContext()
                        .startActivity(
                            Intent(ac.asContext(), FolderCreatorActivity::class.java)
                                .setPackage(ac.asContext().packageName)
                        )
                }
            )
            add(
                PopupData(
                    R.drawable.condo_planner_icon,
                    R.string.settings_home_organizer,
                    SYSTEM_SHORTCUT,
                    LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
                ) { ac, _, _ ->
                    ac.asContext()
                        .startActivity(
                            Intent(ac.asContext(), OrganizerActivity::class.java)
                                .setPackage(ac.asContext().packageName)
                                .putExtra(
                                    OrganizerActivity.EXTRA_MODE,
                                    OrganizerActivity.MODE_WORKSPACE,
                                )
                        )
                }
            )
        }

        add(
            PopupData(
                R.drawable.ic_setting,
                R.string.settings_button_text,
                SYSTEM_SHORTCUT,
                LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
            ) { ac, _, _ ->
                TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: startSettings")
                ac.asContext()
                    .startActivity(
                        Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                            .setPackage(ac.asContext().packageName)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                    )
            }
        )

        if (HomeScreenFilesProvider.INSTANCE[ctx].canCreateNewFolder()) {
            add(
                PopupData(
                    R.drawable.ic_create_new_folder,
                    R.string.create_new_folder_button_text,
                    SYSTEM_SHORTCUT,
                    LAUNCHER_CREATE_NEW_FOLDER_BUTTON_TAP_OR_LONGPRESS,
                ) { ac, _, _ ->
                    createNewFolder(ac)
                }
            )
        }
    }

    @JvmStatic
    fun getAllAsOptionItems(context: Context): List<OptionItem> =
        getAll(context).map {
            OptionItem(context, it.labelResId, it.iconResId, it.eventId) { v ->
                it.popupAction.invoke(ActivityContext.lookupContext(v.context), ItemInfo(), v)
                true
            }
        }

    /**
     * Event handler for the wallpaper picker button that appears after a long press on the home
     * screen.
     */
    private fun startWallpaperPicker(ac: ActivityContext, v: View) {
        val launcher = ac as? Launcher ?: return
        if (!Utilities.isWallpaperAllowed(launcher)) {
            val message =
                if (launcher.stringCache != null) launcher.stringCache!!.disabledByAdminMessage
                else launcher.getString(R.string.msg_disabled_by_admin)
            Toast.makeText(launcher, message, Toast.LENGTH_SHORT).show()
            return
        }
        val intent =
            Intent(Intent.ACTION_SET_WALLPAPER)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_WALLPAPER_OFFSET, launcher.workspace.wallpaperOffsetForCenterPage)
                .putExtra(EXTRA_WALLPAPER_LAUNCH_SOURCE, "app_launched_launcher")
                .putExtra(EXTRA_WALLPAPER_FLAVOR, "focus_wallpaper")
        val pickerPackage = launcher.getString(R.string.wallpaper_picker_package)
        if (!TextUtils.isEmpty(pickerPackage)) {
            intent.setPackage(pickerPackage)
        }
        launcher.startActivitySafely(
            v,
            intent,
            WorkspaceItemInfo().also {
                it.intent = intent
                it.container = Favorites.CONTAINER_SETTINGS
            },
        )
    }

    private fun createNewFolder(ac: ActivityContext) {
        val launcher = ac as? Launcher ?: return
        val workspace = launcher.workspace
        val currentScreenId = workspace.getScreenIdForPageIndex(workspace.currentPage)

        // NOTE: This causes the launcher to auto-scroll to the new file system folder once created
        // provided that no further touch interaction occurs during the async operation.
        launcher.resetLastTouchUpTime()

        HomeScreenFilesProvider.INSTANCE[launcher].createNewFolder(
                builder()
                    .findSpaceStartingFrom(
                        WorkspaceItemCoordinates(currentScreenId, /* cellX= */ 0, /* cellY= */ 0)
                    )
                    .build()
            )
            .whenComplete { result: Boolean?, throwable: Throwable? ->
                if (throwable != null || result != true) {
                    launcher.runOnUiThread {
                        Toast.makeText(launcher, R.string.something_went_wrong, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
    }

    /** Opens the widget picker UI. Returns true if opened. */
    @JvmStatic
    fun openWidgetPicker(ctx: Context): Boolean {
        if (ctx.packageManager.isSafeMode) {
            Toast.makeText(ctx, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show()
            return false
        } else {
            val intent = Intent(Intent.ACTION_PICK)
            intent.setPackage(ctx.packageName)
            if (ctx !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return true
        }
    }

    // An intent extra to indicate the horizontal scroll of the wallpaper.
    private const val EXTRA_WALLPAPER_OFFSET = "com.android.launcher3.WALLPAPER_OFFSET"
    private const val EXTRA_WALLPAPER_FLAVOR = "com.android.launcher3.WALLPAPER_FLAVOR"
    // An intent extra to indicate the launch source by launcher.
    private const val EXTRA_WALLPAPER_LAUNCH_SOURCE = "com.android.wallpaper.LAUNCH_SOURCE"
}
