///*
// * Copyright (C) 2025 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.android.mechanics.compose.modifier
//
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.geometry.Rect
//import androidx.compose.ui.graphics.CompositingStrategy
//import androidx.compose.ui.layout.ApproachLayoutModifierNode
//import androidx.compose.ui.layout.ApproachMeasureScope
//import androidx.compose.ui.layout.LayoutCoordinates
//import androidx.compose.ui.layout.Measurable
//import androidx.compose.ui.layout.MeasureResult
//import androidx.compose.ui.layout.MeasureScope
//import androidx.compose.ui.layout.Placeable
//import androidx.compose.ui.layout.boundsInParent
//import androidx.compose.ui.node.ModifierNodeElement
//import androidx.compose.ui.platform.InspectorInfo
//import androidx.compose.ui.unit.Constraints
//import androidx.compose.ui.unit.IntOffset
//import androidx.compose.ui.unit.IntSize
//import androidx.compose.ui.util.fastCoerceAtLeast
//import androidx.compose.ui.util.fastCoerceIn
//import com.android.compose.animation.scene.ContentScope
//import com.android.compose.animation.scene.ElementKey
//import com.android.compose.animation.scene.mechanics.gestureContextOrDefault
//import com.android.mechanics.MotionValue
//import com.android.mechanics.debug.findMotionValueDebugger
//import com.android.mechanics.effects.RevealOnThreshold
//import com.android.mechanics.spec.Mapping
//import com.android.mechanics.spec.builder.MotionBuilderContext
//import com.android.mechanics.spec.builder.spatialMotionSpec
//import kotlin.math.roundToInt
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.launch
//
///**
// * This component remains hidden until its target height meets a minimum threshold. At that point,
// * it reveals itself by animating its height from 0 to the current target height.
// *
// * TODO: Once b/413283893 is done, [motionBuilderContext] can be read internally via
// *   CompositionLocalConsumerModifierNode, instead of passing it.
// */
//fun Modifier.verticalTactileSurfaceReveal(
//    contentScope: ContentScope,
//    motionBuilderContext: MotionBuilderContext,
//    container: ElementKey,
//    deltaY: Float = 0f,
//    revealOnThreshold: RevealOnThreshold = DefaultRevealOnThreshold,
//    label: String? = null,
//    debug: Boolean = false,
//): Modifier =
//    this then
//        VerticalTactileSurfaceRevealElement(
//            contentScope = contentScope,
//            motionBuilderContext = motionBuilderContext,
//            container = container,
//            deltaY = deltaY,
//            revealOnThreshold = revealOnThreshold,
//            label = label,
//            debug = debug,
//        )
//
//private val DefaultRevealOnThreshold = RevealOnThreshold()
//
//private data class VerticalTactileSurfaceRevealElement(
//    val contentScope: ContentScope,
//    val motionBuilderContext: MotionBuilderContext,
//    val container: ElementKey,
//    val deltaY: Float,
//    val revealOnThreshold: RevealOnThreshold,
//    val label: String?,
//    val debug: Boolean,
//) : ModifierNodeElement<VerticalTactileSurfaceRevealNode>() {
//    override fun create(): VerticalTactileSurfaceRevealNode =
//        VerticalTactileSurfaceRevealNode(
//            contentScope = contentScope,
//            motionBuilderContext = motionBuilderContext,
//            container = container,
//            deltaY = deltaY,
//            revealOnThreshold = revealOnThreshold,
//            label = label,
//            debug = debug,
//        )
//
//    override fun update(node: VerticalTactileSurfaceRevealNode) {
//        node.update(
//            contentScope = contentScope,
//            motionBuilderContext = motionBuilderContext,
//            container = container,
//            deltaY = deltaY,
//            revealOnThreshold = revealOnThreshold,
//        )
//    }
//
//    override fun InspectorInfo.inspectableProperties() {
//        name = "tactileSurfaceReveal"
//        properties["container"] = container
//        properties["deltaY"] = deltaY
//        properties["revealOnThreshold"] = revealOnThreshold
//        properties["label"] = label
//        properties["debug"] = debug
//    }
//}
//
//private class VerticalTactileSurfaceRevealNode(
//    private var contentScope: ContentScope,
//    private var motionBuilderContext: MotionBuilderContext,
//    private var container: ElementKey,
//    private var deltaY: Float,
//    private var revealOnThreshold: RevealOnThreshold,
//    label: String?,
//    private val debug: Boolean,
//) : Modifier.Node(), ApproachLayoutModifierNode {
//
//    private val motionValue =
//        MotionValue(
//            currentInput = {
//                with(contentScope) {
//                    val containerHeight =
//                        container.lastSize(contentKey)?.height ?: return@MotionValue 0f
//                    val containerCoordinates =
//                        container.targetCoordinates(contentKey) ?: return@MotionValue 0f
//                    val localCoordinates = lastCoordinates ?: return@MotionValue 0f
//
//                    val offsetY = containerCoordinates.localPositionOf(localCoordinates).y
//                    containerHeight - offsetY + deltaY
//                }
//            },
//            gestureContext = contentScope.gestureContextOrDefault(),
//            label = "TactileSurfaceReveal(${label.orEmpty()})",
//            stableThreshold = MotionBuilderContext.StableThresholdSpatial,
//        )
//
//    fun update(
//        contentScope: ContentScope,
//        motionBuilderContext: MotionBuilderContext,
//        container: ElementKey,
//        deltaY: Float,
//        revealOnThreshold: RevealOnThreshold,
//    ) {
//        this.contentScope = contentScope
//        this.motionBuilderContext = motionBuilderContext
//        this.container = container
//        this.deltaY = deltaY
//        this.revealOnThreshold = revealOnThreshold
//        updateMotionSpec()
//    }
//
//    private var motionValueJob: Job? = null
//
//    override fun onAttach() {
//        motionValueJob =
//            coroutineScope.launch {
//                val disposableHandle =
//                    if (debug) {
//                        findMotionValueDebugger()?.register(motionValue)
//                    } else {
//                        null
//                    }
//                try {
//                    motionValue.keepRunning()
//                } finally {
//                    disposableHandle?.dispose()
//                }
//            }
//    }
//
//    override fun onDetach() {
//        motionValueJob?.cancel()
//    }
//
//    private fun isAnimating(): Boolean {
//        return contentScope.layoutState.currentTransition != null || !motionValue.isStable
//    }
//
//    override fun isMeasurementApproachInProgress(lookaheadSize: IntSize) = isAnimating()
//
//    override fun Placeable.PlacementScope.isPlacementApproachInProgress(
//        lookaheadCoordinates: LayoutCoordinates
//    ) = isAnimating()
//
//    private var targetBounds = Rect.Zero
//
//    private var lastCoordinates: LayoutCoordinates? = null
//
//    private fun updateMotionSpec() {
//        motionValue.spec =
//            motionBuilderContext.spatialMotionSpec(Mapping.Zero) {
//                between(
//                    start = targetBounds.top,
//                    end = targetBounds.bottom,
//                    effect = revealOnThreshold,
//                )
//            }
//    }
//
//    override fun MeasureScope.measure(
//        measurable: Measurable,
//        constraints: Constraints,
//    ): MeasureResult {
//        val placeable = measurable.measure(constraints)
//        return layout(placeable.width, placeable.height) {
//            val coordinates = coordinates
//            if (isLookingAhead && coordinates != null) {
//                lastCoordinates = coordinates
//                val bounds = coordinates.boundsInParent()
//                if (targetBounds != bounds) {
//                    targetBounds = bounds
//                    updateMotionSpec()
//                }
//            }
//            placeable.place(IntOffset.Zero)
//        }
//    }
//
//    override fun ApproachMeasureScope.approachMeasure(
//        measurable: Measurable,
//        constraints: Constraints,
//    ): MeasureResult {
//        val height = motionValue.output.roundToInt().fastCoerceAtLeast(0)
//        val animatedConstraints = Constraints.fixed(width = constraints.maxWidth, height = height)
//        return measurable.measure(animatedConstraints).run {
//            layout(width, height) {
//                val revealAlpha = (height / revealOnThreshold.minSize.toPx()).fastCoerceIn(0f, 1f)
//                if (revealAlpha < 1) {
//                    placeWithLayer(IntOffset.Zero) {
//                        alpha = revealAlpha
//                        compositingStrategy = CompositingStrategy.ModulateAlpha
//                    }
//                } else {
//                    place(IntOffset.Zero)
//                }
//            }
//        }
//    }
//}
