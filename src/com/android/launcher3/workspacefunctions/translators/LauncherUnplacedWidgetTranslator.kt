/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.android.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.workspacefunctions.translators

import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetSpec
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetTypeTranslator
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import javax.inject.Inject

/** A translator for converting between [LauncherAppWidgetProviderInfo] and [UnplacedWidgetSpec]. */
class LauncherUnplacedWidgetTranslator
@Inject
constructor(@ApplicationContext private val context: Context) :
    UnplacedWidgetTypeTranslator<LauncherAppWidgetProviderInfo> {
    override fun toSpec(info: LauncherAppWidgetProviderInfo): UnplacedWidgetSpec {
        return UnplacedWidgetSpec(
            packageName = info.provider.packageName,
            className = info.provider.className,
            spanX = info.spanX,
            spanY = info.spanY,
            label = info.getLabel().toString(),
            description = info.loadDescription(context)?.toString(),
            category =
                getCategoryTopic(
                    context,
                    info.getApplicationInfo()?.category ?: ApplicationInfo.CATEGORY_UNDEFINED,
                ),
        )
    }
}
