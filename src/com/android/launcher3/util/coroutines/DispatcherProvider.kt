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

package com.android.launcher3.util.coroutines

import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.concurrent.annotations.TaskbarUi
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface DispatcherProvider {
    /**
     * Background thread pool for longer running work e.g. accessing storage, making network
     * requests, running AI tasks etc.
     */
    val ioBackground: CoroutineDispatcher

    /**
     * Background thread pool for UI related work e.g. manipulating in-memory objects for
     * presentation on the UI.
     */
    val lightweightBackground: CoroutineDispatcher

    /**
     * A coroutine dispatcher that is confined to the Main thread operating with UI objects. It
     * executes coroutines immediately when it is already in the right context without an additional
     * re-dispatch.
     *
     * See Kotlin documentation for [Dispatchers.Main.immediate] for more detailed documentation.
     */
    val main: CoroutineDispatcher

    /**
     * A coroutine dispatcher that is confined to the Taskbar Ui Thread operating with UI objects.
     * It executes coroutines immediately when it is already in the right context without an
     * additional re-dispatch.
     */
    val taskbarUi: CoroutineDispatcher

    /**
     * A coroutine dispatcher that is not confined to any specific thread.
     *
     * See Kotlin documentation for [Dispatchers.Unconfined] for more detailed documentation.
     */
    val unconfined: CoroutineDispatcher
}

@LauncherAppSingleton
class ProductionDispatchers
@Inject
constructor(
    @Background val backgroundDispatcher: CoroutineDispatcher,
    @LightweightBackground(LightweightBackgroundPriority.UI)
    val lightweightBackgroundUiDispatcher: CoroutineDispatcher,
    @Ui val uiDispatcher: CoroutineDispatcher,
    @TaskbarUi override val taskbarUi: CoroutineDispatcher,
) : DispatcherProvider {
    override val ioBackground = backgroundDispatcher

    override val lightweightBackground = lightweightBackgroundUiDispatcher

    override val main = uiDispatcher

    override val unconfined = Dispatchers.Unconfined

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getProductionDispatchers)
    }
}
