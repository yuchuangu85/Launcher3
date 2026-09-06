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

package com.android.launcher3.graphics

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.WorkerThread
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.Utilities
import com.android.launcher3.dragndrop.FolderAdaptiveIcon
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.CacheableShortcutInfo.Companion.getIcon
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.LauncherIconProvider
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.icons.cache.CacheLookupFlag
import com.android.launcher3.icons.cache.CachedObjectCachingLogic.loadFullResIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.FlagOp
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.PendingAddShortcutInfo
import javax.inject.Inject

/** Class to load full drawable for an ItemInfo */
class IconLoader
@Inject
constructor(
    private val activity: ActivityContext,
    private val iconCache: IconCache,
    private val themeManager: ThemeManager,
    private val userCache: UserCache,
    private val idp: InvariantDeviceProfile,
    private val iconProvider: LauncherIconProvider,
) {

    private val context: Context = activity.asContext()

    /**
     * Returns the full drawable for info as multiple layers of AdaptiveIconDrawable. The second
     * drawable in the Pair is the badge used with the icon.
     *
     * @param useTheme If true, will theme icons when applicable
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    @WorkerThread
    fun getFullDrawable(info: ItemInfo, width: Int, height: Int, useTheme: Boolean): Result? {
        var mainIcon: Drawable? = null

        var badge: Drawable? = null
        if ((info is ItemInfoWithIcon) && !info.matchingLookupFlag.useLowRes()) {
            badge = info.bitmap.getBadgeDrawable(context, useTheme)
        }

        if (info is PendingAddShortcutInfo) {
            mainIcon = loadFullResIcon(iconCache, info.getActivityInfo(context))
        } else if (info.itemType == Favorites.ITEM_TYPE_APPLICATION) {
            val activityInfo =
                context
                    .getSystemService(LauncherApps::class.java)
                    ?.resolveActivity(info.intent, info.user) ?: return null
            mainIcon =
                if (
                    info is ItemInfoWithIcon && info.container == Favorites.CONTAINER_PRIVATESPACE
                ) {
                    info.bitmap.getBadgeDrawable(context, useTheme)
                } else {
                    iconProvider.getIcon(activityInfo.activityInfo, idp.fillResIconDpi)
                }
        } else if (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            val siList: List<ShortcutInfo> =
                ShortcutKey.fromItemInfo(info).buildRequest(context).query(ShortcutRequest.ALL)
            if (siList.isEmpty()) {
                return null
            } else {
                val si = siList[0]
                mainIcon = getIcon(context, si, idp.fillResIconDpi)
                // Only fetch badge if the icon is on workspace
                if (info.id != ItemInfo.NO_ID && badge == null) {
                    val badgeInfo =
                        iconCache.getShortcutInfoBadge(
                            si,
                            CacheLookupFlag.DEFAULT_LOOKUP_FLAG.withThemeIcon(useTheme),
                        )
                    val shape = themeManager.iconShapeData.value

                    val flags = if (themeManager.isIconThemeEnabled) BitmapInfo.FLAG_THEMED else 0
                    badge = badgeInfo.newIcon(context, flags, shape)
                }
            }
        } else if (info.itemType == Favorites.ITEM_TYPE_FOLDER) {
            val icon =
                FolderAdaptiveIcon.createFolderAdaptiveIcon(activity, info.id, Point(width, height))
                    ?: return null
            mainIcon = icon
            badge = icon.badge
        }

        if (mainIcon == null) {
            return null
        }
        var result: AdaptiveIconDrawable =
            if (mainIcon is AdaptiveIconDrawable) {
                mainIcon
            } else {
                // Wrap the main icon in AID
                LauncherIcons.obtain(context).use { li -> li.wrapToAdaptiveIcon(mainIcon) }
            }

        // Inject theme icon drawable
        if (Utilities.ATLEAST_T && useTheme) {
            val themeController = themeManager.themeController
            if (themeController != null) {
                result =
                    themeController.createThemedAdaptiveIcon(
                        context,
                        result,
                        if (info is ItemInfoWithIcon) info.bitmap else null,
                    ) ?: return null
            }
        }

        if (badge == null) {
            badge =
                BitmapInfo.LOW_RES_INFO.withFlags(
                        userCache.getUserInfo(info.user).applyBitmapInfoFlags(FlagOp.NO_OP)
                    )
                    .getBadgeDrawable(context, useTheme) ?: ColorDrawable(Color.TRANSPARENT)
        }
        return Result(result, badge)
    }

    class Result(@JvmField val icon: AdaptiveIconDrawable, @JvmField val badge: Drawable)
}
