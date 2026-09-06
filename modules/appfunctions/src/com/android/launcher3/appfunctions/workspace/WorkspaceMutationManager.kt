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

package com.android.launcher3.appfunctions.workspace

import com.android.launcher3.appfunctions.workspace.WorkspaceAppFunctions.Proof
import com.android.launcher3.appfunctions.workspace.validators.RemoveItemValidator
import com.android.launcher3.appfunctions.workspace.validators.ValidationResult

/**
 * Manages workspace mutations by validating requests before execution.
 */
class WorkspaceMutationManager(private val repository: WorkspaceRepository) {

    /**
     * Executes a remove item operation after validation.
     *
     * @param params Parameters for the remove operation.
     * @return [WorkspaceUpdateResult] indicating success or failure.
     */
    suspend fun removeItem(params: RemoveItemParamsSpec): WorkspaceUpdateResult {
        val validator = RemoveItemValidator(params, repository)
        return when (val validationResult = validator.validate()) {
            is ValidationResult.Valid -> {
                val newWorkspace = repository.newTransaction()
                    .removeItem(params)
                    .commit()

        // TODO b/494314201: add diffing logic
        // TODO b/493993708: replace any dummy data with real implementation
                WorkspaceUpdateResult(
                    success = true,
                    message = "Item removed",
                    changes = "Removed item ${params.item}",
                    errorCode = null,
                    resolvedItemIdentifier = null,
                    resolutionDetails = null,
                    proof = Proof.REMOVE_ITEM_PROOF
                )
            }

            is ValidationResult.Invalid -> {
                WorkspaceUpdateResult(
                    success = false,
                    message = validationResult.message,
                    changes = null,
                    errorCode = validationResult.errorCode,
                    resolvedItemIdentifier = null,
                    resolutionDetails = validationResult.resolutionDetails,
                    proof = Proof.REMOVE_ITEM_PROOF
                )
            }
        }
    }
}
