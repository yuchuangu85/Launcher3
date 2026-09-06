/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.desktop

import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Context
import android.content.res.Resources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.RemoteTransition
import android.window.TransitionFilter
import android.window.TransitionFilter.CONTAINER_ORDER_ANY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.display.DisplayController
import com.android.quickstep.SystemUiProxy
import com.android.window.flags.Flags
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopAppLaunchTransitionManagerTest {

    @get:Rule(order = 0) val mSetFlagsRule = SetFlagsRule()
    private val context = mock<Context>()
    private val applicationContext = mock<Context>()
    private val resources = mock<Resources>()
    private val systemUiProxy = mock<SystemUiProxy>()
    private val displayController = mock<DisplayController>()
    private lateinit var transitionManager: DesktopAppLaunchTransitionManager

    @Before
    fun setUp() {
        whenever(context.applicationContext).thenReturn(applicationContext)
        whenever(context.resources).thenReturn(resources)
        whenever(applicationContext.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(42)
        whenever(DesktopModeStatus.canEnterDesktopMode(context)).thenReturn(true)
        transitionManager =
            DesktopAppLaunchTransitionManager(context, systemUiProxy, displayController)
    }

    @Test
    fun registerTransitions_registersTransition() {
        transitionManager.registerTransitions()

        verify(systemUiProxy, times(1)).registerRemoteTransition(any())
    }

    @Test
    @DisableFlags(Flags.FLAG_CROSS_DISPLAY_TRANSITION_V2)
    fun registerTransitions_usesCorrectFilter() {
        transitionManager.registerTransitions()
        val transitionArgumentCaptor = argumentCaptor<RemoteTransition>()

        verify(systemUiProxy).registerRemoteTransition(transitionArgumentCaptor.capture())

        assertThat(transitionArgumentCaptor.lastValue).isNotNull()

        val filter = transitionArgumentCaptor.lastValue.filter ?: TransitionFilter()
        assertThat(filter.mTypeSet).isEqualTo(intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT))

        assertThat(filter.mRequirements).hasLength(1)
        val launchRequirement = filter.mRequirements!![0]
        assertThat(launchRequirement.mModes).isEqualTo(intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT))
        assertThat(launchRequirement.mActivityType).isEqualTo(ACTIVITY_TYPE_STANDARD)
        assertThat(launchRequirement.mWindowingMode).isEqualTo(WINDOWING_MODE_FREEFORM)
        assertThat(launchRequirement.mOrder).isEqualTo(CONTAINER_ORDER_ANY)
    }

    @Test
    @EnableFlags(Flags.FLAG_CROSS_DISPLAY_TRANSITION_V2)
    fun registerTransitions_usesCorrectFilter_withCrossDisplayFlag() {
        transitionManager.registerTransitions()
        val transitionArgumentCaptor = argumentCaptor<RemoteTransition>()

        verify(systemUiProxy).registerRemoteTransition(transitionArgumentCaptor.capture())

        assertThat(transitionArgumentCaptor.lastValue).isNotNull()

        val filter = transitionArgumentCaptor.lastValue.filter ?: TransitionFilter()
        assertThat(filter.mTypeSet).isEqualTo(intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT))

        assertThat(filter.mRequirements).hasLength(2)
        val launchRequirement = filter.mRequirements!![0]
        assertThat(launchRequirement.mModes).isEqualTo(intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT))
        assertThat(launchRequirement.mActivityType).isEqualTo(ACTIVITY_TYPE_STANDARD)
        assertThat(launchRequirement.mWindowingMode).isEqualTo(WINDOWING_MODE_FREEFORM)
        assertThat(launchRequirement.mOrder).isEqualTo(CONTAINER_ORDER_ANY)

        val notCrossDisplayRequirement = filter.mRequirements!![1]
        assertThat(notCrossDisplayRequirement.mNot).isTrue()
        assertThat(notCrossDisplayRequirement.mIsCrossDisplayMove).isTrue()
    }
}
