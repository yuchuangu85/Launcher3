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

import com.example.tracing.demo.FixedThread1
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

@Singleton
class CancellableSharedFlow
@Inject
constructor(@param:FixedThread1 private var dispatcher1: CoroutineDispatcher) : TracedExperiment() {
    override val description: String = "Create shared flows that can be cancelled by the parent"

    override suspend fun runExperiment(): Unit = coroutineScope {
        // GOOD - launched into child scope, parent can cancel this
        flow {
                var n = 0
                while (true) {
                    emit(n++)
                    forceSuspend(timeMillis = 5)
                }
            }
            .flowOn(dispatcher1)
            .shareIn(this, SharingStarted.Eagerly, replay = 10)
    }
}
