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

package com.android.quickstep.fallback

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import com.android.launcher3.statehandlers.DepthController
import com.android.quickstep.window.RecentsWindowManager

class FallbackWindowRecentsView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0) :
    FallbackRecentsView<RecentsWindowManager>(context, attrs, defStyleAttr) {
    override fun initialiseInjectables() {
        mContainer.activityComponent.inject(this)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) {
            mContainer.requestInputFocus(focused = isVisible)
        }
    }

    override fun getDepthController(): DepthController<RecentsState, RecentsWindowManager>? =
        mContainer.depthController
}
