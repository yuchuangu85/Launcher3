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

package com.android.launcher3.icons.cache

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.os.Build
import android.os.Build.VERSION
import android.os.UserHandle
import android.util.Log
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider

object LauncherActivityCachingLogic : CachingLogic<LauncherActivityInfo> {
    const val TAG = "LauncherActivityCachingLogic"

    override fun getComponent(info: LauncherActivityInfo): ComponentName = info.componentName

    override fun getUser(info: LauncherActivityInfo): UserHandle = info.user

    override fun getLabel(info: LauncherActivityInfo): CharSequence? = info.label

    override fun getApplicationInfo(info: LauncherActivityInfo) = info.applicationInfo

    override fun loadIcon(request: IconLoadRequest<LauncherActivityInfo>): BitmapInfo =
        request.run {
            val iconDrawable = getIcon(item.activityInfo)
            if (isDefaultApplicationIcon(iconDrawable)) {
                Log.w(
                    TAG,
                    "loadIcon: Default app icon returned from PackageManager." +
                        " component=${item.componentName}, user=${item.user}",
                    Exception(),
                )
                // Make sure this default icon always matches BaseIconCache#getDefaultIcon
                return getDefaultIcon()
            }
            iconFactory.use { li ->
                val iconOptions: IconOptions =
                    IconOptions()
                        .setUser(item.user)
                        .assumeFullBleedIcon(
                            VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                                && item.activityInfo.isArchived
                        )
                        .setSourceHint(sourceHint)
                li.createBadgedIconBitmap(iconDrawable, iconOptions)
            }
        }

    override fun getFreshnessIdentifier(item: LauncherActivityInfo, provider: IconProvider) =
        provider.getStateForApp(getApplicationInfo(item))
}
