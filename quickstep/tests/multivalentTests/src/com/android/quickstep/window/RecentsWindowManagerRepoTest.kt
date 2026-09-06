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

package com.android.quickstep.window

import android.view.Display.DEFAULT_DISPLAY
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.SandboxApplication
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class RecentsWindowManagerRepoTest {

    @get:Rule val context = SandboxApplication()

    @Test
    fun recentsWindowManager_not_created_preemptively() {
        val displayIds = mutableSetOf<Int>()
        assertThat(context.appComponent.perDisplayComponentRepository[DEFAULT_DISPLAY]).isNotNull()
        context.appComponent.recentsWindowManagerRepository.forEach(createIfAbsent = false) {
            displayIds.add(it.asContext().displayId)
        }

        assertThat(displayIds).isEmpty()
    }

    @Test
    fun recentsWindowManager_available_when_already_created() {
        val displayIds = mutableSetOf<Int>()
        assertThat(context.appComponent.recentsWindowManagerRepository[DEFAULT_DISPLAY]).isNotNull()
        context.appComponent.recentsWindowManagerRepository.forEach(createIfAbsent = false) {
            displayIds.add(it.asContext().displayId)
        }

        assertThat(displayIds).containsExactly(DEFAULT_DISPLAY)
    }

    @Test
    fun recentsWindowManager_created_when_needed() {
        val displayIds = mutableSetOf<Int>()
        assertThat(context.appComponent.perDisplayComponentRepository[DEFAULT_DISPLAY]).isNotNull()
        context.appComponent.recentsWindowManagerRepository.forEach(createIfAbsent = true) {
            displayIds.add(it.asContext().displayId)
        }

        assertThat(displayIds).containsExactly(DEFAULT_DISPLAY)
    }
}
