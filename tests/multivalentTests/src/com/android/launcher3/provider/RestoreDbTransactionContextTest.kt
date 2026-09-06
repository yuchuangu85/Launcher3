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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.TransactionContext
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RestoreDbTransactionContextTest {

    private val mockDelegate: TransactionContext = mock()
    private val mockDbController: ModelDbController = mock()
    private val mockDb: SQLiteDatabase = mock()
    private lateinit var writeDao: RestoreDbTaskWriteDao
    private lateinit var restoreContext: RestoreDbTransactionContext

    @Before
    fun setup() {
        writeDao = RestoreDbTaskWriteDao(mockDbController)
        restoreContext = RestoreDbTransactionContext(mockDelegate, writeDao)
        @Suppress("DEPRECATION") doReturn(mockDb).whenever(mockDbController).db
    }

    @Test
    fun deleteItemsFromUnrestoredProfiles_withValidIds_buildsCorrectSelection() {
        // Given
        val validIds = listOf(10L, 42L)
        val expectedSelection = "${Favorites.PROFILE_ID} NOT IN (10, 42)"
        whenever(mockDbController.delete(eq(expectedSelection), eq(null))).thenReturn(3)

        // When
        val deletedCount = restoreContext.deleteItemsFromUnrestoredProfiles(validIds)

        // Then
        assertEquals(3, deletedCount)
        verify(mockDbController).delete(eq(expectedSelection), eq(null))
    }

    @Test
    fun bulkUpdateRestoredFlag_withSpecificItemType_updatesMatchingItems() {
        // Given
        val flag = 2
        val itemType = Favorites.ITEM_TYPE_APPWIDGET
        val expectedWhereClause = "${Favorites.ITEM_TYPE} = ?"
        whenever(
                mockDbController.update(
                    any(ContentValues::class.java) ?: ContentValues(),
                    eq(expectedWhereClause),
                    any<Array<String>>() ?: arrayOf(),
                )
            )
            .thenReturn(5)

        // When
        val updatedCount = restoreContext.bulkUpdateRestoredFlag(flag, itemType)

        // Then
        assertEquals(5, updatedCount)

        val valuesCaptor = argumentCaptor<ContentValues>()
        val argsCaptor = argumentCaptor<Array<String>>()
        verify(mockDbController)
            .update(
                valuesCaptor.capture() ?: ContentValues(),
                eq(expectedWhereClause),
                argsCaptor.capture() ?: arrayOf(),
            )

        val capturedValues = valuesCaptor.firstValue
        assertEquals(
            "The ContentValues should contain the correct flag",
            flag,
            capturedValues.get(Favorites.RESTORED),
        )

        val capturedArgs = argsCaptor.firstValue
        assertArrayEquals(
            "The selection args should contain the itemType",
            arrayOf(itemType.toString()),
            capturedArgs,
        )
    }

    @Test
    fun migrateProfileId_updatesCorrectRowsWithNewProfileId() {
        // Given
        val oldProfileId = 42L
        val newProfileId = 10L
        val expectedSelection = "${Favorites.PROFILE_ID} = ?"

        whenever(
                mockDbController.update(
                    any(ContentValues::class.java) ?: ContentValues(),
                    eq(expectedSelection),
                    any<Array<String>>() ?: arrayOf(),
                )
            )
            .thenReturn(12)

        // When
        val updatedCount = restoreContext.migrateProfileId(oldProfileId, newProfileId)

        // Then
        assertEquals("Should return the number of updated items", 12, updatedCount)

        val valuesCaptor = argumentCaptor<ContentValues>()
        val argsCaptor = argumentCaptor<Array<String>>()

        verify(mockDbController)
            .update(
                valuesCaptor.capture() ?: ContentValues(),
                eq(expectedSelection),
                argsCaptor.capture() ?: arrayOf(),
            )
        val capturedValues = valuesCaptor.firstValue
        assertEquals(
            "The ContentValues should contain the NEW profile ID",
            newProfileId,
            capturedValues.get(Favorites.PROFILE_ID),
        )
        val capturedArgs = argsCaptor.firstValue
        assertArrayEquals(
            "The selection args should contain the OLD profile ID",
            arrayOf(oldProfileId.toString()),
            capturedArgs,
        )
    }

    @Test
    fun updateDefaultProfileId_executesTableRecreationDance() {
        // Given
        val newProfileId = 33L
        val tableName = Favorites.TABLE_NAME
        val tempTableName = "${tableName}_old"

        // When
        restoreContext.updateDefaultProfileId(newProfileId)

        // Then
        val inOrder = inOrder(mockDb)

        inOrder.verify(mockDb).execSQL("ALTER TABLE $tableName RENAME TO $tempTableName;")
        inOrder.verify(mockDb).execSQL("INSERT INTO $tableName SELECT * FROM $tempTableName;")
        inOrder.verify(mockDb).execSQL("DROP TABLE $tempTableName;")
    }

    @Test
    fun bulkUpdateRestoredFlag_withNullItemType_updatesAllItems() {
        // Given
        val flag = 2
        whenever(
                mockDbController.update(
                    any(ContentValues::class.java) ?: ContentValues(),
                    eq(null), // null whereClause
                    eq(null), // null whereArgs
                )
            )
            .thenReturn(10)

        // When
        val updatedCount = restoreContext.bulkUpdateRestoredFlag(flag, null)

        // Then
        assertEquals(10, updatedCount)

        val valuesCaptor = argumentCaptor<ContentValues>()
        verify(mockDbController)
            .update(valuesCaptor.capture() ?: ContentValues(), eq(null), eq(null))

        val capturedValues = valuesCaptor.firstValue
        assertEquals(
            "The ContentValues should contain the correct flag",
            flag,
            capturedValues.get(Favorites.RESTORED),
        )
    }

    @Test
    fun removeScreenIdGaps_withValidScreens_executesCorrectSql() {
        // Given
        val containerId = Favorites.CONTAINER_DESKTOP
        val distinctScreens = intArrayOf(0, 3, 5)
        val startScreenId = 0

        // When
        restoreContext.removeScreenIdGaps(containerId, distinctScreens, startScreenId)

        // Then
        // We verify that the DAO correctly translates the array into the CASE WHEN SQL string
        val expectedSql =
            """
            UPDATE ${Favorites.TABLE_NAME}
                SET ${Favorites.SCREEN} =
                    CASE
                        WHEN ${Favorites.SCREEN} == 0 THEN 0 WHEN ${Favorites.SCREEN} == 3 THEN 1 WHEN ${Favorites.SCREEN} == 5 THEN 2
                        ELSE ${Favorites.SCREEN}
                    END
            WHERE ${Favorites.CONTAINER} = $containerId;
        """
                .trimIndent()

        verify(mockDb).execSQL(expectedSql)
    }

    @Test
    fun removeScreenIdGaps_withEmptyScreens_doesNothing() {
        // When
        restoreContext.removeScreenIdGaps(Favorites.CONTAINER_DESKTOP, IntArray(0), 0)

        // Then
        verify(mockDb, never()).execSQL(anyString())
    }

    @Test
    fun updateAppWidgetId_withValidData_updatesAndReturnsTrue() {
        // Given
        val oldWidgetId = 100
        val newWidgetId = 200
        val newRestoreState = 4
        val profileId = 10L

        val flagNotValid = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
        val expectedSelection =
            "${Favorites.APPWIDGET_ID} = ? AND " +
                "(${Favorites.RESTORED} & $flagNotValid) = $flagNotValid AND " +
                "${Favorites.PROFILE_ID} = ?"

        // Simulate the DB returning 1 row updated
        whenever(
                mockDbController.update(
                    any(ContentValues::class.java) ?: ContentValues(),
                    eq(expectedSelection),
                    any<Array<String>>() ?: arrayOf(),
                )
            )
            .thenReturn(1)

        // When
        val result =
            restoreContext.updateAppWidgetId(oldWidgetId, newWidgetId, newRestoreState, profileId)

        // Then
        assertEquals("Should return true because 1 row was updated", true, result)

        val valuesCaptor = argumentCaptor<ContentValues>()
        val argsCaptor = argumentCaptor<Array<String>>()

        verify(mockDbController)
            .update(
                valuesCaptor.capture() ?: ContentValues(),
                eq(expectedSelection),
                argsCaptor.capture() ?: arrayOf(),
            )

        assertEquals(newWidgetId, valuesCaptor.firstValue.get(Favorites.APPWIDGET_ID))
        assertEquals(newRestoreState, valuesCaptor.firstValue.get(Favorites.RESTORED))
        assertArrayEquals(
            arrayOf(oldWidgetId.toString(), profileId.toString()),
            argsCaptor.firstValue,
        )
    }

    @Test
    fun updateShortcutOverride_updatesIntentAndProfile() {
        // Given
        val itemId = 55
        val newIntentUri = "app://test_uri"
        val newProfileId = 10L
        val expectedSelection = "${Favorites._ID} = ?"

        whenever(
                mockDbController.update(
                    any(ContentValues::class.java) ?: ContentValues(),
                    eq(expectedSelection),
                    any<Array<String>>() ?: arrayOf(),
                )
            )
            .thenReturn(1)

        // When
        val result = restoreContext.updateShortcutOverride(itemId, newIntentUri, newProfileId)

        // Then
        assertEquals(1, result)

        val valuesCaptor = argumentCaptor<ContentValues>()
        val argsCaptor = argumentCaptor<Array<String>>()

        verify(mockDbController)
            .update(
                valuesCaptor.capture() ?: ContentValues(),
                eq(expectedSelection),
                argsCaptor.capture() ?: arrayOf(),
            )

        assertEquals(newIntentUri, valuesCaptor.firstValue.get(Favorites.INTENT))
        assertEquals(newProfileId, valuesCaptor.firstValue.get(Favorites.PROFILE_ID))
        assertArrayEquals(arrayOf(itemId.toString()), argsCaptor.firstValue)
    }
}
