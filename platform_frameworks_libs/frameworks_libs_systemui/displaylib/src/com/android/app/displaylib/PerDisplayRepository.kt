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

import android.os.Looper
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import com.android.app.tracing.coroutines.TrackTracer
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.app.tracing.coroutines.launchTraced as launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import javax.inject.Qualifier
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

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
 * Extends [PerDisplayInstanceProvider], adding support for setting up an instance after it's
 * created.
 *
 * This is useful to run custom setup after an instance of the repository is created and cached. Why
 * not doing it in the [createInstance] itself? if some deps of the setup code tries to get the
 * instance again through the repository, it would cause a recursive loop (as it will try to create
 * a new instance). Splitting this into another method helps avoiding the recursion.
 */
interface PerDisplayInstanceProviderWithSetup<T> : PerDisplayInstanceProvider<T> {
    /**
     * Sets up a previously created instance of `T`.
     *
     * Note that this can be executed while the instance is used somewhere else already (as in:
     * we're not locking instance creation + setup, but only creation, as the setup could be
     * lengthy).
     */
    fun setupInstance(instance: T)
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

    /**
     * Gets the cached instance or create a new one for a given display. If the given display
     * doesn't exist, returns an instance for the default display.
     */
    fun getOrDefault(displayId: Int): T {
        val instance = get(displayId)
        if (instance == null) {
            Log.e(
                "PerDisplayRepository",
                """<$debugName> getOrDefault: instance for display with id $displayId returned
                    |null. The display likely doesn't exist anymore. Returning an instance for the
                    |default display."""
                    .trimMargin(),
            )
            return get(DEFAULT_DISPLAY)!!
        }
        return instance
    }

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

/** Qualifier for [CoroutineContext] backed by the main thread. Use with care. */
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class DisplayLibMainThread

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
 *
 * If [mainThreadForDefaultDisplayEagerlyCreation] is true, then the creation of the instance for
 * the default display happens on the main thread instead of the background one. This param was
 * introduced due to the chance of creating a deadlock if there are usages of the
 * [PerDisplayRepository#get] in constructors or dagger @Provides blocks. In such cases, a thread
 * stuck in the [get] might need some locks related to dagger (to instantiate its dependencies), but
 * another thread might be holding them. If this other thread at this point, inside a dagger module,
 * calls [get], a deadlock will happen (as one thread holds the [get] lock and waits for the dagger
 * one, while the other holds the dagger one and waits for the [get] one). The param is only for the
 * default display as currently it's the only case where PerDisplayRepository-ies are accessed
 * directly in dagger modules to provide default display bindings (for compatibility reasons, as
 * certain classes undergoing refactors still require the default display instance in the dagger
 * module). For external displays, everything happens in display specific modules (as it actually
 * should for the default display as well), so no deadlock are expected to happen.
 */
class PerDisplayInstanceRepositoryImpl<T>
@AssistedInject
constructor(
    @param:Assisted override val debugName: String,
    @param:Assisted private val instanceProvider: PerDisplayInstanceProvider<T>,
    @Assisted lifecycleManager: DisplayInstanceLifecycleManager? = null,
    @param:DisplayLibMainThread private val mainContext: CoroutineContext,
    @param:DisplayLibBackground private val bgApplicationScope: CoroutineScope,
    private val displayRepository: DisplayRepository,
    private val initCallback: PerDisplayRepository.InitCallback,
    @param:Assisted private val createInstanceEagerly: Boolean = false,
    @param:Assisted("mainThreadForDefaultDisplayEagerlyCreation")
    private val mainThreadForDefaultDisplayEagerlyCreation: Boolean = false,
) : PerDisplayRepository<T> {

    private val t = TrackTracer(debugName, trackGroup = TAG)
    private val perDisplayInstances = ConcurrentHashMap<Int, T?>()

    private val allowedDisplays: StateFlow<Set<Int>> =
        if (lifecycleManager == null) {
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
            }
            .stateInTraced(
                name = "allowed displays for $debugName",
                scope = bgApplicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    if (lifecycleManager == null) {
                        displayRepository.displayIds.value
                    } else {
                        displayRepository.displayIds.value.intersect(
                            lifecycleManager.displayIds.value
                        )
                    },
            )

    init {
        bgApplicationScope.launch("$debugName#start") { start() }
    }

    private suspend fun start() {
        initCallback.onInit(debugName, this)
        allowedDisplays.collectLatest { displayIds ->
            if (createInstanceEagerly) {
                eagerlyCreateInstanceForDisplays(displayIds)
            }
            val toRemove = perDisplayInstances.keys - displayIds
            removeInstances(toRemove)
        }
    }

    private suspend fun eagerlyCreateInstanceForDisplays(displayIds: Set<Int>) {
        val toAdd = displayIds - perDisplayInstances.keys
        t.traceAsync("eager creation for displays: $toAdd") {
            toAdd.forEach { displayId: Int ->
                withContext(getEagerlyInitializationCoroutineContext(displayId)) {
                    log("eagerly calling get() for displayId=$displayId")
                    get(displayId)
                    log("✅ eagerly created instance for displayId=$displayId")
                }
            }
        }
    }

    private fun getEagerlyInitializationCoroutineContext(displayId: Int): CoroutineContext {
        return if (mainThreadForDefaultDisplayEagerlyCreation && displayId == DEFAULT_DISPLAY) {
            return mainContext
        } else {
            bgApplicationScope.coroutineContext
        }
    }

    private fun removeInstances(toRemove: Set<Int>) {
        toRemove.forEach { displayId ->
            log("destroying instance for displayId=$displayId.")
            t.traceSyncAndAsync({ "Removing instance for displayId=$displayId" }) {
                perDisplayInstances.remove(displayId)?.let { instance ->
                    (instanceProvider as? PerDisplayInstanceProviderWithTeardown)?.destroyInstance(
                        instance
                    )
                }
            }
        }
    }

    override fun get(displayId: Int): T? {
        if (
            !displayRepository.containsDisplay(displayId) ||
                displayRepository.getDisplay(displayId) == null
        ) {
            errorLog("get(displayId=$displayId): display doesn't exist.")
            return null
        }

        if (displayId !in allowedDisplays.value) {
            errorLog(
                "get(displayId=$displayId): display exists " +
                    "but it's not allowed by lifecycle manager."
            )
            return null
        }

        // There is no need to synchronize the other accesses to the map as it's already a
        // concurrent one.
        var newlyCreated = false
        val instance =
            synchronized(this) {
                // If it doesn't exist, create it and put it in the map.

                perDisplayInstances.computeIfAbsent(displayId) { key ->
                    if (
                        createInstanceEagerly &&
                            mainThreadForDefaultDisplayEagerlyCreation &&
                            displayId == DEFAULT_DISPLAY &&
                            !Looper.getMainLooper().isCurrentThread
                    ) {
                        errorLog(
                            "get($displayId): called from a non main-thread despite " +
                                "mainThreadForDefaultDisplayEagerlyCreation is true. " +
                                "Thread.currentThread()=${Thread.currentThread().name}"
                        )
                    }
                    log("creating instance for displayId=$key, as it wasn't available.")
                    val instance =
                        t.traceSyncAndAsync({ "$debugName creating instance for displayId=$key" }) {
                            instanceProvider.createInstance(key)
                        }
                    log("creation for displayId=$key finished.")
                    if (instance == null) {
                        errorLog(
                            "get($displayId): returning null because createInstance($key) " +
                                "returned null."
                        )
                    }
                    newlyCreated = true
                    instance
                }
            }

        // The setup happens outside the synchronized block, as it can be expensive. Note that the
        // instance might be used while the setupInstance method is in progress (WAI)
        if (
            newlyCreated &&
                instance != null &&
                instanceProvider is PerDisplayInstanceProviderWithSetup
        ) {
            t.traceSyncAndAsync({ "$debugName#setupInstance for displayId=$displayId" }) {
                instanceProvider.setupInstance(instance)
            }
        }
        return instance
    }

    @AssistedFactory
    interface Factory<T> {
        fun create(
            debugName: String,
            instanceProvider: PerDisplayInstanceProvider<T>,
            overrideLifecycleManager: DisplayInstanceLifecycleManager? = null,
            createInstanceEagerly: Boolean = false,
            @Assisted("mainThreadForDefaultDisplayEagerlyCreation")
            mainThreadForDefaultDisplayEagerlyCreation: Boolean = false,
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

    private fun log(msg: String) {
        Log.d(TAG, "<$debugName> $msg.")
    }

    private fun errorLog(msg: String) {
        Log.e(TAG, "<$debugName> $msg.")
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
        instanceProvider.createInstance(DEFAULT_DISPLAY)
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
