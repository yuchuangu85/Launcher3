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
package com.android.launcher3.workspacefunctions.translators

import com.android.launcher3.appfunctions.workspace.AppInFolderSpec
import com.android.launcher3.appfunctions.workspace.HotseatItemSpec
import com.android.launcher3.appfunctions.workspace.Translator
import com.android.launcher3.appfunctions.workspace.UnplacedAppSpec
import com.android.launcher3.appfunctions.workspace.UnplacedAppTypeTranslator
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetSpec
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetTypeTranslator
import com.android.launcher3.appfunctions.workspace.WorkspaceItemSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTypeTranslator
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * A registry that provides access to various translators used for workspace functions.
 *
 * It wraps multiple Dagger multibinding maps into a single access point.
 */
class TranslatorRegistry
@Inject
constructor(
    private val workspaceItemTranslators:
        Map<Class<*>, @JvmSuppressWildcards Provider<WorkspaceItemTranslator<*>>>,
    private val hotseatItemTranslators:
        Map<Class<*>, @JvmSuppressWildcards Provider<HotseatItemTranslator<*>>>,
    private val appInFolderTranslators:
        Map<Class<*>, @JvmSuppressWildcards Provider<AppInFolderTranslator<*>>>,
    private val workspaceTypeTranslators:
        Map<Class<*>, @JvmSuppressWildcards Provider<WorkspaceTypeTranslator<*>>>,
    private val unplacedAppTypeTranslators:
        Map<Class<*>, @JvmSuppressWildcards Provider<UnplacedAppTypeTranslator<*>>>,
    private val unplacedWidgetTypeTranslators:
        Map<Class<*>, @JvmSuppressWildcards Provider<UnplacedWidgetTypeTranslator<*>>>,
) {
    /** Returns the translator for the given [target] and [sourceClass] types. */
    @PublishedApi
    internal fun getTranslatorForTarget(
        target: KClass<*>,
        sourceClass: Class<*>,
    ): Translator<Any, Any> {
        val map =
            when (target) {
                WorkspaceItemSpec::class -> workspaceItemTranslators
                HotseatItemSpec::class -> hotseatItemTranslators
                AppInFolderSpec::class -> appInFolderTranslators
                WorkspaceSpec::class -> workspaceTypeTranslators
                UnplacedAppSpec::class -> unplacedAppTypeTranslators
                UnplacedWidgetSpec::class -> unplacedWidgetTypeTranslators
                else -> throw IllegalArgumentException("Unknown target type: $target")
            }

        val provider =
            map[sourceClass]
                ?: map.entries.firstOrNull { it.key.isAssignableFrom(sourceClass) }?.value

        return provider?.get() as? Translator<Any, Any>
            ?: throw IllegalArgumentException(
                "No translator found for target $target and source $sourceClass"
            )
    }

    /** Translates the given [obj] to the inferred [Target] type. */
    inline fun <reified Target : Any> translate(obj: Any): Target {
        val sourceClass = if (obj is ItemContext<*>) obj.item::class.java else obj::class.java
        val translator =
            getTranslatorForTarget(Target::class, sourceClass) as Translator<Any, Target>
        return translator.toSpec(obj)
    }
}
