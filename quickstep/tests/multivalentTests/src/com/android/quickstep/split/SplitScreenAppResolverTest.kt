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

package com.android.quickstep.split

import android.app.IActivityTaskManager
import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.views.ActivityContext
import com.android.systemui.shared.recents.model.Task
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SplitScreenAppResolverTest {

    private val mockContext: ActivityContext = mock()
    private val mockAppInfo: AppInfo = mock()
    private val mockIActivityTaskManager: IActivityTaskManager = mock()

    private val resolver = SplitScreenAppResolver(mockContext)
    private val testUserId = 0
    private val initialTaskId = 1
    private val samePackageName = "com.example.same"
    private val trampolinePackageName = "com.example.trampoline"
    private val destinationPackageName = "com.example.destination"

    @Before
    fun setUp() {
        whenever(mockAppInfo.supportsMultiInstance()).thenReturn(false)
    }

    @After fun tearDown() {}

    @Test
    fun getResolvedDestinationPackage_isTrampoline_returnsDestinationPackage() {
        val componentName = ComponentName(trampolinePackageName, ".TrampolineActivity")
        whenever(mockIActivityTaskManager.getDestinationPackage(trampolinePackageName))
            .thenReturn(destinationPackageName)

        val result = resolver.getResolvedDestinationPackage(mockIActivityTaskManager, componentName)

        assertEquals(destinationPackageName, result)
    }

    @Test
    fun getResolvedDestinationPackage_isNotTrampoline_returnsNull() {
        val componentName = ComponentName(samePackageName, ".MainActivity")
        whenever(mockIActivityTaskManager.getDestinationPackage(samePackageName))
            .thenReturn(samePackageName)

        val result = resolver.getResolvedDestinationPackage(mockIActivityTaskManager, componentName)

        assertNull(result)
    }

    @Test
    fun getResolvedDestinationPackage_serviceReturnsNull_returnsNull() {
        val componentName = ComponentName(samePackageName, ".MainActivity")
        whenever(mockIActivityTaskManager.getDestinationPackage(anyString()))
            .thenReturn(componentName.packageName)

        val result = resolver.getResolvedDestinationPackage(mockIActivityTaskManager, componentName)

        assertNull(result)
    }

    @Test
    fun isTaskAppSingleInstance_taskIsNull_returnsFalse() {
        val result =
            resolver.isTaskAppSingleInstance(
                null,
                initialTaskId,
                mockAppInfo,
                destinationPackageName,
                testUserId,
            )
        assertFalse(result)
    }

    @Test
    fun isTaskAppSingleInstance_taskIsInitialTask_returnsFalse() {
        val taskKey = Task.TaskKey(initialTaskId, 0, Intent(), null, 0, 0L)
        val task = Task(taskKey)

        val result =
            resolver.isTaskAppSingleInstance(
                task,
                initialTaskId,
                mockAppInfo,
                destinationPackageName,
                testUserId,
            )
        assertFalse(result)
    }

    @Test
    fun isTaskAppSingleInstance_differentUserId_returnsFalse() {
        val differentUserId = 10
        val taskKey = Task.TaskKey(2, 0, Intent(), null, differentUserId, 0L)
        val task = Task(taskKey)

        val result =
            resolver.isTaskAppSingleInstance(
                task,
                initialTaskId,
                mockAppInfo,
                destinationPackageName,
                testUserId,
            )
        assertFalse(result)
    }

    @Test
    fun isTaskAppSingleInstance_differentPackageName_returnsFalse() {
        val intent = Intent()
        intent.component = ComponentName("com.another.package", ".AnotherActivity")
        val taskKey = Task.TaskKey(2, 0, intent, null, 0, 0L)
        val task = Task(taskKey)

        val result =
            resolver.isTaskAppSingleInstance(
                task,
                initialTaskId,
                mockAppInfo,
                destinationPackageName,
                testUserId,
            )
        assertFalse(result)
    }

    @Test
    fun isTaskAppSingleInstance_appInfoNotResolved_returnsFalse() {
        val intent = Intent()
        intent.component = ComponentName(destinationPackageName, ".AnotherActivity")
        val taskKey = Task.TaskKey(2, 0, intent, null, testUserId, 0L)
        val task = Task(taskKey)

        val result =
            resolver.isTaskAppSingleInstance(
                task,
                initialTaskId,
                null,
                destinationPackageName,
                testUserId,
            )

        assertFalse(result)
    }

    @Test
    fun isTaskAppSingleInstance_appSupportsMultiInstance_returnsFalse() {
        val component = ComponentName(destinationPackageName, ".MainActivity")
        val intent = Intent()
        intent.component = component
        val taskKey = Task.TaskKey(2, 0, intent, null, testUserId, 0L)
        val task = Task(taskKey)

        whenever(mockAppInfo.supportsMultiInstance()).thenReturn(true)

        val result =
            resolver.isTaskAppSingleInstance(
                task,
                initialTaskId,
                mockAppInfo,
                destinationPackageName,
                testUserId,
            )

        assertFalse(result)
    }

    @Test
    fun isTaskAppSingleInstance_appIsSingleInstance_returnsTrue() {
        val component = ComponentName(destinationPackageName, ".MainActivity")
        val intent = Intent()
        intent.component = component
        val taskKey = Task.TaskKey(2, 0, intent, null, testUserId, 0L)
        val task = Task(taskKey)

        whenever(mockAppInfo.supportsMultiInstance()).thenReturn(false)

        val result =
            resolver.isTaskAppSingleInstance(
                task,
                initialTaskId,
                mockAppInfo,
                destinationPackageName,
                testUserId,
            )

        assertTrue(result)
    }
}
