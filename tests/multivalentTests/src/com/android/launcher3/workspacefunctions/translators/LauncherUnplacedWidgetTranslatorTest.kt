/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.android.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.workspacefunctions.translators

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.R
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [LauncherUnplacedWidgetTranslator]. */
@RunWith(AndroidJUnit4::class)
class LauncherUnplacedWidgetTranslatorTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val translator by lazy { LauncherUnplacedWidgetTranslator(context) }

    @Test
    fun toSpec_convertsWidgetCorrectly() {
        val pkg = context.packageName
        val component = ComponentName(pkg, "com.test.Widget")
        val appInfo =
            ApplicationInfo(context.applicationInfo).apply {
                category = ApplicationInfo.CATEGORY_PRODUCTIVITY
            }

        val info =
            object : LauncherAppWidgetProviderInfo() {
                init {
                    provider = component
                    spanX = 2
                    spanY = 2
                    descriptionRes = R.string.topic_category_productivity
                    providerInfo =
                        ActivityInfo().apply {
                            packageName = pkg
                            applicationInfo = appInfo
                        }
                }

                override fun getLabel(): String = "Test Widget"
            }

        val spec = translator.toSpec(info)

        assertThat(spec.packageName).isEqualTo(pkg)
        assertThat(spec.className).isEqualTo("com.test.Widget")
        assertThat(spec.label).isEqualTo("Test Widget")

        val expectedString = context.getString(R.string.topic_category_productivity)
        assertThat(spec.description).isEqualTo(expectedString)
        assertThat(spec.spanX).isEqualTo(2)
        assertThat(spec.spanY).isEqualTo(2)
        assertThat(spec.category).isEqualTo(expectedString)
    }

    @Test
    fun toSpec_handlesNullDescriptionAndCategory() {
        val component = ComponentName("com.test.pkg", "com.test.Widget")

        val info =
            object : LauncherAppWidgetProviderInfo() {
                init {
                    provider = component
                    spanX = 1
                    spanY = 1
                    descriptionRes = 0
                    providerInfo =
                        ActivityInfo().apply {
                            packageName = "com.test.pkg"
                            applicationInfo = ApplicationInfo()
                        }
                }

                override fun getLabel(): String = "Simple Widget"
            }

        val spec = translator.toSpec(info)

        assertThat(spec.packageName).isEqualTo("com.test.pkg")
        assertThat(spec.className).isEqualTo("com.test.Widget")
        assertThat(spec.label).isEqualTo("Simple Widget")
        assertThat(spec.description).isNull()
        assertThat(spec.spanX).isEqualTo(1)
        assertThat(spec.spanY).isEqualTo(1)
        assertThat(spec.category).isNull()
    }
}
