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
package com.android.launcher3.ui

import com.android.launcher3.model.BgDataModel.Callbacks
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.data.ItemInfo

/**
 * Temporary interface for a component that manages and dispatches UI state notifications.
 *
 * TODO(b/457449059): Remove this interface once the implementation is finalized.
 */
interface LauncherUiStateNotifier {

    /** Adds a callback to receive UI state notifications. */
    fun addCallback(callback: Callbacks)

    /** Removes a callback from receiving UI state notifications. */
    fun removeCallback(callback: Callbacks)

    /** Notifies the UI that an item has been modified optimistically. */
    fun notifyItemModifiedOptimistically(item: ItemInfo)

    /** Notifies the UI that the model has changed. */
    fun notifyModelChanged(changeLog: IModelWriter.ChangeLog, owner: Callbacks?)
}

/** A no-op implementation of [LauncherUiStateNotifier]. */
class NoOpLauncherUiStateNotifier : LauncherUiStateNotifier {
    override fun addCallback(callback: Callbacks) {}

    override fun removeCallback(callback: Callbacks) {}

    override fun notifyItemModifiedOptimistically(item: ItemInfo) {}

    override fun notifyModelChanged(changeLog: IModelWriter.ChangeLog, owner: Callbacks?) {}
}
