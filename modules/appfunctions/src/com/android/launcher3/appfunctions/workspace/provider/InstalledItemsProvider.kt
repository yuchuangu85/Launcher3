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
package com.android.launcher3.appfunctions.workspace.provider

/** Provider for installed items on device displayed on the workspace, represented as [T]. */
interface InstalledItemsProvider<T> {

    /**
     * Returns a list of installed items on the workspace.
     *
     * @param orderByUsageStats If true, orders apps by usage; otherwise uses default order for
     *   personalization.
     * @return A list of [T] representing the installed items.
     */
    suspend fun getInstalledItems(orderByUsageStats: Boolean): List<T>
}
