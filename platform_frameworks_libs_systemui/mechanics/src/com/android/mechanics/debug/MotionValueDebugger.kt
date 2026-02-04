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

package com.android.mechanics.debug

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.InspectorInfo
import com.android.mechanics.MotionValue
import com.android.mechanics.debug.MotionValueDebuggerNode.Companion.TRAVERSAL_NODE_KEY
import kotlinx.coroutines.DisposableHandle

/** State for the [MotionValueDebugger]. */
sealed interface MotionValueDebuggerState {
    val observedMotionValues: List<MotionValue>
}

/** Factory for [MotionValueDebugger]. */
fun MotionValueDebuggerState(): MotionValueDebuggerState {
    return MotionValueDebuggerStateImpl()
}

/** Collector for [MotionValue]s in the Node subtree that should be observed for debug purposes. */
fun Modifier.motionValueDebugger(state: MotionValueDebuggerState): Modifier =
    this.then(MotionValueDebuggerElement(state as MotionValueDebuggerStateImpl))

/**
 * [motionValueDebugger]'s interface, nodes in the subtree of a [motionValueDebugger] can retrieve
 * it using [findMotionValueDebugger].
 */
sealed interface MotionValueDebugger {
    fun register(motionValue: MotionValue): DisposableHandle
}

/** Finds a [MotionValueDebugger] that was registered via a [motionValueDebugger] modifier. */
fun DelegatableNode.findMotionValueDebugger(): MotionValueDebugger? {
    return findNearestAncestor(TRAVERSAL_NODE_KEY) as? MotionValueDebugger
}

/** Registers the motion value for debugging with the parent [MotionValue]. */
fun Modifier.debugMotionValue(motionValue: MotionValue): Modifier =
    this.then(DebugMotionValueElement(motionValue))

internal class MotionValueDebuggerNode(internal var state: MotionValueDebuggerStateImpl) :
    Modifier.Node(), TraversableNode, MotionValueDebugger {

    override val traverseKey = TRAVERSAL_NODE_KEY

    override fun register(motionValue: MotionValue): DisposableHandle {
        val state = state
        state.observedMotionValues.add(motionValue)
        return DisposableHandle { state.observedMotionValues.remove(motionValue) }
    }

    companion object {
        const val TRAVERSAL_NODE_KEY = "com.android.mechanics.debug.DEBUG_CONNECTOR_NODE_KEY"
    }
}

private data class MotionValueDebuggerElement(val state: MotionValueDebuggerStateImpl) :
    ModifierNodeElement<MotionValueDebuggerNode>() {
    override fun create(): MotionValueDebuggerNode = MotionValueDebuggerNode(state)

    override fun InspectorInfo.inspectableProperties() {
        // Intentionally empty
    }

    override fun update(node: MotionValueDebuggerNode) {
        check(node.state === state)
    }
}

internal class DebugMotionValueNode(motionValue: MotionValue) : Modifier.Node() {

    private var debugger: MotionValueDebugger? = null

    internal var motionValue = motionValue
        set(value) {
            registration?.dispose()
            registration = debugger?.register(value)
            field = value
        }

    internal var registration: DisposableHandle? = null

    override fun onAttach() {
        debugger = findMotionValueDebugger()
        registration = debugger?.register(motionValue)
    }

    override fun onDetach() {
        debugger = null
        registration?.dispose()
        registration = null
    }
}

private data class DebugMotionValueElement(val motionValue: MotionValue) :
    ModifierNodeElement<DebugMotionValueNode>() {
    override fun create(): DebugMotionValueNode = DebugMotionValueNode(motionValue)

    override fun InspectorInfo.inspectableProperties() {
        // Intentionally empty
    }

    override fun update(node: DebugMotionValueNode) {
        node.motionValue = motionValue
    }
}

internal class MotionValueDebuggerStateImpl : MotionValueDebuggerState {
    override val observedMotionValues: MutableList<MotionValue> = mutableStateListOf()
}
