/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.icons.cache

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.os.UserHandle
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.PersistedItemState

interface CachingLogic<T> {

    fun getComponent(item: T): ComponentName

    fun getUser(item: T): UserHandle

    /** Loads the user visible label for the object */
    fun getLabel(item: T): CharSequence?

    /**
     * Returns the application info associated with the object. This is used to maintain the
     * "freshness" of the disk cache. If null, the item will not be persisted to the disk
     */
    fun getApplicationInfo(item: T): ApplicationInfo?

    fun loadIcon(request: IconLoadRequest<T>): BitmapInfo

    /**
     * Returns a persistable string that can be used to indicate indicate the correctness of the
     * cache for the provided item
     */
    fun getFreshnessIdentifier(item: T, iconProvider: IconProvider): PersistedItemState?
}
