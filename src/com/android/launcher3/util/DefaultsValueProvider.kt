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

package com.android.launcher3.util

import android.content.Context
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import javax.inject.Inject

/**
 * Interface for providing default values for preferences and other configuration settings. This
 * allows different launcher builds (e.g., AOSP vs. Nexus) to specify their own default values.
 */
@LauncherAppSingleton
interface DefaultsValueProvider {
    /** Returns the default value for the two-line toggle preference. */
    val enableTwoLineToggle: Boolean

    /** Returns the default value for adding icons to the home screen upon installation. */
    val addIconToHome: Boolean

    companion object {
        @JvmStatic
        fun get(context: Context): DefaultsValueProvider =
            context.appComponent.defaultsValueProvider
    }
}

/** Default implementation of {@link DefaultsValueProvider}. */
@LauncherAppSingleton
class BaseDefaultsValueProvider @Inject constructor() : DefaultsValueProvider {
    override val enableTwoLineToggle: Boolean = false

    override val addIconToHome: Boolean = true
}
