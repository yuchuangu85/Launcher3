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

package com.android.launcher3.integration.util

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.testutil.Wait.atMost
import com.android.launcher3.util.Executors
import com.android.launcher3.util.TestUtil
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import java.util.function.Supplier
import org.junit.rules.ExternalResource

open class LauncherActivityScenarioRule<LAUNCHER_TYPE : Launcher> : ExternalResource() {

    private var currentScenario: ActivityScenario<LAUNCHER_TYPE>? = null

    val activity: ActivityScenario<LAUNCHER_TYPE>
        get() =
            currentScenario
                ?: ActivityScenario.launch<LAUNCHER_TYPE>(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setPackage(getInstrumentation().targetContext.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        null,
                    )
                    .also { currentScenario = it }

    fun initializeActivity() {
        currentScenario?.recreate()
        activity.moveToState(Lifecycle.State.RESUMED)
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}
        TestUtil.runOnExecutorSync(Executors.UI_HELPER_EXECUTOR) {}
        getInstrumentation().waitForIdleSync()
    }

    override fun after() {
        close()
    }

    fun close() {
        currentScenario?.close()
        currentScenario = null
    }

    fun executeOnLauncher(f: ActivityAction<LAUNCHER_TYPE>): ActivityScenario<LAUNCHER_TYPE> =
        activity.onActivity(f)

    fun <T> getFromLauncher(f: Function<in LAUNCHER_TYPE, out T?>): T? {
        val result = AtomicReference<T>()
        activity.onActivity { result.set(f.apply(it)) }
        return result.get()
    }

    fun goToState(state: LauncherState) = executeOnLauncher {
        it.stateManager.goToState(state, false)
    }

    fun <T> getOnceNotNull(message: String, f: Function<LAUNCHER_TYPE, T?>): T? {
        var output: T? = null
        atMost(
            message,
            {
                val fromLauncher = getFromLauncher<T> { f.apply(it) }
                output = fromLauncher
                fromLauncher != null
            },
        )
        return output
    }

    @JvmOverloads
    fun injectKeyEvent(keyCode: Int, actionDown: Boolean, metaState: Int = 0) {
        executeOnLauncher {
            it.dispatchKeyEvent(TestUtil.createKeyEvent(keyCode, metaState, actionDown))
        }
    }

    protected fun waitForLauncherCondition(message: String, condition: (LAUNCHER_TYPE) -> Boolean) =
        atMost(message) { getFromLauncher(condition)!! }

    fun waitForResumed() =
        waitForLauncherCondition("Launcher activity never resumed") { it.hasBeenResumed() }

    fun waitForStopped() =
        waitForLauncherCondition("Launcher activity never stopped") { !it.isStarted }

    fun isInState(state: Supplier<LauncherState>): Boolean =
        getFromLauncher { it.stateManager.state == state.get() }!!

    /** Waits until the [condition] is not true */
    fun waitUntil(message: String, condition: (LAUNCHER_TYPE) -> Boolean) =
        atMost(message) { getFromLauncher(condition)!! }

    /** Waits until the [condition] is non-null and returns the non-null value */
    fun <T> waitAndGet(message: String, condition: (LAUNCHER_TYPE) -> T?): T {
        var result: T? = null
        atMost(message) {
            result = getFromLauncher(condition)
            result != null
        }
        return result!!
    }
}
