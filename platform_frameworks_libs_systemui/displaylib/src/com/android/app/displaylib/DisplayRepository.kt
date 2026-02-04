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

import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED
import android.hardware.display.DisplayManager.DisplayListener
import android.hardware.display.DisplayManager.EVENT_TYPE_DISPLAY_ADDED
import android.hardware.display.DisplayManager.EVENT_TYPE_DISPLAY_CHANGED
import android.hardware.display.DisplayManager.EVENT_TYPE_DISPLAY_REMOVED
import android.os.Handler
import android.util.Log
import android.view.Display
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/** Repository for providing access to display related information and events. */
interface DisplayRepository {
    /** Provides the current set of displays. */
    val displays: StateFlow<Set<Display>>

    /** Display change event indicating a change to the given displayId has occurred. */
    val displayChangeEvent: Flow<Int>

    /** Display addition event indicating a new display has been added. */
    val displayAdditionEvent: Flow<Display?>

    /** Display removal event indicating a display has been removed. */
    val displayRemovalEvent: Flow<Int>

    /**
     * Provides the current set of display ids.
     *
     * Note that it is preferred to use this instead of [displays] if only the
     * [Display.getDisplayId] is needed.
     */
    val displayIds: StateFlow<Set<Int>>

    /**
     * Pending display id that can be enabled/disabled.
     *
     * When `null`, it means there is no pending display waiting to be enabled.
     */
    val pendingDisplay: Flow<PendingDisplay?>

    /** Whether the default display is currently off. */
    val defaultDisplayOff: Flow<Boolean>

    /**
     * Given a display ID int, return the corresponding Display object, or null if none exist.
     *
     * This method will not result in a binder call in most cases. The only exception is if there is
     * an existing binder call ongoing to get the [Display] instance already. In that case, this
     * will wait for the end of the binder call.
     */
    fun getDisplay(displayId: Int): Display?

    /**
     * As [getDisplay], but it's always guaranteed to not block on any binder call.
     *
     * This might return null if the display id was not mapped to a [Display] object yet.
     */
    fun getCachedDisplay(displayId: Int): Display? =
        displays.value.firstOrNull { it.displayId == displayId }

    /**
     * Returns whether the given displayId is in the set of enabled displays.
     *
     * This is guaranteed to not cause a binder call. Use this instead of [getDisplay] (see its docs
     * for why)
     */
    fun containsDisplay(displayId: Int): Boolean = displayIds.value.contains(displayId)

    /** Represents a connected display that has not been enabled yet. */
    interface PendingDisplay {
        /** Id of the pending display. */
        val id: Int

        /** Enables the display, making it available to the system. */
        suspend fun enable()

        /**
         * Ignores the pending display. When called, this specific display id doesn't appear as
         * pending anymore until the display is disconnected and reconnected again.
         */
        suspend fun ignore()

        /** Disables the display, making it unavailable to the system. */
        suspend fun disable()
    }
}

