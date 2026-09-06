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

package com.android.launcher3.taskbar

import android.content.Context
import com.android.launcher3.ConstantItem
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.TestUtil
import java.util.concurrent.TimeUnit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object TaskbarControllerTestUtil {

    private const val TIME_OUT_SECONDS = 10L

    inline fun runOnTaskbarUiThreadSync(crossinline runTest: () -> Unit) {
        TestUtil.runOnExecutorSync(getTaskbarUiThread()) { runTest() }
    }

    inline fun runOnMainSync(crossinline runTest: () -> Unit) {
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) { runTest() }
    }

    fun LooperExecutor.waitForIdleSync() {
        this.submit<Any?> { null }.get(TIME_OUT_SECONDS, TimeUnit.SECONDS)
    }

    /** Returns a property to read/write the value of a [ConstantItem]. */
    fun <T : Any> ConstantItem<T>.asProperty(context: Context): ReadWriteProperty<Any?, T> {
        return TaskbarItemProperty(context, this)
    }

    private class TaskbarItemProperty<T : Any>(
        private val context: Context,
        private val item: ConstantItem<T>,
    ) : ReadWriteProperty<Any?, T> {

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return LauncherPrefs.get(context).get(item)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            runOnTaskbarUiThreadSync { LauncherPrefs.get(context).put(item, value) }
        }
    }
}
