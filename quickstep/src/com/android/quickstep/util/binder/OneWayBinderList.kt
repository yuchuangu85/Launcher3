/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.quickstep.util.binder

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.launcher3.util.SafeCloseable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A binder class which hold a list of one-way callbacks delegates the events to every callback in
 * the list.
 */
class OneWayBinderList<T : IInterface>
@JvmOverloads
constructor(
    mapper: (Binder) -> T,
    private val onFirstRegister: (T) -> Unit = {},
    private val onLastUnregister: (T) -> Unit = {},
) : Binder() {

    val iInterface = mapper.invoke(this)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val listeners = CopyOnWriteArrayList<IBinder>()

    private val descriptor: String? = iInterface.asBinder().interfaceDescriptor

    override fun getInterfaceDescriptor(): String? = descriptor

    override fun toString(): String = "OneWayBinderList{$listeners}"

    /** Registers the listeners to receive all events, and returns a task to remove this listener */
    fun register(listener: T): SafeCloseable {
        val binder = listener.asBinder()
        val wasEmpty = listeners.isEmpty()
        listeners.add(binder)

        if (wasEmpty) {
            onFirstRegister.invokeWithErrorLog("register")
        }
        return SafeCloseable {
            listeners.remove(binder)
            if (listeners.isEmpty()) {
                onLastUnregister.invokeWithErrorLog("unregister")
            }
        }
    }

    /** Causes the trigger events to be invoked based on the current set of listeners */
    fun triggerRegisterEvent() {
        if (listeners.isNotEmpty()) onFirstRegister.invokeWithErrorLog("unregister")
    }

    private fun ((T) -> Unit).invokeWithErrorLog(eventTag: String) {
        try {
            this.invoke(iInterface)
        } catch (e: RemoteException) {
            Log.w(TAG, "Failed to $eventTag $descriptor", e)
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION) {
            return super.onTransact(code, data, reply, flags)
        }
        if (flags.and(FLAG_ONEWAY) == 0) throw RemoteException("Only oneway calls are supported")
        var lastError: Throwable? = null
        // send the data to all listeners and keep track of the last error
        listeners.forEach {
            lastError =
                runCatching { it.transact(code, data, null, flags) }.exceptionOrNull() ?: lastError
        }
        lastError?.let { throw it }
        return true
    }

    companion object {

        private const val TAG = "OneWayBinderList"

        /**
         * Creates a [OneWayBinderList] where the unregister is same as calling register with null
         */
        fun <T : IInterface> forNullableSetter(mapper: (Binder) -> T, setter: (T?) -> Unit) =
            OneWayBinderList(
                mapper = mapper,
                onFirstRegister = { setter.invoke(it) },
                onLastUnregister = { setter.invoke(null) },
            )
    }
}
