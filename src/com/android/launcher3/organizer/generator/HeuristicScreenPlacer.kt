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

import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * A [Placer] that arranges items based on predefined templates.
 *
 * It iterates through the provided templates and attempts to fill each template slot with a
 * compatible item from the available items list. Items can be reused across different screens but
 * are only placed once per screen.
 */
class HeuristicScreenPlacer : Placer {
    /**
     * Orchestrates the placement of [classifiedItems] into containers defined by [templates].
     *
     * It iterates through each group of items in [classifiedItems], selecting a matching template
     * for each. Within each template, it tries to find the best available item that matches the
     * type and span of each predefined slot.
     *
     * @param classifiedItems A list of items to be arranged in a single screen.
     * @param templates The list of layout templates to follow.
     * @return A list of screens, each with a list of [ItemInfo] with their desired positions filled
     *   in.
     */
    override fun place(
        classifiedItems: List<TopicClassifiedItem>,
        templates: List<Template>,
    ): List<List<ItemInfo>> {
        // Filter templates to only those that match the expected grid size.
        if (classifiedItems.isEmpty() || templates.isEmpty()) {
            return emptyList()
        }

        val filledScreens = mutableListOf<List<ItemInfo>>()
        val numScreensToGenerate = templates.size

        for (screenIndex in 0 until numScreensToGenerate) {
            val currentTemplate = templates[screenIndex]
            val screenItems = mutableListOf<ItemInfo>()

            val sortedItems = classifiedItems.sortedByDescending { it.score }

            val apps =
                sortedItems.filter {
                    it.itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                }
            // Use LinkedHashSet to preserve sorted order and allow O(1) removal by value
            val availableApps = LinkedHashSet(apps)
            val appsByTopic: MutableMap<String, MutableSet<TopicClassifiedItem>> =
                apps.groupBy { it.topic }.mapValues { LinkedHashSet(it.value) }.toMutableMap()

            val availableWidgets =
                sortedItems
                    .filter {
                        it.itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    }
                    .groupBy { Triple(it.itemInfo.spanX, it.itemInfo.spanY, it.itemInfo.itemType) }
                    .mapValues { ArrayDeque(it.value) }

            // Fill each slot in the template with the best available matching item.
            for (templateItem in currentTemplate.items) {
                when (templateItem.itemTypeId) {
                    LauncherSettings.Favorites.ITEM_TYPE_FOLDER -> {
                        tryCreateFolder(availableApps, appsByTopic, templateItem, screenIndex)
                            ?.let { screenItems.add(it) }
                    }
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION -> {
                        availableApps.firstOrNull()?.let { app ->
                            availableApps.remove(app)
                            appsByTopic[app.topic]?.remove(app)
                            val placedItem =
                                WorkspaceItemInfo(app.itemInfo as AppInfo).apply {
                                    this.cellX = templateItem.cellAndSpan.cellX
                                    this.cellY = templateItem.cellAndSpan.cellY
                                    this.screenId = screenIndex
                                }
                            screenItems.add(placedItem)
                        }
                    }
                    LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET -> {
                        val key =
                            Triple(
                                templateItem.cellAndSpan.spanX,
                                templateItem.cellAndSpan.spanY,
                                templateItem.itemTypeId,
                            )
                        availableWidgets[key]?.removeFirstOrNull()?.let { widget ->
                            val placedItem =
                                ItemInfo().apply {
                                    copyFrom(widget.itemInfo)
                                    this.cellX = templateItem.cellAndSpan.cellX
                                    this.cellY = templateItem.cellAndSpan.cellY
                                    this.screenId = screenIndex
                                }
                            screenItems.add(placedItem)
                        }
                    }
                }
            }

            filledScreens.add(screenItems)
        }
        return filledScreens
    }

    private fun tryCreateFolder(
        availableApps: MutableSet<TopicClassifiedItem>,
        appsByTopic: MutableMap<String, MutableSet<TopicClassifiedItem>>,
        templateItem: TemplateItem,
        screenIndex: Int,
    ): FolderInfo? {
        val validTopicEntry =
            appsByTopic.entries.find { it.value.size >= MIN_FOLDER_ITEMS } ?: return null

        val topic = validTopicEntry.key
        val appsForFolder = validTopicEntry.value.take(MAX_FOLDER_ITEMS)

        val folderInfo =
            FolderInfo().apply {
                this.cellX = templateItem.cellAndSpan.cellX
                this.cellY = templateItem.cellAndSpan.cellY
                this.screenId = screenIndex
                this.title = topic
            }
        appsForFolder.forEachIndexed { index, topicItem ->
            val item = topicItem.itemInfo
            val workspaceItem =
                if (item is AppInfo) WorkspaceItemInfo(item) else item.makeShallowCopy()
            workspaceItem.rank = index
            folderInfo.add(workspaceItem)

            availableApps.remove(topicItem)
            validTopicEntry.value.remove(topicItem)
        }
        return folderInfo
    }

    companion object {
        private const val MIN_FOLDER_ITEMS = 2
        private const val MAX_FOLDER_ITEMS = 4
    }
}
