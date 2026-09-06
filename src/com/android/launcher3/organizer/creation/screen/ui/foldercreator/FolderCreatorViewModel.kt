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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.launcher3.concurrent.annotations.LightweightBackgroundContext
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.scheduleTransactionSuspending
import com.android.launcher3.organizer.OrganizerTransactionContext
import com.android.launcher3.organizer.generator.CreationSession
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderCreatorViewModel
@Inject
constructor(
    creationSessionFactory: CreationSession.Factory,
    @LightweightBackgroundContext(priority = UI)
    private val lightweightBackgroundContext: CoroutineContext,
    private val modelWriter: IModelWriter,
    private val organizerTransactionContextFactory: OrganizerTransactionContext.Factory,
) : ViewModel() {

    private val _state = MutableStateFlow(FolderCreatorState())
    val state: StateFlow<FolderCreatorState> = _state.asStateFlow()

    private val folderCreationSession =
        creationSessionFactory.createSession(CreationSession.SessionType.FOLDER)

    init {
        viewModelScope.launch(lightweightBackgroundContext) {
            val classifiedItems = folderCreationSession.startClassification()
            val topics = classifiedItems.map { it.topic }.distinct()
            val topicIcons =
                classifiedItems
                    .filter { it.itemInfo is AppInfo }
                    .groupBy({ it.topic }, { (it.itemInfo as AppInfo).bitmap.icon })

            val topicDataList = topics.map { FolderTopicData(it, topicIcons[it] ?: emptyList()) }
            updateState(topicDataList)
        }
    }

    /**
     * Update the state with new topics and icons.
     *
     * @param topics The new list of topics.
     */
    private fun updateState(topics: List<FolderTopicData>) {
        _state.value = _state.value.copy(topics = topics)
    }

    /**
     * Toggles the selection of a topic.
     *
     * @param topic The topic to toggle.
     */
    fun toggleSelection(topic: String) {
        val currentSelected = _state.value.selectedTopics
        val newSelected =
            if (currentSelected.contains(topic)) {
                currentSelected - topic
            } else {
                currentSelected + topic
            }
        _state.value = _state.value.copy(selectedTopics = newSelected)
    }

    /**
     * Generates a list of folders based on the selected topics.
     *
     * @param selectedTopics The list of topics selected by the user.
     */
    fun generateFolders(selectedTopics: List<String>) {
        viewModelScope.launch(lightweightBackgroundContext) {
            val result = folderCreationSession.startGeneration(selectedTopics)
            _state.value = _state.value.copy(generatedFolders = result)

            if (result is CreationSession.GenerationResult.Folders) {
                persistAndBindFolders(result.folders)
            }
        }
    }

    private suspend fun persistAndBindFolders(folders: List<FolderInfo>) {
        try {
            modelWriter.scheduleTransactionSuspending { context ->
                organizerTransactionContextFactory.create(context).addFolders(folders)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist folders", e)
        }
    }

    companion object {
        private const val TAG = "FolderCreatorViewModel"
    }
}
