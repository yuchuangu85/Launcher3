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
//import com.android.compose.animation.scene.ContentScope
//import com.android.compose.animation.scene.ElementKey
//import com.android.compose.animation.scene.mechanics.gestureContextOrDefault
//import com.android.mechanics.MotionValue
//import com.android.mechanics.debug.findMotionValueDebugger
//import com.android.mechanics.effects.FixedValue
//import com.android.mechanics.spec.Mapping
//import com.android.mechanics.spec.builder.MotionBuilderContext
//import com.android.mechanics.spec.builder.effectsMotionSpec
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.launch
//
///**
// * This component remains hidden until it reach its target height.
// *
// * TODO: Once b/413283893 is done, [motionBuilderContext] can be read internally via
// *   CompositionLocalConsumerModifierNode, instead of passing it.
// */
//fun Modifier.verticalFadeContentReveal(
//    contentScope: ContentScope,
//    motionBuilderContext: MotionBuilderContext,
//    container: ElementKey,
//    deltaY: Float = 0f,
//    label: String? = null,
//    debug: Boolean = false,
//): Modifier =
//    this then
//        FadeContentRevealElement(
//            contentScope = contentScope,
//            motionBuilderContext = motionBuilderContext,
//            container = container,
//            deltaY = deltaY,
//            label = label,
//            debug = debug,
//        )
//
//private data class FadeContentRevealElement(
//    val contentScope: ContentScope,
//    val motionBuilderContext: MotionBuilderContext,
//    val container: ElementKey,
//    val deltaY: Float,
//    val label: String?,
//    val debug: Boolean,
//) : ModifierNodeElement<FadeContentRevealNode>() {
//    override fun create(): FadeContentRevealNode =
//        FadeContentRevealNode(
//            contentScope = contentScope,
//            motionBuilderContext = motionBuilderContext,
//            container = container,
//            deltaY = deltaY,
//            label = label,
//            debug = debug,
//        )
//
//    override fun update(node: FadeContentRevealNode) {
//        node.update(
//            contentScope = contentScope,
//            motionBuilderContext = motionBuilderContext,
//            container = container,
//            deltaY = deltaY,
//        )
//    }
//
//    override fun InspectorInfo.inspectableProperties() {
//        name = "fadeContentReveal"
//        properties["container"] = container
//        properties["deltaY"] = deltaY
//        properties["label"] = label
//        properties["debug"] = debug
//    }
//}
//
//internal class FadeContentRevealNode(
//    private var contentScope: ContentScope,
//    private var motionBuilderContext: MotionBuilderContext,
//    private var container: ElementKey,
//    private var deltaY: Float,
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
//            label = "FadeContentReveal(${label.orEmpty()})",
//        )
//
//    fun update(
//        contentScope: ContentScope,
//        motionBuilderContext: MotionBuilderContext,
//        container: ElementKey,
//        deltaY: Float,
//    ) {
//        this.contentScope = contentScope
//        this.motionBuilderContext = motionBuilderContext
//        this.container = container
//        this.deltaY = deltaY
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
//            motionBuilderContext.effectsMotionSpec(Mapping.Zero) {
//                after(targetBounds.bottom, FixedValue.One)
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
//        return measurable.measure(constraints).run {
//            layout(width, height) {
//                val revealAlpha = motionValue.output
//                if (revealAlpha < 1) {
//                    placeWithLayer(IntOffset.Zero) {
//                        alpha = revealAlpha.fastCoerceAtLeast(0f)
//                        compositingStrategy = CompositingStrategy.ModulateAlpha
//                    }
//                } else {
//                    place(IntOffset.Zero)
//                }
//            }
//        }
//    }
//}
