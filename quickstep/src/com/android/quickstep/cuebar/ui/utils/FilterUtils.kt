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

package com.android.quickstep.cuebar.ui.utils

import com.android.cuebar.ui.viewmodel.ActionType
import com.android.cuebar.ui.viewmodel.ActionViewModel

object FilterUtils {
    /**
     * Filters a list of actions, combining actions with the same icon id. The labels of the
     * combined actions uses the first action label, and the icon repeatCount increases.
     *
     * @param actions The list of actions to filter.
     * @return A new list of filtered actions.
     */
    fun filterActions(actions: List<ActionViewModel>): List<ActionViewModel> {
        val filteredActionMap = mutableMapOf<String, ActionViewModel>()
        actions.forEach { action ->
            filteredActionMap
                .getOrPut(action.icon.iconId) { action }
                .also { existingAction ->
                    if (existingAction !== action) {
                        filteredActionMap[action.icon.iconId] =
                            existingAction.copy(
                                icon =
                                    existingAction.icon.copy(
                                        repeatCount = existingAction.icon.repeatCount + 1
                                    )
                            )
                    }
                }
        }
        val filteredList = mutableListOf<ActionViewModel>()
        for (action in filteredActionMap.values) {
            if (action.actionType == ActionType.MR) {
                filteredList.add(0, action)
            } else {
                filteredList.add(action)
            }
        }
        return filteredList
    }
}
