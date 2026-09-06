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

package com.android.launcher3.automation

import android.os.UserHandle
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.ListenableDiffAwareRef
import com.android.launcher3.util.PackageUserKey

/** Repository to provide information related to automated apps */
interface AutomationRepository {

    /** Unified ref for the set of all automated packages and the changes occurring to them. */
    val automatedPackages: ListenableDiffAwareRef<Set<PackageUserKey>, AutomationChange>

    /** Returns if the provided package is being automated for the provided user */
    fun isPackageAutomated(user: UserHandle, packageName: String): Boolean

    /** Returns if the provided package is being automated for the provided user ID */
    fun isPackageAutomated(userId: Int, packageName: String): Boolean

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getAutomationRepository)
    }
}

/** Contains the delta of automated packages from an automation change for a given user. */
data class AutomationChange(
    /** user for which automation status has changed */
    val userHandle: UserHandle,
    /** Packages that changed from not automated to automated for this user */
    val addedPackages: Set<String>,
    /** Packages that changed from automated to not automated for this user */
    val removedPackages: Set<String>,
)
