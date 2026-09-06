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

package com.google.android.torus.utils.broadcast

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PowerSaveController registers a BroadcastReceiver that listens to changes in Power Save Mode
 * provided by the OS. Forwards received broadcasts to be handled by a [PowerSaveListener].
 */
class PowerSaveController(context: Context, listener: PowerSaveListener?) :
    BroadcastEventController(context) {
    companion object {
        const val DEFAULT_POWER_SAVE_MODE = false
    }

    private val listeners = mutableListOf<PowerSaveListener>()

    private var powerSaving: AtomicBoolean? = null
    private var powerManager: PowerManager? = null

    override fun initResources(): Boolean {
        if (powerSaving == null) powerSaving = AtomicBoolean(DEFAULT_POWER_SAVE_MODE)
        if (powerManager == null)
            powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
        return powerManager != null
    }

    override fun onBroadcastReceived(context: Context, intent: Intent, action: String) {
        if (action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
            /* Check if powerSaveMode has changed. */
            powerManager?.let { setPowerSave(it.isPowerSaveMode, true) }
        }
    }

    override fun onRegister(fire: Boolean): IntentFilter {
        powerManager?.let { setPowerSave(it.isPowerSaveMode, fire) }
        return IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
    }

    override fun onUnregister() {}

    init {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    private fun setPowerSave(isPowerSave: Boolean, fire: Boolean) {
        powerSaving?.let {
            if (it.get() == isPowerSave) return
            it.set(isPowerSave)
        }

        listeners.forEach { listener -> if (fire) listener.onPowerSaveModeChanged(isPowerSave) }
    }

    fun isPowerSaving(): Boolean = powerSaving?.get() ?: false

    fun registerListener(listener: PowerSaveListener) {
        listeners += listener
    }

    fun unregisterListener(listener: PowerSaveListener) {
        listeners -= listener
    }

    interface PowerSaveListener {
        fun onPowerSaveModeChanged(isPowerSaveMode: Boolean)
    }
}
