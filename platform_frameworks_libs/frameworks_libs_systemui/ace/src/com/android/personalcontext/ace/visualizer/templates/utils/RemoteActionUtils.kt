/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.personalcontext.ace.visualizer.templates.utils

import android.app.ActivityOptions
import android.app.RemoteAction
import android.content.Context
import android.util.Log
import com.android.window.flags.Flags.balAdditionalStartModes

/** Utils for [RemoteAction]s. */
object RemoteActionUtils {
    private const val TAG = "RemoteActionUtils"

    /** Takes the pending intent in the [RemoteAction] and sends it to the intended [Activity]. */
    fun RemoteAction.execute(
        context: Context,
        code: Int = 0,
        fillInIntent: android.content.Intent? = null,
        options: ActivityOptions = ActivityOptions.makeBasic(),
    ) {
        try {
            actionIntent.send(
                /* context = */ context,
                /* code = */ code,
                /* intent = */ fillInIntent,
                /* onFinished = */ null,
                /* handler = */ null,
                /* requiredPermission = */ null,
                /* options = */ options
                    .apply {
                        if (balAdditionalStartModes()) {
                            setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                            )
                        }
                    }
                    .toBundle(),
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to execute remote action", t)
        }
    }
}
