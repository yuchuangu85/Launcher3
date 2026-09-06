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
package com.android.personalcontext.ace.client.prototype.crossdevice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.android.personalcontext.ace.client.prototype.PrototypeHint
import com.android.personalcontext.ace.client.prototype.PrototypeHintId.CrossDeviceIntentHintId

/**
 * A hint for cross-device Intents, allowing the receiver to re-construct a custom device-native
 * Intent.
 *
 * @property action The [action][Intent.getAction] of the intent, eg. "android.intent.action.VIEW".
 * @property packageName The [`package`][Intent.getPackage] of the intent, eg.
 *   "com.google.android.apps.maps".
 * @property data The [data URI][Intent.getData] of the intent.
 * @property type The [MIME type][Intent.getType] of the intent.
 * @property extras The [extras][Intent.getExtras] of the intent.
 */
data class CrossDeviceIntentHint(
    val action: String?,
    val packageName: String?,
    val data: Uri?,
    val type: String?,
    val extras: Bundle?,
) : PrototypeHint(CrossDeviceIntentHintId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putString(KEY_ACTION, action)
        bundle.putString(KEY_PACKAGE_NAME, packageName)
        bundle.putParcelable(KEY_DATA, data)
        bundle.putString(KEY_TYPE, type)
        bundle.putBundle(KEY_EXTRAS, extras)
    }

    companion object : Creator {

        override fun create(bundle: Bundle): PrototypeHint =
            CrossDeviceIntentHint(
                action = bundle.getString(KEY_ACTION),
                packageName = bundle.getString(KEY_PACKAGE_NAME),
                data = bundle.getParcelable(KEY_DATA, Uri::class.java),
                type = bundle.getString(KEY_TYPE),
                extras = bundle.getBundle(KEY_EXTRAS),
            )

        private const val KEY_ACTION = "action"
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_DATA = "data"
        private const val KEY_TYPE = "type"
        private const val KEY_EXTRAS = "extras"

        /** Create a [CrossDeviceIntentHint] from an [Intent]. */
        fun Intent.toCrossDeviceIntentHint() =
            CrossDeviceIntentHint(
                action = this.action,
                packageName = this.`package`,
                data = this.data,
                type = this.type,
                extras = this.extras,
            )
    }
}
