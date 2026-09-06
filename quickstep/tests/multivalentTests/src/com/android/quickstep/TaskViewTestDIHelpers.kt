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

package com.android.quickstep

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.Size
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.display.LauncherDisplayInfo
import com.android.launcher3.util.AllModulesMinusWMProxy
import com.android.launcher3.util.launcheremulator.TestWindowManagerProxy
import com.android.launcher3.util.launcheremulator.models.DeviceEmulationData
import dagger.BindsInstance
import dagger.Component
import dagger.Component.Builder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@LauncherAppSingleton
@Component(modules = [AllModulesMinusWMProxy::class])
interface TaskViewTestComponent : LauncherAppComponent {
    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {
        @BindsInstance fun bindRecentsModel(recentsModel: RecentsModel): Builder

        override fun build(): TaskViewTestComponent
    }
}

object TaskViewTestDIHelpers {
    @JvmStatic
    fun mockRecentsModel(): RecentsModel {
        val recentsModel: RecentsModel = mock()
        val taskThumbnailCache: TaskThumbnailCache = mock()
        whenever(taskThumbnailCache.highResLoadingState).thenReturn(HighResLoadingState())
        whenever(recentsModel.thumbnailCache).thenReturn(taskThumbnailCache)
        whenever(recentsModel.iconCache).thenReturn(mock())
        return recentsModel
    }

    @JvmStatic
    @JvmOverloads
    fun fakeLauncherDisplayInfo(size: Size, rotation: Int = Surface.ROTATION_0) =
        LauncherDisplayInfo(
            context = ApplicationProvider.getApplicationContext(),
            wmProxy =
                object :
                    TestWindowManagerProxy(
                        DeviceEmulationData(
                            name = "fake_display",
                            width = size.width,
                            height = size.height,
                            density = DisplayMetrics.DENSITY_DEFAULT,
                            densityMaxScale = 1f,
                            densityMinScale = 1f,
                            densityMinScaleInterval = 0f,
                            cutout = Rect(),
                            defaultGrid = "medium",
                            resourceOverrides = emptyMap(),
                            secondDisplay = null,
                            supportsFixedLandscape = true,
                        )
                    ) {

                    override fun getRotation(displayInfoContext: Context?): Int = rotation
                },
        )
}
