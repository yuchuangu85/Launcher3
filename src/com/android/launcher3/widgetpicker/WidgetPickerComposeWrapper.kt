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

package com.android.launcher3.widgetpicker

import android.content.pm.LauncherApps.PinItemRequest
import android.os.UserHandle
import com.android.launcher3.BaseActivity
import com.android.launcher3.dragndrop.PinItemAddHandler
import com.android.launcher3.util.PackageUserKey
import javax.inject.Inject

/**
 * A wrapper for widget picker activities responsible for displaying the compose based widget picker
 * when compose is enabled via build flag.
 */
interface WidgetPickerComposeWrapper {
    /** Show the catalog of widgets available from all apps available on device. */
    fun showAllWidgets(activity: BaseActivity, widgetPickerConfig: WidgetPickerConfig)

    /** Show the catalog of widgets available for the app with [packageName] and [userHandle]. */
    fun showWidgetsFor(
        packageName: String,
        userHandle: UserHandle,
        activity: BaseActivity,
        widgetPickerConfig: WidgetPickerConfig,
    )

    /** Show the catalog of widgets / shortcuts requested in [pinItemRequest]. */
    fun showWidgetsForPinRequest(
        activity: BaseActivity,
        targetApp: PackageUserKey,
        pinItemRequest: PinItemRequest,
        widgetPickerConfig: WidgetPickerConfig,
        pinItemAddHandler: PinItemAddHandler,
    )
}

/**
 * A No-op [WidgetPickerComposeWrapper] that doesn't include widget picker in dagger graph that
 * don't involve widget picker e.g. launcher preview OR when compose is disabled via build flag.
 */
class NoOpWidgetPickerComposeWrapper @Inject constructor() : WidgetPickerComposeWrapper {
    override fun showAllWidgets(activity: BaseActivity, widgetPickerConfig: WidgetPickerConfig) {
        error("Widget picker with compose is not supported")
    }

    override fun showWidgetsFor(
        packageName: String,
        userHandle: UserHandle,
        activity: BaseActivity,
        widgetPickerConfig: WidgetPickerConfig,
    ) {
        error("Widget picker with compose is not supported")
    }

    override fun showWidgetsForPinRequest(
        activity: BaseActivity,
        targetApp: PackageUserKey,
        pinItemRequest: PinItemRequest,
        widgetPickerConfig: WidgetPickerConfig,
        pinItemAddHandler: PinItemAddHandler,
    ) {
        error("Widget picker with compose is not supported")
    }
}
