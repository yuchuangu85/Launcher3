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

import com.android.app.tracing.coroutines.launchTraced as launch
import com.example.tracing.demo.FixedThread1
import com.example.tracing.demo.FixedThread2
import com.example.tracing.demo.FixedThread3
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope

@Singleton
class LaunchSequentially
@Inject
constructor(
    @param:FixedThread1 private var dispatcher1: CoroutineDispatcher,
    @param:FixedThread2 private var dispatcher2: CoroutineDispatcher,
    @param:FixedThread3 private val dispatcher3: CoroutineDispatcher,
) : TracedExperiment() {
    override val description: String = "launch{};launch{};launch{};launch{}"

    override suspend fun runExperiment(): Unit = coroutineScope {
        launch("launch(empty)") { forceSuspend("000", 5) }
        launch("launch(thread1)", dispatcher1) { forceSuspend("111", 5) }
        launch("launch(thread2)", dispatcher2) { forceSuspend("222", 5) }
        launch("launch(thread3)", dispatcher3) { forceSuspend("333", 5) }
    }
}
