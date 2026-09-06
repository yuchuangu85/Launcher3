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

package com.android.launcher3.testutil.rule

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.RoboApiWrapper.convertToSpy
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

/** Set of utility test rules */
object TestRules {

    /**
     * TestRule to override the activity creation using a mapper function
     *
     * @param mockitoRule defined to ensure that the rule is applied in the test
     * @param mapper function which receives two arguments, the className of the activity and a
     *   function to get the actual activity
     */
    @JvmStatic
    fun overrideActivityFactory(
        mockitoRule: MockitoRule,
        mapper: ((String, () -> Activity) -> Activity),
    ) =
        ExecutionRule()
            .runBefore {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.convertToSpy()
                TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
                    doAnswer { invocation ->
                            val className = invocation.getArgument(1, String::class.java)
                            mapper.invoke(className) { invocation.callRealMethod() as Activity }
                        }
                        .whenever(instrumentation)
                        .newActivity(any(), any(), any())
                }
            }
            .runAfter { reset(InstrumentationRegistry.getInstrumentation()) }

    /**
     * TestRule to override the base application for an activity
     *
     * @param mockitoRule defined to ensure that the rule is applied in the test
     */
    @JvmStatic
    fun overrideApplicationInActivity(app: SandboxApplication, mockitoRule: MockitoRule) =
        overrideActivityFactory(mockitoRule) { _, realProvider ->
            realProvider.invoke().apply {
                convertToSpy()
                doReturn(app).whenever(this).applicationContext
            }
        }
}
