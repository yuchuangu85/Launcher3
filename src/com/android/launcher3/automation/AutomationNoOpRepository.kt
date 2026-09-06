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
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.ListenableDiffAwareRef
import com.android.launcher3.util.MutableDiffAwareRef
import com.android.launcher3.util.PackageUserKey
import javax.inject.Inject

@LauncherAppSingleton
class AutomationNoOpRepository @Inject constructor() : AutomationRepository {

    override val automatedPackages: ListenableDiffAwareRef<Set<PackageUserKey>, AutomationChange> =
        MutableDiffAwareRef<Set<PackageUserKey>, AutomationChange>(hashSetOf()).asListenable()

    override fun isPackageAutomated(user: UserHandle, packageName: String): Boolean = false

    override fun isPackageAutomated(userId: Int, packageName: String): Boolean = false
}
