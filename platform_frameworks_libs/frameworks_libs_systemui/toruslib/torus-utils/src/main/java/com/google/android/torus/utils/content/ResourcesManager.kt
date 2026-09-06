/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.torus.utils.content

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Class that holds the resources to be used by the engine in concurrent instances.
 * This class is used to re-use resources that take time and memory in the system. Adding them here
 * they can be re-used by different instances.
 */
class ResourcesManager {
    private val resources: ConcurrentMap<String, WeakReference<Any>> = ConcurrentHashMap(0)

    /**
     * The number of resources hold.
     */
    var size: Int = 0
        get() = resources.size
        private set

    /**
     * Stores the given resource in the ResourceManager.
     *
     * @param key A string identifying the resource (it can be any).
     * @param resource The resource that we want to use in multiple instances.
     * @return True if the resource was added; false if the resource already existed.
     */
    fun addResource(key: String, resource: Any): Boolean {
        if (resources.contains(key) &&
            resources[key] != null &&
            resources[key]!!.get() != null
        ) {
            return false
        }

        resources[key] = WeakReference(resource)
        return true
    }

    /**
     * Gets a resource from the ResourcesManager, using the supplied function to create the resource
     * if it didn't already exist. The key is always mapped to a non-null resource as a
     * post-condition of this method.
     *
     * @param key A string identifying the resource (it can be any).
     * @param provider A function to create the resource if it's not already indexed.
     * @return The (new or existing) resource associated with the key.
     */
    fun <T> getOrAddResource(key: String, provider: () -> T): T {
        val resource: T = (resources[key]?.get() as T) ?: provider()
        resources[key] = WeakReference<Any>(resource)
        return resource
    }

    /**
     * Returns the resource associated with the [key].
     *
     * @param key The key associated with the resource.
     * @return The given resource; null if the resource wasn't found.
     */
    fun getResource(key: String): Any? {
        if (!resources.contains(key)) return null
        resources[key]?.let {
            return it.get()
        }
        return null
    }

    /**
     * Stops the resource associated with the given [key].
     *
     * @param key The key associated with the resource.
     * @return The resource reference that has been removed; null if the resource wasn't found.
     */
    fun removeResource(key: String): WeakReference<Any>? = resources.remove(key)
}
