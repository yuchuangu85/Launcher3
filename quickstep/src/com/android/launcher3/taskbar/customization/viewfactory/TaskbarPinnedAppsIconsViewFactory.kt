/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.taskbar.customization.viewfactory

import android.view.View
import android.view.ViewGroup
import androidx.core.view.get
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.R
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.util.ViewCache

/** View factory for each taskbar icon in pinned container. */
class TaskbarPinnedAppsIconsViewFactory(
    private val activityContext: TaskbarActivityContext,
    private val parentView: ViewGroup,
) : TaskbarIconsViewFactory<ItemInfo> {

    private val viewCache = ViewCache()

    override fun getView(item: ItemInfo, index: Int): View {
        val expectedLayoutResId = getExpectedLayoutResId(item)
        var itemView = findViewToRecycle(item, expectedLayoutResId, index)
        if (itemView != null) return itemView

        if (item !is CollectionInfo) {
            itemView = viewCache.getView(expectedLayoutResId, activityContext, parentView)
            (itemView as BubbleTextView).setContainerTextVisibility(false)
            return itemView
        }

        when (item.itemType) {
            ITEM_TYPE_APP_GROUP -> {
                itemView =
                    AppPairIcon.inflateIcon(
                        expectedLayoutResId,
                        activityContext,
                        parentView,
                        item as AppPairInfo,
                        BubbleTextView.DISPLAY_TASKBAR,
                    )
                itemView.titleTextView.setContainerTextVisibility(false)
            }

            ITEM_TYPE_FOLDER -> {
                itemView =
                    FolderIcon.inflateFolderAndIcon(
                        expectedLayoutResId,
                        activityContext,
                        parentView,
                        item as FolderInfo,
                    )
                itemView.folderName.setContainerTextVisibility(false)
            }

            else -> {
                throw IllegalStateException("Unexpected item type: " + item.itemType)
            }
        }
        return itemView
    }

    override fun getExpectedLayoutResId(item: ItemInfo): Int {
        return if (item.isPredictedItem) {
            R.layout.taskbar_predicted_app_icon
        } else if (item is CollectionInfo) {
            if (item.itemType == ITEM_TYPE_APP_GROUP) {
                R.layout.app_pair_icon
            } else {
                R.layout.folder_icon
            }
        } else {
            R.layout.taskbar_app_icon
        }
    }

    override fun findViewToRecycle(
        item: ItemInfo,
        expectedLayoutResId: Int,
        currentIndex: Int,
    ): View? {
        while (currentIndex < parentView.childCount) {
            val itemView = parentView[currentIndex]
            if (
                itemView.sourceLayoutResId != expectedLayoutResId ||
                    (item is CollectionInfo && itemView.tag !== item)
            ) {
                removeAndRecycle(itemView)
            } else {
                // View found
                return itemView
            }
        }
        return null
    }

    override fun removeAndRecycle(view: View) {
        parentView.removeView(view)
        view.setOnClickListener(null)
        view.onLongClickListener = null
        if (view.tag !is CollectionInfo) {
            viewCache.recycleView(view.sourceLayoutResId, view)
        }
        view.tag = null
    }
}
