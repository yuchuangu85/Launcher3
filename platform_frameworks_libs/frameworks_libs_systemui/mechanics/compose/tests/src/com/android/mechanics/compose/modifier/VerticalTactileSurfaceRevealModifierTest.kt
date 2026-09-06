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

package com.android.mechanics.compose.modifier

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.SwipeDirection
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.featureOfElement
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.mechanics.debug.LocalMotionValueDebugController
import com.android.mechanics.debug.MotionValueDebugController
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import platform.test.motion.MotionTestRule
import platform.test.motion.compose.ComposeFeatureCaptures.height
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.ComposeToolkit
import platform.test.motion.compose.MotionControlScope
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.golden.asDataPoint
import platform.test.motion.testing.createGoldenPathManager

@RunWith(Parameterized::class)
class VerticalTactileSurfaceRevealModifierTest(private val useOverlays: Boolean) :
    MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    @get:Rule
    val motionRule: MotionTestRule<ComposeToolkit> =
        createFixedConfigurationComposeMotionTestRule(
            createGoldenPathManager("frameworks/libs/systemui/mechanics/compose/tests/goldens")
        )

    private val debugger = MotionValueDebugController()

    private fun assertVerticalTactileSurfaceRevealMotion(
        goldenName: String,
        testController: TestController,
    ) =
        motionRule.runTest(timeout = 40.seconds) {
            lateinit var animationScope: CoroutineScope
            lateinit var state: MutableSceneTransitionLayoutState

            val boxes = 8
            val animatedBoxValues = List(boxes) { AnimatedValuesForTests() }

            @Composable
            fun ContentScope.TestContent(modifier: Modifier = Modifier) {
                Box(modifier = modifier.fillMaxSize()) {
                    Column(
                        modifier =
                            Modifier.element(ContainerElement)
                                .motionDriver(contentScope = this@TestContent)
                                .verticalScroll(rememberScrollState())
                                .background(Color.LightGray)
                                .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(boxes) {
                            Box(
                                Modifier.testTag("box$it")
                                    .border(
                                        2.dp,
                                        when (it) {
                                            0 -> Color.Green
                                            boxes - 1 -> Color.Red
                                            else -> Color.Blue
                                        },
                                    )
                                    .verticalTactileSurfaceReveal(
                                        label = "box$it",
                                        animatedValuesForTests = animatedBoxValues[it],
                                    )
                                    .size(50.dp)
                            )
                        }
                    }
                }
            }

            val motion =
                recordMotion(
                    content = {
                        animationScope = rememberCoroutineScope()
                        CompositionLocalProvider(
                            LocalMotionValueDebugController provides debugger
                        ) {
                            state =
                                rememberMutableSceneTransitionLayoutState(
                                    initialScene = testController.startScene,
                                    initialOverlays = testController.startOverlays,
                                    transitions =
                                        transitions {
                                            from(CollapsedScene, to = ExpandedOverlay) {
                                                // Spec for triggered animations.
                                                spec = tween(500)
                                                intrinsicDirection = SwipeDirection.Down
                                                scaleSize(ContainerElement, height = 0f)
                                            }
                                            from(CollapsedScene, to = ExpandedScene) {
                                                // Spec for triggered animations.
                                                spec = tween(500)
                                                intrinsicDirection = SwipeDirection.Down
                                                scaleSize(ContainerElement, height = 0f)
                                            }
                                            from(
                                                CollapsedScene,
                                                to = ExpandedOverlay,
                                                key = NoIntrinsicDirection,
                                            ) {
                                                // Spec for triggered animations.
                                                spec = tween(500)
                                                scaleSize(ContainerElement, height = 0f)
                                            }
                                            from(
                                                CollapsedScene,
                                                to = ExpandedScene,
                                                key = NoIntrinsicDirection,
                                            ) {
                                                // Spec for triggered animations.
                                                spec = tween(500)
                                                scaleSize(ContainerElement, height = 0f)
                                            }
                                        },
                                )
                            SceneTransitionLayout(
                                state = state,
                                modifier =
                                    Modifier.background(Color.Yellow)
                                        .size(ContainerSize)
                                        .testTag(STL_TAG),
                                implicitTestTags = true,
                            ) {
                                scene(
                                    key = CollapsedScene,
                                    userActions =
                                        mapOf(
                                            if (useOverlays) {
                                                Swipe.Down to ExpandedOverlay
                                            } else {
                                                Swipe.Down to ExpandedScene
                                            }
                                        ),
                                    content = { Box(modifier = Modifier.fillMaxSize()) },
                                )
                                if (useOverlays) {
                                    overlay(
                                        ExpandedOverlay,
                                        userActions =
                                            mapOf(
                                                Swipe.Up to
                                                    UserActionResult.HideOverlay(ExpandedOverlay)
                                            ),
                                        content = {
                                            TestContent(Modifier.border(2.dp, Color.Magenta))
                                        },
                                    )
                                } else {
                                    scene(
                                        key = ExpandedScene,
                                        userActions = mapOf(Swipe.Up to CollapsedScene),
                                        content = { TestContent(Modifier.border(2.dp, Color.Cyan)) },
                                    )
                                }
                            }
                        }
                    },
                    ComposeRecordingSpec(
                        recording = {
                            testController.onRecord(
                                RecordScope(
                                    motionScope = this,
                                    stlState = state,
                                    animationScope = animationScope,
                                )
                            )

                            awaitCondition {
                                !state.isTransitioning() && debugger.observed.all { it.isStable }
                            }
                        },
                        timeSeriesCapture = {
                            feature("isTransitioning") { state.isTransitioning().asDataPoint() }
                            featureOfElement(ContainerElement, height)
                            repeat(boxes) { boxId ->
                                val testTag = "box$boxId"
                                on({ animatedBoxValues[boxId] }) {
                                    feature("${testTag}_y-graphic", { it.offsetY.asDataPoint() })
                                    feature("${testTag}_height-graphic") { it.height.asDataPoint() }
                                    feature("${testTag}_radius-graphic") { it.radius.asDataPoint() }
                                }
                            }
                        },
                    ),
                )

            assertThat(motion).timeSeriesMatchesGolden(goldenName)
        }

    @Test
    fun verticalTactileSurfaceReveal_triggered_open() {
        assertVerticalTactileSurfaceRevealMotion(
            // We are using the same golden for scene-to-scene and scene-to-overlay transition.
            goldenName = "verticalTactileSurfaceReveal_triggered_open",
            TestController(
                startScene = CollapsedScene,
                startOverlays = emptySet(),
                onRecord = {
                    motionRule.toolkit.composeContentTestRule.runOnUiThread {
                        if (useOverlays) {
                            stlState.showOverlay(ExpandedOverlay, animationScope)
                        } else {
                            stlState.setTargetScene(ExpandedScene, animationScope)
                        }
                    }
                },
            ),
        )
    }

    @Test
    fun verticalTactileSurfaceReveal_gesture_flingOpen() {
        assertVerticalTactileSurfaceRevealMotion(
            // We are using the same golden for scene-to-scene and scene-to-overlay transition.
            goldenName = "verticalTactileSurfaceReveal_gesture_flingOpen",
            TestController(
                startScene = CollapsedScene,
                startOverlays = emptySet(),
                onRecord = {
                    performTouchInputAsync(onNodeWithTag(STL_TAG)) {
                        val end = Offset(centerX, 80.dp.toPx())
                        swipeWithVelocity(
                            start = topCenter,
                            end = end,
                            endVelocity = FlingVelocity.toPx(),
                        )
                    }
                },
            ),
        )
    }

    @Test
    fun verticalTactileSurfaceReveal_triggered_close() {
        assertVerticalTactileSurfaceRevealMotion(
            // We are using the same golden for scene-to-scene and scene-to-overlay transition.
            goldenName = "verticalTactileSurfaceReveal_triggered_close",
            TestController(
                startScene = if (useOverlays) CollapsedScene else ExpandedScene,
                startOverlays = if (useOverlays) setOf(ExpandedOverlay) else emptySet(),
                onRecord = {
                    motionRule.toolkit.composeContentTestRule.runOnUiThread {
                        if (useOverlays) {
                            stlState.hideOverlay(ExpandedOverlay, animationScope)
                        } else {
                            stlState.setTargetScene(CollapsedScene, animationScope)
                        }
                    }
                },
            ),
        )
    }

    @Test
    fun verticalTactileSurfaceReveal_triggered_close_noIntrinsicDirection() {
        assertFails {
            assertVerticalTactileSurfaceRevealMotion(
                // We are using the same golden for scene-to-scene and scene-to-overlay transition.
                goldenName = "verticalTactileSurfaceReveal_triggered_close",
                TestController(
                    startScene = if (useOverlays) CollapsedScene else ExpandedScene,
                    startOverlays = if (useOverlays) setOf(ExpandedOverlay) else emptySet(),
                    onRecord = {
                        motionRule.toolkit.composeContentTestRule.runOnUiThread {
                            if (useOverlays) {
                                stlState.hideOverlay(
                                    overlay = ExpandedOverlay,
                                    animationScope = animationScope,
                                    transitionKey = NoIntrinsicDirection,
                                )
                            } else {
                                stlState.setTargetScene(
                                    targetScene = CollapsedScene,
                                    animationScope = animationScope,
                                    transitionKey = NoIntrinsicDirection,
                                )
                            }
                        }
                    },
                ),
            )
        }
    }

    @Test
    fun verticalTactileSurfaceReveal_gesture_flingClose() {
        assertVerticalTactileSurfaceRevealMotion(
            // TODO(b/477544904): The goldens for this test do not match both passes.
            //  More investigation is needed.
            goldenName = "verticalTactileSurfaceReveal_gesture_flingClose_overlay_$useOverlays",
            TestController(
                startScene = if (useOverlays) CollapsedScene else ExpandedScene,
                startOverlays = if (useOverlays) setOf(ExpandedOverlay) else emptySet(),
                onRecord = {
                    performTouchInputAsync(onNodeWithTag(STL_TAG)) {
                        val start = Offset(centerX, 260.dp.toPx())
                        val end = Offset(centerX, 200.dp.toPx())
                        swipeWithVelocity(start, end, FlingVelocity.toPx())
                    }
                },
            ),
        )
    }

    private class RecordScope(
        motionScope: MotionControlScope,
        val stlState: MutableSceneTransitionLayoutState,
        val animationScope: CoroutineScope,
    ) : MotionControlScope by motionScope

    private class TestController(
        val startScene: SceneKey,
        val startOverlays: Set<OverlayKey>,
        val onRecord: suspend RecordScope.() -> Unit,
    )

    private companion object {
        const val STL_TAG = "stl"

        val CollapsedScene = SceneKey("CollapsedScene")
        val ExpandedScene = SceneKey("ExpandedScene")
        val ExpandedOverlay = OverlayKey("ExpandedOverlay")
        val ContainerElement = ElementKey("ContainerElement")
        val NoIntrinsicDirection = TransitionKey("NoIntrinsicDirection")

        val ContainerSize = DpSize(150.dp, 300.dp)
        val FlingVelocity = 1000.dp // dp/sec

        @Parameterized.Parameters(name = "useOverlays={0}")
        @JvmStatic
        fun useOverlays() = listOf(false, true)
    }
}
