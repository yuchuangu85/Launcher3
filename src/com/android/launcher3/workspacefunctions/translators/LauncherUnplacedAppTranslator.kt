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
package com.android.launcher3.workspacefunctions.translators

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import com.android.launcher3.R
import com.android.launcher3.appfunctions.workspace.UnplacedAppSpec
import com.android.launcher3.appfunctions.workspace.UnplacedAppTypeTranslator
import com.android.launcher3.dagger.ApplicationContext
import javax.inject.Inject

/** A translator for converting between [ItemInfo] and [UnplacedAppSpec]. */
class LauncherUnplacedAppTranslator
@Inject
constructor(@ApplicationContext private val context: Context) :
    UnplacedAppTypeTranslator<LauncherActivityInfo> {
    override fun toSpec(app: LauncherActivityInfo): UnplacedAppSpec {
        return UnplacedAppSpec(
            packageName = app.componentName.packageName,
            className = app.componentName.className,
            label = app.label.toString(),
            category = getCategoryTopic(context, app.getApplicationInfo().category),
        )
    }
}
