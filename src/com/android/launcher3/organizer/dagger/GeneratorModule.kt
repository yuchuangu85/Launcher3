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

package com.android.launcher3.organizer.dagger

import com.android.launcher3.organizer.generator.CompositeClassifier
import com.android.launcher3.organizer.generator.DefaultTopicProvider
import com.android.launcher3.organizer.generator.ItemInfoClassifier
import com.android.launcher3.organizer.generator.MostUsedItemInfoClassifier
import com.android.launcher3.organizer.generator.PackageManagerItemInfoClassifier
import com.android.launcher3.organizer.generator.TopicProvider
import dagger.Binds
import dagger.Module
import dagger.Provides

@Module
abstract class GeneratorModule {

    @Binds @OrganizerScope abstract fun bindTopicProvider(impl: DefaultTopicProvider): TopicProvider

    companion object {
        @Provides
        @OrganizerScope
        fun provideItemInfoClassifier(
            packageManagerItemInfoClassifier: PackageManagerItemInfoClassifier,
            mostUsedItemInfoClassifier: MostUsedItemInfoClassifier,
        ): ItemInfoClassifier {
            return CompositeClassifier(
                listOf(packageManagerItemInfoClassifier, mostUsedItemInfoClassifier)
            )
        }
    }
}
