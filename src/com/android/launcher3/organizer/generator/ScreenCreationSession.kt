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
import android.graphics.Point
import androidx.annotation.RequiresPermission
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.model.repository.AppsListRepository
import javax.inject.Inject

/**
 * A [CreationSession] that handles the generation of new workspace screens.
 *
 * This session uses a combination of classifiers to categorize apps into topics and then uses a
 * template-based approach to arrange items on new screens.
 *
 * @param appsListRepository Repository for retrieving the list of installed applications.
 * @param idp The invariant device profile used to get screen layout.
 * @param classifier Classifier that categorizes apps into topics.
 * @param topicProvider Provider for the list of potential topics.
 */
class ScreenCreationSession
@Inject
constructor(
    private val appsListRepository: AppsListRepository,
    private val idp: InvariantDeviceProfile,
    private val classifier: ItemInfoClassifier,
    private val topicProvider: TopicProvider,
) : CreationSession {
    private lateinit var topicClassifiedItems: List<TopicClassifiedItem>

    /**
     * Categorizes apps using both usage statistics and system categories.
     *
     * Classification results are stored internally for use in [startGeneration].
     */
    @RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
    override suspend fun startClassification(): List<TopicClassifiedItem> {
        val apps = appsListRepository.appsListStateRef.value.apps.toList()
        val topics = topicProvider.getTopics()
        topicClassifiedItems = classifier.classify(apps, topics)
        return topicClassifiedItems
    }

    /**
     * Generates a set of workspace screens for the [selectedTopics].
     *
     * It uses [PresetTemplateGenerator] to create layout templates and [HeuristicScreenPlacer] to
     * fill those templates with the classified items.
     *
     * @param selectedTopics The list of topic names selected by the user for generation.
     */
    override suspend fun startGeneration(
        selectedTopics: List<String>
    ): CreationSession.GenerationResult {
        val templateGenerator = PresetTemplateGenerator()
        val templates =
            templateGenerator.generateTemplates(
                NUM_PAGES_TO_GENERATE,
                Point(idp.numColumns, idp.numRows),
            )
        val placer = HeuristicScreenPlacer()
        val pages =
            placer.place(
                topicClassifiedItems.filter { selectedTopics.contains(it.topic) },
                templates,
            )
        return CreationSession.GenerationResult.Screens(pages)
    }

    override suspend fun cancelSession() {
        TODO("Not yet implemented")
    }

    companion object {
        private const val NUM_PAGES_TO_GENERATE = 3
    }
}
