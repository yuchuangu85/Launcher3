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

import android.os.Handler
import android.os.Looper
import com.android.dx.mockito.inline.InlineDexmakerMockMaker
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.dx.mockito.inline.extended.MockedVoidMethod
import com.android.launcher3.util.rule.ScreenRecordRule
import com.android.launcher3.util.rule.ShellCommandRule
import java.util.concurrent.CompletableFuture
import org.junit.rules.TestRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.mockito.Mockito
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import org.mockito.quality.Strictness.LENIENT
import org.mockito.stubbing.Answer

object RoboApiWrapper {

    fun waitForLooperSync(looper: Looper) {
        CompletableFuture<Void>().apply { Handler(looper).post { complete(null) } }.get()
    }

    fun yieldToMainLooper() {
        // Intentionally left empty as this is not needed on device.
        // Main looper continues running anyway without the need to yield.
    }

    /** Rule to grant shortcuts permission */
    fun grantShortcutsPermissionRule(): TestRule = ShellCommandRule.setDefaultLauncher()

    /** Rule to grant widget bind permission */
    fun grantWidgetBindPermissionRule(): TestRule = ShellCommandRule.grantWidgetBind()

    /** Rule to screen record the device */
    fun screenRecordRule(): TestRule = ScreenRecordRule()

    /**
     * Converts the object into spy
     *
     * When [defaultAnswer] is provided, it reimplements ExtendedMockito.spyOn. An object might
     * already be in use while some methods are already being mocked, causing race condition during
     * setup. This makes the mocking mostly atomic.
     */
    @JvmOverloads
    fun Any.convertToSpy(defaultAnswer: Answer<Any> = CALLS_REAL_METHODS) {
        if (defaultAnswer == CALLS_REAL_METHODS) {
            spyOn(this)
        } else {
            check(InlineDexmakerMockMaker.onSpyInProgressInstance.get() == null) {
                "Cannot set up spying on an existing object while setting up spying for another existing object"
            }
            InlineDexmakerMockMaker.onSpyInProgressInstance.set(this)
            try {
                Mockito.mock(
                    this.javaClass,
                    withSettings().spiedInstance(this).defaultAnswer(defaultAnswer),
                )
            } finally {
                InlineDexmakerMockMaker.onSpyInProgressInstance.remove()
            }
        }
    }

    inline fun <reified T> staticMockHelper() = StaticMockHelper(T::class.java)

    class StaticMockHelper(val clazz: Class<*>) {
        fun whenever(method: MockedVoidMethod) = Stubber(method)

        class Stubber(val method: MockedVoidMethod) {

            fun thenReturn(value: Any?) = ExtendedMockito.doReturn(value).`when`(method)

            fun thenThrow(vararg throwables: Throwable?) =
                ExtendedMockito.doThrow(*throwables).`when`(method)

            fun thenAnswer(answer: Answer<*>) = ExtendedMockito.doAnswer(answer).`when`(method)
        }
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
                        ExtendedMockito.mockitoSession()
                            .initMocks(target)
                            .strictness(strictness)
                            .apply {
                                helpers.forEach {
                                    mockStatic(
                                        it.clazz,
                                        withSettings().lenient().defaultAnswer(CALLS_REAL_METHODS),
                                    )
                                }
                            }
                            .startMocking()
                    val error = kotlin.runCatching { base.evaluate() }.exceptionOrNull()

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
