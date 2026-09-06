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

package com.android.launcher3.model.tasks

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo.FEATURE_RECONFIGURABLE
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.ModelTestExtensions.loadModelSync
import com.android.launcher3.util.ModelTestExtensions.preloadModelData
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WidgetFeaturesUpdateTaskTest {

    @get:Rule val context = SandboxApplication().withModelDependency()

    private val widget1 =
        LauncherAppWidgetInfo().apply {
            id = 1
            appWidgetId = 1
        }
    private val widget2 =
        LauncherAppWidgetInfo().apply {
            id = 2
            appWidgetId = 2
        }

    @Test
    fun mismatchItemsIgnored() {
        simulateUpdate(appWidgetId = 5) { doReturn(true).whenever(this).isReconfigurable }

        Truth.assertThat(widget1.widgetFeatures).isEqualTo(0)
        Truth.assertThat(widget2.widgetFeatures).isEqualTo(0)
    }

    @Test
    fun widgetFeaturesUpdates() {
        simulateUpdate(appWidgetId = 2) { doReturn(true).whenever(this).isReconfigurable }

        Truth.assertThat(widget1.widgetFeatures).isEqualTo(0)
        Truth.assertThat(widget2.widgetFeatures).isEqualTo(FEATURE_RECONFIGURABLE)
    }

    private fun simulateUpdate(
        appWidgetId: Int,
        infoBuilder: LauncherAppWidgetProviderInfo.() -> Unit,
    ) {
        context.appComponent.testableModelState.model.loadModelSync()
        context.preloadModelData(widget1, widget2)

        val info = mock<LauncherAppWidgetProviderInfo>()
        infoBuilder.invoke(info)
        context.appComponent.testableModelState.model.enqueueModelUpdateTask(
            WidgetFeaturesUpdateTask(appWidgetId, info)
        )
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {}
    }
}
