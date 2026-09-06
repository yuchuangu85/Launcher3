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

import androidx.appfunctions.AppFunctionSerializable
import com.android.launcher3.appfunctions.workspace.WorkspaceAppFunctions.Proof

/// Flat types, required by AppFunctions and btm ingestion
/// Each definition here has extension functions to convert to and from the
/// more structured data types through [WorkspaceTypeTranslator].
/// Each of these are AppFunctionSerializable, which means they can be
/// ingested by the AppFunction agent and they can utilize the kdoc for
/// static context.

/**
 * Launcher workspace: screens and hotseat.
 *
 * @property screens List of [WorkspaceScreenSpec].
 * @property hotseat [HotseatSpec].
 * @property rows Grid rows; null for default.
 * @property columns Grid columns; null for default.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class WorkspaceSpec(
    val screens: List<WorkspaceScreenSpec>,
    val hotseat: HotseatSpec,
    val rows: Int?,
    val columns: Int?,
)

/**
 * A workspace screen containing items.
 *
 * @property items [WorkspaceItemSpec] list.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class WorkspaceScreenSpec(val items: List<WorkspaceItemSpec>)

/**
 * Hotseat bar containing items.
 *
 * @property items [HotseatItemSpec] list.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class HotseatSpec(val items: List<HotseatItemSpec>)

/**
 * Item on a workspace screen. Type is inferred from non-null properties:
 * - Widget: `spanX`, `spanY`, `packageName`, `className`
 * - Folder: `items`, `title`
 * - Shortcut: `shortcutId`, `packageName`
 * - App: `packageName`, `className`
 *
 * @property x 0-based x-coordinate.
 * @property y 0-based y-coordinate.
 * @property appLabel App label if identified by label.
 * @property packageName Pkg name for app, widget, shortcut.
 * @property className Class name for app, widget.
 * @property label Item label.
 * @property category Item category.
 * @property title Folder title.
 * @property items [AppInFolderSpec] list for folder.
 * @property spanX Widget width.
 * @property spanY Widget height.
 * @property description Widget description.
 * @property shortcutId Shortcut ID.
 * @property shortLabel Shortcut short label.
 * @property longLabel Shortcut long label.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class WorkspaceItemSpec(
    val x: Int,
    val y: Int,
    val appLabel: String? = null,
    val packageName: String? = null,
    val className: String? = null,
    val label: String? = null,
    val category: String? = null,
    val title: String? = null,
    val items: List<AppInFolderSpec>? = null,
    val spanX: Int? = null,
    val spanY: Int? = null,
    val description: String? = null,
    val shortcutId: String? = null,
    val shortLabel: String? = null,
    val longLabel: String? = null,
)

/**
 * Item in the hotseat. Type is inferred:
 * - Folder: `items`, `title`
 * - App: `packageName`, `className`
 *
 * @property appLabel App label if identified by label.
 * @property packageName App package name.
 * @property className App class name.
 * @property label App label.
 * @property category App category.
 * @property title Folder title.
 * @property items [AppInFolderSpec] list for folder.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class HotseatItemSpec(
    val appLabel: String? = null,
    val packageName: String? = null,
    val className: String? = null,
    val label: String? = null,
    val category: String? = null,
    val title: String? = null,
    val items: List<AppInFolderSpec>? = null,
)

/**
 * App within a folder.
 *
 * @property packageName App package name.
 * @property className Main activity class name.
 * @property label App label.
 * @property category App category.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppInFolderSpec(
  val packageName: String,
  val className: String,
  val label: String? = null,
  val category: String? = null,
)

/**
 * Unplaced app identified by component name.
 *
 * @property packageName App package name.
 * @property className Main activity class name.
 * @property label App label.
 * @property category App category.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class UnplacedAppSpec(
  val packageName: String,
  val className: String,
  val label: String? = null,
  val category: String? = null,
)

/**
 * Widget identified by component name.
 *
 * @property packageName Widget provider package name.
 * @property className Widget provider class name.
 * @property spanX Widget width in cells.
 * @property spanY Widget height in cells.
 * @property label Widget label.
 * @property description Widget description.
 * @property category Parent app category.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class UnplacedWidgetSpec(
  val packageName: String,
  val className: String,
  val spanX: Int,
  val spanY: Int,
  val label: String? = null,
  val description: String? = null,
  val category: String? = null,
)

/**
 * Result of a workspace modification.
 *
 * If `success`=true, operation succeeded. `resolutionDetails` indicates auto-corrections. If
 * `success`=false, see `errorCode` and [ErrorCode] for recovery strategy. `NO_CHANGE_MADE` means
 * workspace was already in desired state.
 *
 * @property success True if successful.
 * @property message Outcome summary.
 * @property changes Description of changes; null on failure.
 * @property errorCode [ErrorCode] on failure; null on success.
 * @property resolvedItemIdentifier Stable ID of affected item (e.g., "app:com.pkg/com.class").
 * @property resolutionDetails Explanation of auto-corrections (e.g., "Corrected 'Gmal'->'Gmail'").
 * @property proof Proof token. **CRITICAL**: Never show to user.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class WorkspaceUpdateResult(
  val success: Boolean,
  val message: String,
  val changes: String? = null,
  val errorCode: ErrorCode? = null,
  val resolvedItemIdentifier: String? = null,
  val resolutionDetails: String? = null,
  val proof: Proof,
)

/**
 * Machine-readable error for failed operations.
 *
 * @property code Error string. Values:
 *     - `ITEM_NOT_FOUND`: Item not found.
 *         - **Recovery:** Call `getCurrentWorkspace`, check for similar labels (misspellings?),
 *           retry with corrected label, or inform user if not found.
 *     - `SCREEN_NOT_FOUND`: Screen index out of bounds.
 *         - **Recovery:** Call `getCurrentWorkspace`, re-check `workspace.screens.size`, retry with
 *           correct index, or inform user.
 *     - `LOCATION_OCCUPIED`: Target location occupied.
 *         - **Recovery:** Retry with `firstAvailableScreenIndex`, or call `getCurrentWorkspace` and
 *           find next-best slot via `screenIndex`/`x`/`y`.
 *     - `INVALID_PARAMETERS`: Invalid params in function call.
 *         - **Recovery:** Review function docs, correct params, retry.
 *     - `NO_CHANGE_MADE`: No change needed; workspace already in desired state.
 *         - **Recovery:** Not an error. Inform user no change was needed.
 *     - `PROVIDER_ERROR`: Internal error.
 *         - **Recovery:** Non-recoverable. Inform user of temporary error.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ErrorCode(val code: String) {
  companion object {
    const val ITEM_NOT_FOUND = "ITEM_NOT_FOUND"
    const val SCREEN_NOT_FOUND = "SCREEN_NOT_FOUND"
    const val LOCATION_OCCUPIED = "LOCATION_OCCUPIED"
    const val INVALID_PARAMETERS = "INVALID_PARAMETERS"
    const val NO_CHANGE_MADE = "NO_CHANGE_MADE"
    const val PROVIDER_ERROR = "PROVIDER_ERROR"
  }
}

/**
 * Selects a workspace or hotseat item for `moveItem` or `removeItem`.
 *
 * Use one method:
 * - By label: `ItemSelectorSpec(label="Gmail")`
 * - By coordinates: `ItemSelectorSpec(screenIndex=0, x=1, y=1)`
 * - By hotseat: `ItemSelectorSpec(hotseatRank=0)`
 * - By component: `ItemSelectorSpec(packageName="...", className="...")`
 *
 * @property label Selects by label (case-insensitive).
 * @property screenIndex 0-based screen index.
 * @property x 0-based x-coordinate.
 * @property y 0-based y-coordinate.
 * @property hotseatRank 0-based hotseat rank.
 * @property packageName Pkg name for component selection.
 * @property className Class name for component selection.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ItemSelectorSpec(
  val label: String?,
  val screenIndex: Int?,
  val x: Int?,
  val y: Int?,
  val hotseatRank: Int?,
  val packageName: String?,
  val className: String?,
)

/**
 * Params for `removeItem`.
 *
 * ### Examples
 * - "Remove Gmail": `item=ItemSelectorSpec(label="Gmail")`
 * - "Remove first item in dock": `item=ItemSelectorSpec(rank=0)`
 *
 * @property item [ItemSelectorSpec] of item to remove.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class RemoveItemParamsSpec(val item: ItemSelectorSpec)
