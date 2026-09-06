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

package com.android.tools.dagger.mutation

import com.android.tools.dagger.mutation.TypeUtils.className
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import dagger.Binds
import dagger.BindsInstance
import dagger.BindsOptionalOf
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import javax.inject.Qualifier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/** Class representing an annotation definition */
enum class AnnotationRef(clazz: KClass<*>) {
    QUALIFIER(Qualifier::class),
    BINDS(Binds::class),
    BINDS_OPTIONAL(BindsOptionalOf::class),
    MULTI_BINDS(Multibinds::class),
    BINDS_INSTANCE(BindsInstance::class),
    PROVIDES(Provides::class),
    MODULE(Module::class),
    COMPONENT(Component::class),
    COMPONENT_BUILDER(Component.Builder::class),
    BIND_VALUE(BindValue::class),
    MUTATED_COMPONENT(MutatedComponent::class);

    val className = clazz.qualifiedName!!

    override fun toString(): String = "@$className"

    fun matcher(): (KSAnnotation) -> Boolean = { it.className() == className }
}

/** Class representing a single parameter of an annotation */
class AnnotationListValueRef(clazz: KClass<*>, val param: String) {

    val className = clazz.qualifiedName!!

    companion object {

        val TARGET_MODULE = MutatedComponent::target.asValueRef()
        val INSTALL_MODULES = MutatedComponent::installModules.asValueRef()
        val UNINSTALL_MODULES = MutatedComponent::uninstallModules.asValueRef()
        val UNBIND_VALUES = MutatedComponent::unbindValues.asValueRef()

        val COMPONENT_MODULES = Component::modules.asValueRef()
        val MODULE_INCLUDES = Module::includes.asValueRef()
        val MODULE_SUBCOMPONENTS = Module::subcomponents.asValueRef()

        private inline fun <reified T> KProperty1<T, Any>.asValueRef() =
            AnnotationListValueRef(T::class, name)

        inline fun <reified T> KSClassDeclaration.find(annotation: AnnotationListValueRef): T? =
            annotations
                .filter { it.className() == annotation.className }
                .mapNotNull {
                    it.arguments.find { arg -> arg.name?.asString() == annotation.param }?.value
                        as? T
                }
                .firstOrNull()

        fun KSClassDeclaration.findClassList(annotation: AnnotationListValueRef) =
            find<List<KSType>>(annotation)?.mapNotNull { it.declaration as? KSClassDeclaration }
    }
}
