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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/** Wraps [LifecycleRegistry] and check thread on [targetExecutor]. */
class LifecycleRegistryWrapper
private constructor(
    private val base: LifecycleRegistry,
    private val targetExecutor: LooperExecutor?,
) : Lifecycle() {

    /**
     * Creates a registry confined to the specific [targetExecutor].
     *
     * Design Note: We use [LifecycleRegistry.createUnsafe] to bypass the default Main Thread
     * checks. This shifts the responsibility of thread safety to this wrapper, which verifies
     * [targetExecutor] before every delegation.
     */
    constructor(
        owner: LifecycleOwner,
        targetExecutor: LooperExecutor,
    ) : this(
        base = @Suppress("VisibleForTests") LifecycleRegistry.createUnsafe(owner),
        targetExecutor = targetExecutor,
    )

    /** Creates a standard, Main-Thread-bound [LifecycleRegistry]. */
    constructor(
        owner: LifecycleOwner
    ) : this(base = LifecycleRegistry(owner), targetExecutor = null)

    override fun addObserver(observer: LifecycleObserver) {
        if (targetExecutor != null) {
            Preconditions.assertThreadOnExecutor(targetExecutor)
        }
        base.addObserver(observer)
    }

    override fun removeObserver(observer: LifecycleObserver) {
        if (targetExecutor != null) {
            Preconditions.assertThreadOnExecutor(targetExecutor)
        }
        base.removeObserver(observer)
    }

    fun handleLifecycleEvent(event: Event) {
        if (targetExecutor != null) {
            Preconditions.assertThreadOnExecutor(targetExecutor)
        }
        base.handleLifecycleEvent(event)
    }

    override var currentState: State
        get() {
            if (targetExecutor != null) {
                Preconditions.assertThreadOnExecutor(targetExecutor)
            }
            return base.currentState
        }
        set(value) {
            if (targetExecutor != null) {
                Preconditions.assertThreadOnExecutor(targetExecutor)
            }
            base.currentState = value
        }
}
