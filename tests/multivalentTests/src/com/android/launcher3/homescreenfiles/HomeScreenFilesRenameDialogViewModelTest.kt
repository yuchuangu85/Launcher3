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

package com.android.launcher3.homescreenfiles

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.Launcher
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.SandboxApplication
import com.android.tools.dagger.mutation.annotations.BindValue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [HomeScreenFilesRenameDialogViewModel]. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class HomeScreenFilesRenameDialogViewModelTest {
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @BindValue @Mock lateinit var provider: HomeScreenFilesProvider
    @Mock private lateinit var file: HomeScreenFile
    private lateinit var viewModel: HomeScreenFilesRenameDialogViewModel

    @Before
    fun setUp() {
        // Set up mocks.
        whenever(file.displayName).thenReturn("File")
        whenever(file.uri).thenReturn(mock())
        whenever(provider.onReady()).thenReturn(CompletableFuture())
        whenever(provider.rename(any(), any())).thenReturn(completedFuture(true))

        // Set up view model.
        viewModel =
            launcherActivity.getFromLauncher { launcher ->
                HomeScreenFilesRenameDialogViewModel(launcher, file, provider)
            }!!
    }

    @Test
    fun testPositiveButtonClick() {
        // Verify that triggering a positive button click event performs a rename.
        val name = "${file.displayName} (1)"
        viewModel.name.value = viewModel.name.value.copy(text = name)
        assertTrue(viewModel.onPositiveButtonClick?.invoke(viewModel) ?: false)
        verify(provider).rename(file.uri, name)
    }
}
