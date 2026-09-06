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

package com.android.launcher3.appfunctions.workspace.validators

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.appfunctions.workspace.ErrorCode
import com.android.launcher3.appfunctions.workspace.HotseatItemSpec
import com.android.launcher3.appfunctions.workspace.HotseatSpec
import com.android.launcher3.appfunctions.workspace.ItemSelectorSpec
import com.android.launcher3.appfunctions.workspace.RemoveItemParamsSpec
import com.android.launcher3.appfunctions.workspace.UnplacedAppSpec
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceItemSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceRepository
import com.android.launcher3.appfunctions.workspace.WorkspaceScreenSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTransaction
import com.android.launcher3.workspacefunctions.testing.FakeWorkspaceRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoveItemValidatorTest {

    private val workspaceRepository = FakeWorkspaceRepository()

    @Test
    fun validate_hotseatItem_found_returnsValid(): Unit = runBlocking {
        workspaceRepository.workspace = createWorkspace(
            hotseat = HotseatSpec(items = listOf(HotseatItemSpec(packageName = "com.pkg", className = "com.cls")))
        )
        val validator = RemoveItemValidator(
            params = RemoveItemParamsSpec(item = createSelector(hotseatRank = 0)),
            repository = workspaceRepository
        )

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun validate_hotseatItem_notFound_returnsInvalid(): Unit = runBlocking {
        workspaceRepository.workspace = createWorkspace(hotseat = HotseatSpec(items = emptyList()))
        val validator = RemoveItemValidator(
            params = RemoveItemParamsSpec(item = createSelector(hotseatRank = 0)),
            repository = workspaceRepository
        )

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((result as ValidationResult.Invalid).errorCode?.code).isEqualTo(ErrorCode.ITEM_NOT_FOUND)
    }

    @Test
    fun validate_coordinateItem_found_returnsValid(): Unit = runBlocking {
        workspaceRepository.workspace = createWorkspace(
            screens = listOf(
                WorkspaceScreenSpec(items = listOf(WorkspaceItemSpec(x = 1, y = 1)))
            )
        )
        val validator = RemoveItemValidator(
            params = RemoveItemParamsSpec(item = createSelector(screenIndex = 0, x = 1, y = 1)),
            repository = workspaceRepository
        )

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun validate_coordinateItem_notFound_returnsInvalid(): Unit = runBlocking {
        workspaceRepository.workspace = createWorkspace(
            screens = listOf(WorkspaceScreenSpec(items = emptyList()))
        )
        val validator = RemoveItemValidator(
            params = RemoveItemParamsSpec(item = createSelector(screenIndex = 0, x = 1, y = 1)),
            repository = workspaceRepository
        )

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun validate_labelItem_foundInScreen_returnsValid(): Unit = runBlocking {
        workspaceRepository.workspace = createWorkspace(
            screens = listOf(
                WorkspaceScreenSpec(items = listOf(WorkspaceItemSpec(x = 0, y = 0, label = "Gmail")))
            )
        )
        val validator = RemoveItemValidator(
            params = RemoveItemParamsSpec(item = createSelector(label = "gmail")),
            repository = workspaceRepository
        )

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun validate_labelItem_foundInHotseat_returnsValid(): Unit = runBlocking {
        workspaceRepository.workspace = createWorkspace(
            hotseat = HotseatSpec(items = listOf(HotseatItemSpec(label = "Gmail")))
        )
        val validator = RemoveItemValidator(
            params = RemoveItemParamsSpec(item = createSelector(label = "Gmail")),
            repository = workspaceRepository
        )

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun validate_componentItem_foundInScreen_returnsValid(): Unit = runBlocking {
        workspaceRepository.workspace = createWorkspace(
            screens = listOf(
                WorkspaceScreenSpec(items = listOf(WorkspaceItemSpec(x = 0, y = 0, packageName = "com.pkg", className = "com.cls")))
            )
        )
        val validator = RemoveItemValidator(
            params = RemoveItemParamsSpec(item = createSelector(packageName = "com.pkg", className = "com.cls")),
            repository = workspaceRepository
        )

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    private fun createWorkspace(
        screens: List<WorkspaceScreenSpec> = emptyList(),
        hotseat: HotseatSpec = HotseatSpec(emptyList())
    ) = WorkspaceSpec(
        screens = screens,
        hotseat = hotseat,
        rows = null,
        columns = null
    )

    private fun createSelector(
        label: String? = null,
        screenIndex: Int? = null,
        x: Int? = null,
        y: Int? = null,
        hotseatRank: Int? = null,
        packageName: String? = null,
        className: String? = null
    ) = ItemSelectorSpec(
        label = label,
        screenIndex = screenIndex,
        x = x,
        y = y,
        hotseatRank = hotseatRank,
        packageName = packageName,
        className = className
    )
}
