/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.util

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableRef
import javax.inject.Inject

/** Utility class to maintain a list of actively connected trackpad devices */
@LauncherAppSingleton
class ActiveTrackpadList
@Inject
constructor(
    @ApplicationContext ctx: Context,
    lifecycle: DaggerSingletonTracker,
    @LightweightBackground(priority = UI) private val executor: LooperExecutor,
) : InputManager.InputDeviceListener {

    private val inputManager =
        ctx.getSystemService(InputManager::class.java)!!.also {
            it.registerInputDeviceListener(this, executor.handler)
        }

    private val connectedDevices =
        IntSet.wrap(inputManager.inputDeviceIds.filter(this::isTrackpadDevice))
    private val _isConnected = MutableListenableRef(!connectedDevices.isEmpty)

    val connected = _isConnected.asListenable()

    init {
        lifecycle.addCloseable(executor) { inputManager.unregisterInputDeviceListener(this) }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        if (isTrackpadDevice(deviceId)) addInputDeviceUnchecked(deviceId)
    }

    fun addInputDeviceUnchecked(deviceId: Int) {
        connectedDevices.add(deviceId)
        update()
    }

    override fun onInputDeviceChanged(deviceId: Int) {}

    override fun onInputDeviceRemoved(deviceId: Int) {
        connectedDevices.remove(deviceId)
        update()
    }

    private fun update() {
        val newValue = !connectedDevices.isEmpty
        if (_isConnected.value != newValue) _isConnected.dispatchValue(newValue)
    }

    /** This is a blocking binder call that should run on a bg thread. */
    private fun isTrackpadDevice(deviceId: Int) =
        inputManager.getInputDevice(deviceId)?.sources ==
            (InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_TOUCHPAD)
}
