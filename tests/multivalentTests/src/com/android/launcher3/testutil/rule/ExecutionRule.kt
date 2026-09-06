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

import com.android.launcher3.util.RunnableList
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * [TestRule] which allows executing code blocks at various points. The implementation is based on
 * ExternalResource and TestWatcher with slight modifications:
 * - ExternalResource: doesn't expose [Description] which is useful in tagging debug logs
 * - TestWatcher: Silently ignores all setup errors even assumption failures
 */
class ExecutionRule : TestRule {

    private lateinit var description: Description

    private val before = RunnableList()
    private val after = RunnableList()

    override fun apply(base: Statement, description: Description) =
        object : Statement() {

            override fun evaluate() {
                this@ExecutionRule.description = description
                before.executeAllAndDestroy()
                val errors = mutableListOf<Throwable>()
                try {
                    base.evaluate()
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    try {
                        after.executeAllAndDestroy()
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }
                // Any assumption failures should have happened in [before]. Treat other failures as
                // test failures.
                MultipleFailureException.assertEmpty(errors)
            }
        }

    /** Executes the [task] during the initialization phase */
    fun runBefore(task: (Description) -> Unit) = apply { before.add { task.invoke(description) } }

    /** Executes the [task] during the tear-down phase */
    fun runAfter(task: (Description) -> Unit) = apply { after.add { task.invoke(description) } }
}
