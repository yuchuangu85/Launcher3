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

import android.content.ComponentName
import android.content.Intent
import android.graphics.Point
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.CellAndSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeuristicScreenPlacerTest {

    private val gridSize = Point(4, 5)
    private val placer = HeuristicScreenPlacer()

    @Test
    fun placeItemsIntoMatchingSlots() {
        val item1 = createClassifiedItem(1, LauncherSettings.Favorites.ITEM_TYPE_APPLICATION)
        val item2 = createClassifiedItem(2, LauncherSettings.Favorites.ITEM_TYPE_APPLICATION)
        val widget = createClassifiedItem(3, LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET, 2, 2)
        val items = listOf(item1, item2, widget)

        val template =
            Template(
                listOf(
                    TemplateItem(
                        CellAndSpan(0, 0, 1, 1),
                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    ),
                    TemplateItem(
                        CellAndSpan(1, 0, 1, 1),
                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    ),
                    TemplateItem(
                        CellAndSpan(0, 1, 2, 2),
                        LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET,
                    ),
                )
            )

        val screens = placer.place(items, listOf(template))

        assertEquals(1, screens.size)
        val expected =
            listOf(
                CellAndSpan(0, 0, 1, 1) to 1,
                CellAndSpan(1, 0, 1, 1) to 2,
                CellAndSpan(0, 1, 2, 2) to 3,
            )
        GeneratorTestHelper.verifyLayout(screens[0], gridSize, expected)
    }

    @Test
    fun reuseItemsAcrossMultipleScreens() {
        val item = createClassifiedItem(1, LauncherSettings.Favorites.ITEM_TYPE_APPLICATION)
        val items = listOf(item)

        val template =
            Template(
                listOf(
                    TemplateItem(
                        CellAndSpan(0, 0, 1, 1),
                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    )
                )
            )

        // Provide two templates to allow two screens to be generated
        val screens = placer.place(items, listOf(template, template))

        assertEquals(2, screens.size)
        assertEquals(1, screens[0].size)
        assertEquals(1, screens[0][0].id)
        assertEquals(0, screens[0][0].screenId)

        assertEquals(1, screens[1].size)
        assertEquals(1, screens[1][0].id)
        assertEquals(1, screens[1][0].screenId)
    }

    @Test
    fun doNotReuseItemOnSameScreen() {
        val item = createClassifiedItem(1, LauncherSettings.Favorites.ITEM_TYPE_APPLICATION)
        val items = listOf(item)

        val template =
            Template(
                listOf(
                    TemplateItem(
                        CellAndSpan(0, 0, 1, 1),
                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    ),
                    TemplateItem(
                        CellAndSpan(1, 0, 1, 1),
                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    ),
                )
            )

        val screens = placer.place(items, listOf(template))

        assertEquals(1, screens.size)
        assertEquals(
            "Only one item should be placed even if two slots are available",
            1,
            screens[0].size,
        )
    }

    @Test
    fun respectTemplateCountLimit() {
        val item = createClassifiedItem(1, LauncherSettings.Favorites.ITEM_TYPE_APPLICATION)
        val template =
            Template(
                listOf(
                    TemplateItem(
                        CellAndSpan(0, 0, 1, 1),
                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    )
                )
            )

        val screens = placer.place(listOf(item), listOf(template))

        assertEquals(1, screens.size)
    }

    @Test
    fun handleEmptyInputs() {
        val item = createClassifiedItem(1, LauncherSettings.Favorites.ITEM_TYPE_APPLICATION)
        val template = Template(emptyList())

        assertTrue(placer.place(emptyList(), listOf(template)).isEmpty())
        assertTrue(placer.place(listOf(item), emptyList()).isEmpty())
        assertTrue(placer.place(emptyList(), listOf(template)).isEmpty())
    }

    @Test
    fun placeFolder_withMultipleTopics_picksFirstTopicWithEnoughItems() {
        // Setup: 1 Social app, 2 Games apps, 3 News apps
        // We use scores to ensure order.
        val newsItems =
            (1..3).map {
                createClassifiedItem(
                    it,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    topic = "News",
                    score = 0.9f,
                )
            }
        val gamesItems =
            (4..5).map {
                createClassifiedItem(
                    it,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    topic = "Games",
                    score = 0.8f,
                )
            }
        val socialItems =
            listOf(
                createClassifiedItem(
                    6,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    topic = "Social",
                    score = 0.7f,
                )
            )

        val itemsToPlace = newsItems + gamesItems + socialItems

        val templateItems =
            listOf(
                TemplateItem(CellAndSpan(0, 0, 1, 1), LauncherSettings.Favorites.ITEM_TYPE_FOLDER)
            )
        val templates = listOf(Template(templateItems))

        val result = placer.place(itemsToPlace, templates)

        assertEquals(1, result.size)
        val screen = result[0]
        assertEquals(1, screen.size)

        val folder = screen[0] as FolderInfo
        assertEquals("News", folder.title)
        assertEquals(3, folder.getContents().size)
        assertTrue(folder.getContents().all { it.id in 1..3 })
    }

    @Test
    fun placeFolder_notEnoughItems_doesNotPlaceFolder() {
        // Setup: Only 1 item for each topic. MIN_FOLDER_ITEMS is 2.
        val itemsToPlace =
            listOf(
                createClassifiedItem(
                    1,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    topic = "Social",
                ),
                createClassifiedItem(
                    2,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    topic = "Games",
                ),
            )

        val templateItems =
            listOf(
                TemplateItem(CellAndSpan(0, 0, 1, 1), LauncherSettings.Favorites.ITEM_TYPE_FOLDER)
            )
        val templates = listOf(Template(templateItems))

        val result = placer.place(itemsToPlace, templates)

        assertEquals(1, result.size)
        val screen = result[0]
        assertTrue("No folder should be placed if requirements are not met", screen.isEmpty())
    }

    @Test
    fun placeFolder_tooManyItems_maxesOutAtFourAndPlacesRestElsewhere() {
        // Setup: 6 Games apps. Folder should take 4.
        // Template has 1 folder slot and 1 app slot.
        // The remaining 2 games apps should be available for the app slot.
        val gamesItems =
            (1..6).map {
                createClassifiedItem(
                    it,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    topic = "Games",
                )
            }

        val templateItems =
            listOf(
                TemplateItem(CellAndSpan(0, 0, 1, 1), LauncherSettings.Favorites.ITEM_TYPE_FOLDER),
                TemplateItem(
                    CellAndSpan(1, 0, 1, 1),
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                ),
            )
        val templates = listOf(Template(templateItems))

        val result = placer.place(gamesItems, templates)

        assertEquals(1, result.size)
        val screen = result[0]
        assertEquals(2, screen.size)

        val folder = screen.find { it is FolderInfo } as? FolderInfo
        val standaloneApp = screen.find { it !is FolderInfo }

        assertNotNull("Folder should be placed", folder)
        assertNotNull("Standalone app should be placed", standaloneApp)

        assertEquals("Folder should max out at 4 items", 4, folder?.getContents()?.size)

        // Items 1, 2, 3, 4 should be in the folder (picked first as they have same score 1.0)
        val folderIds = folder?.getContents()?.map { it.id }?.toSet()
        assertEquals(setOf(1, 2, 3, 4), folderIds)

        // Item 5 should be placed in the standalone app slot (it was the next available one)
        assertEquals(5, standaloneApp?.id)
    }

    @Test
    fun placeFolder_withMultipleTopics_picksTopicWithHighestScoreItems() {
        // Setup: 3 News apps (low score), 2 Games apps (high score)
        // Both meet MIN_FOLDER_ITEMS (2). Games should be picked if its items are higher in the
        // sorted list.
        val newsItems =
            (1..3).map {
                createClassifiedItem(
                    it,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    topic = "News",
                    score = 0.1f,
                )
            }
        val gamesItems =
            (4..5).map {
                createClassifiedItem(
                    it,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    topic = "Games",
                    score = 0.9f,
                )
            }

        val itemsToPlace = newsItems + gamesItems

        val templateItems =
            listOf(
                TemplateItem(CellAndSpan(0, 0, 1, 1), LauncherSettings.Favorites.ITEM_TYPE_FOLDER)
            )
        val templates = listOf(Template(templateItems))

        val result = placer.place(itemsToPlace, templates)

        assertEquals(1, result.size)
        val screen = result[0]
        assertEquals(1, screen.size)

        val folder = screen[0] as FolderInfo
        assertEquals("Games", folder.title)
    }

    @Test
    fun prioritizeHighestScoreItems() {
        val lowScoreItem =
            createClassifiedItem(1, LauncherSettings.Favorites.ITEM_TYPE_APPLICATION, score = 0.1f)
        val highScoreItem =
            createClassifiedItem(2, LauncherSettings.Favorites.ITEM_TYPE_APPLICATION, score = 0.9f)
        val items = listOf(lowScoreItem, highScoreItem)

        val template =
            Template(
                listOf(
                    TemplateItem(
                        CellAndSpan(0, 0, 1, 1),
                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    )
                )
            )

        val screens = placer.place(items, listOf(template))

        assertEquals(1, screens.size)
        assertEquals(1, screens[0].size)
        assertEquals(2, screens[0][0].id) // Item with ID 2 has higher score
    }

    private fun createClassifiedItem(
        id: Int,
        type: Int,
        spanX: Int = 1,
        spanY: Int = 1,
        topic: String = "Test",
        score: Float = 1.0f,
    ): TopicClassifiedItem {
        val info =
            if (type == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                AppInfo(ComponentName("pkg", "class$id"), topic, Process.myUserHandle(), Intent())
                    .apply {
                        this.id = id
                        this.spanX = spanX
                        this.spanY = spanY
                    }
            } else {
                ItemInfo().apply {
                    this.id = id
                    this.itemType = type
                    this.spanX = spanX
                    this.spanY = spanY
                }
            }
        return TopicClassifiedItem(info, topic, score)
    }
}
