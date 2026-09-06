/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.shared.plugins

import com.android.systemui.plugins.annotations.Dependencies
import com.android.systemui.plugins.annotations.DependsOn
import com.android.systemui.plugins.annotations.ProvidesInterface
import com.android.systemui.plugins.annotations.Requirements
import com.android.systemui.plugins.annotations.Requires

inline fun <reified T> VersionInfo() = VersionInfo(T::class.java)

class VersionInfo(classes: List<Class<*>>) {
    constructor(cls: Class<*>) : this(listOf(cls))

    init {
        if (classes.isEmpty()) {
            throw IllegalArgumentException("Must specify at least one class")
        }
    }

    private data class Version(val version: Int, val isRequired: Boolean)

    private val versions = buildMap { classes.forEach { cls -> addClass(cls, isRequired = false) } }
    val hasVersionInfo: Boolean = versions.isNotEmpty()
    val defaultVersion: Int = classes.firstOrNull()?.let { versions[it]?.version } ?: -1

    fun checkVersion(plugin: VersionInfo) {
        val versions = versions.toMutableMap()
        plugin.versions.forEach { (aClass, version) ->
            val v =
                versions.remove(aClass)
                    ?: createVersion(aClass)
                    ?: throw InvalidVersionException(
                        "${aClass.simpleName} does not provide an interface",
                        isTooNew = false,
                    )

            if (v.version != version.version) {
                throw InvalidVersionException(
                    aClass,
                    isTooNew = v.version < version.version,
                    expectedVersion = v.version,
                    actualVersion = version.version,
                )
            }
        }

        versions.forEach { (aClass, version) ->
            if (version.isRequired) {
                throw InvalidVersionException(
                    "Missing required dependency ${aClass.simpleName}",
                    isTooNew = false,
                )
            }
        }
    }

    private fun createVersion(cls: Class<*>): Version? {
        return cls.getDeclaredAnnotation<ProvidesInterface>()?.let { annotation ->
            Version(annotation.version, isRequired = false)
        }
    }

    fun <T> hasClass(cls: Class<T>): Boolean {
        return versions.containsKey(cls)
    }

    class InvalidVersionException(
        message: String,
        val isTooNew: Boolean,
        val expectedVersion: Int = 0,
        val actualVersion: Int = 0,
    ) : RuntimeException(message) {
        constructor(
            cls: Class<*>,
            isTooNew: Boolean,
            expectedVersion: Int = 0,
            actualVersion: Int = 0,
        ) : this(
            "${cls.simpleName} expected version $expectedVersion but had $actualVersion",
            isTooNew,
            expectedVersion,
            actualVersion,
        )
    }

    companion object {
        private inline fun <reified T : Annotation> Class<*>.getDeclaredAnnotation(): T? {
            return getDeclaredAnnotation(T::class.java)
        }

        private fun MutableMap<Class<*>, Version>.addClass(cls: Class<*>, isRequired: Boolean) {
            if (containsKey(cls)) return
            cls.getDeclaredAnnotation<ProvidesInterface>()?.let { annotation ->
                this[cls] = Version(annotation.version, isRequired = true)
            }
            cls.getDeclaredAnnotation<Requires>()?.let { annotation ->
                this[annotation.target.java] = Version(annotation.version, isRequired)
            }
            cls.getDeclaredAnnotation<Requirements>()?.let { annotation ->
                annotation.value.forEach { this[it.target.java] = Version(it.version, isRequired) }
            }
            cls.getDeclaredAnnotation<DependsOn>()?.let { annotation ->
                addClass(annotation.target.java, isRequired = true)
            }
            cls.getDeclaredAnnotation<Dependencies>()?.let { annotation ->
                annotation.value.forEach { addClass(it.target.java, isRequired = true) }
            }
        }
    }
}
