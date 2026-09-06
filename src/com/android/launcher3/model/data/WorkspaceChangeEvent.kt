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

package com.android.launcher3.model.data

import com.android.launcher3.model.BgDataModel.ModificationSource
import com.android.launcher3.model.BgDataModel.ModificationSource.ModelTask
import com.android.launcher3.model.BgDataModel.ModificationSource.UISurface
import com.android.launcher3.views.ActivityContext
import java.lang.ref.WeakReference
import java.util.function.Predicate

/** Represents a change being made to the existing [WorkspaceData] */
sealed class WorkspaceChangeEvent(actualOwner: ModificationSource) {

    private val ownerRef = WeakReference(actualOwner)

    /**
     * Returns true if the modification source is same as [surface]. UI surface changes are
     * generally user driven and the UI is updated as part of the interaction itself. Clients can
     * use this to exclude self-made changes.
     */
    fun isSource(surface: ActivityContext) = (ownerRef.get() as? UISurface)?.surface == surface

    /** New items were added to the model */
    class AddEvent(val items: List<ItemInfo>, owner: ModificationSource) :
        WorkspaceChangeEvent(owner)

    /** Some properties of existing items changed */
    class UpdateEvent(val items: Set<ItemInfo>, owner: ModificationSource) :
        WorkspaceChangeEvent(owner)

    /**
     * Some items were removed from the model. Note that the event uses a [Predicate] instead of
     * actual [ItemInfo] as the items may not exist anymore
     */
    class RemoveEvent(val items: Predicate<ItemInfo?>, owner: ModificationSource) :
        WorkspaceChangeEvent(owner)

    /**
     * Indicates a full refresh of data causing the UI surface to rebind their UI. Since a full
     * refresh is not meant to be skipped, source is always treated as [ModelTask].
     */
    class FullRefresh(val reason: String) : WorkspaceChangeEvent(ModelTask)
}
