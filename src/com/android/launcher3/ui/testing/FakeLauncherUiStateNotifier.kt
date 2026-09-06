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
package com.android.launcher3.ui.testing

import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.ui.LauncherUiStateNotifier

class FakeLauncherUiStateNotifier : LauncherUiStateNotifier {
    val receivedCallbacks = mutableListOf<BgDataModel.Callbacks>()
    val optimisticModifications = mutableListOf<ItemInfo>()
    val modelChanges = mutableListOf<Pair<IModelWriter.ChangeLog, BgDataModel.Callbacks?>>()

    override fun addCallback(callback: BgDataModel.Callbacks) {
        receivedCallbacks.add(callback)
    }

    override fun removeCallback(callback: BgDataModel.Callbacks) {
        receivedCallbacks.remove(callback)
    }

    override fun notifyItemModifiedOptimistically(item: ItemInfo) {
        optimisticModifications.add(item)
    }

    override fun notifyModelChanged(
        changeLog: IModelWriter.ChangeLog,
        owner: BgDataModel.Callbacks?,
    ) {
        modelChanges.add(Pair(changeLog, owner))
    }
}