@Singleton
class DisplayRepositoryImpl
@Inject
constructor(
    private val displayManager: DisplayManager,
    backgroundHandler: Handler,
    bgApplicationScope: CoroutineScope,
    backgroundCoroutineDispatcher: CoroutineDispatcher,
) : DisplayRepository {
    private val allDisplayEvents: Flow<DisplayEvent> =
        callbackFlow {
                val callback =
                    object : DisplayListener {
                        override fun onDisplayAdded(displayId: Int) {
                            trySend(DisplayEvent.Added(displayId))
                        }

                        override fun onDisplayRemoved(displayId: Int) {
                            trySend(DisplayEvent.Removed(displayId))
                        }

                        override fun onDisplayChanged(displayId: Int) {
                            trySend(DisplayEvent.Changed(displayId))
                        }
                    }
                displayManager.registerDisplayListener(
                    callback,
                    backgroundHandler,
                    EVENT_TYPE_DISPLAY_ADDED or
                        EVENT_TYPE_DISPLAY_CHANGED or
                        EVENT_TYPE_DISPLAY_REMOVED,
                )
                awaitClose { displayManager.unregisterDisplayListener(callback) }
            }
            .conflate()
            .onStart { emit(DisplayEvent.Changed(Display.DEFAULT_DISPLAY)) }
            .debugLog("allDisplayEvents")
            .flowOn(backgroundCoroutineDispatcher)

    override val displayChangeEvent: Flow<Int> =
        allDisplayEvents.filterIsInstance<DisplayEvent.Changed>().map { event -> event.displayId }

    override val displayRemovalEvent: Flow<Int> =
        allDisplayEvents.filterIsInstance<DisplayEvent.Removed>().map { it.displayId }

    // This is necessary because there might be multiple displays, and we could
    // have missed events for those added before this process or flow started.
    // Note it causes a binder call from the main thread (it's traced).
    private val initialDisplays: Set<Display> = displayManager.displays?.toSet() ?: emptySet()
    private val initialDisplayIds = initialDisplays.map { display -> display.displayId }.toSet()

    /** Propagate to the listeners only enabled displays */
    private val enabledDisplayIds: StateFlow<Set<Int>> =
        allDisplayEvents
            .scan(initial = initialDisplayIds) { previousIds: Set<Int>, event: DisplayEvent ->
                val id = event.displayId
                when (event) {
                    is DisplayEvent.Removed -> previousIds - id
                    is DisplayEvent.Added,
                    is DisplayEvent.Changed -> previousIds + id
                }
            }
            .distinctUntilChanged()
            .debugLog("enabledDisplayIds")
            .stateIn(bgApplicationScope, SharingStarted.WhileSubscribed(), initialDisplayIds)

    private val defaultDisplay by lazy {
        getDisplayFromDisplayManager(Display.DEFAULT_DISPLAY)
            ?: error("Unable to get default display.")
    }
    /**
     * Represents displays that went though the [DisplayListener.onDisplayAdded] callback.
     *
     * Those are commonly the ones provided by [DisplayManager.getDisplays] by default.
     */
    private val enabledDisplays: StateFlow<Set<Display>> =
        enabledDisplayIds
            .mapElementsLazily { displayId -> getDisplayFromDisplayManager(displayId) }
            .onEach {
                if (it.isEmpty()) Log.wtf(TAG, "No enabled displays. This should never happen.")
            }
            .flowOn(backgroundCoroutineDispatcher)
            .debugLog("enabledDisplays")
            .stateIn(
                bgApplicationScope,
                started = SharingStarted.WhileSubscribed(),
                // This triggers a single binder call on the UI thread per process. The
                // alternative would be to use sharedFlows, but they are prohibited due to
                // performance concerns.
                // Ultimately, this is a trade-off between a one-time UI thread binder call and
                // the constant overhead of sharedFlows.
                initialValue = initialDisplays,
            )

    /**
     * Represents displays that went though the [DisplayListener.onDisplayAdded] callback.
     *
     * Those are commonly the ones provided by [DisplayManager.getDisplays] by default.
     */
    override val displays: StateFlow<Set<Display>> = enabledDisplays

    override val displayIds: StateFlow<Set<Int>> = enabledDisplayIds

    /**
     * Implementation that maps from [displays], instead of [allDisplayEvents] for 2 reasons:
     * 1. Guarantee that it emits __after__ [displays] emitted. This way it is guaranteed that
     *    calling [getDisplay] for the newly added display will be non-null.
     * 2. Reuse the existing instance of [Display] without a new call to [DisplayManager].
     */
    override val displayAdditionEvent: Flow<Display?> =
        displays
            .pairwiseBy { previousDisplays, currentDisplays -> currentDisplays - previousDisplays }
            .flatMapLatest { it.asFlow() }

    val _ignoredDisplayIds = MutableStateFlow<Set<Int>>(emptySet())
    private val ignoredDisplayIds: Flow<Set<Int>> = _ignoredDisplayIds.debugLog("ignoredDisplayIds")

    private fun getInitialConnectedDisplays(): Set<Int> =
            displayManager
                .getDisplays(DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)
                .map { it.displayId }
                .toSet()
                .also {
                    if (DEBUG) {
                        Log.d(TAG, "getInitialConnectedDisplays: $it")
                    }
                }

    /* keeps connected displays until they are disconnected. */
    private val connectedDisplayIds: StateFlow<Set<Int>> =
        callbackFlow {
                val connectedIds = getInitialConnectedDisplays().toMutableSet()
                val callback =
                    object : DisplayConnectionListener {
                        override fun onDisplayConnected(id: Int) {
                            if (DEBUG) {
                                Log.d(TAG, "display with id=$id connected.")
                            }
                            connectedIds += id
                            _ignoredDisplayIds.value -= id
                            trySend(connectedIds.toSet())
                        }

                        override fun onDisplayDisconnected(id: Int) {
                            connectedIds -= id
                            if (DEBUG) {
                                Log.d(TAG, "display with id=$id disconnected.")
                            }
                            _ignoredDisplayIds.value -= id
                            trySend(connectedIds.toSet())
                        }
                    }
                trySend(connectedIds.toSet())
                displayManager.registerDisplayListener(
                    callback,
                    backgroundHandler,
                    /* eventFlags */ 0,
                    DisplayManager.PRIVATE_EVENT_TYPE_DISPLAY_CONNECTION_CHANGED,
                )
                awaitClose { displayManager.unregisterDisplayListener(callback) }
            }
            .conflate()
            .distinctUntilChanged()
            .debugLog("connectedDisplayIds")
            .stateIn(
                bgApplicationScope,
                started = SharingStarted.WhileSubscribed(),
                // The initial value is set to empty, but connected displays are gathered as soon as
                // the flow starts being collected. This is to ensure the call to get displays (an
                // IPC) happens in the background instead of when this object
                // is instantiated.
                initialValue = emptySet(),
            )

    private val connectedExternalDisplayIds: Flow<Set<Int>> =
        connectedDisplayIds
            .map { connectedDisplayIds ->
                    connectedDisplayIds
                        .filter { id -> getDisplayType(id) == Display.TYPE_EXTERNAL }
                        .toSet()
            }
            .flowOn(backgroundCoroutineDispatcher)
            .debugLog("connectedExternalDisplayIds")

    private fun getDisplayType(displayId: Int): Int? = displayManager.getDisplay(displayId)?.type

    private fun getDisplayFromDisplayManager(displayId: Int): Display? = displayManager.getDisplay(displayId)

    /**
     * Pending displays are the ones connected, but not enabled and not ignored.
     *
     * A connected display is ignored after the user makes the decision to use it or not. For now,
     * the initial decision from the user is final and not reversible.
     */
    private val pendingDisplayIds: Flow<Set<Int>> =
        combine(enabledDisplayIds, connectedExternalDisplayIds, ignoredDisplayIds) {
                enabledDisplaysIds,
                connectedExternalDisplayIds,
                ignoredDisplayIds ->
                if (DEBUG) {
                    Log.d(
                        TAG,
                        "combining enabled=$enabledDisplaysIds, " +
                            "connectedExternalDisplayIds=$connectedExternalDisplayIds, " +
                            "ignored=$ignoredDisplayIds",
                    )
                }
                connectedExternalDisplayIds - enabledDisplaysIds - ignoredDisplayIds
            }
            .debugLog("allPendingDisplayIds")

    /** Which display id should be enabled among the pending ones. */
    private val pendingDisplayId: Flow<Int?> =
        pendingDisplayIds.map { it.maxOrNull() }.distinctUntilChanged().debugLog("pendingDisplayId")

    override val pendingDisplay: Flow<DisplayRepository.PendingDisplay?> =
        pendingDisplayId
            .map { displayId ->
                val id = displayId ?: return@map null
                object : DisplayRepository.PendingDisplay {
                    override val id = id

                    override suspend fun enable() {
                            if (DEBUG) {
                                Log.d(TAG, "Enabling display with id=$id")
                            }
                            displayManager.enableConnectedDisplay(id)
                        // After the display has been enabled, it is automatically ignored.
                        ignore()
                    }

                    override suspend fun ignore() {
                            _ignoredDisplayIds.value += id
                    }

                    override suspend fun disable() {
                        ignore()
                            if (DEBUG) {
                                Log.d(TAG, "Disabling display with id=$id")
                            }
                            displayManager.disableConnectedDisplay(id)
                    }
                }
            }
            .debugLog("pendingDisplay")

    override val defaultDisplayOff: Flow<Boolean> =
        displayChangeEvent
            .filter { it == Display.DEFAULT_DISPLAY }
            .map { defaultDisplay.state == Display.STATE_OFF }
            .distinctUntilChanged()

    override fun getDisplay(displayId: Int): Display? {
        val cachedDisplay = getCachedDisplay(displayId)
        if (cachedDisplay != null) return cachedDisplay
        // cachedDisplay could be null for 2 reasons:
        // 1. the displayId is being mapped to a display in the background, but the binder call is
        // not done
        // 2. the display is not there
        // In case of option one, let's get it synchronously from display manager to make sure for
        // this to be consistent.
        return if (displayIds.value.contains(displayId)) {
                getDisplayFromDisplayManager(displayId)
        } else {
            null
        }
    }

    private fun <T> Flow<T>.debugLog(flowName: String): Flow<T> {
        return if (DEBUG) {
            // LC-Ignored
            this
        } else {
            this
        }
    }

    /**
     * Maps a set of T to a set of V, minimizing the number of `createValue` calls taking into
     * account the diff between each root flow emission.
     *
     * This is needed to minimize the number of [getDisplayFromDisplayManager] in this class. Note
     * that if the [createValue] returns a null element, it will not be added in the output set.
     */
    private fun <T, V> Flow<Set<T>>.mapElementsLazily(createValue: (T) -> V?): Flow<Set<V>> {
        data class State<T, V>(
            val previousSet: Set<T>,
            // Caches T values from the previousSet that were already converted to V
            val valueMap: Map<T, V>,
            val resultSet: Set<V>,
        )

        val emptyInitialState = State(emptySet<T>(), emptyMap(), emptySet<V>())
        return this.scan(emptyInitialState) { state, currentSet ->
                if (currentSet == state.previousSet) {
                    state
                } else {
                    val removed = state.previousSet - currentSet
                    val added = currentSet - state.previousSet
                    val newMap = state.valueMap.toMutableMap()

                    added.forEach { key -> createValue(key)?.let { newMap[key] = it } }
                    removed.forEach { key -> newMap.remove(key) }

                    val resultSet = newMap.values.toSet()
                    State(currentSet, newMap, resultSet)
                }
            }
            .filter { it != emptyInitialState }
            .map { it.resultSet }
    }

    private companion object {
        const val TAG = "DisplayRepository"
        val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }
}

