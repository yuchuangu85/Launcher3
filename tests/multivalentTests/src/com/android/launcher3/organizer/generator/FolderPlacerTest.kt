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

package com.android.launcher3.organizer.generator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.ItemInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderPlacerTest {

    private val placer = FolderPlacer()

    @Test
    fun placeItemsSequentially() {
        val items =
            listOf(createClassifiedItem(1), createClassifiedItem(2), createClassifiedItem(3))

        val result = placer.place(items)

        assertEquals(3, result.size)
        assertEquals(0, result[0].rank)
        assertEquals(1, result[1].rank)
        assertEquals(2, result[2].rank)

        assertEquals(1, result[0].id)
        assertEquals(2, result[1].id)
        assertEquals(3, result[2].id)
    }

    private fun createClassifiedItem(
        id: Int,
        topic: String = "Test",
        score: Float = 1.0f,
    ): TopicClassifiedItem {
        val info =
            ItemInfo().apply {
                this.id = id
                this.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
            }
        return TopicClassifiedItem(info, topic, score)
    }
}
