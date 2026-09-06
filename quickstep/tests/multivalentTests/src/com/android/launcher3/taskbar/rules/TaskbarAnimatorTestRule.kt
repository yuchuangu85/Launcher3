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

package com.android.launcher3.taskbar.rules

import android.animation.AnimatorTestRule
import android.view.animation.AnimationUtils.currentAnimationTimeMillis
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.LauncherMultivalentJUnit.Companion.isRunningInRobolectric
import com.android.launcher3.util.TestUtil.getOnTaskbarUiThread
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Lazy [AnimatorTestRule] constructed at the time of applying this rule.
 *
 * Includes workaround for potential time mismatch in Robolectric environment by starting at
 * [currentAnimationTimeMillis].
 */
class TaskbarAnimatorTestRule(private val test: Any) : TestRule {

    private lateinit var animatorTestRule: AnimatorTestRule

    override fun apply(base: Statement, description: Description): Statement {
        animatorTestRule =
            if (isRunningInRobolectric) {
                getOnTaskbarUiThread {
                    AnimatorTestRule(this, currentAnimationTimeMillis(), getTaskbarUiThread())
                }
            } else {
                AnimatorTestRule(this, getTaskbarUiThread())
            }
        return animatorTestRule.apply(base, description)
    }

    fun advanceTimeBy(timeDelta: Long) = animatorTestRule.advanceTimeBy(timeDelta)
}
