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

package com.android.launcher3.statehandlers

import android.app.WallpaperManager
import android.content.res.Resources
import android.os.IBinder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.view.ViewTreeObserver
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_EXPRESSIVE_FOLDER_EXPANSION
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherRootView
import com.android.launcher3.LauncherState
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.views.ScrimView
import java.util.Collections
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.reset
import org.mockito.Mockito.same
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any

@SmallTest
@RunWith(AndroidJUnit4::class)
@SkipOnDeviceless
class DepthControllerTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    /**
     * This test needs to be skipped in Robolectric because Mockito in Kotlin doesn't work well with
     * java interfaces with default methods. Outside of Robolectric, these methods cannot be mocked
     * and will call the default implementation; inside Robolectric, these methods must be mocked.
     * As a result, there is no way to write a test that work both inside and outside Robolectric.
     */
    @get:Rule var mlimitDevicesRule: LimitDevicesRule = LimitDevicesRule()

    private lateinit var underTest: LauncherDepthController
    @get:Rule val mockito = MockitoJUnit.rule()
    @Mock private lateinit var launcher: QuickstepLauncher
    @Mock private lateinit var stateManager: StateManager<LauncherState, Launcher>
    @Mock private lateinit var resource: Resources
    @Mock private lateinit var dragLayer: DragLayer
    @Mock private lateinit var viewTreeObserver: ViewTreeObserver
    @Mock private lateinit var wallpaperManager: WallpaperManager
    @Mock private lateinit var rootView: LauncherRootView
    @Mock private lateinit var windowToken: IBinder
    @Mock private lateinit var scrimView: ScrimView
    @Mock private lateinit var lifeCycle: Lifecycle

    @Before
    fun setUp() {
        `when`(launcher.resources).thenReturn(resource)
        `when`(launcher.dragLayer).thenReturn(dragLayer)
        `when`(dragLayer.viewTreeObserver).thenReturn(viewTreeObserver)
        `when`(launcher.stateManager).thenReturn(stateManager)
        `when`(launcher.depthBlurTargets).thenReturn(Collections.emptyList())
        `when`(launcher.getSystemService(WallpaperManager::class.java)).thenReturn(wallpaperManager)
        `when`(wallpaperManager.setWallpaperZoomOut(any(), anyFloat())).then { /* Do nothing */ }
        `when`(launcher.rootView).thenReturn(rootView)
        `when`(rootView.windowToken).thenReturn(windowToken)
        `when`(launcher.scrimView).thenReturn(scrimView)
        `when`(launcher.lifecycle).thenReturn(lifeCycle)
        `when`(lifeCycle.addObserver(any())).then { /* Do nothing */ }

        val blurState = MutableListenableRef(true)
        underTest = LauncherDepthController(launcher, blurState)
    }

    @Test
    fun setActivityStarted_add_onDrawListener() {
        underTest.setActivityStarted(true)

        verify(viewTreeObserver).addOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun setActivityStopped_not_remove_onDrawListener() {
        underTest.setActivityStarted(false)

        // Because underTest.mOnDrawListener is never added
        verifyNoMoreInteractions(viewTreeObserver)
    }

    @Test
    fun setActivityStared_then_stopped_remove_onDrawListener() {
        underTest.setActivityStarted(true)
        reset(viewTreeObserver)

        underTest.setActivityStarted(false)

        verify(viewTreeObserver).removeOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun setActivityStared_then_stopped_multiple_times_remove_onDrawListener_once() {
        underTest.setActivityStarted(true)
        reset(viewTreeObserver)

        underTest.setActivityStarted(false)
        underTest.setActivityStarted(false)
        underTest.setActivityStarted(false)

        // Should just remove mOnDrawListener once
        verify(viewTreeObserver).removeOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun test_onInvalidSurface_multiple_times_add_onDrawListener_once() {
        underTest.onInvalidSurface()
        underTest.onInvalidSurface()
        underTest.onInvalidSurface()

        // We should only call addOnDrawListener 1 time
        verify(viewTreeObserver).addOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun test_blurWorkspaceDepthTargets() {
        // Transitioning to ALL_APPS from any state should blur the workspace depth targets.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.NORMAL)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.SPRING_LOADED)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.EDIT_MODE)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.BACKGROUND_APP)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        // Returning from ALL_APPS to NORMAL should continue blurring the workspace depth targets.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.NORMAL)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        // Exiting ALL_APPS to other states such as drag-and-drop should not blur the workspace.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.SPRING_LOADED)
        assertFalse(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.EDIT_MODE)
        assertFalse(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.OVERVIEW)
        assertFalse(underTest.blurWorkspaceDepthTargets())
    }

    @Test
    fun test_blurWorkspaceDepthTargets_withTargetState() {
        // Transitioning to ALL_APPS from any state should blur the workspace depth targets.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.NORMAL)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.SPRING_LOADED)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.EDIT_MODE)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.BACKGROUND_APP)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        // Returning from ALL_APPS to NORMAL should continue blurring the workspace depth targets.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.NORMAL)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        // Exiting ALL_APPS to other states such as drag-and-drop should not blur the workspace.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.SPRING_LOADED)
        assertFalse(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.EDIT_MODE)
        assertFalse(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.OVERVIEW)
        assertFalse(underTest.blurWorkspaceDepthTargets())
    }

    @Test
    @DisableFlags(FLAG_ENABLE_EXPRESSIVE_FOLDER_EXPANSION)
    fun whenExpressiveFolderExpansionFalse_onlySetDepth() {
        `when`(stateManager.state).thenReturn(LauncherState.NORMAL)
        assertNull(underTest.folderZoom)
        underTest.stateDepth.value = 1f
        verify(wallpaperManager).setWallpaperZoomOut(windowToken, 1f)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_EXPRESSIVE_FOLDER_EXPANSION)
    fun wallpaperZoom_setsWallpaperZoom() {
        val zoom = 0.75f
        underTest.folderZoom.value = zoom
        verify(wallpaperManager).setWallpaperZoomOut(windowToken, zoom)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_EXPRESSIVE_FOLDER_EXPANSION)
    fun wallpaperZoom_andStateDepth_setsMaxWallpaperZoom() {
        `when`(stateManager.state).thenReturn(LauncherState.NORMAL)
        val folderZoom = 0.8f
        val stateDepth = 0.5f
        underTest.stateDepth.value = stateDepth
        underTest.folderZoom.value = folderZoom

        // wallpaperZoom should be max of folderZoom and stateDepth
        verify(wallpaperManager, atLeastOnce()).setWallpaperZoomOut(windowToken, folderZoom)

        reset(wallpaperManager)

        val folderZoom2 = 0.6f
        val stateDepth2 = 1f
        underTest.folderZoom.value = folderZoom2
        underTest.stateDepth.value = stateDepth2

        verify(wallpaperManager, atLeastOnce()).setWallpaperZoomOut(windowToken, stateDepth2)
    }
}
