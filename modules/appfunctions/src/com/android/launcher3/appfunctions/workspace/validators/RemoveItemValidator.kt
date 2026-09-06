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

import com.android.launcher3.appfunctions.workspace.ErrorCode
import com.android.launcher3.appfunctions.workspace.ItemSelectorSpec
import com.android.launcher3.appfunctions.workspace.RemoveItemParamsSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceRepository
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec

/**
 * Validates that the item requested to be removed exists in the workspace.
 *
 * @property params The parameters for the removal request.
 * @property repository The repository to check the workspace state.
 */
class RemoveItemValidator(
    private val params: RemoveItemParamsSpec,
    private val repository: WorkspaceRepository
) : SelectorValidator {

    override suspend fun validate(): ValidationResult {
        val itemSelectorValidation = ItemSelectorValidator(params.item).validate()
        if (itemSelectorValidation is ValidationResult.Invalid) {
            return itemSelectorValidation
        }

        val workspace = repository.getWorkspace()
        val itemFound = isItemPresent(workspace, params.item)

        return if (itemFound) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(
                message = "Item not found",
                errorCode = ErrorCode(ErrorCode.ITEM_NOT_FOUND)
            )
        }
    }

    private fun isItemPresent(workspace: WorkspaceSpec, selector: ItemSelectorSpec): Boolean {
        return when {
            selector.hotseatRank != null -> {
                selector.hotseatRank in workspace.hotseat.items.indices
            }

            selector.screenIndex != null && selector.x != null && selector.y != null -> {
                isAtCell(workspace, selector.screenIndex, selector.x, selector.y)
            }

            selector.label != null -> {
                isLabelPresent(workspace, selector.label)
            }

            selector.packageName != null && selector.className != null -> {
                isComponentPresent(workspace, selector.packageName, selector.className)
            }

            else -> false
        }
    }

    private fun isAtCell(workspace: WorkspaceSpec, screenIndex: Int, x: Int, y: Int): Boolean {
        val screen = workspace.screens.getOrNull(screenIndex)
        return screen?.items?.any { it.x == x && it.y == y } == true
    }

    private fun isLabelPresent(workspace: WorkspaceSpec, label: String): Boolean {
        val labelMatch: (String?) -> Boolean = { it?.equals(label, ignoreCase = true) == true }

        val foundInScreens = workspace.screens.any { screen ->
            screen.items.any { item ->
                labelMatch(item.label) || labelMatch(item.appLabel) || labelMatch(item.title) ||
                        item.items?.any { labelMatch(it.label) } == true
            }
        }
        if (foundInScreens) return true

        return workspace.hotseat.items.any { item ->
            labelMatch(item.label) || labelMatch(item.appLabel) || labelMatch(item.title) ||
                    item.items?.any { labelMatch(it.label) } == true
        }
    }

    private fun isComponentPresent(
        workspace: WorkspaceSpec,
        packageName: String,
        className: String
    ): Boolean {
        val componentMatch: (String?, String?) -> Boolean = { p, c ->
            p == packageName && c == className
        }

        val foundInScreens = workspace.screens.any { screen ->
            screen.items.any { item ->
                (componentMatch(item.packageName, item.className)) ||
                        item.items?.any { componentMatch(it.packageName, it.className) } == true
            }
        }
        if (foundInScreens) return true

        return workspace.hotseat.items.any { item ->
            (componentMatch(item.packageName, item.className)) ||
                    item.items?.any { componentMatch(it.packageName, it.className) } == true
        }
    }
}

