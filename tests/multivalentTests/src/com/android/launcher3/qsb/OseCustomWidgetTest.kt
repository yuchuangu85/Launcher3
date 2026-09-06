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

package com.android.launcher3.qsb

import androidx.test.filters.LargeTest
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.ModelTestExtensions.setModelLayout
import com.android.launcher3.util.SandboxApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit

@LargeTest
@RunWith(LauncherMultivalentJUnit::class)
class OseCustomWidgetTest {

    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @Test
    fun oseWidget_added_to_homescreen() {
        val numColumns = app.appComponent.idp.numColumns
        app.setModelLayout(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <workspace>
                <searchwidget
                    container="desktop"
                    screen="0"
                    x="0"
                    y="1"
                    spanX="$numColumns"
                    spanY="1" />
            </workspace>
        """
                .trimIndent()
        )

        launcherActivity.waitUntil("Ose Widget not added") {
            it.workspace.mapOverItems { item, view ->
                item?.container == Favorites.CONTAINER_DESKTOP && view is OseWidgetView
            } != null
        }
    }
}
