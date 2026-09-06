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

package com.android.quickstep.sysuiconnection

import android.content.Context
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.dagger.SysUIConnectionComponent
import java.util.function.Consumer
import javax.inject.Inject

/** Class to track active sysUI connection */
@LauncherAppSingleton
class SysUIConnectionTracker @Inject constructor() {

    private val _activeComponent = MutableListenableRef<SysUIConnectionComponent?>(null)

    val activeComponent = _activeComponent.asListenable()

    fun setActiveComponent(component: SysUIConnectionComponent?) {
        _activeComponent.dispatchValue(component)
    }

    /**
     * Calls the [callback] on [context]'s UI thread while the context is active and the sysUI is
     * connected
     */
    fun onConnected(context: ActivityContext, callback: Consumer<SysUIConnectionComponent>) {
        context.closeOnDestroy(
            activeComponent.forEach(context.uiExecutor) { if (it != null) callback.accept(it) }
        )
    }

    companion object {

        @JvmStatic
        fun get(ctx: Context): SysUIConnectionTracker = ctx.appComponent.sysUIConnectionTracker
    }
}
