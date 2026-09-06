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

package com.android.quickstep

import android.util.Log
import com.android.app.displaylib.DisplayDecorationListener
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.quickstep.dagger.QuickstepBaseAppComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

@LauncherAppSingleton
class SystemDecorationChangeObserver @Inject constructor() {
    companion object {
        private const val TAG = "SystemDecorationChangeObserver"
        private const val DEBUG = false

        @JvmStatic
        val INSTANCE: DaggerSingletonObject<SystemDecorationChangeObserver> =
            DaggerSingletonObject<SystemDecorationChangeObserver>(
                QuickstepBaseAppComponent::getSystemDecorationChangeObserver
            )
    }

    fun notifyAddSystemDecorations(displayId: Int) {
        if (DEBUG) Log.d(TAG, "SystemDecorationAdded: $displayId")
        mListeners.forEach { (listener, dispatcher) ->
            dispatcher.dispatch(EmptyCoroutineContext) {
                listener.onDisplayAddSystemDecorations(displayId)
            }
        }
    }

    fun notifyOnDisplayRemoved(displayId: Int) {
        if (DEBUG) Log.d(TAG, "displayRemoved: $displayId")
        mListeners.forEach { (listener, dispatcher) ->
            dispatcher.dispatch(EmptyCoroutineContext) { listener.onDisplayRemoved(displayId) }
        }
    }

    fun notifyDisplayRemoveSystemDecorations(displayId: Int) {
        if (DEBUG) Log.d(TAG, "SystemDecorationRemoved: $displayId")
        mListeners.forEach { (listener, dispatcher) ->
            dispatcher.dispatch(EmptyCoroutineContext) {
                listener.onDisplayRemoveSystemDecorations(displayId)
            }
        }
    }

    private val mListeners = ConcurrentHashMap<DisplayDecorationListener, CoroutineDispatcher>()

    fun registerDisplayDecorationListener(
        listener: DisplayDecorationListener,
        dispatcher: CoroutineDispatcher,
    ) {
        if (DEBUG) Log.d(TAG, "registerDisplayDecorationListener")
        mListeners[listener] = dispatcher
    }

    fun unregisterDisplayDecorationListener(listener: DisplayDecorationListener) {
        if (DEBUG) Log.d(TAG, "unregisterDisplayDecorationListener")
        mListeners.remove(listener)
    }
}
