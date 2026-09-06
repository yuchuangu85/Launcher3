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

package com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.concurrent.annotations.LightweightBackgroundContext
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.icons.BitmapRenderer
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.repository.HomeScreenRepository
import com.android.launcher3.model.scheduleTransactionSuspending
import com.android.launcher3.organizer.OrganizerTransactionContext
import com.android.launcher3.organizer.dagger.OrganizerScope
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** View model used by the [OrganizerActivity] and its composables. */
@OrganizerScope
class WorkspaceOrganizerViewModel
@Inject
constructor(
    private val homeScreenRepository: HomeScreenRepository,
    private val modelWriter: IModelWriter,
    private val organizerTransactionContextFactory: OrganizerTransactionContext.Factory,
    @LightweightBackgroundContext(priority = UI)
    private val lightweightBackgroundContext: CoroutineContext,
) : ViewModel() {
    private val _workspacePages = MutableStateFlow<List<WorkspacePage>>(emptyList())
    val workspacePages: StateFlow<List<WorkspacePage>> = _workspacePages.asStateFlow()
    var workspaceOrganizerState: WorkspaceOrganizerState by
        mutableStateOf(WorkspaceOrganizerState())
        private set

    init {
        viewModelScope.launch { loadPages() }
    }

    /**
     * Loads the workspace pages from the [Launcher]'s workspace.
     *
     * This method initializes the [_workspacePages] state flow with the current workspace screen
     * IDs. Bitmaps are loaded lazily as the user scrolls through the workspace.
     */
    private suspend fun loadPages() {
        _workspacePages.value =
            withContext(lightweightBackgroundContext) {
                val screens = homeScreenRepository.workspaceState.value.collectWorkspaceScreens()
                screens.map { screenId -> WorkspacePage(bitmap = null, screenId = screenId) }
            }
    }

    /**
     * Loads the bitmap for the workspace page at the given [index].
     *
     * @param index The index of the page to load the bitmap for.
     */
    fun loadPageBitmap(index: Int) {
        val pages = _workspacePages.value
        if (index !in pages.indices || pages[index].bitmap != null) return

        val cachedBitmap = pages[index].lastGeneratedBitmap?.get()
        if (cachedBitmap != null) {
            val updatedPages = pages.toMutableList()
            updatedPages[index] = updatedPages[index].copy(bitmap = cachedBitmap)
            _workspacePages.value = updatedPages
            return
        }

        viewModelScope.launch {
            val bitmap =
                withContext(lightweightBackgroundContext) {
                    val launcher =
                        Launcher.ACTIVITY_TRACKER.getCreatedContext<Launcher>()
                            ?: return@withContext null

                    val workspace = launcher.workspace ?: return@withContext null
                    val page = workspace.getPageAt(index) as? CellLayout ?: return@withContext null

                    if (page.width <= 0 || page.height <= 0) return@withContext null

                    BitmapRenderer.createHardwareBitmap(
                        page.width / BITMAP_SCALE_FACTOR,
                        page.height / BITMAP_SCALE_FACTOR,
                    ) { canvas ->
                        canvas.scale(CANVAS_SCALE_RATIO, CANVAS_SCALE_RATIO)
                        page.draw(canvas)
                    }
                }

            bitmap?.let {
                val updatedPages = _workspacePages.value.toMutableList()
                if (index in updatedPages.indices) {
                    updatedPages[index] = updatedPages[index].copy(bitmap = it)
                    _workspacePages.value = updatedPages
                }
            }
        }
    }

    /**
     * Unloads the bitmap for the workspace page at the given [index].
     *
     * @param index The index of the page to unload the bitmap for.
     */
    fun unloadPageBitmap(index: Int) {
        val pages = _workspacePages.value.toMutableList()
        if (index !in pages.indices || pages[index].bitmap == null) return

        pages[index] =
            pages[index].run {
                copy(bitmap = null, lastGeneratedBitmap = bitmap?.let { WeakReference(it) })
            }
        _workspacePages.value = pages
    }

    /**
     * Moves the currently selected workspace page by the given [delta].
     *
     * @param delta The number of positions to move the page (positive for right, negative for
     *   left).
     */
    fun moveSelectedWorkspacePage(delta: Int) {
        val originalPages = _workspacePages.value
        val pages = originalPages.toMutableList()
        val currentIndex = workspaceOrganizerState.selectedPage
        val targetIndex = currentIndex + delta
        if (targetIndex in pages.indices) {
            val orderedScreenIds = originalPages.map { it.screenId }
            val page = pages.removeAt(currentIndex)
            pages.add(targetIndex, page)

            // Optimistically update screenIds.
            for (i in pages.indices) {
                pages[i] = pages[i].copy(screenId = orderedScreenIds[i])
            }

            _workspacePages.value = pages
            workspaceOrganizerState = workspaceOrganizerState.copy(selectedPage = targetIndex)

            viewModelScope.launch {
                try {
                    withContext(lightweightBackgroundContext) {
                        modelWriter.scheduleTransactionSuspending {
                            val context = organizerTransactionContextFactory.create(it)
                            context.moveScreen(currentIndex, targetIndex, orderedScreenIds)
                        }
                    }
                } catch (e: Exception) {
                    _workspacePages.value = originalPages
                    workspaceOrganizerState =
                        workspaceOrganizerState.copy(selectedPage = currentIndex)
                }
            }
        }
    }

    /**
     * Removes the currently selected workspace page from the workspace.
     *
     * This operation deletes the screen and all its items from the database.
     */
    fun removeSelectedWorkspacePage() {
        val originalPages = _workspacePages.value
        val pages = originalPages.toMutableList()
        val currentIndex = workspaceOrganizerState.selectedPage
        if (currentIndex in pages.indices) {
            val page = pages.removeAt(currentIndex)

            // Optimistically shift screenIds of subsequent pages down to match deleteScreen logic.
            for (i in currentIndex until pages.size) {
                pages[i] = pages[i].copy(screenId = pages[i].screenId - 1)
            }

            _workspacePages.value = pages
            val newSelectedPage =
                if (pages.isEmpty()) 0 else currentIndex.coerceAtMost(pages.size - 1)
            workspaceOrganizerState = workspaceOrganizerState.copy(selectedPage = newSelectedPage)

            viewModelScope.launch {
                try {
                    withContext(lightweightBackgroundContext) {
                        modelWriter.scheduleTransactionSuspending {
                            val context = organizerTransactionContextFactory.create(it)
                            context.deleteScreen(page.screenId)
                        }
                    }
                } catch (e: Exception) {
                    _workspacePages.value = originalPages
                    workspaceOrganizerState =
                        workspaceOrganizerState.copy(selectedPage = currentIndex)
                }
            }
        }
    }

    /**
     * Sets the currently selected workspace page to the given [index].
     *
     * @param index The index of the page to select.
     */
    fun setSelectedWorkspacePage(index: Int) {
        val size = workspacePages.value.size
        if (size == 0) return
        workspaceOrganizerState =
            workspaceOrganizerState.copy(selectedPage = index.coerceIn(0, size - 1))
    }

    companion object {
        private const val BITMAP_SCALE_FACTOR = 4
        private const val CANVAS_SCALE_RATIO = 1f / BITMAP_SCALE_FACTOR
    }
}
