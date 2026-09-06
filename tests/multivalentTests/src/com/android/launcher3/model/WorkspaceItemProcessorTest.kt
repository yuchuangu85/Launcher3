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

package com.android.launcher3.model

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.ShortcutInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.LongSparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_ID
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_PROVIDER
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_SOURCE
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.INTENT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.OPTIONS
import com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID
import com.android.launcher3.LauncherSettings.Favorites.RESTORED
import com.android.launcher3.LauncherSettings.Favorites.SPANX
import com.android.launcher3.LauncherSettings.Favorites.SPANY
import com.android.launcher3.LauncherSettings.Favorites.TITLE
import com.android.launcher3.LauncherSettings.Favorites._ID
import com.android.launcher3.LauncherSettings.Favorites.getColumnsToTypes
import com.android.launcher3.Utilities.EMPTY_PERSON_ARRAY
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError
import com.android.launcher3.homescreenfiles.HomeScreenFile
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.icons.CacheableShortcutInfo
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo.FLAG_UI_NOT_READY
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_DISABLED_FILE_SYSTEM_NOT_READY
import com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_RESTORED_ICON
import com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_RESTORE_STARTED
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ContentWriter
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.RoboApiWrapper
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.WidgetInflater
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class WorkspaceItemProcessorTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()
    @get:Rule val mContext = SandboxApplication().withModelDependency()
    @get:Rule val shortcutAccessRule = RoboApiWrapper.grantShortcutsPermissionRule()

    private val realCursor = MatrixCursor(getColumnsToTypes(0L).keys.toTypedArray<String>())
    private val realCursorRow = realCursor.newRow()

    private lateinit var workspaceInfo: WorkspaceItemInfo

    @Mock private lateinit var mockIconRequestInfo: IconRequestInfo<WorkspaceItemInfo>
    @Mock private lateinit var mockPmHelper: PackageManagerHelper
    @Mock private lateinit var mockWidgetInflater: WidgetInflater
    @Mock private lateinit var mockIconCache: IconCache
    @Mock private lateinit var mockWorkspaceItemSpaceFinder: WorkspaceItemSpaceFinder
    @Mock(answer = Answers.RETURNS_SELF) private lateinit var mockContentWriter: ContentWriter

    private lateinit var mLauncherApps: LauncherApps
    private var mIntent: Intent = Intent()
    private var mUserHandle: UserHandle = Process.myUserHandle()
    private var mIconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>> = mutableListOf()
    private var mComponentName: ComponentName = ComponentName("package", "class")
    private var mUnlockedUsersArray: LongSparseArray<Boolean> = LongSparseArray()
    private var mKeyToPinnedShortcutsMap: MutableMap<ShortcutKey, ShortcutInfo> = mutableMapOf()
    private var mInstallingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo> = hashMapOf()
    private var mAllDeepShortcuts: MutableList<CacheableShortcutInfo> = mutableListOf()
    private var mPendingPackages: MutableSet<PackageUserKey> = mutableSetOf()

    private lateinit var mockCursor: LoaderCursor
    private lateinit var itemProcessorUnderTest: WorkspaceItemProcessor

    @Before
    fun setup() {
        mLauncherApps =
            mContext.spyService(LauncherApps::class.java).apply {
                doReturn(true).whenever(this).isPackageEnabled("package", mUserHandle)
                doReturn(true).whenever(this).isActivityEnabled(mComponentName, mUserHandle)
            }
        mUserHandle = Process.myUserHandle()
        mComponentName = ComponentName("package", "class")
        mUnlockedUsersArray = LongSparseArray<Boolean>(1).apply { put(101, true) }
        mIntent =
            Intent().apply {
                component = mComponentName
                `package` = "pkg"
                putExtra(ShortcutKey.EXTRA_SHORTCUT_ID, "")
            }
        whenever(mockIconCache.getShortcutIcon(any(), any(), any(), any())).then {}
        whenever(mockPmHelper.getAppLaunchIntent(mComponentName.packageName, mUserHandle))
            .thenReturn(mIntent)

        realCursorRow
            .add(ITEM_TYPE, ITEM_TYPE_APPLICATION)
            .add(_ID, 1)
            .add(PROFILE_ID, 101)
            .add(RESTORED, 1)
            .add(INTENT, mIntent.toUri(0))

        doReturn(1).whenever(mockContentWriter).commit()

        mKeyToPinnedShortcutsMap = mutableMapOf()
        mInstallingPkgs = hashMapOf()
        mAllDeepShortcuts = mutableListOf()
        mIconRequestInfos = mutableListOf()
        mPendingPackages = mutableSetOf()
        workspaceInfo =
            WorkspaceItemInfo().apply {
                id = 1
                cellX = 0
                cellY = 0
                runtimeStatusFlags = 0
            }
    }

    /**
     * Helper to create WorkspaceItemProcessor with defaults. WorkspaceItemProcessor has a lot of
     * dependencies, so this method can be used to inject concrete arguments while keeping the rest
     * as mocks/defaults, or to recreate it after modifying the default vars.
     */
    private fun createWorkspaceItemProcessorUnderTest(
        memoryLogger: LoaderMemoryLogger? = null,
        launcherApps: LauncherApps = mLauncherApps,
        shortcutKeyToPinnedShortcuts: Map<ShortcutKey, ShortcutInfo> = mKeyToPinnedShortcutsMap,
        widgetInflater: WidgetInflater = mockWidgetInflater,
        pmHelper: PackageManagerHelper = mockPmHelper,
        iconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>> = mIconRequestInfos,
        isSdCardReady: Boolean = false,
        pendingPackages: MutableSet<PackageUserKey> = mPendingPackages,
        unlockedUsers: LongSparseArray<Boolean> = mUnlockedUsersArray,
        installingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo> = mInstallingPkgs,
        allDeepShortcuts: MutableList<CacheableShortcutInfo> = mAllDeepShortcuts,
        homeScreenFiles: CompletableFuture<Map<Uri, HomeScreenFile>> =
            CompletableFuture.completedFuture(mapOf()),
        automationRepo: AutomationRepository = mContext.appComponent.automationRepository,
    ): WorkspaceItemProcessor {
        // Create the loader cursor after all the stubbing is set up as accessing the dagger graph
        // objects initiates the creation of the full tree which starts various API calls on
        // different threads. This can conflict with stubbing as stubbing is not thread safe
        val ums = mContext.appComponent.userCache.userManagerState
        mockCursor =
            spy(
                LoaderCursor(
                    cursor = realCursor,
                    userManagerState = ums,
                    restoreEventLogger = null,
                    context = mContext,
                    iconCache = mockIconCache,
                    idp = mContext.appComponent.idp,
                    model = mContext.appComponent.testableModelState.model,
                    pmHelper = mContext.appComponent.packageManagerHelper,
                    automationRepo = automationRepo,
                )
            )

        doReturn(workspaceInfo).whenever(mockCursor).getAppShortcutInfo(any(), any(), any(), any())
        doReturn(mockIconRequestInfo).whenever(mockCursor).createIconRequestInfo(any(), any())
        doReturn(mockContentWriter).whenever(mockCursor).updater()

        mockCursor.moveToNext()

        return WorkspaceItemProcessor(
            c = mockCursor,
            memoryLogger = memoryLogger,
            userManagerState = ums,
            launcherApps = launcherApps,
            context = mContext,
            widgetInflater = widgetInflater,
            pmHelper = pmHelper,
            unlockedUsers = unlockedUsers,
            iconRequestInfos = iconRequestInfos,
            pendingPackages = pendingPackages,
            isSdCardReady = isSdCardReady,
            shortcutKeyToPinnedShortcuts = shortcutKeyToPinnedShortcuts,
            installingPkgs = installingPkgs,
            allDeepShortcuts = allDeepShortcuts,
            iconCache = mockIconCache,
            idp = mContext.appComponent.idp,
            isSafeMode = false,
            widgetSizeHandler = mContext.appComponent.widgetSizeHandler,
            workspaceItemSpaceFinder = mockWorkspaceItemSpaceFinder,
            homeScreenFiles = homeScreenFiles,
            automationRepo = automationRepo,
        )
    }

    @Test
    fun `When app has null intent then mark deleted`() {
        // Given
        realCursorRow.add(INTENT, null)

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()
        // Then
        verify(mockCursor)
            .markDeleted("Null intent from db for item id=1", RestoreError.APP_NO_DB_INTENT)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has null target package then mark deleted`() {
        // Given
        realCursorRow.add(INTENT, Intent().toUri(0))

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor)
            .markDeleted("No target package for item id=1", RestoreError.APP_NO_TARGET_PACKAGE)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has empty String target package then mark deleted`() {
        // Given
        mComponentName = ComponentName("", "")
        realCursorRow.add(INTENT, Intent().setComponent(mComponentName).toUri(0))

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor)
            .markDeleted("No target package for item id=1", RestoreError.APP_NO_TARGET_PACKAGE)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When valid app then mark restored`() {
        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        // currently gets marked restored twice, although markRestore() has check for restoreFlag
        verify(mockCursor, times(2)).markRestored()
        assertThat(mIconRequestInfos).containsExactly(mockIconRequestInfo)
        verify(mockCursor).checkAndAddItem(eq(workspaceInfo), any(), anyOrNull())
    }

    @Test
    fun `When fallback Activity found for app then mark restored`() {
        // Given
        mLauncherApps.apply {
            whenever(isPackageEnabled("package", mUserHandle)).thenReturn(true)
            whenever(isActivityEnabled(mComponentName, mUserHandle)).thenReturn(false)
        }
        val fallbackIntent = Intent("fallback.action").setPackage("package")
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(mComponentName.packageName, mUserHandle))
                    .thenReturn(fallbackIntent)
            }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        verify(mockCursor.updater()).put(Favorites.INTENT, fallbackIntent.toUri(0))
        assertThat(mIconRequestInfos).containsExactly(mockIconRequestInfo)
        assertThat(workspaceInfo.intent).isEqualTo(fallbackIntent)
        verify(mockCursor).checkAndAddItem(eq(workspaceInfo), any(), anyOrNull())
    }

    @Test
    fun `When app with disabled activity and no fallback found then mark deleted`() {

        // Given
        mLauncherApps.apply {
            whenever(isPackageEnabled("package", mUserHandle)).thenReturn(true)
            whenever(isActivityEnabled(mComponentName, mUserHandle)).thenReturn(false)
        }
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(mComponentName.packageName, mUserHandle))
                    .thenReturn(null)
            }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be unchanged")
            .that(mockCursor.restoreFlag)
            .isEqualTo(1)
        verify(mockCursor)
            .markDeleted(
                "No Activities or install sessions found for id=1, targetPkg=package, component=ComponentInfo{package/class}. Unable to create launch Intent.",
                RestoreError.APP_NO_LAUNCH_INTENT,
            )
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun whenActivityDisabled_andPackageInstalling_forRestore_thenKeepItemForRestore() {
        // Given
        mLauncherApps.apply {
            whenever(isPackageEnabled("package", mUserHandle)).thenReturn(true)
            whenever(isActivityEnabled(mComponentName, mUserHandle)).thenReturn(false)
        }
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(mComponentName.packageName, mUserHandle))
                    .thenReturn(null)
            }
        val packageUserKey = PackageUserKey("package", mUserHandle)
        mInstallingPkgs[packageUserKey] = mock<PackageInstaller.SessionInfo>()

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        val expectedRestoreFlag = FLAG_RESTORED_ICON or FLAG_RESTORE_STARTED
        assertThat(mockCursor.restoreFlag).isEqualTo(expectedRestoreFlag)
        verify(mockCursor.updater()).put(eq(RESTORED), eq(expectedRestoreFlag))
        verify(mockCursor, times(0)).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When valid Pinned Deep Shortcut then mark restored`() {
        // Given
        realCursorRow.add(ITEM_TYPE, ITEM_TYPE_DEEP_SHORTCUT)
        val expectedShortcutInfo =
            mock<ShortcutInfo>().apply {
                whenever(userHandle).thenReturn(mUserHandle)
                whenever(id).thenReturn("")
                whenever(`package`).thenReturn("")
                whenever(activity).thenReturn(mock())
                whenever(longLabel).thenReturn("")
                whenever(isEnabled).thenReturn(true)
                whenever(disabledMessage).thenReturn("")
                whenever(disabledReason).thenReturn(0)
                whenever(persons).thenReturn(EMPTY_PERSON_ARRAY)
            }
        val shortcutKey = ShortcutKey.fromIntent(mIntent, mUserHandle)
        mKeyToPinnedShortcutsMap[shortcutKey] = expectedShortcutInfo
        mIconRequestInfos = mutableListOf()

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = mAllDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(mIconRequestInfos).isEmpty()
        assertThat(mAllDeepShortcuts.size).isEqualTo(1)
        assertThat(mAllDeepShortcuts[0].shortcutInfo).isEqualTo(expectedShortcutInfo)
        verify(mockCursor).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When Archived Deep Shortcut with flag on then mark restored`() {
        // Given
        val mockAppInfo: ApplicationInfo =
            mock<ApplicationInfo>().apply {
                isArchived = true
                enabled = true
            }
        val expectedRestoreFlag = FLAG_RESTORED_ICON or FLAG_RESTORE_STARTED
        doReturn(mockAppInfo).whenever(mLauncherApps).getApplicationInfo(any(), any(), any())

        realCursorRow.add(ITEM_TYPE, ITEM_TYPE_DEEP_SHORTCUT).add(RESTORED, FLAG_RESTORED_ICON)

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertThat(mockCursor.restoreFlag and FLAG_RESTORED_ICON).isEqualTo(FLAG_RESTORED_ICON)
        assertThat(mockCursor.restoreFlag and FLAG_RESTORE_STARTED).isEqualTo(FLAG_RESTORE_STARTED)
        assertThat(mAllDeepShortcuts).isEmpty()
        verify(mockContentWriter).put(RESTORED, expectedRestoreFlag)
        verify(mockCursor).checkAndAddItem(any(), any(), eq(null))
    }

    @Test
    fun `When Pinned Deep Shortcut is not stored in ShortcutManager re-query by Shortcut ID`() {
        // Given
        realCursorRow.add(ITEM_TYPE, ITEM_TYPE_DEEP_SHORTCUT)
        val si =
            mock<ShortcutInfo>().apply {
                whenever(id).thenReturn("")
                whenever(`package`).thenReturn("")
                whenever(activity).thenReturn(mock())
                whenever(longLabel).thenReturn("")
                whenever(isEnabled).thenReturn(true)
                whenever(disabledMessage).thenReturn("")
                whenever(disabledReason).thenReturn(0)
                whenever(persons).thenReturn(EMPTY_PERSON_ARRAY)
                whenever(userHandle).thenReturn(mUserHandle)
            }
        doReturn(listOf(si)).whenever(mLauncherApps).getShortcuts(any(), any())
        mKeyToPinnedShortcutsMap.clear()
        mIconRequestInfos = mutableListOf()

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = mAllDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        verify(mLauncherApps).getShortcuts(any(), any())
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        verify(mockCursor).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), eq(null))
    }

    @Test
    fun `When Pinned Deep Shortcut not found then mark deleted`() {

        // Given
        realCursorRow.add(ITEM_TYPE, ITEM_TYPE_DEEP_SHORTCUT)
        mIconRequestInfos = mutableListOf()
        mKeyToPinnedShortcutsMap = hashMapOf()

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(mIconRequestInfos).isEmpty()
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
        verify(mockCursor)
            .markDeleted(
                "Pinned shortcut not found from request. package=pkg, user=$mUserHandle",
                "shortcut_not_found",
            )
    }

    @Test
    fun `When valid Pinned Deep Shortcut with null intent package then use targetPkg`() {

        // Given
        val expectedShortcutInfo =
            mock<ShortcutInfo>().apply {
                whenever(id).thenReturn("")
                whenever(`package`).thenReturn("")
                whenever(activity).thenReturn(mock())
                whenever(longLabel).thenReturn("")
                whenever(isEnabled).thenReturn(true)
                whenever(disabledMessage).thenReturn("")
                whenever(disabledReason).thenReturn(0)
                whenever(persons).thenReturn(EMPTY_PERSON_ARRAY)
                whenever(userHandle).thenReturn(mUserHandle)
            }
        mIconRequestInfos = mutableListOf()
        // Make sure shortcuts map has expected key from expected package
        mIntent.`package` = mComponentName.packageName
        val shortcutKey = ShortcutKey.fromIntent(mIntent, Process.myUserHandle())
        mKeyToPinnedShortcutsMap[shortcutKey] = expectedShortcutInfo
        // set intent package back to null to test scenario
        mIntent.`package` = null
        realCursorRow.add(ITEM_TYPE, ITEM_TYPE_DEEP_SHORTCUT).add(INTENT, mIntent.toUri(0))

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = mAllDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(mIconRequestInfos).isEmpty()
        assertThat(mAllDeepShortcuts.size).isEqualTo(1)
        assertThat(mAllDeepShortcuts[0].shortcutInfo).isEqualTo(expectedShortcutInfo)
        verify(mockCursor).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When processing Folder then create FolderInfo and mark restored`() {
        realCursorRow
            .add(ITEM_TYPE, ITEM_TYPE_FOLDER)
            .add(TITLE, "title")
            .add(OPTIONS, 5)
            .add(_ID, 3)

        val expectedFolderInfo =
            FolderInfo().apply {
                itemType = ITEM_TYPE_FOLDER
                title = "title"
                spanX = 1
                spanY = 1
                options = 5
                id = 3
            }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        verify(mockCursor).markRestored()

        val folderCaptor = argumentCaptor<FolderInfo>()
        verify(mockCursor).checkAndAddItem(folderCaptor.capture(), any(), anyOrNull())
        val actualFolderInfo = folderCaptor.firstValue

        assertThat(actualFolderInfo.id).isEqualTo(expectedFolderInfo.id)
        assertThat(actualFolderInfo.title).isEqualTo(expectedFolderInfo.title)
        assertThat(actualFolderInfo.itemType).isEqualTo(expectedFolderInfo.itemType)
        assertThat(actualFolderInfo.spanX).isEqualTo(expectedFolderInfo.spanX)
        assertThat(actualFolderInfo.spanY).isEqualTo(expectedFolderInfo.spanY)
        assertThat(actualFolderInfo.options).isEqualTo(expectedFolderInfo.options)
    }

    @Test
    fun `When valid TYPE_REAL App Widget then add item`() {

        // Given
        val expectedProvider = "com.google.android.testApp/com.android.testApp.testAppProvider"
        val expectedRestoreStatus = FLAG_UI_NOT_READY
        val expectedAppWidgetId = 0

        realCursorRow
            .add(ITEM_TYPE, ITEM_TYPE_APPWIDGET)
            .add(RESTORED, FLAG_UI_NOT_READY)
            .add(CONTAINER, CONTAINER_DESKTOP)
            .add(APPWIDGET_PROVIDER, expectedProvider)
            .add(APPWIDGET_ID, expectedAppWidgetId)
            .add(SPANX, 2)
            .add(SPANY, 1)
            .add(OPTIONS, 0)
            .add(APPWIDGET_SOURCE, 20)

        val expectedWidgetInfo =
            LauncherAppWidgetInfo().apply {
                appWidgetId = expectedAppWidgetId
                providerName = ComponentName.unflattenFromString(expectedProvider)
                restoreStatus = expectedRestoreStatus
                contentDescription = "Widget Label"
            }
        val expectedWidgetProviderInfo =
            mock<LauncherAppWidgetProviderInfo>().apply {
                provider = ComponentName.unflattenFromString(expectedProvider)
                whenever(user).thenReturn(mUserHandle)
                whenever(loadLabel(mContext.packageManager)).thenReturn("Widget Label")
            }
        val inflationResult =
            WidgetInflater.InflationResult(
                type = WidgetInflater.TYPE_REAL,
                widgetInfo = expectedWidgetProviderInfo,
            )
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        val packageUserKey = PackageUserKey("com.google.android.testApp", mUserHandle)
        mInstallingPkgs[packageUserKey] = PackageInstaller.SessionInfo()

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        val widgetInfoCaptor = argumentCaptor<LauncherAppWidgetInfo>()
        verify(mockCursor).checkAndAddItem(widgetInfoCaptor.capture(), any(), anyOrNull())
        val actualWidgetInfo = widgetInfoCaptor.firstValue
        with(actualWidgetInfo) {
            assertThat(providerName).isEqualTo(expectedWidgetInfo.providerName)
            assertThat(restoreStatus).isEqualTo(expectedWidgetInfo.restoreStatus)
            assertThat(targetComponent).isEqualTo(expectedWidgetInfo.targetComponent)
            assertThat(appWidgetId).isEqualTo(expectedWidgetInfo.appWidgetId)
            assertThat(contentDescription).isEqualTo(expectedWidgetInfo.contentDescription)
        }
    }

    @Test
    fun `When valid Pending Widget then checkAndAddItem`() {
        // Given
        realCursorRow
            .add(ITEM_TYPE, ITEM_TYPE_APPWIDGET)
            .add(RESTORED, FLAG_UI_NOT_READY)
            .add(CONTAINER, CONTAINER_DESKTOP)
            .add(
                APPWIDGET_PROVIDER,
                "com.google.android.testApp/com.android.testApp.testAppProvider",
            )
            .add(APPWIDGET_ID, 0)
            .add(SPANX, 2)
            .add(SPANY, 1)
            .add(OPTIONS, 0)
            .add(APPWIDGET_SOURCE, 20)
        val mockProviderInfo =
            mock<LauncherAppWidgetProviderInfo>().apply {
                provider = mock()
                whenever(user).thenReturn(UserHandle(1))
            }
        val inflationResult =
            WidgetInflater.InflationResult(
                type = WidgetInflater.TYPE_PENDING,
                widgetInfo = mockProviderInfo,
            )
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor).checkAndAddItem(any(), any(), eq(null))
    }

    @Test
    fun `When Unrestored Pending App Widget then mark deleted`() {
        // Given
        val expectedProvider = "com.google.android.testApp/com.android.testApp.testAppProvider"
        realCursorRow
            .add(ITEM_TYPE, ITEM_TYPE_APPWIDGET)
            .add(RESTORED, FLAG_UI_NOT_READY)
            .add(CONTAINER, CONTAINER_DESKTOP)
            .add(APPWIDGET_PROVIDER, expectedProvider)
            .add(APPWIDGET_ID, 0)
            .add(SPANX, 2)
            .add(SPANY, 1)
            .add(OPTIONS, 0)
            .add(APPWIDGET_SOURCE, 20)
        mInstallingPkgs = hashMapOf()
        val inflationResult =
            WidgetInflater.InflationResult(type = WidgetInflater.TYPE_PENDING, widgetInfo = null)
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        val expectedComponentName = ComponentName.unflattenFromString(expectedProvider)

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor)
            .markDeleted(
                "processWidget: Unrestored Pending widget removed: id=1, appWidgetId=0, component=$expectedComponentName, restoreFlag:=4",
                RestoreError.UNRESTORED_PENDING_WIDGET,
            )
    }

    @Test
    fun `When widget inflation result is TYPE_DELETE then mark deleted`() {
        // Given
        val expectedProvider = "com.google.android.testApp/com.android.testApp.testAppProvider"
        realCursorRow
            .add(ITEM_TYPE, ITEM_TYPE_APPWIDGET)
            .add(RESTORED, FLAG_UI_NOT_READY)
            .add(CONTAINER, CONTAINER_DESKTOP)
            .add(APPWIDGET_PROVIDER, expectedProvider)
            .add(APPWIDGET_ID, 0)
            .add(SPANX, 2)
            .add(SPANY, 1)
            .add(OPTIONS, 0)
            .add(APPWIDGET_SOURCE, 20)
        mInstallingPkgs = hashMapOf()
        val inflationResult =
            WidgetInflater.InflationResult(
                type = WidgetInflater.TYPE_DELETE,
                widgetInfo = null,
                reason = "test_delete_reason",
                restoreErrorType = RestoreError.MISSING_WIDGET_PROVIDER,
            )
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor).markDeleted(inflationResult.reason, inflationResult.restoreErrorType)
    }

    @Test
    fun deletesFileSystemFileItemTypeWhenRestoringFromBackup() {
        testRestoresFileSystemItemIfNotRestoringFromBackup(
            ITEM_TYPE_FILE_SYSTEM_FILE,
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "image/png",
                isDirectory = false,
                user = Process.myUserHandle(),
            ),
            /*isFileSystemReady=*/ true,
            /*isRestoreFromBackup=*/ true,
        )
    }

    @Test
    fun deletesFileSystemFolderItemTypeWhenRestoringFromBackup() {
        testRestoresFileSystemItemIfNotRestoringFromBackup(
            ITEM_TYPE_FILE_SYSTEM_FOLDER,
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "folder_a",
                mimeType = null,
                isDirectory = true,
                user = Process.myUserHandle(),
            ),
            /*isFileSystemReady=*/ true,
            /*isRestoreFromBackup=*/ true,
        )
    }

    @Test
    fun disablesFilesSystemFileItemTypeWhenNotRestoringFromBackup() {
        testRestoresFileSystemItemIfNotRestoringFromBackup(
            ITEM_TYPE_FILE_SYSTEM_FILE,
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "folder_a",
                mimeType = null,
                isDirectory = true,
                user = Process.myUserHandle(),
            ),
            /*isFileSystemReady=*/ false,
            /*isRestoreFromBackup=*/ false,
        )
    }

    @Test
    fun disablesFilesSystemFolderItemTypeWhenNotRestoringFromBackup() {
        testRestoresFileSystemItemIfNotRestoringFromBackup(
            ITEM_TYPE_FILE_SYSTEM_FOLDER,
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "folder_a",
                mimeType = null,
                isDirectory = true,
                user = Process.myUserHandle(),
            ),
            /*isFileSystemReady=*/ false,
            /*isRestoreFromBackup=*/ false,
        )
    }

    @Test
    fun restoresFileSystemFileItemTypeWhenNotRestoringFromBackup() {
        testRestoresFileSystemItemIfNotRestoringFromBackup(
            ITEM_TYPE_FILE_SYSTEM_FILE,
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "file.png",
                mimeType = "image/png",
                isDirectory = false,
                user = Process.myUserHandle(),
            ),
            /*isFileSystemReady=*/ true,
            /*isRestoreFromBackup=*/ false,
        )
    }

    @Test
    fun restoresFileSystemFolderItemTypeWhenNotRestoringFromBackup() {
        testRestoresFileSystemItemIfNotRestoringFromBackup(
            ITEM_TYPE_FILE_SYSTEM_FOLDER,
            HomeScreenFile(
                uri = Uri.parse("content://media/external_primary/file/1"),
                displayName = "folder_a",
                mimeType = null,
                isDirectory = true,
                user = Process.myUserHandle(),
            ),
            /*isFileSystemReady=*/ true,
            /*isRestoreFromBackup=*/ false,
        )
    }

    private fun testRestoresFileSystemItemIfNotRestoringFromBackup(
        itemType: Int,
        homeScreenFile: HomeScreenFile,
        isFileSystemReady: Boolean,
        isRestoreFromBackup: Boolean,
    ) {
        // Given
        val homeScreenFiles =
            mock<CompletableFuture<Map<Uri, HomeScreenFile>>>().also { mock ->
                val isDone = AtomicBoolean(isFileSystemReady)
                whenever(mock.isDone).thenAnswer { isDone.get() }
                whenever(mock.get()).thenAnswer {
                    isDone.set(true)
                    mapOf(homeScreenFile.uri to homeScreenFile)
                }
            }
        realCursorRow
            .add(ITEM_TYPE, itemType)
            .add(RESTORED, if (isRestoreFromBackup) FLAG_RESTORED_ICON else 0)
            .add(TITLE, homeScreenFile.displayName)
            .add(
                INTENT,
                HomeScreenFilesUtils.buildLaunchIntent(homeScreenFile.uri, homeScreenFile).toUri(0),
            )

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(homeScreenFiles = homeScreenFiles)
        itemProcessorUnderTest.processItem()

        // Then
        if (isRestoreFromBackup) {
            verify(mockCursor)
                .markDeleted(
                    "File system items are not restored from backup",
                    RestoreError.FILE_SYSTEM_ITEM_FROM_BACKUP,
                )
            verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
        } else {
            val itemCaptor = argumentCaptor<ItemInfoWithIcon>()
            verify(mockCursor).markRestored()
            verify(mockCursor).checkAndAddItem(itemCaptor.capture(), any(), anyOrNull())
            with(itemCaptor.firstValue) {
                assertThat(itemType).isEqualTo(itemType)
                assertThat(title).isEqualTo(homeScreenFile.displayName)
                with(intent!!) {
                    assertThat(data).isEqualTo(homeScreenFile.uri)
                    assertThat(flags).isEqualTo(HomeScreenFilesUtils.LAUNCH_INTENT_DEFAULT_FLAGS)
                }
                val disabled = (runtimeStatusFlags and FLAG_DISABLED_FILE_SYSTEM_NOT_READY) != 0
                val expectedDisabled = !isFileSystemReady
                assertThat(disabled).isEqualTo(expectedDisabled)
            }
        }
    }

    @Test
    fun deletesFileSystemItemThatNoLongerExists() {
        // Given
        realCursorRow
            .add(ITEM_TYPE, ITEM_TYPE_FILE_SYSTEM_FILE)
            .add(RESTORED, 0)
            .add(TITLE, "name.ext")
            .add(
                INTENT,
                HomeScreenFilesUtils.buildLaunchIntent(
                        Uri.parse("content://media/external_primary/file/1")
                    )
                    .toUri(0),
            )

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor)
            .markDeleted(
                "File system item name.ext no longer exists",
                RestoreError.FILE_SYSTEM_ITEM_NO_LONGER_EXISTS,
            )
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_AUTOMATION_INDICATOR)
    fun whenAppAutomated_setFlagAutomated() {
        // Given
        val mockAutomationRepo = mock<AutomationRepository>()
        whenever(mockAutomationRepo.isPackageAutomated(any<UserHandle>(), any())).thenReturn(true)

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(automationRepo = mockAutomationRepo)
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor).checkAndAddItem(eq(workspaceInfo), any(), anyOrNull())
        assertThat(workspaceInfo.runtimeStatusFlags and ItemInfoWithIcon.FLAG_AUTOMATED)
            .isNotEqualTo(0)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_AUTOMATION_INDICATOR)
    fun whenAppNotAutomated_unsetFlagAutomated() {
        // Given
        val mockAutomationRepo = mock<AutomationRepository>()
        whenever(mockAutomationRepo.isPackageAutomated(any<UserHandle>(), any())).thenReturn(false)

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(automationRepo = mockAutomationRepo)
        itemProcessorUnderTest.processItem()

        // Then
        val itemCaptor = argumentCaptor<WorkspaceItemInfo>()
        verify(mockCursor).checkAndAddItem(eq(workspaceInfo), any(), anyOrNull())
        assertThat(workspaceInfo.runtimeStatusFlags and ItemInfoWithIcon.FLAG_AUTOMATED)
            .isEqualTo(0)
    }

    @Test
    fun `When Pending App Widget has not started restore then update db and add item`() {
        // Given
        val expectedProvider = "com.google.android.testApp/com.android.testApp.testAppProvider"
        val expectedComponentName =
            ComponentName.unflattenFromString(expectedProvider)!!.flattenToString()
        val expectedRestoreStatus = FLAG_UI_NOT_READY or LauncherAppWidgetInfo.FLAG_RESTORE_STARTED
        val expectedAppWidgetId = 0
        realCursorRow
            .add(APPWIDGET_PROVIDER, expectedComponentName)
            .add(ITEM_TYPE, ITEM_TYPE_APPWIDGET)
            .add(RESTORED, FLAG_UI_NOT_READY)
            .add(CONTAINER, CONTAINER_DESKTOP)
            .add(APPWIDGET_ID, 0)
            .add(SPANY, 1)
            .add(SPANX, 1)
            .add(OPTIONS, 0)
            .add(APPWIDGET_SOURCE, 20)

        val inflationResult =
            WidgetInflater.InflationResult(type = WidgetInflater.TYPE_PENDING, widgetInfo = null)
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        val packageUserKey = PackageUserKey("com.google.android.testApp", mUserHandle)
        mInstallingPkgs[packageUserKey] = PackageInstaller.SessionInfo()

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        val expectedWidgetInfo =
            LauncherAppWidgetInfo().apply {
                appWidgetId = expectedAppWidgetId
                providerName = ComponentName.unflattenFromString(expectedProvider)
                restoreStatus = expectedRestoreStatus
            }
        Mockito.verify(
                mockCursor
                    .updater()
                    .put(Favorites.APPWIDGET_PROVIDER, expectedProvider)
                    .put(Favorites.APPWIDGET_ID, expectedAppWidgetId)
                    .put(Favorites.RESTORED, expectedRestoreStatus)
            )
            .commit()
        val widgetInfoCaptor = argumentCaptor<LauncherAppWidgetInfo>()
        Mockito.verify(mockCursor).checkAndAddItem(widgetInfoCaptor.capture(), any(), anyOrNull())
        val actualWidgetInfo = widgetInfoCaptor.firstValue
        with(actualWidgetInfo) {
            assertThat(providerName).isEqualTo(expectedWidgetInfo.providerName)
            assertThat(restoreStatus).isEqualTo(expectedWidgetInfo.restoreStatus)
            assertThat(targetComponent).isEqualTo(expectedWidgetInfo.targetComponent)
            assertThat(appWidgetId).isEqualTo(expectedWidgetInfo.appWidgetId)
        }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SUPPORT_FOR_ARCHIVING)
    fun `When Archived Pending App Widget then checkAndAddItem`() {
        // Given
        val expectedProvider = "com.google.android.testApp/com.android.testApp.testAppProvider"
        val expectedComponentName = ComponentName.unflattenFromString(expectedProvider)
        val mockAppInfo: ApplicationInfo =
            mock<ApplicationInfo>().apply {
                isArchived = true
                enabled = true
            }
        doReturn(mockAppInfo).whenever(mLauncherApps).getApplicationInfo(any(), any(), any())

        realCursorRow
            .add(APPWIDGET_PROVIDER, expectedComponentName)
            .add(_ID, 1)
            .add(ITEM_TYPE, ITEM_TYPE_APPWIDGET)
            .add(RESTORED, FLAG_UI_NOT_READY)
            .add(CONTAINER, CONTAINER_DESKTOP)
            .add(APPWIDGET_ID, 0)
            .add(SPANY, 1)
            .add(SPANX, 1)
            .add(OPTIONS, 0)
            .add(APPWIDGET_SOURCE, 20)

        mInstallingPkgs = hashMapOf()
        val inflationResult =
            WidgetInflater.InflationResult(type = WidgetInflater.TYPE_PENDING, widgetInfo = null)
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        Mockito.verify(mockCursor).checkAndAddItem(any(), any(), anyOrNull())
    }
}
