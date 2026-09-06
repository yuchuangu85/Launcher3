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

package com.google.android.torus.core.app

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Listens to keyguard lock state changes.
 *
 * @constructor Creates a new [KeyguardLockController].
 * @param lockStateListener a listener that we receive Keyguard Lock state changes.
 */
class KeyguardLockController(
    private val context: Context,
    private val lockStateListener: LockStateListener? = null
) {
    @Volatile
    var locked: Boolean = false
        private set

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) onChange(false)
        }
    }

    private val userPresentIntentFilter: IntentFilter = IntentFilter(Intent.ACTION_USER_PRESENT)
    private val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
    private var isRegistered: Boolean = false

    init {
        keyguardManager?.let { locked = it.isKeyguardLocked }
    }

    /**
     * Starts listening for [Intent.ACTION_USER_PRESENT] state changes. This should be used
     * together with [KeyguardLockController.updateLockState] to detect lock state changes. Using a
     * broadcast listener is not ideal, but there isn't an alternative event to detect lock state
     * changes.
     */
    fun start() {
        context.registerReceiver(userPresentReceiver, userPresentIntentFilter)
        isRegistered = true
    }

    /**
     * Stops listening for [Intent.ACTION_USER_PRESENT] state changes. This should be used
     * together with [KeyguardLockController.updateLockState] to detect lock state changes. Using a
     * broadcast listener is not ideal, but there isn't an alternative event to detect lock state
     * changes.
     */
    fun stop() {
        if (isRegistered) {
            context.unregisterReceiver(userPresentReceiver)
            isRegistered = false
        }
    }

    /**
     * Reads the [KeyguardManager.isKeyguardLocked] new value to know the current Lock state.
     * This function should be also called on Screen state changes (i.e. [Intent.ACTION_SCREEN_ON],
     * [Intent.ACTION_SCREEN_OFF]). This function can be used also to do polling of the lock state.
     */
    fun updateLockState() = keyguardManager?.let { onChange(it.isKeyguardLocked) }

    private fun onChange(locked: Boolean) {
        if (this.locked != locked) {
            this.locked = locked
            lockStateListener?.onLockStateChanged(locked)
        }
    }

    /** Interface to listen to Keyguard lock changes. */
    interface LockStateListener {
        /**
         * Called when the Keyguard lock state has changed.
         *
         * @param locked true if the keyguard is currently locked.
         */
        fun onLockStateChanged(locked: Boolean)
    }
}
