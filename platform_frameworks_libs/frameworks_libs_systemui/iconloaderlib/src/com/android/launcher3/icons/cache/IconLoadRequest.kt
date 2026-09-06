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

package com.android.launcher3.icons.cache

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.graphics.drawable.Drawable
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.SourceHint
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.util.ComponentKey

/** Class to encapsulate various icon loading arguments and some utility methods */
class IconLoadRequest<T>(
    val context: Context,
    val item: T,
    private val logic: CachingLogic<T>,
    private val cache: BaseIconCache,
    @JvmField val iconDpi: Int,
    private val lookupFlag: CacheLookupFlag = DEFAULT_LOOKUP_FLAG,
) {

    private val iconProvider: IconProvider
        get() = cache.iconProvider

    val iconFactory: BaseIconFactory
        get() = cache.iconFactory

    val sourceHint: SourceHint
        get() =
            SourceHint(
                key = ComponentKey(logic.getComponent(item), logic.getUser(item)),
                logic = logic,
                freshnessId = logic.getFreshnessIdentifier(item, iconProvider)?.toString(),
                lookupFlag = lookupFlag,
            )

    /** Loads the icon for the provided component info */
    fun getIcon(info: ComponentInfo): Drawable? = iconProvider.getIcon(info, iconDpi)

    /** Loads the icon for the provided application info */
    fun getIcon(info: ApplicationInfo): Drawable? = iconProvider.getIcon(info, iconDpi)

    /** Returns true if the provided icon is the system default icon */
    fun isDefaultApplicationIcon(icon: Drawable?) =
        icon == null || context.packageManager.isDefaultApplicationIcon(icon)

    /** Returns the default icon for the associated user */
    fun getDefaultIcon(): BitmapInfo = cache.getDefaultIcon(logic.getUser(item))

    /** Executes the requests and returns the [BitmapInfo] */
    fun evaluate(): BitmapInfo = logic.loadIcon(this)
}
