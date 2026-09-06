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
package com.android.launcher3.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherModel
import com.android.launcher3.model.AbstractWorkspaceModelTest
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RoboApiWrapper
import java.util.Collections
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class DefaultLauncherUiStateNotifierTest : AbstractWorkspaceModelTest() {

    private lateinit var notifier: DefaultLauncherUiStateNotifier
    private lateinit var spiedModel: LauncherModel

    private val bgDataModel: BgDataModel
        get() = mTargetContext.appComponent.testableModelState.dataModel

    @Before
    override fun setup() {
        super.setup()
        spiedModel = spy(model)
        notifier =
            DefaultLauncherUiStateNotifier(Executors.MAIN_EXECUTOR, bgDataModel, true, spiedModel)
    }

    @Test
    fun addCallback_shouldReceiveNotifications() {
        val callback = mock<BgDataModel.Callbacks>()
        notifier.addCallback(callback)
        val item = ItemInfo()
        notifier.notifyItemModifiedOptimistically(item)
        RoboApiWrapper.waitForLooperSync(Executors.MAIN_EXECUTOR.looper)
        verify(callback).bindItemsUpdated(Collections.singleton(item))
    }

    @Test
    fun removeCallback_shouldNotReceiveNotifications() {
        val callback = mock<BgDataModel.Callbacks>()
        notifier.addCallback(callback)
        notifier.removeCallback(callback)
        val item = ItemInfo()
        notifier.notifyItemModifiedOptimistically(item)
        RoboApiWrapper.waitForLooperSync(Executors.MAIN_EXECUTOR.looper)
        verify(callback, never()).bindItemsUpdated(any())
    }

    @Test
    fun notifyItemModifiedOptimistically_shouldNotifyCallbacks() {
        val callback1 = mock<BgDataModel.Callbacks>()
        val callback2 = mock<BgDataModel.Callbacks>()
        notifier.addCallback(callback1)
        notifier.addCallback(callback2)
        val item = ItemInfo()
        notifier.notifyItemModifiedOptimistically(item)
        RoboApiWrapper.waitForLooperSync(Executors.MAIN_EXECUTOR.looper)
        verify(callback1).bindItemsUpdated(Collections.singleton(item))
        verify(callback2).bindItemsUpdated(Collections.singleton(item))
    }

    @Test
    fun notifyModelChanged_shouldNotifyCallbacks() {
        val callback = mock<BgDataModel.Callbacks>()
        notifier.addCallback(callback)
        val item = ItemInfo()
        val changeLog = IModelWriter.ChangeLog(itemsModified = mutableSetOf(item))
        notifier.notifyModelChanged(changeLog, null)
        RoboApiWrapper.waitForLooperSync(Executors.MAIN_EXECUTOR.looper)
        verify(callback).bindItemsUpdated(setOf(item))
    }

    @Test
    fun notifyModelChanged_shouldNotNotifyOwner() {
        val owner = mock<BgDataModel.Callbacks>()
        val other = mock<BgDataModel.Callbacks>()
        notifier.addCallback(owner)
        notifier.addCallback(other)
        val item = ItemInfo()
        val changeLog = IModelWriter.ChangeLog(itemsModified = mutableSetOf(item))
        notifier.notifyModelChanged(changeLog, owner)
        RoboApiWrapper.waitForLooperSync(Executors.MAIN_EXECUTOR.looper)
        verify(owner, never()).bindItemsUpdated(any())
        verify(other).bindItemsUpdated(setOf(item))
    }

    @Test
    fun verifyUiConsistency_whenModelChanged_shouldRebind() {
        notifier.addCallback(mock<BgDataModel.Callbacks>())
        val changeLog = IModelWriter.ChangeLog()
        bgDataModel.lastBindId = 5
        notifier.notifyModelChanged(changeLog, null)
        bgDataModel.lastBindId = 4
        RoboApiWrapper.waitForLooperSync(Executors.MAIN_EXECUTOR.looper)
        verify(spiedModel).rebindCallbacks(any())
    }

    @Test
    fun verifyUiConsistency_whenModelNotChanged_shouldNotRebind() {
        notifier.addCallback(mock<BgDataModel.Callbacks>())
        val changeLog = IModelWriter.ChangeLog()
        bgDataModel.lastBindId = 5
        notifier.notifyModelChanged(changeLog, null)
        RoboApiWrapper.waitForLooperSync(Executors.MAIN_EXECUTOR.looper)
        verify(spiedModel, never()).rebindCallbacks(any())
    }

    @Test
    fun verifyUiConsistency_whenNewerModelBound_shouldNotRebind() {
        notifier.addCallback(mock<BgDataModel.Callbacks>())
        val changeLog = IModelWriter.ChangeLog()
        bgDataModel.lastBindId = 5
        notifier.notifyModelChanged(changeLog, null)
        bgDataModel.lastBindId = 6
        RoboApiWrapper.waitForLooperSync(Executors.MAIN_EXECUTOR.looper)
        verify(spiedModel, never()).rebindCallbacks(any())
    }
}
