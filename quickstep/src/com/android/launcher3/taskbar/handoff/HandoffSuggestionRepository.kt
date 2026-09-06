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

package com.android.launcher3.taskbar.handoff

import android.companion.Flags.taskContinuity
import android.companion.datatransfer.continuity.RemoteTask
import android.companion.datatransfer.continuity.TaskContinuityManager
import android.content.Context
import android.os.Process
import androidx.annotation.VisibleForTesting
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.LauncherIcons.IconPool
import com.android.launcher3.model.repository.AppsListRepository
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableRef
import javax.inject.Inject

/** Stores the latest [HandoffSuggestion] to display in the taskbar, if one is available. */
@LauncherAppSingleton
class HandoffSuggestionRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appsListRepository: AppsListRepository,
    @LightweightBackground(priority = UI) private val executor: LooperExecutor,
    lifecycle: DaggerSingletonTracker,
    private val iconPool: IconPool,
) {

    private val _suggestion = MutableListenableRef<HandoffSuggestion?>(null)

    @VisibleForTesting
    val listener =
        TaskContinuityManager.RemoteTaskListener {
            _suggestion.dispatchValue(
                it.filter { remoteTask -> remoteTask.isTaskInForeground }
                    .maxByOrNull { remoteTask -> remoteTask.lastUsedTimestampMillis }
                    ?.let { remoteTask -> getHandoffSuggestion(remoteTask) }
            )
        }
    private val handoffSuggestionBadge: BitmapInfo by lazy {
        iconPool.obtain().use {
            it.createBadgedIconBitmap(
                context.getDrawable(R.drawable.ic_handoff_suggestion_badge),
                IconOptions().setUser(Process.myUserHandle()),
            )
        }
    }

    val suggestion: ListenableRef<HandoffSuggestion?> = _suggestion.asListenable()

    init {
        if (taskContinuity()) {
            context.getSystemService(TaskContinuityManager::class.java)?.let {
                it.registerRemoteTaskListener(executor, listener)
                lifecycle.addCloseable { it.unregisterRemoteTaskListener(listener) }
            }
        }
    }

    private fun getHandoffSuggestion(remoteTask: RemoteTask): HandoffSuggestion? {
        val packageName = remoteTask.packageName ?: return null
        val appInfo =
            appsListRepository.appsListStateRef.value.apps.firstOrNull {
                it.targetPackage == packageName
            } ?: return null

        val suggestionIconInfo = appInfo.clone()
        suggestionIconInfo.bitmap = appInfo.bitmap.copy(badgeInfo = handoffSuggestionBadge)

        return HandoffSuggestion(
            remoteTask.companionDeviceAssociationId,
            remoteTask.taskId,
            suggestionIconInfo,
        )
    }

    companion object {
        /** Returns the [HandoffSuggestionRepository] for the given [Context]. */
        @JvmStatic
        fun get(context: Context): HandoffSuggestionRepository =
            context.appComponent.handoffSuggestionRepository
    }
}
