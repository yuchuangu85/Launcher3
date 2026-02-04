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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reports the display ids that should have a per-display instance, if any.
 *
 * This can be overridden to support different policies (e.g. display being connected, display
 * having decorations, etc..). A [PerDisplayRepository] instance is expected to be cleaned up when a
 * displayId is removed from this set.
 */
interface DisplayInstanceLifecycleManager {
    /** Set of display ids that are allowed to have an instance. */
    val displayIds: StateFlow<Set<Int>>
}

/** Meant to be used in tests. */
class FakeDisplayInstanceLifecycleManager : DisplayInstanceLifecycleManager {
    override val displayIds = MutableStateFlow<Set<Int>>(emptySet())
}
