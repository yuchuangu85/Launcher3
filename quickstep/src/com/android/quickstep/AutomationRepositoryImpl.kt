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

package com.android.quickstep

import android.content.Context
import android.os.UserHandle
import android.util.Log
import com.android.extensions.computercontrol.AutomatedPackageListener
import com.android.extensions.computercontrol.ComputerControlExtensions
import com.android.launcher3.automation.AutomationChange
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.ListenableDiffAwareRef
import com.android.launcher3.util.MutableDiffAwareRef
import com.android.launcher3.util.PackageUserKey
import java.util.concurrent.Executor
import javax.inject.Inject

@LauncherAppSingleton
class AutomationRepositoryImpl
@Inject
constructor(
    @ApplicationContext context: Context,
    computerControlExtensions: ComputerControlExtensions?,
    @LightweightBackground(LightweightBackgroundPriority.DATA) packageListenerExecutor: Executor,
    lifecycle: DaggerSingletonTracker,
) : AutomationRepository {

    private val _automatedPackages: MutableDiffAwareRef<Set<PackageUserKey>, AutomationChange> =
        MutableDiffAwareRef(hashSetOf())

    override val automatedPackages: ListenableDiffAwareRef<Set<PackageUserKey>, AutomationChange> =
        _automatedPackages.asListenable()

    /** Callback from ComputerControlExtensions for automation changes. */
    private val automatedPackageListener = AutomatedPackageListener(::onAutomationChange)

    /** Cache for local automation state in case of reload. */
    private val automationCache: MutableMap<AutomationKey, Set<String>> = mutableMapOf()

    init {
        computerControlExtensions?.registerAutomatedPackageListener(
            context,
            packageListenerExecutor,
            automatedPackageListener,
        )

        lifecycle.addCloseable {
            computerControlExtensions?.unregisterAutomatedPackageListener(
                context,
                automatedPackageListener,
            )
        }
    }

    override fun isPackageAutomated(user: UserHandle, packageName: String): Boolean =
        _automatedPackages.value.contains(PackageUserKey(packageName, user))

    // UserHandle.of() is a system API, so we only use in Quickstep implementation.
    override fun isPackageAutomated(userId: Int, packageName: String): Boolean =
        _automatedPackages.value.contains(PackageUserKey(packageName, UserHandle.of(userId)))

    /**
     * Updates cache with the current state of automation for each automatingPackage (automator) and
     * User combo. Also dispatches [AutomationChange] with the delta of newly automated and no
     * longer automated packages.
     *
     * @param automator the package that is automating [automatedPackages] for [user]
     * @param automatedPackages the packages being automated for [user] by automator
     * @param user the UserHandle for the [automatedPackages]
     */
    private fun onAutomationChange(
        automator: String,
        automatedPackages: List<String>,
        user: UserHandle,
    ) {
        Log.d(
            TAG,
            "onAutomationChange: automator:$automator," +
                " automatedPackages:$automatedPackages," +
                " user:$user",
        )
        val key = AutomationKey(user, automator)
        val updatedPackagesSet = automatedPackages.toSet()

        // Update the underlying cache for this change.
        if (updatedPackagesSet.isEmpty()) {
            // Automator/User session is no longer active, remove this entry.
            automationCache.remove(key)
        } else {
            automationCache[key] = updatedPackagesSet
        }

        // Calculate the new set of user packages automated across all automators.
        val newAllAutomatedPackages =
            automationCache
                .flatMap { (automationKey, packages) ->
                    packages.map { pkg -> PackageUserKey(pkg, automationKey.userHandle) }
                }
                .toHashSet()

        // Calculate delta of newly automated and no longer automated packages.
        val currAllAutomatedPackages = _automatedPackages.value
        val addedPackages =
            (newAllAutomatedPackages - currAllAutomatedPackages)
                .filter { packageUserKey -> packageUserKey.mUser == user }
                .map { packageUserKey -> packageUserKey.mPackageName }
                .toHashSet()
        val removedPackages =
            (currAllAutomatedPackages - newAllAutomatedPackages)
                .filter { packageUserKey -> packageUserKey.mUser == user }
                .map { packageUserKey -> packageUserKey.mPackageName }
                .toHashSet()

        // If there is a net change to automated packages, dispatch update.
        if (addedPackages.isNotEmpty() || removedPackages.isNotEmpty()) {
            val change = AutomationChange(user, addedPackages, removedPackages)
            Log.d(
                TAG,
                "onAutomationChange: updating state and dispatching AutomationChange:$change",
            )
            // Atomically update state and delta change.
            _automatedPackages.dispatchValue(newAllAutomatedPackages, change)
        }
    }

    /* Key for storing automated packages in [automationCache] **/
    private data class AutomationKey(val userHandle: UserHandle, val automatingPackage: String)

    companion object {
        const val TAG = "AutomationRepository"
    }
}
