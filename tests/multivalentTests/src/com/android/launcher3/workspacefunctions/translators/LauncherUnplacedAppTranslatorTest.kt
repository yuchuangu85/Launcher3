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
package com.android.launcher3.workspacefunctions

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.workspacefunctions.translators.LauncherUnplacedAppTranslator
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for [LauncherUnplacedAppTranslator]. */
@RunWith(AndroidJUnit4::class)
class LauncherUnplacedAppTranslatorTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val translator by lazy { LauncherUnplacedAppTranslator(context) }

    @Test
    fun toSpec_convertsAppCorrectly() {
        val component = ComponentName("com.test.pkg", "com.test.Activity")
        val app = mock<LauncherActivityInfo>()
        val appInfo = ApplicationInfo().apply { category = ApplicationInfo.CATEGORY_GAME }
        whenever(app.componentName).thenReturn(component)
        whenever(app.label).thenReturn("Test App")
        whenever(app.applicationInfo).thenReturn(appInfo)

        val spec = translator.toSpec(app)

        assertThat(spec.packageName).isEqualTo("com.test.pkg")
        assertThat(spec.className).isEqualTo("com.test.Activity")
        assertThat(spec.label).isEqualTo("Test App")
        assertThat(spec.category).isEqualTo("Games")
    }
}
