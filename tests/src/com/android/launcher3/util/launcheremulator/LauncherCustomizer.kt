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
package com.android.launcher3.util.launcheremulator

import android.app.UiModeManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri.Builder
import android.os.RemoteException
import android.os.UserHandle
import android.platform.uiautomatorhelpers.DeviceHelpers.context
import android.platform.uiautomatorhelpers.DeviceHelpers.uiDevice
import android.provider.Settings.Global
import android.provider.Settings.System
import android.util.Log
import android.view.Display
import android.view.IWindowManager
import android.view.Surface
import android.view.WindowManagerGlobal
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.display.DisplayController
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.ModelTestExtensions.clearModelDb
import com.android.launcher3.util.NavigationMode
import com.android.launcher3.util.RoboApiWrapper.convertToSpy
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.launcheremulator.DensityPicker.Density
import com.android.launcher3.util.launcheremulator.models.DeviceEmulationData
import com.android.launcher3.util.launcheremulator.models.EmulationParams
import com.android.launcher3.util.launcheremulator.models.FontScale.DEFAULT
import com.android.launcher3.util.launcheremulator.models.LauncherOrientation
import com.android.launcher3.util.launcheremulator.models.LauncherOrientation.LANDSCAPE
import com.android.launcher3.util.launcheremulator.models.LauncherOrientation.PORTRAIT
import com.android.launcher3.util.launcheremulator.models.LauncherOrientation.SEASCAPE
import com.android.launcher3.util.window.WindowManagerProxy
import java.util.concurrent.CountDownLatch
import org.junit.Assert
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.reset

object LauncherCustomizer {
    private const val TAG = "LauncherCustomizer"
    private const val RESULT_SUCCESS = 1
    private const val COL_ICON_THEMED_VALUE = "boolean_value"

    // As specified in com.android.settings.development.RtlLayoutPreferenceController
    private const val RTL_ON = 1
    private const val RTL_OFF = 0

    /** Apply all non null customizations starting from device, then grid, font scale and theme */
    @Throws(Exception::class)
    fun applyAll(context: Context, params: EmulationParams) {
        context.appComponent.testableModelState.model.clearModelDb()

        System.putFloat(context.contentResolver, System.FONT_SCALE, params.fontScale.value)

        // Equivalent to adb shell settings put global debug.force_rtl 1 (or 0)
        Global.putInt(
            context.contentResolver,
            Global.DEVELOPMENT_FORCE_RTL,
            if (params.isRtl) RTL_ON else RTL_OFF,
        )

        emulate(context, params.device, params.density, params.navigationMode)

        applyFixedLandscape(context, params.isFixedLandscape)
        applyGridOption(context, params.grid)

        context
            .getSystemService(UiModeManager::class.java)!!
            .setApplicationNightMode(
                if (params.isDarkTheme) UiModeManager.MODE_NIGHT_YES
                else UiModeManager.MODE_NIGHT_NO
            )

        applyIsThemed(context, params.isUsingThemeIcons)

        // Flush the main thread and taskbar ui thread so that all the settings are applied
        syncUiState()

        if (!isOrientationCorrect(params.orientation) && !params.isFixedLandscape) {
            Log.d(TAG, "retry orientation: ${params.orientation}")
            applyOrientation(params.orientation)
        }
    }

    private fun applyFixedLandscape(context: Context, isFixedLandscape: Boolean) {
        val idp = InvariantDeviceProfile.INSTANCE[context]
        if (idp.isFixedLandscape == isFixedLandscape) return
        val latch = CountDownLatch(1)
        val listener = OnIDPChangeListener {
            if (idp.isFixedLandscape == isFixedLandscape) latch.countDown()
        }
        idp.addOnChangeListener(listener)
        LauncherPrefs.get(context).put(LauncherPrefs.FIXED_LANDSCAPE_MODE, isFixedLandscape)
        latch.await()
        idp.removeOnChangeListener(listener)
    }

    private fun applyGridOption(context: Context, gridParam: String) {
        sendGridRequest(context, "default_grid", "name", gridParam)
    }

    private fun applyIsThemed(context: Context, isThemed: Boolean) =
        sendGridRequest(context, "icon_themed", COL_ICON_THEMED_VALUE, isThemed)

    private fun sendGridRequest(context: Context, method: String, arg: String, argValue: Any?) {
        val testProviderAuthority = context.packageName + ".grid_control"
        val gridUri =
            Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(testProviderAuthority)
                .appendPath(method)
                .build()
        val values = ContentValues()
        values.putObject(arg, argValue)
        Assert.assertEquals(
            RESULT_SUCCESS,
            context.appComponent.gridCustomizationsProxy.update(gridUri, values, null, null, null),
        )
    }

    private fun isOrientationCorrect(orientation: LauncherOrientation): Boolean {
        return (orientation == PORTRAIT && !isLandscape() && !isSeascape()) ||
            (orientation == LANDSCAPE && isLandscape()) ||
            (orientation == SEASCAPE && isSeascape())
    }

