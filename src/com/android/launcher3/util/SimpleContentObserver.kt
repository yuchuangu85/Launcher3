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

package com.android.launcher3.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.util.Log
import com.android.launcher3.dagger.ApplicationContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.function.BiConsumer

/** A wrapper over [ContentObserver] for easier testing */
class SimpleContentObserver
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    @Assisted private val executor: LooperExecutor,
    @Assisted private val callback: BiConsumer<Boolean, Uri?>,
) : ContentObserver(executor.handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        callback.accept(selfChange, uri)
    }

    fun registerSafely(uri: Uri, notifyForDescendants: Boolean): Boolean =
        runCatching {
                context.contentResolver.registerContentObserver(uri, notifyForDescendants, this)
            }
            .onFailure { Log.e(TAG, "Error registering observer", it) }
            .isSuccess

    fun unregisterSafely(): Boolean =
        runCatching { context.contentResolver.unregisterContentObserver(this) }
            .onFailure { Log.e(TAG, "Error unregistering observer", it) }
            .isSuccess

    @AssistedFactory
    interface Factory {

        fun create(
            @Assisted executor: LooperExecutor,
            @Assisted callback: BiConsumer<Boolean, Uri?>,
        ): SimpleContentObserver
    }

    companion object {
        const val TAG = "SimpleContentObserver"
    }
}
