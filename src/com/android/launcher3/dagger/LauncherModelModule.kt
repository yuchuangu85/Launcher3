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

package com.android.launcher3.dagger

import com.android.launcher3.ConstantItem
import com.android.launcher3.LauncherModel
import com.android.launcher3.LifecycleTracker
import com.android.launcher3.ModelReloader
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager.Companion.ICON_FACTORY_DAGGER_KEY
import com.android.launcher3.graphics.theme.IconThemeFactory
import com.android.launcher3.graphics.theme.MonoIconThemeFactory
import com.android.launcher3.graphics.theme.MonoIconThemeFactory.MONO_FACTORY_ID
import com.android.launcher3.graphics.theme.ThemePreference.Companion.THEME_OVERRIDES_DAGGER_KEY
import com.android.launcher3.model.ModelWriterFactory
import com.android.launcher3.model.ModelWriterFactoryImpl
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.AppPairSystemShortcuts
import com.android.launcher3.popup.AppWidgetSystemShortcuts
import com.android.launcher3.popup.CustomWidgetSystemShortcuts
import com.android.launcher3.popup.FileSystemShortcuts
import com.android.launcher3.popup.FolderSystemShortcuts
import com.android.launcher3.popup.PopupDataMapper
import com.android.launcher3.popup.PopupDataRepository.Companion.POPUP_DATA_MAPPER
import com.android.launcher3.qsb.OseCustomWidget
import com.android.launcher3.widget.custom.CustomWidget
import com.android.launcher3.widget.custom.CustomWidgetManager.NAMED_CUSTOM_WIDGETS
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dagger.multibindings.StringKey
import javax.inject.Named

@Module
abstract class LauncherModelModule {

    @Binds
    @LauncherAppSingleton
    abstract fun bindModelWriterFactory(impl: ModelWriterFactoryImpl): ModelWriterFactory

    @Multibinds @Named("MODEL_ITEMS") abstract fun extraModelItems(): Set<ItemInfo>

    @Multibinds @Named(POPUP_DATA_MAPPER) abstract fun popDataMappers(): Set<PopupDataMapper>

    @Multibinds abstract fun lifecycleTrackers(): Set<LifecycleTracker>

    @Multibinds
    @Named(THEME_OVERRIDES_DAGGER_KEY)
    abstract fun legacyThemeKeys(): Map<String, ConstantItem<String>>

    @Multibinds @Named(NAMED_CUSTOM_WIDGETS) abstract fun extraCustomWidgets(): Set<CustomWidget>

    companion object {

        @Provides
        @IntoMap
        @StringKey(MONO_FACTORY_ID)
        @Named(ICON_FACTORY_DAGGER_KEY)
        @JvmStatic
        fun monoIconFactory(): IconThemeFactory = MonoIconThemeFactory

        @Provides
        @JvmStatic
        fun provideModelReloader(model: LauncherModel): ModelReloader {
            return ModelReloader { model.reloadIfActive("ModelReloader") }
        }

        @Provides
        @IntoSet
        @Named(NAMED_CUSTOM_WIDGETS)
        @JvmStatic
        fun monoSearchCustomWidget(): CustomWidget = OseCustomWidget

        @Provides
        @JvmStatic
        @ElementsIntoSet
        @Named(POPUP_DATA_MAPPER)
        fun defaultPopupDataMappers(): Set<PopupDataMapper> =
            setOf(
                FolderSystemShortcuts,
                AppPairSystemShortcuts,
                AppWidgetSystemShortcuts,
                CustomWidgetSystemShortcuts,
            )

        @Provides
        @JvmStatic
        @IntoSet
        @Named(POPUP_DATA_MAPPER)
        fun provideFilePopupDataMapper(fileShortcuts: FileSystemShortcuts): PopupDataMapper =
            fileShortcuts
    }
}