    /** Returns if the device orientation is in landscape (width >= height) and the rotation is 0 */
    private fun isLandscape(): Boolean {
        val displayRotation = uiDevice.displayRotation
        val isLandscape = uiDevice.displayWidth >= uiDevice.displayHeight
        return isLandscape &&
            (displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_90)
    }

    /** Returns if the device orientation is in landscape (width >= height) and the rotation is */
    private fun isSeascape(): Boolean {
        val displayRotation = uiDevice.displayRotation
        val isLandscape = uiDevice.displayWidth >= uiDevice.displayHeight
        return isLandscape && displayRotation == Surface.ROTATION_270
    }

    @Throws(RemoteException::class)
    private fun applyOrientation(orientation: LauncherOrientation) {
        if (!isOrientationCorrect(orientation)) {
            when (orientation) {
                PORTRAIT -> uiDevice.setOrientationPortrait()
                LANDSCAPE -> uiDevice.setOrientationLandscape()
                SEASCAPE -> uiDevice.setOrientationRight()
            }
        }
    }

    /** @param device data required to emulate a given device display */
    @Throws(Exception::class)
    private fun emulate(
        context: Context,
        device: DeviceEmulationData,
        densityScale: Density,
        navigationMode: NavigationMode? = null,
    ) {
        val densities = DensityPicker.getDisplayEntries(device)

        // Force ui state to settle before touching the mocks
        syncUiState()

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            val wmp = WindowManagerProxy.INSTANCE[context]

            // Avoid aggressive resets if possible, or wrap them tightly
            if (mockingDetails(wmp).isSpy) reset(wmp)

            val wmpOverride =
                TestWindowManagerProxy(device).apply { setNavigationMode(navigationMode) }
            wmp.convertToSpy {
                WindowManagerProxy::class
                    .java
                    .getDeclaredMethod(it.method.name, *it.method.parameterTypes)
                    .apply { isAccessible = true }
                    .invoke(wmpOverride, *it.arguments)
            }
        }

        WindowManagerGlobal.getWindowManagerService()!!.apply {
            setForcedDisplayScalingMode(Display.DEFAULT_DISPLAY, 1)

            // This is equivalent to calling: adb shell wm size {device.width}x{device.height}"
            setForcedDisplaySize(Display.DEFAULT_DISPLAY, device.width, device.height)

            // Change density twice to force display controller to reset its state
            setAndWaitForDensity(context, densities[densityScale]!! + 1)
            setAndWaitForDensity(context, densities[densityScale]!!)
        }
    }

    @Throws(Exception::class)
    private fun IWindowManager.setAndWaitForDensity(context: Context, targetDensity: Int) {
        val latch = CountDownLatch(1)
        MAIN_EXECUTOR.execute {
            val controller = DisplayController.INSTANCE[context]
            if (controller.info.densityDpi == targetDensity) {
                latch.countDown()
                return@execute
            }
            controller.listenable?.let {
                var safeCloseable: SafeCloseable? = null
                safeCloseable =
                    it.forEachChange(MAIN_EXECUTOR) { info, _ ->
                        if (info.densityDpi == targetDensity) {
                            // Remove listener asynchronously
                            safeCloseable?.close()
                            latch.countDown()
                        }
                    }
            }
        }
        setForcedDisplayDensityForUser(
            Display.DEFAULT_DISPLAY,
            targetDensity,
            UserHandle.myUserId(),
        )
        latch.await()
    }

    @Throws(Exception::class)
    fun stopEmulation() {
        // Disable themed icon to prevent interfering with future image tests
        sendGridRequest(context, "icon_themed", COL_ICON_THEMED_VALUE, false)
        System.putFloat(context.contentResolver, System.FONT_SCALE, DEFAULT.value)
        Global.putInt(context.contentResolver, Global.DEVELOPMENT_FORCE_RTL, RTL_OFF)
        applyFixedLandscape(context, false)

        // IMPORTANT: Wait for the main thread and taskbar ui thread to settle after those settings
        // changes. This ensures the Taskbar thread has finished reacting to the new config
        // BEFORE we pull the rug out by resetting the mocks.
        syncUiState()

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            val wmp = WindowManagerProxy.INSTANCE[context]
            if (mockingDetails(wmp).isSpy) reset(wmp)
        }

        WindowManagerGlobal.getWindowManagerService()!!.apply {
            clearForcedDisplaySize(Display.DEFAULT_DISPLAY)
            clearForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, UserHandle.myUserId())
        }
        uiDevice.setOrientationNatural()
        syncUiState()
    }

    private fun syncUiState() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {}
        TestUtil.runOnExecutorSync(THREAD_POOL_EXECUTOR) {}
        if (enableTaskbarUiThread()) {
            TestUtil.runOnExecutorSync(getTaskbarUiThread()) {}
        }
        // Sleep 500ms to let theme to be applied and icon alpha animation to finish
        Thread.sleep(500)
        uiDevice.waitForIdle()
    }
}
