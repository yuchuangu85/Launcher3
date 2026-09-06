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

package com.android.launcher3.icons.cache

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.cache.BaseIconCache.Companion.EMPTY_CLASS_NAME

/** Caching logic for ApplicationInfo */
class AppInfoCachingLogic(
    private val pm: PackageManager,
    private val instantAppResolver: (ApplicationInfo) -> Boolean,
    private val errorLogger: (String, Exception?) -> Unit = { _, _ -> },
) : CachingLogic<ApplicationInfo> {

    override fun getComponent(item: ApplicationInfo) =
        ComponentName(item.packageName, item.packageName + EMPTY_CLASS_NAME)

    override fun getUser(item: ApplicationInfo) = UserHandle.getUserHandleForUid(item.uid)

    override fun getLabel(item: ApplicationInfo) = item.loadLabel(pm)

    override fun getApplicationInfo(item: ApplicationInfo) = item

    override fun loadIcon(request: IconLoadRequest<ApplicationInfo>): BitmapInfo =
        request.run {
            // Load the full res icon for the application, but if useLowResIcon is set, then
            // only keep the low resolution icon instead of the larger full-sized icon
            val appIcon = getIcon(item)
            if (isDefaultApplicationIcon(appIcon)) {
                errorLogger.invoke(
                    String.format("Default icon returned for %s", item.packageName),
                    null,
                )
                getDefaultIcon()
            } else {
                iconFactory.use { li ->
                    li.createBadgedIconBitmap(
                        appIcon,
                        IconOptions()
                            .setUser(getUser(item))
                            .setInstantApp(instantAppResolver.invoke(item))
                            .setSourceHint(sourceHint),
                    )
                }
            }
        }

    override fun getFreshnessIdentifier(item: ApplicationInfo, iconProvider: IconProvider) =
        iconProvider.getStateForApp(item)
}
