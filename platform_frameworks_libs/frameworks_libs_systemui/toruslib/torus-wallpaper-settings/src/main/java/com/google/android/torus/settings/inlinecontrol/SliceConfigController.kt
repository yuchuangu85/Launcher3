/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.torus.settings.inlinecontrol

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.google.android.torus.utils.broadcast.BroadcastEventController

/**
 * SliceConfigController registers a BroadcastReceiver that listens to
 * the intent filter provided by an implementation of [BaseSliceConfigProvider].
 * Forwards received broadcasts to be handled by a [SliceConfigController.SliceConfigListener].
 */
class SliceConfigController(
    context: Context,
    private val sliceIntentFilter: IntentFilter,
    private val sliceConfigListener: SliceConfigListener
) : BroadcastEventController(context) {

    override fun initResources(): Boolean {
        return true
    }

    override fun onBroadcastReceived(context: Context, intent: Intent, action: String) {
        intent?.let { sliceConfigListener.onSliceConfig(it) }
    }

    override fun onRegister(fire: Boolean): IntentFilter {
        return sliceIntentFilter
    }

    override fun onUnregister() {}

    interface SliceConfigListener {
        fun onSliceConfig(intent: Intent)
    }
}
