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

package com.android.launcher3.taskbar.rules

import android.companion.datatransfer.continuity.TaskContinuityManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import androidx.test.core.app.ApplicationProvider
import com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat
import com.android.app.displaylib.PerDisplayRepository
import com.android.app.displaylib.fakes.FakePerDisplayRepository
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.LauncherMultivalentJUnit.Companion.isRunningInRobolectric
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SandboxWmProxyModule
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCacheSandbox
import com.android.launcher3.util.TaskbarModeUtil
import com.android.launcher3.util.VirtualDisplaysRule
import com.android.launcher3.util.launcheremulator.LauncherCustomizer
import com.android.launcher3.util.launcheremulator.models.DeviceEmulationData
import com.android.launcher3.util.launcheremulator.models.EmulationParams
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.quickstep.SystemUiProxy
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import dagger.Module
import dagger.Provides
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

/**
 * [SandboxApplication] for running Taskbar tests.
 *
 * Tests needs to declare this [Context] as a [TestRule] for it to work. Context operations will
 * fail before the rule's setup phase, because [base] will not be initialized and attached yet.
 * Premature access will throw a [NullPointerException].
 *
 * Tests need to run on a [VirtualDisplay] to avoid conflicting with Launcher's Taskbar on the
 * [DEFAULT_DISPLAY] (i.e. test is executing on a device).
 */
