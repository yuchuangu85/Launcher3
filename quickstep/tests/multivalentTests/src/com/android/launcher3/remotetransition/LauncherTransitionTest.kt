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

package com.android.launcher3.remotetransition

import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.view.SurfaceControl
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.remotetransitions.AnimationResult
import com.android.launcher3.remotetransitions.LauncherTransition
import com.android.systemui.animation.RemoteTransitionDelegate
import com.android.systemui.animation.RemoteTransitionHelper
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyBoolean
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class LauncherTransitionTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock lateinit var transitionHelper: RemoteTransitionHelper
    @Mock lateinit var handler: Handler
    @Mock lateinit var delegate: RemoteTransitionDelegate<AnimationResult>
    @Mock lateinit var executor: Executor
    @Mock lateinit var transition: IBinder
    @Mock lateinit var info: TransitionInfo
    @Mock lateinit var transaction: SurfaceControl.Transaction
    @Mock lateinit var finishCallback: IRemoteTransitionFinishedCallback

    private lateinit var underTest: LauncherTransition

    @Before
    fun setup() {
        underTest =
            LauncherTransition(
                handler = handler,
                delegate = delegate,
                startAtFrontOfQueue = false,
                transitionHelper = transitionHelper,
                bgExecutor = executor,
                mainExecutor = executor,
            )
    }

    @Test
    fun nullParamsPassedForTransition() {
        underTest.startAnimation(null, null, null, null)

        verify(transitionHelper, times(0)).setUpAnimation(any(), any(), any(), any())
    }

    @Test
    fun validParamsPassedForTransition() {
        underTest.startAnimation(transition, info, transaction, finishCallback)

        verify(transitionHelper, times(1)).setUpAnimation(any(), any(), any(), any())
        verify(handler, times(1)).sendMessage(any())
    }

    @Test
    fun validParamsPassedForTransition_postInFrontOfQueue() {
        underTest =
            LauncherTransition(
                handler = handler,
                delegate = delegate,
                startAtFrontOfQueue = true,
                transitionHelper = transitionHelper,
                bgExecutor = executor,
                mainExecutor = executor,
            )
        whenever(handler.obtainMessage()).thenReturn(Message())

        underTest.startAnimation(transition, info, transaction, finishCallback)

        verify(transitionHelper, times(1)).setUpAnimation(any(), any(), any(), any())
        verify(handler, times(1)).sendMessageAtFrontOfQueue(any())
    }

    @Test
    fun mergeAnimation() {
        underTest.mergeAnimation(transition, info, transaction, transition, finishCallback)

        verify(transitionHelper, times(1)).mergeAnimation(any(), any(), any())
    }

    @Test
    fun onTransitionConsumed_nullTransitionBinder() {
        underTest.onTransitionConsumed(null, false)

        verify(transitionHelper, times(0)).onTransitionConsumed(any())
    }

    @Test
    fun onTransitionConsumed_validTransitionBinder() {
        underTest.onTransitionConsumed(transition, anyBoolean())

        verify(transitionHelper, times(1)).onTransitionConsumed(any())
    }
}
