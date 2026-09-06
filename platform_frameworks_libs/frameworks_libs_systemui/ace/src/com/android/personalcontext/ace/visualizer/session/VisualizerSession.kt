/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.personalcontext.ace.visualizer.session

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A {@link VisualizerSession} maintains the lifecycle state needed to support sending {@link
 * ComposeView}s to remote {@link SurfaceView}s. It is also responsible for other view interactions
 * (e.g. embedded scrolling, etc.).
 */
class VisualizerSession(val view: View) {

    private val lifecycle: ViewLifecycle = ViewLifecycle().apply { attachToView(view) }

    fun destroy() {
        lifecycle.destroy()
    }
}

/** A class used to manage the compose lifecycle for a ComposeView. */
private class ViewLifecycle : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val savedInstanceState: Bundle = Bundle()
    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    /** Attach compose lifecycle to the given ComposeView. */
    fun attachToView(view: View) {
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            savedStateRegistryController.performRestore(savedInstanceState)
        }

        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        view.rootView.setViewTreeLifecycleOwner(this)
        view.rootView.setViewTreeSavedStateRegistryOwner(this)
        view.rootView.setViewTreeViewModelStoreOwner(this)

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** Destroy the compose lifecycle for a previously attached ComposeView. */
    fun destroy() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            Log.e(TAG, "ViewLifecycle already destroyed!")
            return
        }

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        savedStateRegistryController.performSave(savedInstanceState)
        viewModelStore.clear()
    }

    private companion object {
        const val TAG = "ViewLifecycle"
    }
}
