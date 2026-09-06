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
package com.android.launcher3.preview

import android.content.Context
import android.text.TextUtils
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Item
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.ProxyPrefs
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.BootSafeModules
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.dagger.NoOpLoggerModule
import com.android.launcher3.dagger.PerDisplayModule
import com.android.launcher3.graphics.theme.ThemePreference
import com.android.launcher3.model.ModelInitializer
import com.android.launcher3.model.data.LoaderParams
import com.android.launcher3.organizer.dagger.NoOpGeneratorModule
import com.android.launcher3.organizer.dagger.NoOpOrganizerModule
import com.android.launcher3.provider.LauncherDbUtils.selectionForWorkspaceScreen
import com.android.launcher3.qsb.OseWidgetManager
import com.android.launcher3.util.SandboxContext
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.launcher3.widget.LocalColorExtractor
import com.android.launcher3.widget.util.WidgetSizeHandler
import com.android.launcher3.widgetpicker.NoOpWidgetPickerModule
import com.android.launcher3.workspacefunctions.NoOpWorkspaceFunctionsModule
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import java.io.File
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * Context used just for preview. It also provides a few objects (e.g. UserCache) just for preview
 * purposes.
 */
class PreviewContext
@JvmOverloads
constructor(
    base: Context,
    gridName: String?,
    widgetHostId: Int = LauncherWidgetHolder.APPWIDGET_HOST_ID,
    layoutXml: String? = null,
    workspacePageId: Int = WorkspaceLayoutManager.FIRST_SCREEN_ID,
    workspaceHideItemsLabel: Boolean = false,
) : SandboxContext(base) {
    private val mPrefName: String

    private val mDbDir: File?

    init {
        val randomUid = UUID.randomUUID().toString()
        mPrefName = "preview-$randomUid"
        val prefs = ProxyPrefs(this, getSharedPreferences(mPrefName, MODE_PRIVATE))
        prefs.putOrRemove(LauncherPrefs.GRID_NAME, gridName)
        prefs.put(LauncherPrefs.FIXED_LANDSCAPE_MODE, false)
        if (com.android.systemui.shared.Flags.workspaceItemsLabelHidden()) {
            prefs.put(LauncherPrefs.WORKSPACE_ITEMS_LABEL_HIDDEN, workspaceHideItemsLabel)
        }

        val isTwoPanel =
            base.appComponent.idp.supportedProfiles.any { it.deviceProperties.isTwoPanels }
        val closestEvenPageId: Int = workspacePageId - (workspacePageId % 2)
        val selectionQuery =
            if (isTwoPanel) selectionForWorkspaceScreen(closestEvenPageId, closestEvenPageId + 1)
            else selectionForWorkspaceScreen(workspacePageId)

        val builder = DaggerPreviewContext_PreviewAppComponent.builder().bindPrefs(prefs)
        builder.bindLoaderParams(
            LoaderParams(
                workspaceSelection = selectionQuery,
                sanitizeData = false,
                loadNonWorkspaceItems = false,
            )
        )

        // Bind the LauncherApp's single OseWidgetManager to PreviewComponent. This way same
        // OseWidgetManager is shared between the Preview and Launcher.
        // If the OseWidgetManager's are different, QsbAppWidgetHost will be different and this will
        // cause either launcher appcomponent or preview to app component to go out of sync.
        builder.bindOseWidgetManager(base.appComponent.oseWidgetManager)

        if (layoutXml.isNullOrEmpty()) {
            mDbDir = null
            initDaggerComponent(builder.bindWidgetsFactory(base.appComponent.widgetHolderFactory))
        } else {
            mDbDir = File(base.filesDir, randomUid)
            emptyDbDir()
            mDbDir.mkdirs()
            initDaggerComponent(
                builder.bindWidgetsFactory { NonPrimaryWidgetHolder(it, widgetHostId) }
            )
            appComponent.layoutParserFactory.overrideXmlLayout(layoutXml)
        }

        if (!TextUtils.isEmpty(layoutXml)) {
            // Use null the DB file so that we use a new in-memory DB
            InvariantDeviceProfile.INSTANCE[this].dbFile = null
        }
    }

    fun <T : Any> LauncherPrefs.putOrRemove(item: Item, value: T?) {
        if (value != null) put(item, value) else remove(item)
    }

    private fun emptyDbDir() {
        if (mDbDir != null && mDbDir.exists()) {
            Arrays.stream(mDbDir.listFiles()).forEach { obj: File -> obj.delete() }
        }
    }

    override fun cleanUpObjects() {
        super.cleanUpObjects()
        deleteSharedPreferences(mPrefName)
        if (mDbDir != null) {
            emptyDbDir()
            mDbDir.delete()
        }
    }

    override fun getDatabasePath(name: String): File =
        if (mDbDir != null) File(mDbDir, name) else super.getDatabasePath(name)

    class NoOpWidgetSizeHandler
    @Inject
    constructor(
        @ApplicationContext context: Context,
        idp: InvariantDeviceProfile,
        themePreference: ThemePreference,
    ) : WidgetSizeHandler(context, idp, themePreference) {

        override fun updateSizeRangesAsync(
            widgetId: Int,
            spanX: Int,
            spanY: Int,
            executor: Executor,
        ) {
            // Ignore
        }

        override fun updateHotseatQsbSizeRangesAsync(widgetId: Int, executor: Executor) {
            // Ignore
        }
    }

    private class NonPrimaryWidgetHolder(context: Context, hostId: Int) :
        LauncherWidgetHolder(context, hostId) {

        override fun startListeningForSharedUpdate() = startListening()
    }

    @Module
    abstract class PreviewModule {

        @Binds abstract fun bindWidgetSizeHandler(handler: NoOpWidgetSizeHandler): WidgetSizeHandler
    }

    @LauncherAppSingleton // Exclude widget module since we bind widget holder separately
    @Component(
        modules =
            [
                PerDisplayModule::class,
                NoOpWidgetPickerModule::class,
                NoOpWorkspaceFunctionsModule::class,
                NoOpLoggerModule::class,
                PreviewModule::class,
                BootSafeModules::class,
                NoOpOrganizerModule::class,
                NoOpGeneratorModule::class,
            ]
    )
    interface PreviewAppComponent : LauncherAppComponent {
        val model: LauncherModel
        val modelInitializer: ModelInitializer
        val localColorExtractor: LocalColorExtractor

        /** Builder for NexusLauncherAppComponent. */
        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindPrefs(prefs: LauncherPrefs): Builder

            @BindsInstance fun bindWidgetsFactory(holderFactory: WidgetHolderFactory): Builder

            @BindsInstance fun bindLoaderParams(params: LoaderParams): Builder

            @BindsInstance fun bindOseWidgetManager(oseWidgetManager: OseWidgetManager): Builder

            override fun build(): PreviewAppComponent
        }
    }
}
