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

package com.android.launcher3.widget

import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.MutableListenableStream
import javax.inject.Inject

/** Class to dispatch updates to bound AppWidgetProvider */
@LauncherAppSingleton
open class ProvidersUpdateDispatcher @Inject constructor() {

    private val _updates = MutableListenableStream<Update>()
    val updates = _updates.asListenable()

    internal fun dispatchUpdate(appWidgetId: Int, info: LauncherAppWidgetProviderInfo) {
        _updates.dispatchValue(Update(appWidgetId, info))
    }

    data class Update(val appWidgetId: Int, val info: LauncherAppWidgetProviderInfo)
}
