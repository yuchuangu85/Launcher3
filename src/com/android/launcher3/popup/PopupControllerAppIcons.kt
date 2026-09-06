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

import android.content.Context
import android.os.Trace
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.ui.PopupItem
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.ShortcutUtil
import com.android.launcher3.views.ActivityContext
import java.util.stream.Collectors

/**
 * Controller for app icons. It handles actions for the popups such as showing and dismissing
 * popups. This is used for icons and shortcuts in the workspace, hotseat, and all apps.
 */
class PopupControllerForAppIcon<T> : PopupController<T> where T : Context, T : ActivityContext {

    override fun show(view: View): Popup? {
        val container: PopupContainer<T>
        val icon = view as BubbleTextView
        val activityContext: T = ActivityContext.lookupContext(icon.context) as T
        val item = icon.tag as ItemInfo
        try {
            Trace.beginSection("showPopupMenu")
            if (PopupContainer.getOpen(activityContext) != null) {
                // There is already an items container open, so don't open this one.
                icon.clearFocus()
                return null
            }
            if (!ShortcutUtil.supportsShortcuts(item)) {
                return null
            }
            val popupDataProvider = activityContext.activityComponent.popupDataProvider
            val deepShortcutCount = popupDataProvider.getShortcutCountForItem(item)
            val systemShortcuts =
                activityContext
                    .getSupportedShortcuts(item)
                    .map<SystemShortcut<T>> { s ->
                        s.getShortcut(activityContext, item, icon) as SystemShortcut<T>?
                    }
                    .filter { it != null }
                    .collect(Collectors.toList())

            container =
                PopupContainerWithArrow.create(
                    context = activityContext,
                    originalView = icon,
                    itemInfo = item,
                )
            container.deepShortcutDragHandler =
                LauncherDeepShortcutDragHandler(activityContext as Launcher, container)
            container.configureForLauncher(activityContext, item)
            if (Flags.expandableLongPressMenu()) {
                val systemShortcutRedesign =
                    systemShortcuts.map { shortcut ->
                        PopupItem(
                            iconResId = shortcut.iconResId,
                            labelResId = shortcut.labelResId,
                            popupAction = { shortcut.onClick(view) },
                            category =
                                if (shortcut.mIsCollapsible) PopupCategory.SYSTEM_SHORTCUT
                                else PopupCategory.SYSTEM_SHORTCUT_FIXED,
                        )
                    }

                container.showComposePopup(
                    if (view.showingMinimalPopup) emptyList() else systemShortcutRedesign,
                    deepShortcutCount,
                )
            } else {
                container.populateAndShowRows(
                    deepShortcutCount,
                    if (view.showingMinimalPopup) emptyList() else systemShortcuts,
                )
            }
            activityContext.refreshAndBindWidgetsForPackageUser(PackageUserKey.fromItemInfo(item))
            container.requestFocus()
        } finally {
            logEvent(activityContext.statsLogManager, item.itemType, PopupEvent.OPEN)
            Trace.endSection()
        }
        return container
    }

    override fun dismiss() {}
}
