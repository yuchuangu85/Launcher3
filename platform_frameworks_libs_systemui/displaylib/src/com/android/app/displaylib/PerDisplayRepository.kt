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

package com.android.app.displaylib

import android.util.Log
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Used to create instances of type `T` for a specific display.
 *
 * This is useful for resources or objects that need to be managed independently for each connected
 * display (e.g., UI state, rendering contexts, or display-specific configurations).
 *
 * Note that in most cases this can be implemented by a simple `@AssistedFactory` with `displayId`
 * parameter
 *
 * ```kotlin
 * class SomeType @AssistedInject constructor(@Assisted displayId: Int,..)
 *      @AssistedFactory
 *      interface Factory {
 *         fun create(displayId: Int): SomeType
 *      }
 *  }
 * ```
 *
 * Then it can be used to create a [PerDisplayRepository] as follows:
 * ```kotlin
 * // Injected:
 * val repositoryFactory: PerDisplayRepositoryImpl.Factory
 * val instanceFactory: PerDisplayRepositoryImpl.Factory
 * // repository creation:
 * repositoryFactory.create(instanceFactory::create)
 * ```
 *
 * @see PerDisplayRepository For how to retrieve and manage instances created by this factory.
 */
fun interface PerDisplayInstanceProvider<T> {
    /** Creates an instance for a display. */
    fun createInstance(displayId: Int): T?
}

/**
 * Extends [PerDisplayInstanceProvider], adding support for destroying the instance.
 *
 * This is useful for releasing resources associated with a display when it is disconnected or when
 * the per-display instance is no longer needed.
 */
interface PerDisplayInstanceProviderWithTeardown<T> : PerDisplayInstanceProvider<T> {
    /** Destroys a previously created instance of `T` forever. */
    fun destroyInstance(instance: T)
}

/**
 * Provides access to per-display instances of type `T`.
 *
 * Acts as a repository, managing the caching and retrieval of instances created by a
 * [PerDisplayInstanceProvider]. It ensures that only one instance of `T` exists per display ID.
 */
interface PerDisplayRepository<T> {
    /** Gets the cached instance or create a new one for a given display. */
    operator fun get(displayId: Int): T?

    /** Debug name for this repository, mainly for tracing and logging. */
    val debugName: String

    /**
     * Callback to run when a given repository is initialized.
     *
     * This allows the caller to perform custom logic when the repository is ready to be used, e.g.
     * register to dumpManager.
     *
     * Note that the instance is *leaked* outside of this class, so it should only be done when
     * repository is meant to live as long as the caller. In systemUI this is ok because the
     * repository lives as long as the process itself.
     */
    fun interface InitCallback {
        fun onInit(debugName: String, instance: Any)
    }

    /**
     * Iterate over all the available displays performing the action on each object of type T.
     *
     * @param createIfAbsent If true, create instances of T if they are not already created. If
     *   false, do not and skip calling action..
     * @param action The action to perform on each instance.
     */
    fun forEach(createIfAbsent: Boolean, action: Consumer<T>)
}

/** Qualifier for [CoroutineScope] used for displaylib background tasks. */
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class DisplayLibBackground

/**
 * Default implementation of [PerDisplayRepository].
 *
 * This class manages a cache of per-display instances of type `T`, creating them using a provided
 * [PerDisplayInstanceProvider] and optionally tearing them down using a
 * [PerDisplayInstanceProviderWithTeardown] when based on [lifecycleManager].
 *
 * An instance will be destroyed when either
 * - The display is not connected anymore
 * - or based on [lifecycleManager]. If no lifecycle manager is provided, instances are destroyed
 *   when the display is disconnected.
 *
 * [DisplayInstanceLifecycleManager] can decide to delete instances for a display even before it is
 * disconnected. An example of usecase for it, is to delete instances when screen decorations are
 * removed.
 *
 * Note that this is a [PerDisplayStoreImpl] 2.0 that doesn't require [CoreStartable] bindings,
 * providing all args in the constructor.
 */
