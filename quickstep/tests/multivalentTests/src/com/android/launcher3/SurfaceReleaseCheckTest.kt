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

package com.android.launcher3

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.quickstep.SurfaceReleaseCheck
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class SurfaceReleaseCheckTest {

    private lateinit var underTest: SurfaceReleaseCheck

    @Before
    fun before() {
        underTest = SurfaceReleaseCheck()
    }

    @Test
    fun defaultCanReleaseFalse() {
        Assert.assertFalse(underTest.canRelease)
    }

    @Test
    fun setCanRelease_whenApplyCallbackNonNull() {
        var ran = false
        val runnable = Runnable { ran = true }
        underTest.addOnSafeToReleaseCallback(runnable)
        underTest.setCanRelease(true)
        Assert.assertTrue(ran)
    }

    @Test
    fun addReleaseCallback_canReleaseFalse() {
        var ran = false
        val runnable = Runnable { ran = true }
        underTest.addOnSafeToReleaseCallback(runnable)
        underTest.setCanRelease(false)
        Assert.assertFalse(ran)
    }

    @Test
    fun addMultipleReleaseCallback_canReleaseTrue() {
        var ranFirst = false
        var ranSecond = false

        val runnableFirst = Runnable { ranFirst = true }
        val runnableSecond = Runnable { ranSecond = true }

        underTest.addOnSafeToReleaseCallback(runnableFirst)
        underTest.addOnSafeToReleaseCallback(runnableSecond)

        underTest.setCanRelease(true)

        Assert.assertTrue(ranFirst)
        Assert.assertTrue(ranSecond)
    }
}
