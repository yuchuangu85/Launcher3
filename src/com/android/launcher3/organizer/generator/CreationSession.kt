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

import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import javax.inject.Inject
import javax.inject.Provider

/** A session to handle all aspects of space/folder creation from classification to generation. */
interface CreationSession {
    /**
     * Starts classification of all items.
     *
     * @return a list of [TopicClassifiedItem].
     */
    suspend fun startClassification(): List<TopicClassifiedItem>

    /**
     * Starts generation of screens or folders.
     *
     * @param selectedTopics A list of topics that were selected for generation.
     * @return a [GenerationResult] containing screens or folders.
     */
    suspend fun startGeneration(selectedTopics: List<String>): GenerationResult

    /** Result of the generation process. */
    sealed class GenerationResult {
        /** Represents a list of screens, where each screen is a list of [ItemInfo]. */
        data class Screens(val pages: List<List<ItemInfo>>) : GenerationResult()

        /** Represents a list of [FolderInfo] objects. */
        data class Folders(val folders: List<FolderInfo>) : GenerationResult()
    }

    /** Cancels a creation session. */
    suspend fun cancelSession()

    /** Session types supported. */
    enum class SessionType {
        SCREEN,
        FOLDER,
    }

    /** Factory for creating [CreationSession]s. */
    class Factory
    @Inject
    constructor(
        private val screenSessionProvider: Provider<ScreenCreationSession>,
        private val folderSessionProvider: Provider<FolderCreationSession>,
    ) {
        /** Creates a session based on the [type]. */
        fun createSession(type: SessionType): CreationSession {
            return when (type) {
                SessionType.SCREEN -> screenSessionProvider.get()
                SessionType.FOLDER -> folderSessionProvider.get()
            }
        }
    }
}