class PerDisplayInstanceRepositoryImpl<T>
@AssistedInject
constructor(
    @Assisted override val debugName: String,
    @Assisted private val instanceProvider: PerDisplayInstanceProvider<T>,
    @Assisted lifecycleManager: DisplayInstanceLifecycleManager? = null,
    @DisplayLibBackground bgApplicationScope: CoroutineScope,
    private val displayRepository: DisplayRepository,
    private val initCallback: PerDisplayRepository.InitCallback,
) : PerDisplayRepository<T> {

    private val perDisplayInstances = ConcurrentHashMap<Int, T?>()

    private val allowedDisplays: StateFlow<Set<Int>> =
        (if (lifecycleManager == null) {
            displayRepository.displayIds
        } else {
            // If there is a lifecycle manager, we still consider the smallest subset between
            // the ones connected and the ones from the lifecycle. This is to safeguard against
            // leaks, in case of lifecycle manager misbehaving (as it's provided by clients, and
            // we can't guarantee it's correct).
            combine(lifecycleManager.displayIds, displayRepository.displayIds) {
                    lifecycleAllowedDisplayIds,
                    connectedDisplays ->
                lifecycleAllowedDisplayIds.intersect(connectedDisplays)
            }
        }) as StateFlow<Set<Int>>

    init {
        bgApplicationScope.launch { start() }
    }

    private suspend fun start() {
        initCallback.onInit(debugName, this)
        allowedDisplays.collectLatest { displayIds ->
            val toRemove = perDisplayInstances.keys - displayIds
            toRemove.forEach { displayId ->
                Log.d(TAG, "<$debugName> destroying instance for displayId=$displayId.")
                perDisplayInstances.remove(displayId)?.let { instance ->
                    (instanceProvider as? PerDisplayInstanceProviderWithTeardown)?.destroyInstance(
                        instance
                    )
                }
            }
        }
    }

    override fun get(displayId: Int): T? {
        if (!displayRepository.containsDisplay(displayId)) {
            Log.e(TAG, "<$debugName: Display with id $displayId doesn't exist.")
            return null
        }

        if (displayId !in allowedDisplays.value) {
            Log.e(
                TAG,
                "<$debugName: Display with id $displayId exists but it's not " +
                    "allowed by lifecycle manager.",
            )
            return null
        }

        // If it doesn't exist, create it and put it in the map.
        return perDisplayInstances.computeIfAbsent(displayId) { key ->
            Log.d(TAG, "<$debugName> creating instance for displayId=$key, as it wasn't available.")
            val instance = instanceProvider.createInstance(key)
            if (instance == null) {
                Log.e(
                    TAG,
                    "<$debugName> returning null because createInstance($key) returned null.",
                )
            }
            instance
        }
    }

    @AssistedFactory
    interface Factory<T> {
        fun create(
            debugName: String,
            instanceProvider: PerDisplayInstanceProvider<T>,
            overrideLifecycleManager: DisplayInstanceLifecycleManager? = null,
        ): PerDisplayInstanceRepositoryImpl<T>
    }

    companion object {
        private const val TAG = "PerDisplayInstanceRepo"
    }

    override fun toString(): String {
        return "PerDisplayInstanceRepositoryImpl(" +
            "debugName='$debugName', instances=$perDisplayInstances)"
    }

    override fun forEach(createIfAbsent: Boolean, action: Consumer<T>) {
        if (createIfAbsent) {
            allowedDisplays.value.forEach { displayId -> get(displayId)?.let { action.accept(it) } }
        } else {
            perDisplayInstances.forEach { (_, instance) -> instance?.let { action.accept(it) } }
        }
    }
}

/**
 * Provides an instance of a given class **only** for the default display, even if asked for another
 * display.
 *
 * This is useful in case of **flag refactors**: it can be provided instead of an instance of
 * [PerDisplayInstanceRepositoryImpl] when a flag related to multi display refactoring is off.
 *
 * Note that this still requires all instances to be provided by a [PerDisplayInstanceProvider]. If
 * you want to provide an existing instance instead for the default display, either implement it in
 * a custom [PerDisplayInstanceProvider] (e.g. inject it in the constructor and return it if the
 * displayId is zero), or use [SingleInstanceRepositoryImpl].
 */
class DefaultDisplayOnlyInstanceRepositoryImpl<T>(
    override val debugName: String,
    private val instanceProvider: PerDisplayInstanceProvider<T>,
) : PerDisplayRepository<T> {
    private val lazyDefaultDisplayInstanceDelegate = lazy {
        instanceProvider.createInstance(Display.DEFAULT_DISPLAY)
    }
    private val lazyDefaultDisplayInstance by lazyDefaultDisplayInstanceDelegate

    override fun get(displayId: Int): T? = lazyDefaultDisplayInstance

    override fun forEach(createIfAbsent: Boolean, action: Consumer<T>) {
        if (createIfAbsent) {
            get(DEFAULT_DISPLAY)?.let { action.accept(it) }
        } else {
            if (lazyDefaultDisplayInstanceDelegate.isInitialized()) {
                lazyDefaultDisplayInstance?.let { action.accept(it) }
            }
        }
    }
}

/**
 * Always returns [instance] for any display.
 *
 * This can be used to provide a single instance based on a flag value during a refactor. Similar to
 * [DefaultDisplayOnlyInstanceRepositoryImpl], but also avoids creating the
 * [PerDisplayInstanceProvider]. This is useful when you want to provide an existing instance only,
 * without even instantiating a [PerDisplayInstanceProvider].
 */
class SingleInstanceRepositoryImpl<T>(override val debugName: String, private val instance: T) :
    PerDisplayRepository<T> {
    override fun get(displayId: Int): T? = instance

    override fun forEach(createIfAbsent: Boolean, action: Consumer<T>) {
        action.accept(instance)
    }
}
