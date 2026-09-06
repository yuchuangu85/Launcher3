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

package com.android.launcher3.provider

interface IRestoreDbTaskWriteDao {

    /**
     * Bulk deletes all workspace items and widgets that belong to user profiles from the old device
     * that were not restored to the new device.
     *
     * @param validProfileIds The list of profile IDs that were successfully restored.
     * @return The number of items deleted.
     */
    fun deleteItemsFromUnrestoredProfiles(validProfileIds: Collection<Long>): Int

    /**
     * Applies standard restore flags to items in the database.
     *
     * @param flag The restored flag integer to apply.
     * @param itemType If provided, the flag is only applied to this specific item type (e.g.,
     *   ITEM_TYPE_APPWIDGET). If null, it applies to all items.
     * @return The number of items updated.
     */
    fun bulkUpdateRestoredFlag(flag: Int, itemType: Int? = null): Int

    /**
     * Shifts items from an old user profile ID (from the backed-up device) to the corresponding new
     * user profile ID on the current device.
     *
     * @param oldProfileId The profile ID from the old device.
     * @param newProfileId The corresponding profile ID on the new device.
     * @return The number of items updated.
     */
    fun migrateProfileId(oldProfileId: Long, newProfileId: Long): Int

    /**
     * Updates the database schema so the default value for the profileId column matches the primary
     * user of the new device. Because SQLite does not support altering a column's default value
     * directly, this performs a full table recreation (Rename -> Create -> Copy -> Drop).
     *
     * @param newProfileId The new default profile ID to apply to the schema.
     */
    fun updateDefaultProfileId(newProfileId: Long)

    /**
     * Condenses screen IDs for single-display restores.
     *
     * @param containerId The container to update (e.g., CONTAINER_DESKTOP).
     * @param distinctScreens A sorted array of the existing screen IDs.
     * @param startScreenId The ID to use for the first screen (usually 0 or 1).
     */
    fun removeScreenIdGaps(containerId: Int, distinctScreens: IntArray, startScreenId: Int)

    /**
     * Remaps an old widget ID from the backup to a newly allocated widget ID from the OS, updating
     * its readiness state.
     *
     * @param oldWidgetId The original widget ID from the backup.
     * @param newWidgetId The newly allocated widget ID.
     * @param newRestoreState The updated restore flag for the widget.
     * @param profileId The user profile ID associated with the widget.
     * @return true if the row was successfully found and updated, false otherwise.
     */
    fun updateAppWidgetId(
        oldWidgetId: Int,
        newWidgetId: Int,
        newRestoreState: Int,
        profileId: Long,
    ): Boolean

    /**
     * Updates the intent and profile ID for specific telephony or app shortcuts that the system
     * mandates must be overridden/replaced upon restore.
     *
     * @param itemId The specific database ID of the shortcut to override.
     * @param newIntentUri The new intent URI string to save.
     * @param newProfileId The new profile ID to associate with the shortcut.
     * @return The number of rows updated (should be 1).
     */
    fun updateShortcutOverride(itemId: Int, newIntentUri: String, newProfileId: Long): Int
}
