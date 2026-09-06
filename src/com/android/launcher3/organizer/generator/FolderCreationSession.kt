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

import android.Manifest
import androidx.annotation.RequiresPermission
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.repository.AppsListRepository
import javax.inject.Inject

/**
 * A [CreationSession] that handles the generation of new folders.
 *
 * This session uses a combination of classifiers to categorize apps into topics and then generates
 * a list of folders for the selected topics to place on the home screen.
 *
 * @param appsListRepository Repository for retrieving the list of installed applications.
 * @param classifier Classifier that categorizes apps into topics.
 * @param topicProvider Provider for the list of potential topics.
 */
class FolderCreationSession
@Inject
constructor(
    private val appsListRepository: AppsListRepository,
    private val classifier: ItemInfoClassifier,
    private val topicProvider: TopicProvider,
) : CreationSession {
    private lateinit var topicClassifiedItems: List<TopicClassifiedItem>

    @RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
    override suspend fun startClassification(): List<TopicClassifiedItem> {
        val apps = appsListRepository.appsListStateRef.value.apps.toList()
        val topics = topicProvider.getTopics()
        topicClassifiedItems = classifier.classify(apps, topics)
        return topicClassifiedItems
    }

    override suspend fun startGeneration(
        selectedTopics: List<String>
    ): CreationSession.GenerationResult {
        val folders =
            topicClassifiedItems
                .asSequence()
                .filter { selectedTopics.contains(it.topic) && it.score >= 0.8f }
                .groupBy { it.topic }
                .filter { (_, items) -> items.size >= 3 }
                .map { (topic, items) ->
                    val folderInfo = FolderInfo()
                    folderInfo.title = topic
                    items.forEachIndexed { index, topicItem ->
                        val item = topicItem.itemInfo
                        val workspaceItem =
                            if (item is AppInfo) WorkspaceItemInfo(item) else item.makeShallowCopy()
                        workspaceItem.rank = index
                        folderInfo.add(workspaceItem)
                    }
                    folderInfo
                }
                .toList()
        return CreationSession.GenerationResult.Folders(folders)
    }

    override suspend fun cancelSession() {
        TODO("Not yet implemented")
    }
}
