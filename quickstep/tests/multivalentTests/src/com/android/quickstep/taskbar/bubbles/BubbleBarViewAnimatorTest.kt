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

package com.android.quickstep.taskbar.bubbles

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.animation.AnimatorTestRule
import androidx.core.graphics.drawable.toBitmap
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.taskbar.TaskbarInsetsController
import com.android.launcher3.taskbar.bubbles.BubbleBarBubble
import com.android.launcher3.taskbar.bubbles.BubbleBarOverflow
import com.android.launcher3.taskbar.bubbles.BubbleBarParentViewHeightUpdateNotifier
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController
import com.android.launcher3.taskbar.bubbles.BubbleView
import com.android.launcher3.taskbar.bubbles.animation.BubbleBarViewAnimator
import com.android.launcher3.taskbar.bubbles.flyout.BubbleBarFlyoutController
import com.android.launcher3.taskbar.bubbles.flyout.BubbleBarFlyoutMessage
import com.android.launcher3.taskbar.bubbles.flyout.BubbleBarFlyoutPositioner
import com.android.launcher3.taskbar.bubbles.flyout.FlyoutCallbacks
import com.android.launcher3.taskbar.bubbles.flyout.FlyoutScheduler
import com.android.launcher3.taskbar.bubbles.model.BubbleIcon
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.BubbleLauncherState
import com.android.users.UserType
import com.android.wm.shell.Flags
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.shared.animation.PhysicsAnimatorTestUtils
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.BubbleInfo
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@SkipOnDeviceless
class BubbleBarViewAnimatorTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val animatorTestRule = AnimatorTestRule()
    @get:Rule var limitDevicesRule: LimitDevicesRule = LimitDevicesRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var animatorScheduler: TestBubbleBarViewAnimatorScheduler
    private lateinit var bubbleBarParentViewController: TestBubbleBarParentViewHeightUpdateNotifier
    private lateinit var overflowView: BubbleView
    private lateinit var bubbleView: BubbleView
    private lateinit var bubble: BubbleBarBubble
    private lateinit var bubbleBarView: BubbleBarView
    private lateinit var flyoutContainer: FrameLayout
    private lateinit var bubbleStashController: FakeBubbleStashController
    private lateinit var flyoutController: BubbleBarFlyoutController
    private val emptyRunnable = Runnable {}

    private val flyoutView: View?
        get() = flyoutContainer.findViewById(R.id.bubble_bar_flyout_view)

    private var animationEnded = false
    private val onAnimationEnded = { animationEnded = true }

    @Before
    fun setUp() {
        animatorScheduler = TestBubbleBarViewAnimatorScheduler()
        bubbleBarParentViewController = TestBubbleBarParentViewHeightUpdateNotifier()
        bubbleStashController = FakeBubbleStashController()
        PhysicsAnimatorTestUtils.prepareForTest()
        setupFlyoutController()
    }

    @Test
    fun animateBubbleInForStashed() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        // execute the hide bubble animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(handle.alpha).isEqualTo(1)
        assertThat(handle.translationY).isEqualTo(0)
        assertThat(bubbleBarView.alpha).isEqualTo(0)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleStashController.isStashed).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleInForStashed_tapAnimatingBubble() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isTrue()

        assertThat(bubbleStashController.taskbarTouchRegionUpdated).isTrue()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync { animator.interruptForTouch() }

        waitForFlyoutToHide()

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(animatorScheduler.delayedBlock).isNull()
        assertThat(bubbleBarView.alpha).isEqualTo(1)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isFalse()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleInForStashed_touchTaskbarArea_whileShowing() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // wait for the animation to start
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(handleAnimator) { true }

        handleAnimator.assertIsRunning()
        assertThat(animator.isAnimating).isTrue()
        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.onStashStateChangingWhileAnimating()
        }

        // wait for the animation to cancel
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
            handleAnimator,
            DynamicAnimation.TRANSLATION_Y,
        )

        // verify that the hide animation was canceled
        assertThat(animatorScheduler.delayedBlock).isNull()
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleStashController.animationInterrupted).isTrue()
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleStashController.isStashed).isTrue()

        // PhysicsAnimatorTestUtils posts the cancellation to the main thread so we need to wait
        // again
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        handleAnimator.assertIsNotRunning()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleInForStashed_touchTaskbarArea_whileHiding() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        // execute the hide bubble animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        // wait for the hide animation to start
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        handleAnimator.assertIsRunning()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.onStashStateChangingWhileAnimating()
        }
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleStashController.animationInterrupted).isTrue()

        // PhysicsAnimatorTestUtils posts the cancellation to the main thread so we need to wait
        // again
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        handleAnimator.assertIsNotRunning()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleInForStashed_autoExpanding() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        var notifiedExpanded = false
        val onExpanded = Runnable { notifiedExpanded = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = true)
        }

        // wait for the animation to start
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarView.isExpanded).isTrue()

        // verify there is no hide animation
        assertThat(animatorScheduler.delayedBlock).isNull()

        assertThat(bubbleStashController.isStashed).isFalse()
        assertThat(notifiedExpanded).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleInForStashed_expandedWhileAnimatingIn() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        var notifiedExpanded = false
        val onExpanded = Runnable { notifiedExpanded = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // wait for the animation to start
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(handleAnimator) { true }

        handleAnimator.assertIsRunning()
        assertThat(animator.isAnimating).isTrue()
        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.expandedWhileAnimating()
        }

        // let the animation finish
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        // verify that the hide animation was canceled
        assertThat(animatorScheduler.delayedBlock).isNull()

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        verifyBubbleBarIsExpandedWithTranslation(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isFalse()
        assertThat(notifiedExpanded).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleInForStashed_expandedWhileFullyIn() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        var notifiedExpanded = false
        val onExpanded = Runnable { notifiedExpanded = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // wait for the animation to start
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        // wait for the animation to end
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.expandedWhileAnimating()
        }

        // verify that the hide animation was canceled
        assertThat(animatorScheduler.delayedBlock).isNull()

        waitForFlyoutToHide()

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        verifyBubbleBarIsExpandedWithTranslation(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isFalse()
        assertThat(notifiedExpanded).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateToInitialState_inApp() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.IN_APP

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        var notifiedBubbleBarVisible = false
        val onBubbleBarVisible = Runnable { notifiedBubbleBarVisible = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = onBubbleBarVisible,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleBarView.visibility = INVISIBLE
            animator.animateToInitialState(bubble, isInApp = true, isExpanding = false)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        barAnimator.assertIsNotRunning()
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarView.alpha).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarView.alpha).isEqualTo(0)
        assertThat(handle.translationY).isEqualTo(0)
        assertThat(handle.alpha).isEqualTo(1)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(notifiedBubbleBarVisible).isTrue()
        assertThat(bubbleStashController.isStashed).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_DISMISS_FLYOUT_PERSISTENT_TASKBAR)
    fun animateToInitialState_persistentTaskbar_inApp() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.IN_APP
        bubbleStashController.isTransientTaskBar = false

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        var notifiedBubbleBarVisible = false
        val onBubbleBarVisible = Runnable { notifiedBubbleBarVisible = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = onBubbleBarVisible,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleBarView.visibility = INVISIBLE
            animator.animateToInitialState(bubble, isInApp = true, isExpanding = false)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        barAnimator.assertIsNotRunning()
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarView.alpha).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(notifiedBubbleBarVisible).isTrue()
        assertThat(bubbleStashController.isStashed).isFalse()
        assertThat(animationEnded).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_BUBBLE_BAR_VISIBILITY_UPDATE_DROPPED)
    fun animateToInitialState_cancelledByNextBubble() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.IN_APP

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        var notifiedBubbleBarVisible = false
        val onBubbleBarVisible = Runnable { notifiedBubbleBarVisible = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = onBubbleBarVisible,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleBarView.visibility = INVISIBLE
            animator.animateToInitialState(bubble, isInApp = true, isExpanding = false)
            animator.animateBubbleBarForCollapsed(bubble, isExpanding = false)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        // Verify that the bubble bar is visible at the end
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(notifiedBubbleBarVisible).isTrue()
    }

    @Test
    fun animateToInitialState_whileDragging_inApp() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.IN_APP

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        var notifiedBubbleBarVisible = false
        val onBubbleBarVisible = Runnable { notifiedBubbleBarVisible = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = onBubbleBarVisible,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleBarView.visibility = INVISIBLE
            animator.animateToInitialState(
                bubble,
                isInApp = true,
                isExpanding = false,
                isDragging = true,
            )
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        barAnimator.assertIsNotRunning()
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarView.alpha).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarView.alpha).isEqualTo(1)
        assertThat(handle.translationY).isEqualTo(0)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(notifiedBubbleBarVisible).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateToInitialState_inApp_autoExpanding() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.IN_APP

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        var notifiedExpanded = false
        val onExpanded = Runnable { notifiedExpanded = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateToInitialState(bubble, isInApp = true, isExpanding = true)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        barAnimator.assertIsNotRunning()
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarView.alpha).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)

        assertThat(animatorScheduler.delayedBlock).isNull()
        assertThat(bubbleStashController.isStashed).isFalse()
        assertThat(notifiedExpanded).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateToInitialState_inHome() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateToInitialState(bubble, isInApp = false, isExpanding = false)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        barAnimator.assertIsNotRunning()
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarView.alpha).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarView.alpha).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(bubbleStashController.isStashed).isFalse()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateToInitialState_expandedWhileAnimatingIn() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME

        var notifiedExpanded = false
        val onExpanded = Runnable { notifiedExpanded = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateToInitialState(bubble, isInApp = false, isExpanding = false)
        }

        val bubbleBarAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        // wait for the animation to start
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(bubbleBarAnimator) { true }

        bubbleBarAnimator.assertIsRunning()
        assertThat(animator.isAnimating).isTrue()
        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.expandedWhileAnimating()
        }

        // let the animation finish
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        // verify that the hide animation was canceled
        assertThat(animatorScheduler.delayedBlock).isNull()

        verifyBubbleBarIsExpandedWithTranslation(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleStashController.isStashed).isFalse()
        assertThat(notifiedExpanded).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateToInitialState_expandedWhileFullyIn() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME

        var notifiedExpanded = false
        val onExpanded = Runnable { notifiedExpanded = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateToInitialState(bubble, isInApp = false, isExpanding = false)
        }

        // wait for the animation to start
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        assertThat(animator.isAnimating).isTrue()
        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.expandedWhileAnimating()
        }

        waitForFlyoutToHide()

        // verify that the hide animation was canceled
        assertThat(animatorScheduler.delayedBlock).isNull()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)

        verifyBubbleBarIsExpandedWithTranslation(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(animator.isAnimating).isFalse()
        assertThat(notifiedExpanded).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleBarForCollapsed() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleBarForCollapsed(bubble, isExpanding = false)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        // verify we started animating
        assertThat(animator.isAnimating).isTrue()

        // advance the animation handler by the duration of the initial lift
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(250)
        }

        // the lift animation is complete; the spring back animation should start now
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        barAnimator.assertIsRunning()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        // the bubble bar translation y should be back to its initial value
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(bubbleStashController.isStashed).isFalse()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleBarForCollapsed_interruptForIme() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleBarForCollapsed(bubble, isExpanding = true)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        // verify we started animating
        assertThat(animator.isAnimating).isTrue()
        // advance the animation handler by the duration of the initial lift
        InstrumentationRegistry.getInstrumentation().runOnMainSync { animator.interruptForIme() }

        // verify that the show flyout was canceled
        assertThat(animatorScheduler.pausedBlock).isNull()
        // verify no longer animating
        assertThat(animator.isAnimating).isFalse()

        // PhysicsAnimatorTestUtils posts the cancellation to the main thread so we need to wait
        // again
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        barAnimator.assertIsNotRunning()
        // Check bubble bar translation Y is restored and bubble bar expanded
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(bubbleBarView.isExpanded).isTrue()
    }

    @Test
    fun animateBubbleBarForCollapsed_autoExpanding() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME

        val semaphore = Semaphore(0)
        var notifiedExpanded = false
        val onExpanded = Runnable {
            notifiedExpanded = true
            semaphore.release()
        }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleBarForCollapsed(bubble, isExpanding = true)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        // verify we started animating
        assertThat(animator.isAnimating).isTrue()

        // advance the animation handler by the duration of the initial lift
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(250)
        }

        // the lift animation is complete; the spring back animation should start now
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        // we should be expanded now
        assertThat(bubbleBarView.isExpanded).isTrue()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        // verify there is no hide animation
        assertThat(animatorScheduler.delayedBlock).isNull()

        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(bubbleStashController.isStashed).isFalse()
        assertThat(notifiedExpanded).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleBarForCollapsed_expandingWhileAnimatingIn() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME

        val semaphore = Semaphore(0)
        var notifiedExpanded = false
        val onExpanded = Runnable {
            notifiedExpanded = true
            semaphore.release()
        }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleBarForCollapsed(bubble, isExpanding = false)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        // verify we started animating
        assertThat(animator.isAnimating).isTrue()

        // advance the animation handler by the duration of the initial lift
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(100)
        }

        // verify there is a pending hide animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        assertThat(animator.isAnimating).isTrue()

        // send the expand signal in the middle of the lift animation
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.expandedWhileAnimating()
        }

        // let the lift animation complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(150)
        }

        // the lift animation is complete; the spring back animation should start now. wait for it
        // to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        // verify that the hide animation was canceled
        assertThat(animatorScheduler.delayedBlock).isNull()

        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(bubbleBarView.isExpanded).isTrue()
        assertThat(bubbleStashController.isStashed).isFalse()
        assertThat(notifiedExpanded).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleBarForCollapsed_expandingWhileFullyIn() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        var notifiedExpanded = false
        val onExpanded = Runnable { notifiedExpanded = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleBarForCollapsed(bubble, isExpanding = false)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        // verify we started animating
        assertThat(animator.isAnimating).isTrue()

        // advance the animation handler by the duration of the initial lift
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(250)
        }

        // the lift animation is complete; the spring back animation should start now
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        barAnimator.assertIsRunning()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        // verify there is a pending hide animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.expandedWhileAnimating()
        }

        // verify that the hide animation was canceled
        assertThat(animatorScheduler.delayedBlock).isNull()

        waitForFlyoutToHide()

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(bubbleBarView.isExpanded).isTrue()
        assertThat(bubbleStashController.isStashed).isFalse()
        assertThat(notifiedExpanded).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun interruptAnimation_whileAnimatingIn() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // let the animation start and wait until the first frame
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(handleAnimator) { true }

        handleAnimator.assertIsRunning()
        assertThat(animator.isAnimating).isTrue()

        val updatedBubble =
            bubble.copy(flyoutMessage = bubble.flyoutMessage!!.copy(message = "updated message"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleView.setBubble(updatedBubble)
            animator.animateBubbleInForStashed(updatedBubble, isExpanding = false)
        }

        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()
        assertThat(flyoutView!!.findViewById<TextView>(R.id.bubble_flyout_text).text)
            .isEqualTo("updated message")

        // run the hide animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(handle.alpha).isEqualTo(1)
        assertThat(handle.translationY).isEqualTo(0)
        assertThat(bubbleBarView.alpha).isEqualTo(0)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleStashController.isStashed).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun interruptAnimation_whileIn() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        assertThat(flyoutView!!.findViewById<TextView>(R.id.bubble_flyout_text).text)
            .isEqualTo("message")

        // verify the hide animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        val updatedBubble =
            bubble.copy(flyoutMessage = bubble.flyoutMessage!!.copy(message = "updated message"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleView.setBubble(updatedBubble)
            animator.animateBubbleInForStashed(updatedBubble, isExpanding = false)
        }

        // verify the hide animation was rescheduled
        assertThat(animatorScheduler.canceledBlock).isNotNull()
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        waitForFlyoutToFadeOutAndBackIn()

        assertThat(flyoutView!!.findViewById<TextView>(R.id.bubble_flyout_text).text)
            .isEqualTo("updated message")

        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(handle.alpha).isEqualTo(1)
        assertThat(handle.translationY).isEqualTo(0)
        assertThat(bubbleBarView.alpha).isEqualTo(0)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleStashController.isStashed).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun interruptAnimation_whileAnimatingOut_whileCollapsingFlyout() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        assertThat(flyoutView!!.findViewById<TextView>(R.id.bubble_flyout_text).text)
            .isEqualTo("message")

        // run the hide animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        // interrupt the animation while the flyout is collapsing
        val updatedBubble =
            bubble.copy(flyoutMessage = bubble.flyoutMessage!!.copy(message = "updated message"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(100)
            bubbleView.setBubble(updatedBubble)
            animator.animateBubbleInForStashed(updatedBubble, isExpanding = false)

            // the flyout should now reverse and expand
            animatorTestRule.advanceTimeBy(400)
        }
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)

        assertThat(flyoutView!!.findViewById<TextView>(R.id.bubble_flyout_text).text)
            .isEqualTo("updated message")

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)

        // verify the hide animation was rescheduled and run it
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(3)
        assertThat(handle.alpha).isEqualTo(1)
        assertThat(handle.translationY).isEqualTo(0)
        assertThat(bubbleBarView.alpha).isEqualTo(0)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleStashController.isStashed).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun interruptAnimation_whileAnimatingOut_barToHandle() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(animator.isAnimating).isTrue()
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)
        waitForFlyoutToShow()

        assertThat(flyoutView!!.findViewById<TextView>(R.id.bubble_flyout_text).text)
            .isEqualTo("message")

        // run the hide animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        // interrupt the animation while the bar is animating to the handle
        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(handleAnimator) {
            bubbleBarView.alpha < 0.5
        }
        // we're about to interrupt the animation which will cancel the current animation and start
        // a new one. pause the scheduler to delay starting the new animation. this allows us to run
        // the test deterministically
        animatorScheduler.pauseScheduler = true

        val updatedBubble =
            bubble.copy(flyoutMessage = bubble.flyoutMessage!!.copy(message = "updated message"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleView.setBubble(updatedBubble)
            animator.animateBubbleInForStashed(updatedBubble, isExpanding = false)
        }

        // since animation was interrupted there shouldn't be additional calls to adjust window
        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)
        // verify there's a new job scheduled and start it. this is starting the animation from the
        // handle back to the bar
        assertThat(animatorScheduler.pausedBlock).isNotNull()
        animatorScheduler.pauseScheduler = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.pausedBlock!!)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)
        waitForFlyoutToShow()

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(2)
        assertThat(flyoutView!!.findViewById<TextView>(R.id.bubble_flyout_text).text)
            .isEqualTo("updated message")
        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)

        // run the hide animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        // verify the hide animation was rescheduled and run it
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        waitForFlyoutToHide()

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(bubbleBarParentViewController.timesInvoked).isEqualTo(3)
        assertThat(handle.alpha).isEqualTo(1)
        assertThat(handle.translationY).isEqualTo(0)
        assertThat(bubbleBarView.alpha).isEqualTo(0)
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleStashController.isStashed).isTrue()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun animateBubbleInForStashed_interruptForIme() {
        setUpBubbleBar()

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        bubbleStashController.handleAnimator = handleAnimator

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = emptyRunnable,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble, isExpanding = false)
        }

        // wait for the animation to start
        InstrumentationRegistry.getInstrumentation().runOnMainSync {}
        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(handleAnimator) { true }

        handleAnimator.assertIsRunning()
        assertThat(animator.isAnimating).isTrue()
        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync { animator.interruptForIme() }

        // verify that the hide animation was canceled
        assertThat(animatorScheduler.delayedBlock).isNull()
        assertThat(animator.isAnimating).isFalse()
        assertThat(bubbleStashController.animationInterrupted).isTrue()
        assertThat(bubbleStashController.isStashed).isTrue()

        // PhysicsAnimatorTestUtils posts the cancellation to the main thread so we need to wait
        // again
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        handleAnimator.assertIsNotRunning()
        assertThat(animationEnded).isTrue()
    }

    @Test
    fun collapseWhileAnimating_onHome() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        var bubbleBarExpanded = false
        val onExpanded = { bubbleBarExpanded = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateToInitialState(bubble, isInApp = false, isExpanding = true)
        }

        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(barAnimator) { true }

        barAnimator.assertIsRunning()
        assertThat(animator.isAnimating).isTrue()
        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.collapsedWhileAnimating()
        }

        barAnimator.assertIsNotRunning()
        assertThat(animator.isAnimating).isFalse()
        assertThat(animatorScheduler.delayedBlock).isNull()
        // wait for the bubble bar translation animation
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_HOTSEAT)
        assertThat(bubbleBarExpanded).isFalse()
        assertThat(bubbleBarView.isExpanded).isFalse()
        assertThat(animationEnded).isTrue()
    }

    @DisableFlags(Flags.FLAG_FIX_BUBBLE_BAR_NOT_COLLAPSED_IF_ANIMATING_BUBBLE)
    @Test
    fun collapseWhileAnimating_onHomeWhenFullin_barNotCollapsed() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME
        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = {},
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateToInitialState(bubble, isInApp = false, isExpanding = false)
            bubbleBarView.isExpanded = true
        }
        assertThat(bubbleBarView.isExpanded).isTrue()

        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(barAnimator) { true }
        barAnimator.assertIsRunning()
        assertThat(animator.isAnimating).isTrue()
        // wait until spring animation is completed.
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)
        // wait until flyout view is set up
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(450)
        }
        // now the bubble bar view animator should be in the "full in" state

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // send signal to collapse the bubble bar
            animator.collapsedWhileAnimating()
            // wait for bubble bar collapse animation to finish
            animatorTestRule.advanceTimeBy(250)
        }
        // verify bubble bar was not collapsed
        assertThat(bubbleBarView.isExpanded).isTrue()
    }

    @EnableFlags(Flags.FLAG_FIX_BUBBLE_BAR_NOT_COLLAPSED_IF_ANIMATING_BUBBLE)
    @Test
    fun collapseWhileAnimating_onHomeWhenFullin_barCollapsed() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.HOME
        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = {},
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateToInitialState(bubble, isInApp = false, isExpanding = false)
            bubbleBarView.isExpanded = true
        }
        assertThat(bubbleBarView.isExpanded).isTrue()
        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(barAnimator) { true }
        barAnimator.assertIsRunning()
        assertThat(animator.isAnimating).isTrue()
        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        // wait until spring animation is completed.
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)
        // wait until flyout view is set up
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(450)
        }
        // now the bubble bar view animator should be in the "full in" state
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // send signal to collapse the bubble bar
            animator.collapsedWhileAnimating()
            // wait for bubble bar collapse animation to finish and flyout to hide
            animatorTestRule.advanceTimeBy(250)
        }
        // verify bubble bar was collapsed
        assertThat(bubbleBarView.isExpanded).isFalse()
        // verify the hide bubble animation was canceled
        assertThat(animatorScheduler.delayedBlock).isNull()
        // verify flyout is hidden
        waitForFlyoutToHide()
    }

    @Test
    fun collapseWhileAnimating_inApp() {
        setUpBubbleBar()
        bubbleStashController.launcherState = BubbleLauncherState.IN_APP

        val barAnimator = PhysicsAnimator.getInstance(bubbleBarView)

        var bubbleBarExpanded = false
        val onExpanded = { bubbleBarExpanded = true }
        val animator =
            BubbleBarViewAnimator(
                bubbleBarView,
                bubbleStashController,
                flyoutController,
                bubbleBarParentViewController,
                onExpanded = onExpanded,
                onBubbleBarVisible = emptyRunnable,
                onAnimationEnded = onAnimationEnded,
                animatorScheduler,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateToInitialState(bubble, isInApp = true, isExpanding = true)
        }

        PhysicsAnimatorTestUtils.blockUntilFirstAnimationFrameWhereTrue(barAnimator) { true }

        barAnimator.assertIsRunning()
        assertThat(animator.isAnimating).isTrue()
        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.collapsedWhileAnimating()
        }

        barAnimator.assertIsNotRunning()
        assertThat(animator.isAnimating).isFalse()
        assertThat(animatorScheduler.delayedBlock).isNull()
        assertThat(bubbleStashController.isStashed).isTrue()
        assertThat(bubbleBarExpanded).isFalse()
        assertThat(bubbleBarView.isExpanded).isFalse()
        assertThat(animationEnded).isTrue()
    }

    private fun setUpBubbleBar() {
        bubbleBarView = BubbleBarView(context)
        bubbleBarView.setController(
            object : BubbleBarView.Controller {
                override fun getBubbleBarTranslationY(): Float = 0f

                override fun onBubbleBarTouched() {}

                override fun expandBubbleBar() {}

                override fun dismissBubbleBar() {}

                override fun updateBubbleBarLocation(location: BubbleBarLocation?, source: Int) {}

                override fun setIsDragging(dragging: Boolean) {}

                override fun onBubbleBarExpandedStateChanged(expanded: Boolean) {}

                override fun onMarginUpdated() {}
            }
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleBarView.layoutParams = FrameLayout.LayoutParams(0, 0)
            val inflater = LayoutInflater.from(context)

            val bitmap = ColorDrawable(Color.WHITE).toBitmap(width = 20, height = 20)
            overflowView =
                inflater.inflate(R.layout.bubblebar_item_view, bubbleBarView, false) as BubbleView
            overflowView.setOverflow(BubbleBarOverflow(overflowView), bitmap)
            bubbleBarView.addView(overflowView)

            val bubbleInfo =
                BubbleInfo(
                    "key",
                    0,
                    null,
                    null,
                    0,
                    context.packageName,
                    null,
                    null,
                    false,
                    null,
                    false,
                    false,
                    UserType.MAIN,
                )
            bubbleView =
                inflater.inflate(R.layout.bubblebar_item_view, bubbleBarView, false) as BubbleView
            bubble =
                BubbleBarBubble(
                    bubbleInfo,
                    bubbleView,
                    BitmapInfo.of(bitmap, Color.WHITE),
                    BubbleIcon.Custom(bitmap),
                    Color.WHITE,
                    "",
                    BubbleBarFlyoutMessage(icon = null, title = "title", message = "message"),
                )
            bubbleView.setBubble(bubble)
            bubbleBarView.addView(bubbleView)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun setupFlyoutController() {
        flyoutContainer = FrameLayout(context)
        val flyoutPositioner =
            object : BubbleBarFlyoutPositioner {
                override val isOnLeft = true
                override val targetTy = 100f
                override val distanceToCollapsedPosition = PointF(0f, 0f)
                override val collapsedSize = 30f
                override val collapsedColor = Color.BLUE
                override val collapsedElevation = 1f
                override val distanceToRevealTriangle = 10f
                override val horizontalMargin = 20
            }
        val flyoutCallbacks =
            object : FlyoutCallbacks {
                override fun flyoutClicked() {}
            }
        val flyoutScheduler = FlyoutScheduler { block -> block.invoke() }
        flyoutController =
            BubbleBarFlyoutController(
                flyoutContainer,
                flyoutPositioner,
                flyoutCallbacks,
                flyoutScheduler,
            )
    }

    private fun verifyBubbleBarIsExpandedWithTranslation(ty: Float) {
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(ty)
        assertThat(bubbleBarView.isExpanded).isTrue()
    }

    private fun waitForFlyoutToShow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(400)
        }
        assertThat(flyoutView).isNotNull()
    }

    private fun waitForFlyoutToHide() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(350)
        }
        assertThat(flyoutView).isNull()
    }

    private fun waitForFlyoutToFadeOutAndBackIn() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(750)
        }
        assertThat(flyoutView).isNotNull()
    }

    private fun <T> PhysicsAnimator<T>.assertIsRunning() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertThat(isRunning()).isTrue()
        }
    }

    private fun <T> PhysicsAnimator<T>.assertIsNotRunning() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertThat(isRunning()).isFalse()
        }
    }

    private class TestBubbleBarViewAnimatorScheduler : BubbleBarViewAnimator.Scheduler {

        var pauseScheduler = false
        var pausedBlock: Runnable? = null
            private set

        var delayedBlock: Runnable? = null
            private set

        var canceledBlock: Runnable? = null
            private set

        override fun post(block: Runnable) {
            if (pauseScheduler) {
                pausedBlock = block
                return
            }
            block.run()
        }

        override fun postDelayed(delayMillis: Long, block: Runnable) {
            delayedBlock = block
        }

        override fun cancel(block: Runnable) {
            canceledBlock = delayedBlock
            delayedBlock = null
        }
    }

    private class TestBubbleBarParentViewHeightUpdateNotifier :
        BubbleBarParentViewHeightUpdateNotifier {

        var timesInvoked: Int = 0

        override fun updateTopBoundary() {
            timesInvoked++
        }
    }

    private class FakeBubbleStashController : BubbleStashController {

        var handleAnimator: PhysicsAnimator<View>? = null
        var taskbarTouchRegionUpdated = false
            private set

        var animationInterrupted = false
            private set

        private var _isStashed = true

        override var launcherState = BubbleLauncherState.HOME
        override val isStashed: Boolean
            get() = _isStashed

        override val isStashingAllowed: Boolean
            get() = isTransientTaskBar && launcherState == BubbleLauncherState.IN_APP

        override var bubbleBarVerticalCenterForHome = 0
        override var isSysuiLocked = false
        override var isTransientTaskBar = true
        override val hasHandleView: Boolean
            get() = isTransientTaskBar

        override val bubbleBarTranslationYForTaskbar = BAR_TRANSLATION_Y_FOR_TASKBAR
        override val bubbleBarTranslationYForHotseat = BAR_TRANSLATION_Y_FOR_HOTSEAT
        override var inAppDisplayOverrideProgress = 0f

        override fun init(
            taskbarInsetsController: TaskbarInsetsController,
            bubbleBarViewController: BubbleBarViewController,
            bubbleStashedHandleViewController: BubbleStashedHandleViewController?,
            controllersAfterInitAction: BubbleStashController.ControllersAfterInitAction,
        ) {}

        override fun showBubbleBarImmediate() {
            _isStashed = false
        }

        override fun showBubbleBarImmediate(bubbleBarTranslationY: Float) {
            _isStashed = false
        }

        override fun stashBubbleBarImmediate() {
            _isStashed = true
        }

        override fun getTouchableHeight() = 100

        override fun isBubbleBarVisible() = true

        override fun onNewBubbleAnimationInterrupted(
            isStashed: Boolean,
            bubbleBarTranslationY: Float,
        ) {
            _isStashed = isStashed
            animationInterrupted = true
        }

        override fun isEventOverBubbleBarViews(ev: MotionEvent) = false

        override fun setBubbleBarLocation(bubbleBarLocation: BubbleBarLocation) {}

        override fun stashBubbleBar() {
            _isStashed = true
        }

        override fun showBubbleBar(expandBubbles: Boolean, bubbleBarGesture: Boolean) {
            _isStashed = false
        }

        override fun getDiffBetweenHandleAndBarCenters() = DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS

        override fun getStashedHandleTranslationForNewBubbleAnimation() = HANDLE_TRANSLATION

        override fun getStashedHandlePhysicsAnimator(): PhysicsAnimator<View>? {
            return if (hasHandleView) handleAnimator else null
        }

        override fun updateTaskbarTouchRegion() {
            taskbarTouchRegionUpdated = true
        }

        override fun setHandleTranslationY(translationY: Float) {}

        override fun getHandleTranslationY() = 0f

        override fun getHandleBounds(bounds: Rect) {}

        override fun updateHandleBounds() {}
    }
}

private const val DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS = -20f
private const val HANDLE_TRANSLATION = -30f
private const val BAR_TRANSLATION_Y_FOR_TASKBAR = -50f
private const val BAR_TRANSLATION_Y_FOR_HOTSEAT = -40f
