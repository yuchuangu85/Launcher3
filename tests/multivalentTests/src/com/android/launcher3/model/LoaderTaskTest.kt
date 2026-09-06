package com.android.launcher3.model

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.LoaderTransaction
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.IS_FIRST_LOAD_AFTER_RESTORE
import com.android.launcher3.LauncherPrefs.Companion.RESTORE_DEVICE
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUpdateTask
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.icons.cache.CachingLogic
import com.android.launcher3.icons.cache.IconCacheUpdateHandler
import com.android.launcher3.model.FirstScreenBroadcastHelper.Companion.DISABLE_INSTALLED_APPS_BROADCAST
import com.android.launcher3.model.LoaderTask.LoaderTaskFactory
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.testutil.rule.LazyInitRule.Companion.lazyRule
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherLayoutBuilder.FolderBuilder
import com.android.launcher3.util.LauncherModelHelper.ACTIVITY_LIST
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY3
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY4
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY5
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.ModelTestExtensions.setModelLayout
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import java.util.concurrent.CountDownLatch
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.RETURNS_MOCKS
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class LoaderTaskTest {
    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule
    val contextSpy =
        lazyRule({ spy(SandboxApplication().withModelDependency()) }, { MockUsersRule(it.get()) })

    private val expectedBroadcastModel =
        FirstScreenBroadcastModel(
            installerPackage = "installerPackage",
            pendingCollectionItems = mutableSetOf("pendingCollectionItem"),
            pendingWidgetItems = mutableSetOf("pendingWidgetItem"),
            pendingHotseatItems = mutableSetOf("pendingHotseatItem"),
            pendingWorkspaceItems = mutableSetOf("pendingWorkspaceItem"),
            installedHotseatItems = mutableSetOf("installedHotseatItem"),
            installedWorkspaceItems = mutableSetOf("installedWorkspaceItem"),
            installedWidgets = linkedSetOf("installedFirstScreenWidget"),
        )

    @Mock private lateinit var bgAllAppsList: AllAppsList
    @Mock private lateinit var modelDelegate: ModelDelegate
    @Mock private lateinit var launcherModel: LauncherModel
    @Mock private lateinit var iconCache: IconCache
    @Mock private lateinit var modelDbController: ModelDbController
    @Mock private lateinit var broadcastHelper: FirstScreenBroadcastHelper

    @Mock private lateinit var launcherBinder: BaseLauncherBinder
    @Mock private lateinit var transaction: LoaderTransaction
    @Mock private lateinit var iconCacheUpdateHandler: IconCacheUpdateHandler
    @Mock private lateinit var settingsCache: SettingsCache

    private val context: SandboxApplication by contextSpy

    private val testComponent: TestComponent
        get() = context.appComponent as TestComponent

    private val bgDataModel: BgDataModel
        get() = testComponent.getDataModel()

    private val inMemoryDb: SQLiteDatabase by lazy {
        SQLiteDatabase.createInMemory(SQLiteDatabase.OpenParams.Builder().build()).also {
            Favorites.addTableToDb(
                it,
                UserCache.INSTANCE.get(context).getSerialNumberForUser(Process.myUserHandle()),
                false,
            )
        }
    }

    @Before
    fun setup() {
        val allWidgetManager = context.spyService(AppWidgetManager::class.java)
        doReturn(WidgetUtils.findWidgetProvider(false))
            .whenever(allWidgetManager)
            .getAppWidgetInfo(any())

        `when`(launcherModel.beginLoader(any())).thenReturn(transaction)

        `when`(launcherModel.modelDbController).thenReturn(modelDbController)
        doReturn(BitmapInfo.LOW_RES_INFO).whenever(iconCache).getDefaultIcon(any())
        doReturn(false).whenever(modelDbController).loadDefaultFavoritesIfNecessary()
        whenever(modelDbController.getTable()).thenReturn(mock(defaultAnswer = RETURNS_MOCKS))
        doAnswer { i ->
                inMemoryDb.query(
                    TABLE_NAME,
                    i.getArgument(0),
                    i.getArgument(1),
                    i.getArgument(2),
                    null,
                    null,
                    i.getArgument(3),
                )
            }
            .whenever(modelDbController)
            .query(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

        `when`(launcherModel.modelDelegate).thenReturn(modelDelegate)
        `when`(iconCache.getUpdateHandler()).thenReturn(iconCacheUpdateHandler)

        val listenableRef = MutableListenableRef(false)
        `when`(settingsCache.getListenableRef(any())).thenReturn(listenableRef)

        context.initDaggerComponent(
            DaggerLoaderTaskTest_TestComponent.builder()
                .bindIconCache(iconCache)
                .bindLauncherModel(launcherModel)
                .bindAllAppsList(bgAllAppsList)
                .bindSettingsCache(settingsCache)
                .bindBroadcastHelper(broadcastHelper)
        )
        context.appComponent.idp.apply {
            numRows = 5
            numColumns = 6
            numDatabaseHotseatIcons = 5
        }
        TestUtil.grantWriteSecurePermission()
    }

    @After
    fun tearDown() {
        LauncherPrefs.get(context).removeSync(RESTORE_DEVICE)
        LauncherPrefs.get(context).putSync(IS_FIRST_LOAD_AFTER_RESTORE.to(false))
        inMemoryDb.close()
    }

    private fun FolderBuilder.addApps(count: Int) =
        apply {
                for (i in 0..<count) {
                    putApp(TEST_PACKAGE, ACTIVITY_LIST[i])
                }
            }
            .build()

    fun Cursor.forEveryValue(callback: (ContentValues) -> Unit) = use {
        val columnNames = it.columnNames
        while (it.moveToNext()) {
            val rowValues = ContentValues()
            for ((index, columnName) in columnNames.withIndex()) {
                when (it.getType(index)) {
                    Cursor.FIELD_TYPE_STRING -> rowValues.put(columnName, it.getString(index))
                    Cursor.FIELD_TYPE_INTEGER -> rowValues.put(columnName, it.getLong(index))
                    Cursor.FIELD_TYPE_FLOAT -> rowValues.put(columnName, it.getDouble(index))
                    Cursor.FIELD_TYPE_BLOB -> rowValues.put(columnName, it.getBlob(index))
                    Cursor.FIELD_TYPE_NULL -> rowValues.putNull(columnName)
                }
            }
            callback.invoke(rowValues)
        }
    }

    private fun SandboxApplication.setupMockWidget() {
        val widget = WidgetUtils.findWidgetProvider(false)

        val appWidget = spyService<AppWidgetManager>()
        doReturn(listOf(widget))
            .whenever(appWidget)
            .getInstalledProvidersForPackage(widget.provider.packageName, widget.user)
        doReturn(listOf(widget)).whenever(appWidget).getInstalledProvidersForProfile(widget.user)
    }

    @Test
    fun loadsDataProperly() {
        // Create a new sandbox context, and copy-over its db to our test db and verify reload
        val sourceSandbox = SandboxApplication()
        sourceSandbox.init()
        sourceSandbox.setupMockWidget()
        sourceSandbox.appComponent.idp.apply {
            numRows = context.appComponent.idp.numRows
            numColumns = context.appComponent.idp.numColumns
            numDatabaseHotseatIcons = context.appComponent.idp.numDatabaseHotseatIcons
        }
        val widget = WidgetUtils.findWidgetProvider(false)
        val builder =
            LauncherLayoutBuilder()
                .atWorkspace(0, -1, 0)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY)
                .atWorkspace(1, -1, 0)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY2)
                .atWorkspace(0, -1, 1)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY3)
                .atWorkspace(1, -1, 1)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY4)
                .atWorkspace(0, -1, 2)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY5)
                .atWorkspace(0, -1, 4)
                .putFolder("Folder 1")
                .addApps(3)
                .atWorkspace(1, -1, 4)
                .putFolder("Folder 2")
                .addApps(8)
                .atWorkspace(0, 0, 4)
                .putFolder("Folder 3")
                .addApps(5)
                .atWorkspace(1, 0, 4)
                .putFolder("Folder 4")
                .addApps(2)
                .atWorkspace(0, 0, 5)
                .putWidget(widget.provider.packageName, widget.provider.className, 2, 2)
                .atWorkspace(0, 0, 6)
                .putWidget(widget.provider.packageName, widget.provider.className, 3, 3)
                .atWorkspace(0, 0, 7)
                .putWidget(widget.provider.packageName, widget.provider.className, 2, 2)
        sourceSandbox.setModelLayout(builder)

        sourceSandbox.appComponent.testableModelState.dbController
            .query(null, null, null, null)
            .forEveryValue { inMemoryDb.insert(TABLE_NAME, null, it) }
        sourceSandbox.onDestroy()

        context.setupMockWidget()

        with(bgDataModel) {
            testComponent
                .getLoaderTaskFactory()
                .newLoaderTask("test", launcherBinder)
                .runSyncOnBackgroundThread()
            assertThat(
                    itemsIdMap
                        .filter {
                            it.container == CONTAINER_DESKTOP || it.container == CONTAINER_HOTSEAT
                        }
                        .size
                )
                .isAtLeast(12)
            assertThat(itemsIdMap.filter { ModelUtils.WIDGET_FILTER.test(it) }.size).isAtLeast(3)
            assertThat(
                    itemsIdMap
                        .filter {
                            it.itemType == ITEM_TYPE_FOLDER || it.itemType == ITEM_TYPE_APP_GROUP
                        }
                        .size
                )
                .isAtLeast(4)
            assertThat(itemsIdMap.count()).isAtLeast(30)
        }
    }

    @Test
    fun bindsLoadedDataCorrectly() {
        testComponent
            .getLoaderTaskFactory()
            .newLoaderTask("test", launcherBinder)
            .runSyncOnBackgroundThread()

        verify(launcherBinder).bindWorkspace(true, false)
        verify(modelDelegate).workspaceLoadComplete()
        verify(modelDelegate).loadAndAddExtraModelItems(any())
        verify(launcherBinder).bindAllApps()
        verify(iconCacheUpdateHandler, times(4)).updateIcons(any(), any<CachingLogic<Any>>(), any())
        verify(launcherBinder).bindWidgets()
        verify(iconCacheUpdateHandler).finish()
        verify(modelDelegate).modelLoadComplete()
        verify(transaction).commit()
    }

    @Test
    @MockUser(userType = UserType.WORK, isQuietModeEnabled = true)
    fun setsQuietModeFlagCorrectlyForWorkProfile() =
        with(bgDataModel) {
            setFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE)

            testComponent
                .getLoaderTaskFactory()
                .newLoaderTask("test", launcherBinder)
                .runSyncOnBackgroundThread()

            verify(bgAllAppsList).setFlags(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, true)
            verify(bgAllAppsList).setFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, false)
            verify(bgAllAppsList, Mockito.never()).setFlags(FLAG_QUIET_MODE_ENABLED, true)
        }

    @Test
    @MockUser(userType = UserType.PRIVATE, isQuietModeEnabled = true)
    fun setsQuietModeFlagCorrectlyForPrivateProfile() =
        with(bgDataModel) {
            setFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE)

            testComponent
                .getLoaderTaskFactory()
                .newLoaderTask("test", launcherBinder)
                .runSyncOnBackgroundThread()

            verify(bgAllAppsList).setFlags(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, false)
            verify(bgAllAppsList).setFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, true)
            verify(bgAllAppsList, Mockito.never()).setFlags(FLAG_QUIET_MODE_ENABLED, true)
        }

    @Test
    fun `When broadcast flag on and is restore and secure setting off then send new broadcast`() {
        // Given
        doReturn(listOf(expectedBroadcastModel))
            .whenever(broadcastHelper)
            .createModelsForFirstScreenBroadcast(any(), any(), any(), any())

        RestoreDbTask.setPending(context)

        // When
        testComponent
            .getLoaderTaskFactory()
            .newLoaderTask("test", launcherBinder)
            .runSyncOnBackgroundThread()

        // Then
        val argumentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(context).sendBroadcast(argumentCaptor.capture())
        val actualBroadcastIntent = argumentCaptor.value
        assertEquals(expectedBroadcastModel.installerPackage, actualBroadcastIntent.`package`)
        assertEquals(
            ArrayList(expectedBroadcastModel.installedWorkspaceItems),
            actualBroadcastIntent.getStringArrayListExtra("workspaceInstalledItems"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.installedHotseatItems),
            actualBroadcastIntent.getStringArrayListExtra("hotseatInstalledItems"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.installedWidgets),
            actualBroadcastIntent.getStringArrayListExtra("widgetInstalledItems"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingCollectionItems),
            actualBroadcastIntent.getStringArrayListExtra("folderItem"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingWorkspaceItems),
            actualBroadcastIntent.getStringArrayListExtra("workspaceItem"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingHotseatItems),
            actualBroadcastIntent.getStringArrayListExtra("hotseatItem"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingWidgetItems),
            actualBroadcastIntent.getStringArrayListExtra("widgetItem"),
        )
    }

    @Test
    fun `When not a restore then archiving extras are not present`() {
        // Given
        doReturn(listOf(expectedBroadcastModel))
            .whenever(broadcastHelper)
            .createModelsForFirstScreenBroadcast(any(), any(), any(), any())

        // When
        testComponent
            .getLoaderTaskFactory()
            .newLoaderTask("test", launcherBinder)
            .runSyncOnBackgroundThread()

        // Then
        verify(broadcastHelper).createModelsForFirstScreenBroadcast(any(), any(), any(), eq(false))
    }

    @Test
    fun `When failsafe secure setting on then installed item broadcast not sent`() {
        // Given
        doReturn(true).whenever(settingsCache).getValue(DISABLE_INSTALLED_APPS_BROADCAST)
        doReturn(listOf(expectedBroadcastModel))
            .whenever(broadcastHelper)
            .createModelsForFirstScreenBroadcast(any(), any(), any(), any())
        RestoreDbTask.setPending(context)

        // When
        testComponent
            .getLoaderTaskFactory()
            .newLoaderTask("test", launcherBinder)
            .runSyncOnBackgroundThread()

        // Then
        verify(broadcastHelper).createModelsForFirstScreenBroadcast(any(), any(), any(), eq(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag on and restore then archived AllApps icons on Workspace load from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = true }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val expectedComponent = ComponentName("package", "class")
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo().apply {
                        intent = Intent().apply { component = expectedComponent }
                    },
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo = AppInfo().apply { componentName = expectedComponent }
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask("test", launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ true,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isEqualTo(expectedIconBlob)
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag on and not restore then archived AllApps icons do not load from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = true }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val expectedComponent = ComponentName("package", "class")
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo().apply {
                        intent = Intent().apply { component = expectedComponent }
                    },
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo = AppInfo().apply { componentName = expectedComponent }
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask("test", launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ false,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isNull()
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag on and restore then unarchived AllApps icons not loaded from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = false }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val expectedComponent = ComponentName("package", "class")
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo().apply {
                        intent = Intent().apply { component = expectedComponent }
                    },
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo = AppInfo().apply { componentName = expectedComponent }
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask("test", launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ true,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isNull()
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag on and restore then all apps icon not on workspace is not loaded from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = true }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val expectedComponent = ComponentName("package", "class")
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo().apply {
                        intent = Intent().apply { component = expectedComponent }
                    },
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo =
            AppInfo().apply { componentName = ComponentName("differentPkg", "differentClass") }
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask("test", launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ true,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isNull()
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag off and restore then archived AllApps icons not loaded from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = true }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo(),
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo = AppInfo()
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask("test", launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ true,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isNull()
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @Test
    fun `When home screen files init is delayed then task is enqueued`() {
        // When.
        testComponent
            .getLoaderTaskFactory()
            .newLoaderTask("test", launcherBinder)
            .runSyncOnBackgroundThread()

        // NOTE: The update task would be enqueued only after home screen files become available.
        val homeScreenFilesProvider = HomeScreenFilesProvider.INSTANCE.get(context)
        homeScreenFilesProvider.onReady().thenCompose { homeScreenFilesProvider.query() }.get()

        // Then.
        val task = argumentCaptor<HomeScreenFilesUpdateTask>()
        verify(launcherModel).enqueueModelUpdateTask(task.capture())
        assertTrue(task.firstValue.update.extras.isDelayedInit)
    }

    @LauncherAppSingleton
    @Component(modules = [AllModulesForTest::class])
    interface TestComponent : LauncherAppComponent {

        fun getLoaderTaskFactory(): LoaderTaskFactory

        fun getDataModel(): BgDataModel

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindLauncherModel(model: LauncherModel): Builder

            @BindsInstance fun bindIconCache(iconCache: IconCache): Builder

            @BindsInstance fun bindAllAppsList(list: AllAppsList): Builder

            @BindsInstance fun bindSettingsCache(cache: SettingsCache): Builder

            @BindsInstance fun bindBroadcastHelper(helper: FirstScreenBroadcastHelper): Builder

            override fun build(): TestComponent
        }
    }
}

private fun LoaderTask.runSyncOnBackgroundThread() {
    val latch = CountDownLatch(1)
    MODEL_EXECUTOR.execute {
        run()
        latch.countDown()
    }
    latch.await()
}
