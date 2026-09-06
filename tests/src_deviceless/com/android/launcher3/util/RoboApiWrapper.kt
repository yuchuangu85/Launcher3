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

package com.android.launcher3.util

import android.os.Looper
import android.provider.Settings.Global
import android.provider.Settings.Secure
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.mockito.MockedStatic
import org.mockito.MockedStatic.Verification
import org.mockito.Mockito
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import org.mockito.quality.Strictness.LENIENT
import org.mockito.stubbing.Answer
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowSettings.ShadowGlobal
import org.robolectric.shadows.ShadowSettings.ShadowSecure
import org.robolectric.shadows.ShadowSettings.ShadowSystem

object RoboApiWrapper {

    fun waitForLooperSync(looper: Looper) {
        Shadows.shadowOf(looper).runToEndOfTasks()
    }

    fun yieldToMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    /** Rule to grant shortcuts permission. No-op when running on robolectric */
    fun grantShortcutsPermissionRule(): TestRule = TestRule { statement, _ -> statement }

    /** Rule to grant widget bind permission. No-op when running on robolectric */
    fun grantWidgetBindPermissionRule(): TestRule = TestRule { statement, _ -> statement }

    /** Rule to screen record the device. No-op when running on robolectric */
    fun screenRecordRule(): TestRule = TestRule { statement, _ -> statement }

    @JvmOverloads
    fun Any.convertToSpy(defaultAnswer: Answer<Any> = CALLS_REAL_METHODS) {
        Assume.assumeTrue("convertObjectToSpy is not supported in device-less tests", false)
    }

    inline fun <reified T> staticMockHelper() = StaticMockHelper(T::class.java)

    class StaticMockHelper(requestedClazz: Class<*>) {

        /** Map settings to their corresponding shadow classes */
        private val clazz: Class<*> =
            when (requestedClazz) {
                Secure::class.java -> ShadowSecure::class.java
                System::class.java -> ShadowSystem::class.java
                Global::class.java -> ShadowGlobal::class.java
                else -> requestedClazz
            }

        private lateinit var mockSession: MockedStatic<*>

        internal fun init() {
            mockSession =
                mockStatic(
                    clazz,
                    withSettings().strictness(LENIENT).defaultAnswer(CALLS_REAL_METHODS),
                )
        }

        fun whenever(method: Verification) = mockSession.`when`<Any?>(method)

        internal fun cleanup() = mockSession.close()
    }

    /**
     * Rule for using static mocks in unit test. Separate implementations are provided for on-device
     * and robolectric tests, while keeping a common API signature.
     */
    class StaticMockRule(vararg val helpers: StaticMockHelper) : MockitoRule {

        private var strictness = LENIENT

        override fun apply(base: Statement, method: FrameworkMethod?, target: Any) =
            object : Statement() {
                override fun evaluate() {
                    val mockSession =
                        Mockito.mockitoSession()
                            .strictness(strictness)
                            .initMocks(target)
                            .startMocking()
                    helpers.forEach { it.init() }
                    val error = kotlin.runCatching { base.evaluate() }.exceptionOrNull()

                    helpers.forEach { it.cleanup() }
                    mockSession.finishMocking(error)
                    error?.let { throw error }
                }
            }

        override fun silent(): MockitoRule = strictness(LENIENT)

        override fun strictness(strictness: Strictness): MockitoRule = apply {
            this.strictness = strictness
        }
    }
}
