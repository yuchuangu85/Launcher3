/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.launcher3.pm

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.ColorDrawable
import android.os.UserHandle
import android.os.UserManager
import android.os.UserManager.USER_TYPE_PROFILE_CLONE
import android.os.UserManager.USER_TYPE_PROFILE_MANAGED
import android.os.UserManager.USER_TYPE_PROFILE_PRIVATE
import android.util.Log
import androidx.annotation.WorkerThread
import com.android.launcher3.Utilities.ATLEAST_V
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableStream
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.actionsFilter
import com.android.launcher3.util.UserIconInfo
import com.android.users.UserType
import javax.inject.Inject

/** Class which manages a local cache of user handles to avoid system rpc */
@LauncherAppSingleton
class UserCache
@Inject
constructor(
    @ApplicationContext private val context: Context,
    tracker: DaggerSingletonTracker,
    @LightweightBackground(LightweightBackgroundPriority.UI) executor: LooperExecutor,
) {
    private val userManager = context.getSystemService(UserManager::class.java)!!

    private var closed = false

    private var _userInfoMap: UserManagerState? = null

    val userManagerState: UserManagerState
        get() = _userInfoMap ?: rebuildUserCache()

    private val _userChanges = MutableListenableStream<UserChangeEvent>()

    /** Stream for listening to user manager changes */
    val userChanges = _userChanges.asListenable()

    init {
        val userChangeReceiver =
            SimpleBroadcastReceiver(context = context, executor = executor) { onUsersChanged(it) }
        userChangeReceiver.register(
            actionsFilter(
                // go/keep-sorted start
                Intent.ACTION_MANAGED_PROFILE_ADDED,
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_REMOVED,
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
                Intent.ACTION_PROFILE_ACCESSIBLE,
                Intent.ACTION_PROFILE_ADDED,
                Intent.ACTION_PROFILE_AVAILABLE,
                Intent.ACTION_PROFILE_INACCESSIBLE,
                Intent.ACTION_PROFILE_REMOVED,
                Intent.ACTION_PROFILE_UNAVAILABLE,
                // go/keep-sorted end
            )
        ) {
            rebuildUserCache()
        }
        tracker.addCloseable { closed = true }
        tracker.addCloseable(userChangeReceiver)
    }

    @WorkerThread
    private fun onUsersChanged(intent: Intent) {
        if (closed) return
        val oldState = userManagerState
        rebuildUserCache()
        val user = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER) ?: return
        val change =
            UserChangeEvent(
                oldState.getCachedInfoOrNull(user),
                userManagerState.getCachedInfoOrNull(user),
            )
        if (change.oldUser != change.newUser) {
            _userChanges.dispatchValue(change)
        }
    }

    @WorkerThread
    private fun rebuildUserCache(): UserManagerState =
        UserManagerState(
                fetchSafe(emptyList<UserHandle>()) { userProfiles }
                    .mapNotNull { buildCachedUserInfo(it) }
                    .associateBy { it.iconInfo.user }
            )
            .also { _userInfoMap = it }

    private fun buildCachedUserInfo(user: UserHandle): CachedUserInfo? {
        if (!ATLEAST_V) {
            return fetchSafe(null) {
                // Simple check to check if the provided user is work profile
                val isWork =
                    NoopDrawable().let { it !== context.packageManager.getUserBadgedIcon(it, user) }
                CachedUserInfo(
                    UserIconInfo(
                        user = user,
                        type = if (isWork) UserType.WORK else UserType.MAIN,
                        userSerial = getSerialNumberForUser(user),
                    ),
                    isUnlocked = isUserUnlocked(user),
                    isQuietModeEnabled = isQuietModeEnabled(user),
                )
            }
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        return launcherApps.getLauncherUserInfo(user)?.let {
            val userType: String? = it.userType
            CachedUserInfo(
                iconInfo =
                    UserIconInfo(
                        user = user,
                        type =
                            when (userType) {
                                null -> UserType.MAIN
                                USER_TYPE_PROFILE_MANAGED -> UserType.WORK
                                USER_TYPE_PROFILE_CLONE -> UserType.CLONED
                                USER_TYPE_PROFILE_PRIVATE -> UserType.PRIVATE
                                else -> UserType.MAIN
                            },
                        userSerial = it.userSerialNumber.toLong(),
                    ),
                isUnlocked = fetchSafe(false) { isUserUnlocked(user) },
                isQuietModeEnabled = fetchSafe(false) { isQuietModeEnabled(user) },
                preInstallApps = launcherApps.getPreInstalledSystemPackages(user).toSet(),
            )
        }
    }

    private inline fun <T> fetchSafe(defaultValue: T, block: UserManager.() -> T) =
        try {
            block.invoke(userManager)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while fetching user property", e)
            defaultValue
        }

    /** @see UserManager.getSerialNumberForUser */
    fun getSerialNumberForUser(user: UserHandle): Long = getUserInfo(user).userSerial

    /** Returns the user properties for the provided user or default values */
    fun getUserInfo(user: UserHandle) = userManagerState.getUserInfo(user)

    /** Returns the user locked state */
    fun isUserUnlocked(user: UserHandle) = userManagerState.isUserUnlocked(user)

    /** @see UserManager.getUserForSerialNumber */
    fun getUserForSerialNumber(serialNumber: Long): UserHandle =
        userManagerState.getUser(serialNumber)

    /** @see UserManager.getUserProfiles */
    val userProfiles: List<UserHandle>
        get() = userManagerState.userProfiles

    /** Returns the pre-installed apps for a user. */
    fun getPreInstallApps(user: UserHandle) = userManagerState.getPreInstallApps(user)

    private class NoopDrawable : ColorDrawable() {
        override fun getIntrinsicHeight() = 1

        override fun getIntrinsicWidth() = 1
    }

    /** Information about a UserHandle cached in the platform */
    data class CachedUserInfo(
        val iconInfo: UserIconInfo,
        val isUnlocked: Boolean,
        val isQuietModeEnabled: Boolean,

        /**
         * List of the system packages that are installed at user creation. An empty list denotes
         * that all system packages are installed for that user at creation.
         */
        val preInstallApps: Set<String> = emptySet(),
    )

    data class UserChangeEvent(val oldUser: CachedUserInfo?, val newUser: CachedUserInfo?)

    companion object {
        private const val TAG = "UserCache"

        @JvmField var INSTANCE = DaggerSingletonObject { it.userCache }

        /** Returns an instance of UserCache bound to the context provided. */
        @JvmStatic
        fun getInstance(context: Context): UserCache {
            return INSTANCE[context]
        }
    }
}
