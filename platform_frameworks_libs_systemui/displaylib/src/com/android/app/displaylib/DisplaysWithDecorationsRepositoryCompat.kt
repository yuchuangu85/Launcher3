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

package com.android.app.displaylib

import com.android.internal.annotations.GuardedBy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Listener for display system decorations changes. */
interface DisplayDecorationListener {
    /** Called when system decorations should be added to the display.* */
    fun onDisplayAddSystemDecorations(displayId: Int)

    /** Called when a display is removed. */
    fun onDisplayRemoved(displayId: Int)

    /** Called when system decorations should be removed from the display. */
    fun onDisplayRemoveSystemDecorations(displayId: Int)
}

/**
 * This class is a compatibility layer that allows to register and unregister listeners for display
 * decorations changes. It uses a [DisplaysWithDecorationsRepository] to get the current list of
 * displays with decorations and notifies the listeners when the list changes.
 */
@Singleton
class DisplaysWithDecorationsRepositoryCompat
@Inject
constructor(
    private val bgApplicationScope: CoroutineScope,
    private val displayRepository: DisplaysWithDecorationsRepository,
) {
    private val mutex = Mutex()
    private var collectorJob: Job? = null
    private val displayDecorationListenersWithDispatcher =
        ConcurrentHashMap<DisplayDecorationListener, CoroutineDispatcher>()

    /**
     * Registers a [DisplayDecorationListener] to be notified when the list of displays with
     * decorations changes.
     *
     * @param listener The listener to register.
     * @param dispatcher The dispatcher to use when notifying the listener.
     */
    fun registerDisplayDecorationListener(
        listener: DisplayDecorationListener,
        dispatcher: CoroutineDispatcher,
    ) {
        var initialDisplayIdsForListener: Set<Int> = emptySet()
        bgApplicationScope.launch {
            mutex.withLock {
                displayDecorationListenersWithDispatcher[listener] = dispatcher
                initialDisplayIdsForListener =
                    displayRepository.displayIdsWithSystemDecorations.value
                startCollectingIfNeeded(initialDisplayIdsForListener)
            }
            // Emit all the existing displays with decorations when registering.
            initialDisplayIdsForListener.forEach { displayId ->
                withContext(dispatcher) { listener.onDisplayAddSystemDecorations(displayId) }
            }
        }
    }

    /**
     * Unregisters a [DisplayDecorationListener].
     *
     * @param listener The listener to unregister.
     */
    fun unregisterDisplayDecorationListener(listener: DisplayDecorationListener) {
            bgApplicationScope.launch {
                mutex.withLock {
                    displayDecorationListenersWithDispatcher.remove(listener)
                    // stop collecting if no listeners
                    if (displayDecorationListenersWithDispatcher.isEmpty()) {
                        collectorJob?.cancel()
                        collectorJob = null
                    }
                }
            }
    }

    @GuardedBy("mutex")
    private fun startCollectingIfNeeded(lastDisplaysWithDecorations: Set<Int>) {
        if (collectorJob?.isActive == true) {
            return
        }
        var oldDisplays: Set<Int> = lastDisplaysWithDecorations
        collectorJob =
            bgApplicationScope.launch {
                displayRepository.displayIdsWithSystemDecorations.collect { currentDisplays ->
                    val previous = oldDisplays
                    oldDisplays = currentDisplays

                    val newDisplaysWithDecorations = currentDisplays - previous
                    val removedDisplays = previous - currentDisplays
                    displayDecorationListenersWithDispatcher.forEach { (listener, dispatcher) ->
                        withContext(dispatcher) {
                            newDisplaysWithDecorations.forEach { displayId ->
                                listener.onDisplayAddSystemDecorations(displayId)
                            }
                            removedDisplays.forEach { displayId ->
                                listener.onDisplayRemoveSystemDecorations(displayId)
                            }
                        }
                    }
                }
            }
    }
}
