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

package com.android.launcher3.automation

import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.Executors.IMMEDIATE_EXECUTOR
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AutomationNoOpRepositoryTest {
    private lateinit var automationRepo: AutomationNoOpRepository

    @Before
    fun setup() {
        automationRepo = AutomationNoOpRepository()
    }

    @Test
    fun automationChanges_stream_neverEmits() {
        val receivedChanges = mutableListOf<AutomationChange>()
        automationRepo.automatedPackages.changes
            .forEach(IMMEDIATE_EXECUTOR) { receivedChanges.add(it) }
            .use { assertThat(receivedChanges).isEmpty() }
    }

    @Test
    fun isPackageAutomated_always_returnsFalse() {
        // Given
        val user = UserHandle.of(0)
        val packageName = "com.test.any.package"

        // Then
        assertThat(automationRepo.isPackageAutomated(user, packageName)).isFalse()
        assertThat(automationRepo.isPackageAutomated(user.identifier, packageName)).isFalse()
    }
}
