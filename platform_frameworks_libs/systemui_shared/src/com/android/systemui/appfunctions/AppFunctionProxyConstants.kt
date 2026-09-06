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

package com.android.systemui.appfunctions

import android.app.PendingIntent
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.content.ComponentName

object AppFunctionProxyConstants {
    val PROXY_ACTIVITY_COMPONENT =
        ComponentName(
            "com.android.systemui",
            "com.android.systemui.appfunctions.trampoline.AppFunctionExecutorProxy",
        )

    /**
     * Intent extra used to specify the AppFunction request to be executed. The data type expected
     * is of [ExecuteAppFunctionRequest] which is parcelable and must be added using a putExtra(key,
     * value) which automatically handles parcelables.
     */
    const val EXTRA_EXECUTE_APP_FUNCTION_REQUEST = "EXTRA_EXECUTE_APP_FUNCTION_REQUEST"

    /**
     * Intent extra used to specify the AppFunction response will return a [PendingIntent] which
     * would need to be processed and launched. Handlers are recommended to validate that
     * [PendingIntent.isActivity] holds true prior to calling [PendingIntent.send]
     */
    const val EXTRA_LAUNCH_PENDING_INTENT_FROM_RESPONSE =
        "EXTRA_LAUNCH_PENDING_INTENT_FROM_RESPONSE"
}
