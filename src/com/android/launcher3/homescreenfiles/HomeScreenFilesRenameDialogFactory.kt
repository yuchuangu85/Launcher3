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

package com.android.launcher3.homescreenfiles

import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.Dialog
import javax.inject.Inject

/** Type alias for a home screen files rename dialog. */
typealias HomeScreenFilesRenameDialog = Dialog<HomeScreenFilesRenameDialogViewModel>

/** Injectable factory used to create home screen file rename dialogs. */
@LauncherAppSingleton
class HomeScreenFilesRenameDialogFactory @Inject constructor() {
    fun create(activityContext: ActivityContext, file: HomeScreenFile) =
        HomeScreenFilesRenameDialog(
            activityContext,
            HomeScreenFilesRenameDialogViewModel(
                activityContext,
                file,
                HomeScreenFilesProvider.INSTANCE[activityContext.asContext()],
            ),
        )
}
