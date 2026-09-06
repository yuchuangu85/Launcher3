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

package com.android.launcher3.organizer.generator

import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherApplication
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppsListData
import com.android.launcher3.model.repository.AppsListRepository
import com.android.launcher3.util.ListenableRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ScreenCreationTest {

    private val launcherApps: LauncherApps = mock()
    private val usageStatsManager: UsageStatsManager = mock()
    private val activityInfoMap = mutableMapOf<ComponentName, LauncherActivityInfo>()

    private val appComponent: LauncherAppComponent = mock()
    private val appsListRepository: AppsListRepository = mock()
    private val appsListStateRef: ListenableRef<AppsListData> = mock()
    private val idp: InvariantDeviceProfile = mock()

    private lateinit var context: Context
    private val appsList = mutableListOf<AppInfo>()

    @Before
    fun setup() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApp: LauncherApplication = mock()
        whenever(launcherApp.applicationContext).thenReturn(launcherApp)
        context = spy(targetContext)
        whenever(context.applicationContext).thenReturn(launcherApp)
        whenever(launcherApp.appComponent).thenReturn(appComponent)

        whenever(context.getSystemService(LauncherApps::class.java)).thenReturn(launcherApps)
        whenever(context.getSystemService(UsageStatsManager::class.java))
            .thenReturn(usageStatsManager)
        whenever(usageStatsManager.queryAndAggregateUsageStats(any(), any())).thenReturn(emptyMap())

        whenever(launcherApps.resolveActivity(any(), any())).thenAnswer { invocation ->
            val intent = invocation.arguments[0] as Intent
            activityInfoMap[intent.component]
        }

        // Mock string resources for topics
        whenever(context.getString(R.string.topic_category_games)).thenReturn("Games")
        whenever(context.getString(R.string.topic_category_audio)).thenReturn("Audio")
        whenever(context.getString(R.string.topic_category_video)).thenReturn("Video")
        whenever(context.getString(R.string.topic_category_image)).thenReturn("Image")
        whenever(context.getString(R.string.topic_category_social)).thenReturn("Social")
        whenever(context.getString(R.string.topic_category_news)).thenReturn("News")
        whenever(context.getString(R.string.topic_category_maps)).thenReturn("Maps")
        whenever(context.getString(R.string.topic_category_productivity)).thenReturn("Productivity")
        whenever(context.getString(R.string.topic_category_accessibility))
            .thenReturn("Accessibility")
        whenever(context.getString(R.string.topic_category_most_used)).thenReturn("Most Used")

        whenever(appsListRepository.appsListStateRef).thenReturn(appsListStateRef)
        whenever(appsListStateRef.value).thenAnswer { AppsListData(appsList.toTypedArray(), 0) }

        idp.numColumns = 4
        idp.numRows = 5
    }

    @Test
    fun testScreenCreationFlow() = runBlocking {
        // Setup items
        val item1 = createAppInfo(1, "Game 1", "pkg.game1")
        val item2 = createAppInfo(2, "Social 1", "pkg.social1")
        appsList.clear()
        appsList.addAll(listOf(item1, item2))

        // Mock categories
        mockAppCategory(1, "pkg.game1", ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(2, "pkg.social1", ApplicationInfo.CATEGORY_SOCIAL)

        // Create session
        val classifier =
            CompositeClassifier(
                listOf(
                    PackageManagerItemInfoClassifier(context),
                    MostUsedItemInfoClassifier(context),
                )
            )
        val session =
            ScreenCreationSession(
                appsListRepository,
                idp,
                classifier,
                DefaultTopicProvider(context),
            )

        // 1. Classification
        val classifiedItems = session.startClassification()
        assertEquals(2, classifiedItems.size)

        // 2. Generation
        val result = session.startGeneration(listOf("Games"))
        val screens = (result as CreationSession.GenerationResult.Screens).pages

        // PresetTemplateGenerator generates 3 templates.
        // HeuristicScreenPlacer tries to place items into them.
        assertTrue("Should have generated screens", screens.isNotEmpty())

        // Verify that the game app is in the generated screens
        val allPlacedItems = screens.flatten()
        val hasGame = allPlacedItems.any { it.getTargetPackage() == "pkg.game1" }
        assertTrue("Game 1 should be placed", hasGame)

        // Verify that social app is NOT placed if only "Games" topic is selected
        val hasSocial = allPlacedItems.any { it.getTargetPackage() == "pkg.social1" }
        assertTrue("Social 1 should NOT be placed", !hasSocial)
    }

    @Test
    fun testScreenCreationMultipleTopics() = runBlocking {
        // Setup items
        val item1 = createAppInfo(1, "Game 1", "pkg.game1")
        val item2 = createAppInfo(2, "Social 1", "pkg.social1")
        appsList.clear()
        appsList.addAll(listOf(item1, item2))

        // Mock categories
        mockAppCategory(1, "pkg.game1", ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(2, "pkg.social1", ApplicationInfo.CATEGORY_SOCIAL)

        // Create session
        val classifier =
            CompositeClassifier(
                listOf(
                    PackageManagerItemInfoClassifier(context),
                    MostUsedItemInfoClassifier(context),
                )
            )
        val session =
            ScreenCreationSession(
                appsListRepository,
                idp,
                classifier,
                DefaultTopicProvider(context),
            )
        session.startClassification()

        // Generate for both
        val result = session.startGeneration(listOf("Games", "Social"))
        val screens = (result as CreationSession.GenerationResult.Screens).pages
        val allPlacedItems = screens.flatten()
        assertTrue(
            "Game 1 should be placed",
            allPlacedItems.any { it.getTargetPackage() == "pkg.game1" },
        )
        assertTrue(
            "Social 1 should be placed",
            allPlacedItems.any { it.getTargetPackage() == "pkg.social1" },
        )
    }

    private fun createAppInfo(id: Int, title: String, packageName: String): AppInfo {
        val componentName = ComponentName(packageName, "Activity$id")
        val intent = Intent(Intent.ACTION_MAIN).setComponent(componentName)
        return AppInfo(componentName, title, mock(), mock()).apply { this.intent = intent }
    }

    private fun mockAppCategory(id: Int, packageName: String, category: Int) {
        val applicationInfo =
            ApplicationInfo().apply {
                this.category = category
                this.packageName = packageName
            }
        val launcherActivityInfo: LauncherActivityInfo = mock {
            on { getApplicationInfo() } doReturn applicationInfo
        }
        activityInfoMap[ComponentName(packageName, "Activity$id")] = launcherActivityInfo
    }
}
