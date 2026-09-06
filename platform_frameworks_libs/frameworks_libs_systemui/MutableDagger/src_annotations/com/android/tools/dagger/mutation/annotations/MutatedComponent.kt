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

package com.android.tools.dagger.mutation.annotations

import kotlin.reflect.KClass

/**
 * Annotation to specify the entry-point for custom component generation. The component is generated
 * with a suffix "_MutatedComponent" attached to the class name where this annotation is defined.
 *
 * @param target should point to the component interface which needs to be mutated
 *
 * [uninstallModules] can be specified to remove any module definitions from the original
 * definition. Note that removal is performed from the full transitive set. Eg: if {A} include {B}
 * and {C} and {B} in-turn includes {D} Removing {D}, will cause the final definition to include
 * {A}, {B} and {C}, while removing {B} will result in only {A} and {C} The behavior is similar to
 * Hilt's
 * [@UninstallModules annotation](https://dagger.dev/api/2.28/dagger/hilt/android/testing/UninstallModules.html)
 *
 * [installModules] can be provided to add any additional modules to the final component
 *
 * [unbindValues] causes any bindings for the specified types in the original set of modules
 * (without factoring in any additional [installModules] will be removed. Qualifiers are not
 * supported, for that separate the bindings in a custom module and use [uninstallModules] to remove
 * it recursively
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MutatedComponent(
    val target: KClass<*>,
    val installModules: Array<KClass<*>> = [],
    val uninstallModules: Array<KClass<*>> = [],
    val unbindValues: Array<KClass<*>> = [],
)

/**
 * Similar to [MutatedComponent.unbindValues], it removes any existing binding, and also adds a new
 * bindInstance method in the component builder corresponding to the target element.
 *
 * Any qualifiers on the target element are preserved in the mutated component Its behavior is
 * similar to Hilt's
 * [@BindValue annotation](https://dagger.dev/api/2.28/index.html?dagger/hilt/android/testing/UninstallModules.html)
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class BindValue
