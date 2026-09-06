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

import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.ModelWriterFactory
import dagger.Module
import dagger.Provides

/** Module providing UI-specific dependencies for the Organizer subgraph. */
@Module
class OrganizerModule {

    /** Provides an [IModelWriter] instance for the organizer flows. */
    @Provides
    @OrganizerScope
    fun provideModelWriter(factory: ModelWriterFactory): IModelWriter {
        return factory.create(
            verifyChanges = true,
            cellPosMapper = CellPosMapper.DEFAULT,
            modificationSource = BgDataModel.ModificationSource.ModelTask,
            owner = null,
        )
    }
}
