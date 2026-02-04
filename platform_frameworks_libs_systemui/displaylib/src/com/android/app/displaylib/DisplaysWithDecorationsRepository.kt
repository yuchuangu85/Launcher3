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

import android.content.res.Configuration
import android.graphics.Rect
import android.view.IDisplayWindowListener
import android.view.IWindowManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/** Provides the displays with decorations. */
interface DisplaysWithDecorationsRepository {
    /** A [StateFlow] that maintains a set of display IDs that should have system decorations. */
    val displayIdsWithSystemDecorations: StateFlow<Set<Int>>
}

@Singleton
class DisplaysWithDecorationsRepositoryImpl
@Inject
constructor(
    private val windowManager: IWindowManager,
    bgApplicationScope: CoroutineScope,
    displayRepository: DisplayRepository,
) : DisplaysWithDecorationsRepository {

    private val decorationEvents: Flow<Event> = callbackFlow {
        val callback =
            object : IDisplayWindowListener.Stub() {
                override fun onDisplayAddSystemDecorations(displayId: Int) {
                    trySend(Event.Add(displayId))
                }

                override fun onDisplayRemoveSystemDecorations(displayId: Int) {
                    trySend(Event.Remove(displayId))
                }

                override fun onDesktopModeEligibleChanged(displayId: Int) {}

                override fun onDisplayAdded(p0: Int) {}

                override fun onDisplayConfigurationChanged(p0: Int, p1: Configuration?) {}

                override fun onDisplayRemoved(p0: Int) {}

                override fun onFixedRotationStarted(p0: Int, p1: Int) {}

                override fun onFixedRotationFinished(p0: Int) {}

                override fun onKeepClearAreasChanged(
                    p0: Int,
                    p1: MutableList<Rect>?,
                    p2: MutableList<Rect>?,
                ) {}
            }
        windowManager.registerDisplayWindowListener(callback)
        awaitClose { windowManager.unregisterDisplayWindowListener(callback) }
    }

    private val initialDisplayIdsWithDecorations: Set<Int> =
        displayRepository.displayIds.value
            .filter { windowManager.shouldShowSystemDecors(it) }
            .toSet()

    /**
     * A [StateFlow] that maintains a set of display IDs that should have system decorations.
     *
     * Updates to the set are triggered by:
     * - Removing displays via [displayRemovalEvent] emissions.
     *
     * The set is initialized with displays that qualify for system decorations based on
     * [WindowManager.shouldShowSystemDecors].
     */
    override val displayIdsWithSystemDecorations: StateFlow<Set<Int>> =
        merge(decorationEvents, displayRepository.displayRemovalEvent.map { Event.Remove(it) })
            .scan(initialDisplayIdsWithDecorations) { displayIds: Set<Int>, event: Event ->
                when (event) {
                    is Event.Add -> displayIds + event.displayId
                    is Event.Remove -> displayIds - event.displayId
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = bgApplicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = initialDisplayIdsWithDecorations,
            )

    private sealed class Event(val displayId: Int) {
        class Add(displayId: Int) : Event(displayId)

        class Remove(displayId: Int) : Event(displayId)
    }
}
