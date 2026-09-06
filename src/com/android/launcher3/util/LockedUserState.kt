/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.content.Intent
import android.os.Process
import android.os.UserManager
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.actionsFilter
import java.util.concurrent.Executor
import javax.inject.Inject

@LauncherAppSingleton
class LockedUserState
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @LightweightBackground(priority = UI) private val uiHelperExecutor: LooperExecutor,
    @Ui private val uiExecutor: LooperExecutor,
    lifeCycle: DaggerSingletonTracker,
) {

    val isUserUnlockedAtLauncherStartup: Boolean
    var isUserUnlocked = false
        private set(value) {
            field = value
            if (value) {
                notifyUserUnlocked()
            }
        }

    private val mUserUnlockedActions = ThreadSafeRunnableList()

    @VisibleForTesting
    val userUnlockedReceiver =
        SimpleBroadcastReceiver(context, uiHelperExecutor, uiExecutor) {
            if (Intent.ACTION_USER_UNLOCKED == it.action) {
                isUserUnlocked = true
            }
        }

    init {
        // 1) when user reboots devices, launcher process starts at lock screen and both
        // isUserUnlocked and isUserUnlockedAtLauncherStartup are init as false. After user unlocks
        // screen, isUserUnlocked will be updated to true via Intent.ACTION_USER_UNLOCKED,
        // yet isUserUnlockedAtLauncherStartup will remains as false.
        // 2) when launcher process restarts after user has unlocked screen, both variable are
        // init as true and will not change.
        isUserUnlocked = checkIsUserUnlocked()
        isUserUnlockedAtLauncherStartup = isUserUnlocked
        if (!isUserUnlocked) {
            userUnlockedReceiver.register(actionsFilter(Intent.ACTION_USER_UNLOCKED)) {
                // If user is unlocked while registering broadcast receiver, we should update
                // [isUserUnlocked], which will call [notifyUserUnlocked] in setter
                if (checkIsUserUnlocked()) {
                    uiExecutor.execute { isUserUnlocked = true }
                }
            }
        }
        lifeCycle.addCloseable(userUnlockedReceiver)
    }

    private fun checkIsUserUnlocked() =
        context.getSystemService(UserManager::class.java)!!.isUserUnlocked(Process.myUserHandle())

    private fun notifyUserUnlocked() {
        mUserUnlockedActions.complete()
        userUnlockedReceiver.close()
    }

    /**
     * Adds a `Runnable` to be executed when a user is unlocked. If the user is already unlocked,
     * this runnable will run immediately.
     */
    @JvmOverloads
    @AnyThread
    fun runOnUserUnlocked(executor: Executor = uiExecutor, action: Runnable) =
        mUserUnlockedActions.addTask(executor, action)

    /** Removes a previously queued `Runnable` to be run when the user is unlocked. */
    @AnyThread
    fun removeOnUserUnlockedRunnable(action: Runnable) = mUserUnlockedActions.removeTask(action)

    companion object {

        @JvmStatic fun get(context: Context): LockedUserState = context.appComponent.lockedUserState
    }
}