@MutatedComponent(
    target = LauncherAppComponent::class,
    unbindValues = [WindowManagerProxy::class],
    installModules = [TaskbarTestOverridesModule::class],
)
class TaskbarWindowSandboxContext private constructor(private val params: SandboxParams) :
    ContextWrapper(null), TestRule {

    val virtualDisplayRule = VirtualDisplaysRule()

    // SandboxApplication is initialized lazily as it relies on creating virtual display after the
    // test as started
    val base: SandboxApplication by lazy {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val defaultDisplay =
            checkNotNull(
                app.resources.displayMetrics.let {
                    virtualDisplayRule[
                        virtualDisplayRule.add(it.widthPixels, it.heightPixels, it.densityDpi)]
                }
            )
        SandboxApplication(app.createDisplayContext(defaultDisplay.display)).withModelDependency()
    }

    val settingsCacheSandbox = SettingsCacheSandbox()
    lateinit var windowManagerSpy: WindowManager
    private val taskContinuityManagerMock: TaskContinuityManager = mock<TaskContinuityManager>()

    @BindValue lateinit var systemUiProxy: SystemUiProxy
    @BindValue
    val settingsCache: SettingsCache
        get() = settingsCacheSandbox.cache

    @BindValue
    val fakeFeatureEvaluator: PerDisplayRepository<TaskbarFeatureEvaluator> =
        FakePerDisplayRepository { displayId ->
            spy(
                TaskbarFeatureEvaluator(
                    displayId,
                    base,
                    base.appComponent.displayController,
                    base.appComponent.desktopVisibilityController,
                    base.appComponent.launcherPrefs,
                )
            )
        }
    @BindValue val displayRepository: DisplaysWithDecorationsRepositoryCompat = mock()

    private fun setupSpyServices() {
        base.mockService(
            Context.TASK_CONTINUITY_SERVICE,
            TaskContinuityManager::class.java,
            taskContinuityManagerMock,
        )

        // Filter out DEFAULT_DISPLAY in case code accesses displays property. The primary
        // virtual display has a different ID.
        val dm = base.spyService(DisplayManager::class.java)
        whenever(dm.displays).thenAnswer { i ->
            @Suppress("UNCHECKED_CAST")
            val displays = i.callRealMethod() as? Array<Display> ?: emptyArray<Display>()
            displays.filter { it.displayId != DEFAULT_DISPLAY }.toTypedArray()
        }

        windowManagerSpy = base.spyService(WindowManager::class.java)
        if (!DesktopExperienceFlags.ENABLE_SYS_DECORS_CALLBACKS_VIA_WM.isTrue) {
            // Have only our own displays appear as if they support Taskbar
            doAnswer { virtualDisplayRule[it.getArgument(0)] != null }
                .whenever(windowManagerSpy)
                .shouldShowSystemDecors(any())
        }
        // Setup WindowManager spy objects for all children window contexts
        base.spyServiceForChildren(WindowManager::class.java)

        // Mocks required for QuickstepKeyGestureEventsManager
        base.spyService(InputManager::class.java).stub {
            doNothing().whenever(mock).registerKeyGestureEventHandler(any(), any())
            doNothing().whenever(mock).unregisterKeyGestureEventHandler(any())
        }

        systemUiProxy = spy(SystemUiProxy(this, MAIN_EXECUTOR, UI_HELPER_EXECUTOR))
        doAnswer {
                virtualDisplayRule.registerDisplayDecorationListener(
                    it.getArgument(0),
                    it.getArgument(1),
                )
            }
            .whenever(displayRepository)
            .registerDisplayDecorationListener(any(), any())

        params.postSetupCallback.invoke(this)
        base.initDaggerComponent(
            params.builderBase
                .bind_systemUiProxy(systemUiProxy)
                .bind_settingsCache(settingsCache)
                .bind_fakeFeatureEvaluator(fakeFeatureEvaluator)
                .bind_displayRepository(displayRepository)
        )
    }

    override fun apply(baseStatement: Statement, description: Description): Statement =
        object : Statement() {

            override fun evaluate() {
                // Create and attach base context
                attachBaseContext(base)
                base.init()
                setupSpyServices()

                try {
                    baseStatement.evaluate()
                } finally {
                    base.onDestroy()
                    virtualDisplayRule.cleanup()
                }
            }
        }

    companion object {
        private const val DEFAULT_DEVICE = "pixelTablet2023"
        private val ON_DEVICE_ONLY = listOf("onDevice")

        /**
         * Creates a [SandboxApplication] for Taskbar tests
         *
         * Specify [emulatedDeviceName] to mimic a device configuration in the deviceless
         * environment. To run a test with multiple configurations, use a parameterized test runner
         * with [getDeviceParams].
         */
        fun create(
            emulatedDeviceName: String = DEFAULT_DEVICE,
            params: SandboxParams = SandboxParams(),
        ): TaskbarWindowSandboxContext {
            if (isRunningInRobolectric) {
                LauncherCustomizer.applyAll(
                    ApplicationProvider.getApplicationContext(),
                    EmulationParams(DeviceEmulationData.getDevice(emulatedDeviceName)),
                )
            }
            return TaskbarWindowSandboxContext(params)
        }

        /**
         * Creates list of [deviceNames] to emulate for parameterized tests.
         *
         * Returns a dummy singleton list for on-device tests, where there is no emulation.
         */
        fun getDeviceParams(vararg deviceNames: String): List<String> {
            return if (isRunningInRobolectric) deviceNames.toList() else ON_DEVICE_ONLY
        }
    }
}

/** Include additional bindings when building a [TaskbarSandboxComponent]. */
data class SandboxParams(
    val builderBase: TaskbarWindowSandboxContext_ModifiedComponent.Builder =
        DaggerTaskbarWindowSandboxContext_ModifiedComponent.builder(),
    val postSetupCallback: (TaskbarWindowSandboxContext) -> Unit = {},
)

@Module(includes = [FakePrefsModule::class, SandboxWmProxyModule::class])
object TaskbarTestOverridesModule {

    @JvmStatic
    @Provides
    @LauncherAppSingleton
    fun provideTaskbarModeUtil(
        windowManagerProxy: WindowManagerProxy,
        visibilityController: DesktopVisibilityController,
        launcherPrefs: LauncherPrefs,
    ): TaskbarModeUtil {
        return spy(TaskbarModeUtil(windowManagerProxy, launcherPrefs, visibilityController))
    }

    @JvmStatic
    @Provides
    @LauncherAppSingleton
    fun provideDesktopVisibilityController(
        @ApplicationContext context: Context,
        systemUiProxy: SystemUiProxy,
        lifecycleTracker: DaggerSingletonTracker,
    ): DesktopVisibilityController {
        return spy(DesktopVisibilityController(context, systemUiProxy, lifecycleTracker))
    }
}
