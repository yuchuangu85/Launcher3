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

package com.android.launcher3.dagger

import com.android.app.displaylib.PerDisplayRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.function.Consumer

/** A repository that provide object based on [PerDisplayComponent] */
class PerDisplayComponentRepository<T>
@AssistedInject
constructor(
    @Assisted override val debugName: String,
    @Assisted(REQUIRED_GETTER) private val objectGetter: (PerDisplayComponent) -> T,
    @Assisted(OPTIONAL_GETTER) private val optionalObjectGetter: (PerDisplayComponent) -> T?,
    private val perDisplayComponentRepository: PerDisplayRepository<PerDisplayComponent>,
) : PerDisplayRepository<T> {
    override fun get(displayId: Int): T? =
        perDisplayComponentRepository[displayId]?.let { objectGetter(it) }

    override fun getOrDefault(displayId: Int): T =
        objectGetter(perDisplayComponentRepository.getOrDefault(displayId))

    override fun forEach(createIfAbsent: Boolean, action: Consumer<T>) {
        if (createIfAbsent) {
            perDisplayComponentRepository.forEach(createIfAbsent = true) {
                action.accept(objectGetter(it))
            }
        } else {
            perDisplayComponentRepository.forEach(createIfAbsent = false) {
                optionalObjectGetter(it)?.let { item -> action.accept(item) }
            }
        }
    }

    @AssistedFactory
    interface Factory<T> {
        fun createOptional(
            @Assisted debugName: String,
            @Assisted(REQUIRED_GETTER) objectGetter: (PerDisplayComponent) -> T,
            @Assisted(OPTIONAL_GETTER) optionalObjectGetter: (PerDisplayComponent) -> T?,
        ): PerDisplayComponentRepository<T>

        fun create(
            debugName: String,
            objectGetter: (PerDisplayComponent) -> T,
        ): PerDisplayComponentRepository<T> = createOptional(debugName, objectGetter, objectGetter)
    }

    companion object {
        const val OPTIONAL_GETTER = "OPTIONAL_GETTER"
        const val REQUIRED_GETTER = "REQUIRED_GETTER"
    }
}
