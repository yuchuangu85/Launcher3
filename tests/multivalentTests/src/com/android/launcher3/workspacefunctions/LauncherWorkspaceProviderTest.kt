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
package com.android.launcher3.workspacefunctions

import android.util.SparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceChangeEvent
import com.android.launcher3.model.data.WorkspaceData.ImmutableWorkspaceData
import com.android.launcher3.model.repository.HomeScreenRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [LauncherWorkspaceProvider]. */
@RunWith(AndroidJUnit4::class)
class LauncherWorkspaceProviderTest {

    private val homeScreenRepository = HomeScreenRepository()
    private lateinit var provider: LauncherWorkspaceProvider

    @Before
    fun setUp() {
        provider = LauncherWorkspaceProvider(homeScreenRepository)
    }

    @Test
    fun getWorkspace_returnsCurrentSnapshotFromRepository() = runTest {
        val expectedData = ImmutableWorkspaceData(1, 1, SparseArray<ItemInfo>())
        homeScreenRepository.dispatchWorkspaceDataChange(
            expectedData,
            WorkspaceChangeEvent.FullRefresh("test"),
        )

        val actualData = provider.getWorkspace()

        assertThat(actualData).isEqualTo(expectedData)
    }
}
