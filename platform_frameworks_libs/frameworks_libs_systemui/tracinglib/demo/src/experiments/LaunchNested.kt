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
package com.example.tracing.demo.experiments

import com.android.app.tracing.coroutines.launchTraced
import com.android.app.tracing.coroutines.traceCoroutine
import com.example.tracing.demo.FixedThread1
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Singleton
class LaunchNested
@Inject
constructor(@param:FixedThread1 private var dispatcher1: CoroutineDispatcher) : TracedExperiment() {
    override val description: String = "launch{launch{launch{launch{}}}}"

    override suspend fun runExperiment(): Unit = coroutineScope {
        fun CoroutineScope.recursivelyLaunch(n: Int) {
            if (n == 400) return
            launchTraced("launch#$n", start = CoroutineStart.UNDISPATCHED) {
                traceCoroutine("trace-span") {
                    recursivelyLaunch(n + 1)
                    delay(1)
                }
            }
        }
        withContext(dispatcher1) { recursivelyLaunch(0) }
    }
}
