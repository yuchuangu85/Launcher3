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

import com.android.app.tracing.coroutines.asyncTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.traceSection
import com.example.tracing.demo.FixedThread1
import com.example.tracing.demo.FixedThread2
import com.example.tracing.demo.FixedThread3
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Singleton
class CombineDeferred
@Inject
constructor(
    @param:FixedThread1 private var dispatcher1: CoroutineDispatcher,
    @param:FixedThread2 private var dispatcher2: CoroutineDispatcher,
    @param:FixedThread3 private val dispatcher3: CoroutineDispatcher,
) : TracedExperiment() {
    override val description: String = "async{} then start()"

    override suspend fun runExperiment(): Unit = coroutineScope {
        // deferredA -> deferredB -> deferredC
        val deferredC =
            async(start = LAZY, context = dispatcher2) {
                traceCoroutine("async#C") { forceSuspend("deferredC", 15) }
            }
        val deferredB =
            async(start = LAZY, context = Dispatchers.Unconfined) {
                traceCoroutine("async#B") { forceSuspend("deferredB", 10) }
                traceSection("startC") { deferredC.start() }
            }
        val deferredA =
            async(start = LAZY, context = dispatcher3) {
                traceCoroutine("async#A") { forceSuspend("deferredA", 5) }
                traceSection("startB") { deferredB.start() }
            }

        // deferredX -> deferredY -> deferredZ
        val deferredZ =
            async(start = LAZY, context = dispatcher2) {
                traceCoroutine("async#Z") { forceSuspend("deferredZ", 15) }
            }
        val deferredY =
            async(start = LAZY, context = Dispatchers.Unconfined) {
                traceCoroutine("async#Y") { forceSuspend("deferredY", 10) }
                traceSection("startZ") { deferredZ.start() }
            }
        val deferredX =
            async(start = LAZY, context = dispatcher3) {
                traceCoroutine("async#X") { forceSuspend("deferredX", 5) }
                traceSection("startY") { deferredY.start() }
            }

        val deferredNamed =
            asyncTraced("my-async-name", start = LAZY) {
                traceCoroutine("async#my-name") { forceSuspend("my-name", 25) }
            }

        launch(dispatcher1) { traceSection("startA") { deferredA.start() } }
        launchTraced(context = dispatcher1) { traceSection("startX") { deferredX.start() } }
        launchTraced("my-launch-name", dispatcher1) {
            traceSection("start-named-async") { deferredNamed.start() }
        }
    }
}
