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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.data.ItemInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class FakeLauncherUiStateNotifierTest {

    private lateinit var fakeNotifier: FakeLauncherUiStateNotifier

    @Before
    fun setUp() {
        fakeNotifier = FakeLauncherUiStateNotifier()
    }

    @Test
    fun addCallback_shouldRecordCallback() {
        val callback = mock<BgDataModel.Callbacks>()
        fakeNotifier.addCallback(callback)
        assertThat(fakeNotifier.receivedCallbacks).containsExactly(callback)
    }

    @Test
    fun removeCallback_shouldRemoveRecordedCallback() {
        val callback = mock<BgDataModel.Callbacks>()
        fakeNotifier.addCallback(callback)
        fakeNotifier.removeCallback(callback)
        assertThat(fakeNotifier.receivedCallbacks).isEmpty()
    }

    @Test
    fun notifyItemModifiedOptimistically_shouldRecordModification() {
        val itemInfo = ItemInfo()
        fakeNotifier.notifyItemModifiedOptimistically(itemInfo)
        assertThat(fakeNotifier.optimisticModifications).containsExactly(itemInfo)
    }

    @Test
    fun notifyModelChanged_shouldRecordModelChange() {
        val changeLog = IModelWriter.ChangeLog()
        val owner = mock<BgDataModel.Callbacks>()
        fakeNotifier.notifyModelChanged(changeLog, owner)
        assertThat(fakeNotifier.modelChanges).containsExactly(Pair(changeLog, owner))
    }
}
