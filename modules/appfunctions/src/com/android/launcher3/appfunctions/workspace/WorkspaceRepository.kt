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

/**
 * Defines a contract for querying and modifying the workspace. This is the single entry point for
 * all workspace data interactions from the AppFunctions module.
 */
interface WorkspaceRepository {
    /**
     * Retrieves the current state of the workspace as a flat, serializable spec. This is a
     * read-only operation.
     *
     * @return The current [WorkspaceSpec].
     */
    suspend fun getWorkspace(): WorkspaceSpec

    /**
     * Lists all installed apps on the device that have a launcher shortcut.
     *
     * @param orderByUsageStats If true, orders apps by usage; otherwise uses default order for
     *   personalization.
     * @return A list of [UnplacedAppSpec] representing the installed apps.
     */
    suspend fun getInstalledApps(orderByUsageStats: Boolean): List<UnplacedAppSpec>

    /**
     * Lists all installed widgets on the device.
     *
     * @param orderByUsageStats If true, orders widgets by usage; otherwise uses default order for
     *   personalization.
     * @return A list of [UnplacedWidgetSpec] representing the installed widgets.
     */
    suspend fun getInstalledWidgets(orderByUsageStats: Boolean): List<UnplacedWidgetSpec>

    /**
     * Starts a new, atomic transaction for modifying the workspace.
     *
     * @return A [WorkspaceTransaction] handle to chain mutation operations.
     */
    fun newTransaction(): WorkspaceTransaction
}

/**
 * Represents a single, atomic "unit of work" for the workspace.
 *
 * All mutation operations are defined here and operate on the flat `Spec` types. The transaction
 * must be completed by calling [commit].
 */
interface WorkspaceTransaction {

    // Mutation methods can be added here, following a chained ("fluent") API style.
    // For example:
    // fun moveItem(item: WorkspaceItemSpec, newScreen: Int, newX: Int, newY: Int):
    // WorkspaceTransaction
    // fun addItem(item: WorkspaceItemSpec, screen: Int): WorkspaceTransaction

    /**
     * Queues the removal of an item from the workspace, hotseat, or folder.
     *
     * @param target The specification of the item to remove.
     * @return This [WorkspaceTransaction] instance for chaining.
     */
    fun removeItem(target: RemoveItemParamsSpec): WorkspaceTransaction

    /**
     * Commits all the changes made in this transaction to the underlying model.
     *
     * @return The new, updated [WorkspaceSpec] after the transaction is complete.
     */
    suspend fun commit(): WorkspaceSpec
}
