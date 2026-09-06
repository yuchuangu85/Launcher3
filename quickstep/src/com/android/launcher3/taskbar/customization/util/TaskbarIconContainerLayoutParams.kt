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

package com.android.launcher3.taskbar.customization.util

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout.LayoutParams
import com.android.launcher3.celllayout.CellInfo

/** LayoutParams used for taskbar icon views within the containers. */
class TaskbarIconContainerLayoutParams : LayoutParams {
    var bindInfo: CellInfo? = null

    constructor(width: Int, height: Int) : super(width, height)

    constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)

    constructor(p: ViewGroup.LayoutParams?) : super(p)
}
