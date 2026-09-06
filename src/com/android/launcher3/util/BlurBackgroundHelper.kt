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

package com.android.launcher3.util

import android.graphics.Canvas
import android.view.View
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.folder.Folder
import com.android.launcher3.graphics.PathWrapper
import javax.inject.Inject

/**
 * Helper class that creates and updates the blur drawable used for folders and the homescreen
 * popup.
 */
@ActivityContextSingleton
open class BlurBackgroundHelper
@Inject
constructor() {

    open fun prepareToOpenFolder(folder: Folder) {}

    open fun drawFolderBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {}

    open fun folderCloseComplete() {}

    open fun isBlurEnabled() = false
}
