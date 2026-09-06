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

package com.android.launcher3.dragndrop

import android.view.View
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.TestActivityContext
import java.util.function.Consumer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times

/** Tests for {@link DragView}. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class DragViewTest {

    @get:Rule val activity = TestActivityContext()
    @get:Rule val mockito = MockitoJUnit.rule()

    private lateinit var view: DragView

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        view =
            DragView(
                activity,
                /*content=*/ View(context),
                /*width=*/ 0,
                /*height=*/ 0,
                /*initialScale=*/ 0,
                /*registrationX=*/ 0,
                /*registrationY=*/ 1.0f,
                /*scaleOnDrop=*/ 1.0f,
                /*finalScaleDps=*/ 1.0f,
                /*allowSpringDrawable=*/ true,
            )
    }

    @Test
    fun testOnAlphaChangeListener() {
        var alpha = 1.0f
        view.setAlpha(alpha)

        val listener = mock<Consumer<Float>>()
        view.addOnAlphaChangeListener(listener)

        // Case: Change.
        alpha = 0.5f
        view.setAlpha(alpha)
        verify(listener).accept(alpha)
        clearInvocations(listener)

        // Case: No-op.
        view.setAlpha(alpha)
        verify(listener, times(0)).accept(anyOrNull())

        // Case: Detach.
        alpha = 1.0f
        view.onDetachedFromWindow()
        view.setAlpha(alpha)
        verify(listener, times(0)).accept(anyOrNull())
    }
}
