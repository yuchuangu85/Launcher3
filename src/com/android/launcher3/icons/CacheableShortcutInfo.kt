/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import com.android.launcher3.BuildConfig
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.cache.CachingLogic
import com.android.launcher3.icons.cache.IconLoadRequest
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.Themes

/** Wrapper over ShortcutInfo to provide extra information related to ShortcutInfo */
class CacheableShortcutInfo
@JvmOverloads
constructor(
    val shortcutInfo: ShortcutInfo,
    val appInfo: ApplicationInfoWrapper,
    val fallbackIconProvider: (BaseIconFactory) -> BitmapInfo? = { null },
) {

    @JvmOverloads
    constructor(
        info: ShortcutInfo,
        ctx: Context,
        fallbackIconProvider: (BaseIconFactory) -> BitmapInfo? = { null },
    ) : this(
        info,
        ApplicationInfoWrapper(ctx, info.getPackage(), info.userHandle),
        fallbackIconProvider,
    )

    companion object {
        private const val TAG = "CacheableShortcutInfo"

        /**
         * Similar to [LauncherApps.getShortcutIconDrawable] with additional Launcher specific
         * checks
         */
        @JvmStatic
        fun getIcon(context: Context, shortcutInfo: ShortcutInfo, density: Int): Drawable? {
            if (!BuildConfig.WIDGETS_ENABLED) {
                return null
            }
            try {
                return context
                    .getSystemService(LauncherApps::class.java)
                    .getShortcutIconDrawable(shortcutInfo, density)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get shortcut icon", e)
                return null
            }
        }

        /**
         * Converts the provided list of Shortcuts to CacheableShortcuts by using the application
         * info from the provided list of apps
         */
        @JvmStatic
        fun convertShortcutsToCacheableShortcuts(
            shortcuts: List<ShortcutInfo>,
            activities: List<LauncherActivityInfo>,
        ): List<CacheableShortcutInfo> {
            // Create a map of package to applicationInfo
            val appMap =
                activities.associateBy(
                    { PackageUserKey(it.componentName.packageName, it.user) },
                    { it.applicationInfo },
                )

            return shortcuts.map {
                CacheableShortcutInfo(
                    it,
                    ApplicationInfoWrapper(appMap[PackageUserKey(it.getPackage(), it.userHandle)]),
                )
            }
        }
    }
}

/** Caching logic for CacheableShortcutInfo. */
object CacheableShortcutCachingLogic : CachingLogic<CacheableShortcutInfo> {

    override fun getComponent(item: CacheableShortcutInfo): ComponentName =
        ShortcutKey.fromInfo(item.shortcutInfo).componentName

    override fun getUser(item: CacheableShortcutInfo): UserHandle = item.shortcutInfo.userHandle

    override fun getLabel(item: CacheableShortcutInfo): CharSequence? = item.shortcutInfo.shortLabel

    override fun getApplicationInfo(item: CacheableShortcutInfo) = item.appInfo.getInfo()

    override fun loadIcon(request: IconLoadRequest<CacheableShortcutInfo>): BitmapInfo =
        request.run {
            iconFactory.use { li ->
                CacheableShortcutInfo.getIcon(context, item.shortcutInfo, li.fullResIconDpi)?.let {
                    d ->
                    li.createBadgedIconBitmap(
                        d,
                        IconOptions()
                            .setExtractedColor(Themes.getColorAccent(context))
                            .setSourceHint(sourceHint),
                    )
                } ?: item.fallbackIconProvider.invoke(li) ?: BitmapInfo.LOW_RES_INFO
            }
        }

    override fun getFreshnessIdentifier(item: CacheableShortcutInfo, provider: IconProvider) =
        provider
            .getStateForApp(getApplicationInfo(item))
            .withAdditionalValues(
                // Manifest shortcuts get updated on every reboot. Don't include their change
                // timestamp as
                // it gets covered by the app's version
                (if (item.shortcutInfo.isDeclaredInManifest) ""
                else item.shortcutInfo.lastChangedTimestamp.toString())
            )
}
