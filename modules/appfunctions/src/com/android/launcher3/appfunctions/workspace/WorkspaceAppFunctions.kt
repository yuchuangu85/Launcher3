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

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction

/**
 * High level [AppFunctions] interface for managing the workspace. It is paramount that the
 * functions, types and documentation are kept in sync with the evaluation framework. Do not commit
 * changes without testing against the framework.
 *
 * @property repository The repository for querying and modifying the workspace.
 */
class WorkspaceAppFunctions(
    private val repository: WorkspaceRepository
) {
    private val mutationManager = WorkspaceMutationManager(repository)

    /// Query functions
    /// These are exposed [AppFunction]s that can be called by any client
    /// of the AppFunctions framework.

    /**
     * Returns the current [WorkspaceSpec] and a [Proof] token for modifications.
     *
     * @param appFunctionContext App function context.
     * @return [WorkspaceResponse] with workspace and proof.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getCurrentWorkspace(appFunctionContext: AppFunctionContext): WorkspaceResponse {
        val workspaceSpec = repository.getWorkspace()
        return WorkspaceResponse(workspaceSpec, Proof.GET_CURRENT_WORKSPACE_PROOF)
    }

    /**
     * Lists installed apps. May be truncated; if so, `isTruncated` is true.
     *
     * @param appFunctionContext App function context.
     * @param orderByUsageStats If true, orders apps by usage; otherwise uses default order for
     *   personalization.
     * @return [GetInstalledAppsResponse] with apps list and proof.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getInstalledApps(
        appFunctionContext: AppFunctionContext,
        orderByUsageStats: Boolean,
    ): GetInstalledAppsResponse {
        val allAppItems = repository.getInstalledApps(orderByUsageStats)
        return GetInstalledAppsResponse(
            apps = allAppItems,
            proof = Proof.GET_INSTALLED_APPS_PROOF
        )
    }

    /**
     * Lists installed widgets.
     *
     * @param appFunctionContext App function context.
     * @param orderByUsageStats If true, orders widgets by usage; otherwise uses default order for
     *   personalization.
     * @return [GetInstalledWidgetsResponse] with widgets list and proof.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getInstalledWidgets(
        appFunctionContext: AppFunctionContext,
        orderByUsageStats: Boolean,
    ): GetInstalledWidgetsResponse {
        val allWidgetItems = repository.getInstalledWidgets(orderByUsageStats)
        return GetInstalledWidgetsResponse(
            widgets = allWidgetItems,
            proof = Proof.GET_INSTALLED_WIDGETS_PROOF,
        )
    }

    /**
     * Removes an item or folder from the workspace or hotseat, or an app from a folder.
     *
     * @param appFunctionContext App function context.
     * @param target The specification of the item to remove.
     * @return [WorkspaceUpdateResult] with the updated workspace state and a new proof.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun removeItem(
        appFunctionContext: AppFunctionContext,
        target: RemoveItemParamsSpec,
    ): WorkspaceUpdateResult {
        return mutationManager.removeItem(target)
    }


    /// Decorated responses to the AppFunction agents, the kdoc for these is used
    // as the raw documentation ingested by the agents.

    /**
     * Response from [getCurrentWorkspace].
     *
     * @property workspace Current [WorkspaceSpec].
     * @property proof Proof token.
     */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class WorkspaceResponse(val workspace: WorkspaceSpec, val proof: Proof)

    /**
     * Response from [getInstalledApps].
     *
     * @property apps List of [UnplacedAppSpec]; truncated if `isTruncated`=true.
     * @property proof Proof token.
     * @property isTruncated True if list was truncated.
     * @property truncationDetails Why list was truncated.
     */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class GetInstalledAppsResponse(
        val apps: List<UnplacedAppSpec>,
        val proof: Proof
    )

    /**
     * Response from [getInstalledWidgets].
     *
     * @property widgets List of [UnplacedWidgetSpec].
     * @property proof Proof token.
     */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class GetInstalledWidgetsResponse(val widgets: List<UnplacedWidgetSpec>, val proof: Proof)

    /**
     * Verifies function calls are correctly chained.
     *
     * @property token Internal token, never shown to the user.
     */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class Proof(val token: String) {
        companion object {
            val NO_PROOF = Proof("no_proof")
            val GET_CURRENT_WORKSPACE_PROOF = Proof("a7em")
            val MOVE_ITEM_PROOF = Proof("b5d7")
            val ADD_ITEM_PROOF = Proof("d92b")
            val CREATE_FOLDER_PROOF = Proof("h618")
            val REMOVE_ITEM_PROOF = Proof("g27c")
            val GET_INSTALLED_APPS_PROOF = Proof("i08f")
            val GET_INSTALLED_WIDGETS_PROOF = Proof("j4b3")
            val CREATE_SCREEN_PROOF = Proof("k8e9")
            val REMOVE_SCREEN_PROOF = Proof("l1c7")
            val ALIGN_SCREEN_PROOF = Proof("m591")
            val ORGANIZE_SCREEN_PROOF = Proof("n76d")
            val GET_SCREEN_TEMPLATE_PROOF = Proof("p09a")
        }
    }
}
