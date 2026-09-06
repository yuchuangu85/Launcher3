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
package com.android.quickstep

import android.annotation.UserIdInt
import android.content.Context
import android.os.UserHandle
import android.util.Log
import android.view.RemoteAnimationTarget
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors
import com.android.launcher3.util.TraceHelper
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.systemui.shared.system.ActivityManagerWrapper

/** Contains helpful methods for retrieving data from [Task]s. */
object TaskUtils {
    private const val TAG = "TaskUtils"

    fun getTitle(context: Context, task: Task): CharSequence =
        TraceHelper.allowIpcs("TaskUtils.getTitle") {
            getTitle(context, task.key.userId, task.topComponent.packageName)
        }

    fun getTitle(context: Context, @UserIdInt userId: Int?, packageName: String?): CharSequence {
        if (userId == null) {
            Log.e(TAG, "Failed to get title; missing userId")
            return ""
        }
        if (packageName == null) {
            Log.e(TAG, "Failed to get title; missing packageName")
            return ""
        }
        val user = UserHandle.of(userId)
        val applicationInfo = ApplicationInfoWrapper(context, packageName, user).getInfo()
        if (applicationInfo == null) {
            Log.e(TAG, "Failed to get title for userId=$userId, packageName=$packageName")
            return ""
        }
        val packageManager = context.packageManager
        return packageManager.getUserBadgedLabel(applicationInfo.loadLabel(packageManager), user)
    }

    @JvmStatic
    fun getLaunchComponentKeyForTask(taskKey: TaskKey) =
        ComponentKey(taskKey.sourceComponent ?: taskKey.component, UserHandle.of(taskKey.userId))

    @JvmStatic
    fun taskIsATargetWithMode(targets: Array<RemoteAnimationTarget>, taskId: Int, mode: Int) =
        targets.any { it.mode == mode && it.taskId == taskId }

    @JvmStatic
    fun checkCurrentOrManagedUserId(currentUserId: Int, context: Context): Boolean {
        if (currentUserId == UserHandle.myUserId()) return true
        val allUsers = UserCache.INSTANCE[context].userProfiles
        return allUsers.any { it.identifier == currentUserId }
    }

    /** Requests that the system close any open system windows (including other SystemUI). */
    @JvmStatic
    fun closeSystemWindowsAsync(reason: String) {
        Executors.UI_HELPER_EXECUTOR.execute {
            ActivityManagerWrapper.getInstance().closeSystemWindows(reason)
        }
    }
}
