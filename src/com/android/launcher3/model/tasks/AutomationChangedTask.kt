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

package com.android.launcher3.model.tasks

import com.android.launcher3.Flags
import com.android.launcher3.LauncherModel
import com.android.launcher3.automation.AutomationChange
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_AUTOMATED
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.ItemInfoMatcher

/**
 * Updates [ItemInfoWithIcon] for packages that have their automation status changed from
 * [AutomationRepository]
 */
class AutomationChangedTask(val automationChange: AutomationChange) :
    LauncherModel.ModelUpdateTask {
    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        if (!Flags.enableAppAutomationIndicator()) return
        with(automationChange) {
            val packagesToUpdate = addedPackages + removedPackages
            if (packagesToUpdate.isEmpty()) return

            synchronized(dataModel) {
                val updatedWorkspaceItems =
                    dataModel.updateAndCollectWorkspaceItemInfos(
                        userHandle = userHandle,
                        workspaceItemOp = { info ->
                            info.targetPackage in packagesToUpdate && updateAutomationFlag(info)
                        },
                    )
                if (updatedWorkspaceItems.isNotEmpty()) {
                    taskController.bindUpdatedWorkspaceItems(updatedWorkspaceItems)
                }
            }

            synchronized(apps) {
                apps.updateRuntimeFlags(
                    ItemInfoMatcher.ofPackages(addedPackages, userHandle),
                    FlagOp.NO_OP.addFlag(FLAG_AUTOMATED),
                )
                apps.updateRuntimeFlags(
                    ItemInfoMatcher.ofPackages(removedPackages, userHandle),
                    FlagOp.NO_OP.removeFlag(FLAG_AUTOMATED),
                )
                taskController.bindApplicationsIfNeeded()
            }
        }
    }

    /**
     * Update the current flags in the item. Return true if the flags changed, and false if no
     * change.
     */
    private fun updateAutomationFlag(item: ItemInfoWithIcon): Boolean {
        val oldFlags = item.runtimeStatusFlags
        if (item.targetPackage in automationChange.addedPackages) {
            item.runtimeStatusFlags = item.runtimeStatusFlags or FLAG_AUTOMATED
        } else if (item.targetPackage in automationChange.removedPackages) {
            item.runtimeStatusFlags = item.runtimeStatusFlags and FLAG_AUTOMATED.inv()
        }
        return oldFlags != item.runtimeStatusFlags
    }
}
