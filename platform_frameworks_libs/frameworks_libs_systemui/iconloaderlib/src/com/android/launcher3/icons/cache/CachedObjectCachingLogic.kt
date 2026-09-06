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
import android.os.UserHandle
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider

/** Caching logic for ComponentWithLabelAndIcon */
object CachedObjectCachingLogic : CachingLogic<CachedObject> {

    override fun getComponent(item: CachedObject): ComponentName = item.component

    override fun getUser(item: CachedObject): UserHandle = item.user

    override fun getLabel(item: CachedObject): CharSequence? = item.label

    override fun loadIcon(request: IconLoadRequest<CachedObject>): BitmapInfo =
        request.run {
            val d = item.getFullResIcon(request) ?: return BitmapInfo.LOW_RES_INFO
            iconFactory.use { li ->
                li.createBadgedIconBitmap(
                    d,
                    IconOptions().setUser(item.user).setSourceHint(sourceHint),
                )
            }
        }

    override fun getApplicationInfo(item: CachedObject) = item.applicationInfo

    override fun getFreshnessIdentifier(item: CachedObject, iconProvider: IconProvider) =
        item.getFreshnessIdentifier(iconProvider)

    @JvmStatic
    fun loadFullResIcon(cache: BaseIconCache, obj: CachedObject) =
        obj.getFullResIcon(cache.getIconLoadRequest(obj, CachedObjectCachingLogic))
}
