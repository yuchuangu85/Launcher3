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

package com.android.quickstep.window

import android.view.ActionMode
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import com.android.internal.policy.ActionModeController
import com.android.internal.policy.PhoneWindow

/**
 * A root view for the Recents Window that can be used to intercept events that would otherwise go
 * to the Launcher activity.
 */
class RecentsWindowRootView(private val recentsWindowContext: RecentsWindowContext) :
    FrameLayout(recentsWindowContext.asContext()) {

    private val actionMode =
        ActionModeController(/* containerView= */ this, recentsWindowContext, View.NO_ID).apply {
            setWindow(PhoneWindow(recentsWindowContext))
        }

    override fun dispatchKeyEvent(event: KeyEvent?) =
        recentsWindowContext.onRootViewDispatchKeyEvent(event) ||
            actionMode.dispatchKeyEvent(event) ||
            super.dispatchKeyEvent(event)

    override fun startActionModeForChild(
        originalView: View?,
        callback: ActionMode.Callback?,
        type: Int,
    ): ActionMode? = actionMode.startActionMode(originalView, callback, type)

    override fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets {
        actionMode.updateActionModeInsets(insets)

        return super.onApplyWindowInsets(insets)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)

        actionMode.onWindowFocusChanged(hasWindowFocus)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        actionMode.onDetachedFromWindow()
    }
}
