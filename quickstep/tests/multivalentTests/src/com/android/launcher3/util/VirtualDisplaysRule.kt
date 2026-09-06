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

package com.android.launcher3.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.test.core.app.ApplicationProvider
import com.android.app.displaylib.DisplayDecorationListener
import com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/**
 * Manages multiple displays at runtime.
 *
 * Use [registerDisplayDecorationListener] to notify tested code when displays are changed. This
 * rule handles system decoration changes, because [DisplaysWithDecorationsRepositoryCompat] does
 * not respond to [VirtualDisplay] changes in Robolectric.
 *
 * All [virtualDisplays] are released upon teardown.
 */
class VirtualDisplaysRule(context: Context = ApplicationProvider.getApplicationContext()) {

    private val displayManager =
        requireNotNull(context.getSystemService(DisplayManager::class.java))
    private val virtualDisplays = mutableMapOf<Int, VirtualDisplay>()
    private val displayDecorationListeners =
        mutableListOf<Pair<DisplayDecorationListener, CoroutineDispatcher>>()

    /**
     * Adds a display with the provided parameters, and notifies system decorations addition.
     *
     * Returns the added display ID.
     */
    fun add(
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        density: Int = DEFAULT_DENSITY,
    ): Int {
        val virtualDisplay =
            displayManager.createVirtualDisplay(
                "${DISPLAY_NAME_PREFIX}${virtualDisplays.size}",
                width,
                height,
                density,
                /* surface = */ null,
                /* flags = */ 0,
            )

        val displayId = virtualDisplay.display.displayId
        virtualDisplays[displayId] = virtualDisplay

        runBlocking {
            displayDecorationListeners
                .map { (decorator, dispatcher) ->
                    async(dispatcher) { decorator.onDisplayAddSystemDecorations(displayId) }
                }
                .awaitAll()
        }
        return displayId
    }

    /**
     * Removes and releases [displayId], and notifies system decoration removal.
     *
     * Throws [IllegalArgumentException] if the display is not being managed by this rule.
     */
    fun remove(displayId: Int) {
        val virtualDisplay =
            virtualDisplays.remove(displayId)
                ?: throw IllegalArgumentException("Display $displayId not added")

        val displayId = virtualDisplay.display.displayId
        runBlocking {
            displayDecorationListeners
                .map { (decorator, dispatcher) ->
                    async(dispatcher) { decorator.onDisplayRemoveSystemDecorations(displayId) }
                }
                .awaitAll()
        }

        virtualDisplay.release()
    }

    fun registerDisplayDecorationListener(
        listener: DisplayDecorationListener,
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
    ) {
        displayDecorationListeners += listener to dispatcher

        runBlocking {
            async(dispatcher) {
                    virtualDisplays.forEach { listener.onDisplayAddSystemDecorations(it.key) }
                }
                .await()
        }
    }

    /** Returns the [VirtualDisplay] for [displayId]. */
    operator fun get(displayId: Int): VirtualDisplay? = virtualDisplays[displayId]

    fun cleanup() {
        for (v in virtualDisplays.values) v.release()
        displayDecorationListeners.clear()
    }

    companion object {
        private const val DISPLAY_NAME_PREFIX = "VirtualDisplayRule"

        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
        const val DEFAULT_DENSITY = 160
    }
}
