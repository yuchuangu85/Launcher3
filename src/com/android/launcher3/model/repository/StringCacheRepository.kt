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

package com.android.launcher3.model.repository

import android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED
import android.content.Context
import androidx.annotation.WorkerThread
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.model.StringCache
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.actionsFilter
import javax.inject.Inject

/** Repository for [StringCache] */
@LauncherAppSingleton
class StringCacheRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    userCache: UserCache,
    @LightweightBackground(LightweightBackgroundPriority.UI) executor: LooperExecutor,
    lifeCycle: DaggerSingletonTracker,
) {

    private val _stringCache = MutableListenableRef(StringCache.EMPTY)
    /** Cache for strings used in launcher */
    val stringCache = _stringCache.asListenable()

    init {
        // User is added or removed
        lifeCycle.addCloseable(
            userCache.userChanges.forEach(executor) {
                if (it.newUser == null || it.oldUser == null) reloadCache()
            }
        )

        // Device profile policy changes
        val dpUpdateReceiver = SimpleBroadcastReceiver(context, executor) { reloadCache() }
        dpUpdateReceiver.register(actionsFilter(ACTION_DEVICE_POLICY_RESOURCE_UPDATED)) {
            // Cache will reload after the intent is registered
            reloadCache()
        }
        lifeCycle.addCloseable(dpUpdateReceiver)
    }

    @WorkerThread
    private fun reloadCache() = _stringCache.dispatchValue(StringCache.fromContext(context))

    companion object {

        @JvmStatic
        fun getStringCache(ctx: Context) = ctx.appComponent.stringCacheRepoRepository.stringCache
    }
}