/** Used to provide default implementations for all methods. */
private interface DisplayConnectionListener : DisplayListener {

    override fun onDisplayConnected(id: Int) {}

    override fun onDisplayDisconnected(id: Int) {}

    override fun onDisplayAdded(id: Int) {}

    override fun onDisplayRemoved(id: Int) {}

    override fun onDisplayChanged(id: Int) {}
}

private sealed interface DisplayEvent {
    val displayId: Int

    data class Added(override val displayId: Int) : DisplayEvent

    data class Removed(override val displayId: Int) : DisplayEvent

    data class Changed(override val displayId: Int) : DisplayEvent
}

/**
 * Returns a new [Flow] that combines the two most recent emissions from [this] using [transform].
 * Note that the new Flow will not start emitting until it has received two emissions from the
 * upstream Flow.
 *
 * Useful for code that needs to compare the current value to the previous value.
 *
 * Note this has been taken from com.android.systemui.util.kotlin. It was copied to keep deps of
 * displaylib minimal (and avoid creating a new shared lib for it).
 */
fun <T, R> Flow<T>.pairwiseBy(transform: suspend (old: T, new: T) -> R): Flow<R> = flow {
    val noVal = Any()
    var previousValue: Any? = noVal
    collect { newVal ->
        if (previousValue != noVal) {
            @Suppress("UNCHECKED_CAST") emit(transform(previousValue as T, newVal))
        }
        previousValue = newVal
    }
}
