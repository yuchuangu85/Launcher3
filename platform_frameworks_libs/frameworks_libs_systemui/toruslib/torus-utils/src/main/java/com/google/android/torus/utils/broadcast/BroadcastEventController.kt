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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This is the base class to be implemented when we need to listen to broadcast events
 * It registers a broadcast receiver and triggers [onBroadcastReceived] when a new
 * broadcast is received.
 */
abstract class BroadcastEventController constructor(protected var context: Context) {
    private val broadcastRegistered: AtomicBoolean = AtomicBoolean(false)
    private val initialized: AtomicBoolean
    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.action?.let { action ->
                onBroadcastReceived(context, intent, action)
            }
        }
    }

    init {
        val hasInitialized = initResources()
        initialized = AtomicBoolean(hasInitialized)
    }

    protected abstract fun initResources(): Boolean
    abstract fun onBroadcastReceived(context: Context, intent: Intent, action: String)
    protected abstract fun onRegister(fire: Boolean): IntentFilter
    protected abstract fun onUnregister()

    /**
     * Start listening to broadcasts by registering the broadcast receiver.
     *
     * @param fire sets whether to notify the listener right away with the current state.
     */
    fun start(fire: Boolean = false) {
        if (!initialized.get()) {
            val hasInitialized = initResources()
            if (!hasInitialized) return
            initialized.set(true)
        }
        if (!broadcastRegistered.get()) {
            val filter = onRegister(fire)
            registerReceiver(context, broadcastReceiver, filter)
            broadcastRegistered.set(true)
        }
    }

    /**
     * Stop listening to broadcasts by unregistering the broadcast receiver.
     */
    @Synchronized
    fun stop() {
        if (broadcastRegistered.get()) {
            onUnregister()
            unregisterReceiver(context, broadcastReceiver)
            broadcastRegistered.set(false)
        }
    }

    protected fun registerReceiver(
        context: Context,
        broadcastReceiver: BroadcastReceiver?,
        filter: IntentFilter?
    ) {
        context.registerReceiver(broadcastReceiver, filter)
    }

    protected fun unregisterReceiver(context: Context, broadcastReceiver: BroadcastReceiver?) {
        context.unregisterReceiver(broadcastReceiver)
    }
}
