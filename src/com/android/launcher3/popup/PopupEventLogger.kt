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

import com.android.launcher3.LauncherSettings
import com.android.launcher3.logging.StatsLogManager

enum class PopupEvent {
    OPEN,
    CLOSE,
}

fun logEvent(statsLogManager: StatsLogManager, itemType: Int, event: PopupEvent) {
    when (event) {
        PopupEvent.OPEN ->
            when (itemType) {
                LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP ->
                    statsLogManager
                        .logger()
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_OPEN_APP_PAIR_LONG_PRESS_MENU)
                LauncherSettings.Favorites.ITEM_TYPE_FOLDER ->
                    statsLogManager
                        .logger()
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_OPEN_FOLDER_LONG_PRESS_MENU)
                LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET ->
                    statsLogManager
                        .logger()
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_OPEN_WIDGET_LONG_PRESS_MENU)
                LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ->
                    statsLogManager
                        .logger()
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_OPEN_APP_LONG_PRESS_MENU)
                LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT ->
                    statsLogManager
                        .logger()
                        .log(
                            StatsLogManager.LauncherEvent.LAUNCHER_OPEN_APP_SHORTCUT_LONG_PRESS_MENU
                        )
            }
        PopupEvent.CLOSE ->
            when (itemType) {
                LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP ->
                    statsLogManager
                        .logger()
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_CLOSE_APP_PAIR_LONG_PRESS_MENU)
                LauncherSettings.Favorites.ITEM_TYPE_FOLDER ->
                    statsLogManager
                        .logger()
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_CLOSE_FOLDER_LONG_PRESS_MENU)
                LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET ->
                    statsLogManager
                        .logger()
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_CLOSE_WIDGET_LONG_PRESS_MENU)
                LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ->
                    statsLogManager
                        .logger()
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_CLOSE_APP_LONG_PRESS_MENU)
                LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT ->
                    statsLogManager
                        .logger()
                        .log(
                            StatsLogManager.LauncherEvent
                                .LAUNCHER_CLOSE_APP_SHORTCUT_LONG_PRESS_MENU
                        )
            }
    }
}
