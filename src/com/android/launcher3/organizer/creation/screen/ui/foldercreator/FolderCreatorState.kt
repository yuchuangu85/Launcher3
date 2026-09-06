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

package com.android.launcher3.organizer.creation.screen.ui.foldercreator

import android.graphics.Bitmap
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.organizer.generator.CreationSession

/**
 * Data class for topic information.
 *
 * @property topic the name of the topic.
 * @property icons list of icons associated with the topic.
 */
data class FolderTopicData(val topic: String, val icons: List<Bitmap> = emptyList())

/**
 * State for [FolderCreator].
 *
 * @property topics list of topic data.
 * @property selectedTopics list of topics that the user selected
 * @property generatedFolders list of folders, each being a list of [ItemInfo].
 */
data class FolderCreatorState(
    val topics: List<FolderTopicData> = emptyList(),
    val selectedTopics: Set<String> = emptySet(),
    val generatedFolders: CreationSession.GenerationResult? = null,
)
