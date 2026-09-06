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

package com.android.quickstep.util

import android.app.Instrumentation
import com.android.launcher3.desktop.DesktopStateProvider.getDesktopState
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import platform.test.desktop.SimulatedConnectedDisplayTestRule

/**
 * A [TestRule] that enables running a test case on both the default display and a simulated
 * external display.
 *
 * If a test method is annotated with [MultiDisplayTest], this rule will execute the test twice:
 * first on the default display, and then again after setting up a simulated connected display.
 *
 * Note: The entire test lifecycle (including @Before and @After methods) is repeated for the second
 * run.
 */
class MultiDisplayTestRule(
    private val instrumentation: Instrumentation,
    private val onTestDisplayChangeListener: OnTestDisplayChangeListener,
) : TestRule {
    private val simulatedConnectedDisplayTestRule = SimulatedConnectedDisplayTestRule()
    @JvmField
    @Rule
    val ruleChain: RuleChain =
        RuleChain.outerRule(simulatedConnectedDisplayTestRule)
            .around(MultiDisplayExecutionRule(::switchToExternalDisplay))

    override fun apply(base: Statement, description: Description): Statement =
        ruleChain.apply(base, description)

    private fun switchToExternalDisplay() {
        assumeTrue(
            "Cannot enter desktop mode for external display tests. Skipping Tests",
            instrumentation.targetContext.getDesktopState().canEnterDesktopMode,
        )
        val displayId = simulatedConnectedDisplayTestRule.setupTestDisplay()
        onTestDisplayChangeListener.onTestDisplayChanged(displayId)
    }
}

fun interface OnTestDisplayChangeListener {
    fun onTestDisplayChanged(displayId: Int)
}

/**
 * Annotation for Launcher TAPL Tests that should also run on an external display in addition to
 * default display.
 */
@Retention(RUNTIME) @Target(FUNCTION) annotation class MultiDisplayTest

private class MultiDisplayExecutionRule(private val switchToExternalDisplay: () -> Unit) :
    TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        val isMultiDisplayTest = description.getAnnotation(MultiDisplayTest::class.java) != null
        return if (isMultiDisplayTest) {
            object : Statement() {
                override fun evaluate() {
                    base.evaluate()
                    switchToExternalDisplay()
                    base.evaluate()
                }
            }
        } else base
    }
}
