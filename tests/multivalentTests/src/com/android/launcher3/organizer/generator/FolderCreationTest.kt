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
class FolderCreationTest {

    private val launcherApps: LauncherApps = mock()
    private val usageStatsManager: UsageStatsManager = mock()
    private val activityInfoMap = mutableMapOf<ComponentName, LauncherActivityInfo>()

    private val appComponent: LauncherAppComponent = mock()
    private val appsListRepository: AppsListRepository = mock()
    private val appsListStateRef: ListenableRef<AppsListData> = mock()

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
    }

    @Test
    fun testFolderCreationFlow() = runBlocking {
        // Setup items: need at least 3 for a folder as per FolderCreationSession logic
        val item1 = createAppInfo(1, "Game 1", "pkg.game1")
        val item2 = createAppInfo(2, "Game 2", "pkg.game2")
        val item3 = createAppInfo(3, "Game 3", "pkg.game3")
        appsList.clear()
        appsList.addAll(listOf(item1, item2, item3))

        // Mock categories
        mockAppCategory(1, "pkg.game1", ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(2, "pkg.game2", ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(3, "pkg.game3", ApplicationInfo.CATEGORY_GAME)

        // Create session
        val classifier =
            CompositeClassifier(
                listOf(
                    PackageManagerItemInfoClassifier(context),
                    MostUsedItemInfoClassifier(context),
                )
            )
        val session =
            FolderCreationSession(appsListRepository, classifier, DefaultTopicProvider(context))

        // 1. Classification
        val classifiedItems = session.startClassification()
        assertEquals(3, classifiedItems.size)

        // 2. Generation
        // FolderCreationSession filters score > 0.8 and size >= 3
        // PackageManagerClassifier returns score 1.0f
        val result = session.startGeneration(listOf("Games"))
        val folders = (result as CreationSession.GenerationResult.Folders).folders

        assertEquals(1, folders.size)
        assertEquals(3, folders[0].getContents().size)
        assertTrue(folders[0].getContents().any { it.getTargetPackage() == "pkg.game1" })
        assertTrue(folders[0].getContents().any { it.getTargetPackage() == "pkg.game2" })
        assertTrue(folders[0].getContents().any { it.getTargetPackage() == "pkg.game3" })
    }

    @Test
    fun testFolderCreationMultipleTopics() = runBlocking {
        // Setup items: need at least 3 for a folder per topic
        val items =
            listOf(
                createAppInfo(1, "Game 1", "pkg.game1"),
                createAppInfo(2, "Game 2", "pkg.game2"),
                createAppInfo(3, "Game 3", "pkg.game3"),
                createAppInfo(4, "Social 1", "pkg.social1"),
                createAppInfo(5, "Social 2", "pkg.social2"),
                createAppInfo(6, "Social 3", "pkg.social3"),
            )
        appsList.clear()
        appsList.addAll(items)

        // Mock categories
        mockAppCategory(1, "pkg.game1", ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(2, "pkg.game2", ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(3, "pkg.game3", ApplicationInfo.CATEGORY_GAME)
        mockAppCategory(4, "pkg.social1", ApplicationInfo.CATEGORY_SOCIAL)
        mockAppCategory(5, "pkg.social2", ApplicationInfo.CATEGORY_SOCIAL)
        mockAppCategory(6, "pkg.social3", ApplicationInfo.CATEGORY_SOCIAL)

        // Create session
        val classifier =
            CompositeClassifier(
                listOf(
                    PackageManagerItemInfoClassifier(context),
                    MostUsedItemInfoClassifier(context),
                )
            )
        val session =
            FolderCreationSession(appsListRepository, classifier, DefaultTopicProvider(context))

        // 1. Classification
        session.startClassification()

        // 2. Generation for multiple topics
        val result = session.startGeneration(listOf("Games", "Social"))
        val folders = (result as CreationSession.GenerationResult.Folders).folders

        assertEquals(2, folders.size)
        assertTrue(
            "Should have a Games folder",
            folders.any { folder ->
                folder.getContents().any { it.getTargetPackage() == "pkg.game1" }
            },
        )
        assertTrue(
            "Should have a Social folder",
            folders.any { folder ->
                folder.getContents().any { it.getTargetPackage() == "pkg.social1" }
            },
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
