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
package com.android.quickstep.util;


import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;

import static com.android.wm.shell.shared.TransitionUtil.isClosingMode;
import static com.android.wm.shell.shared.TransitionUtil.isOpeningMode;

import android.animation.AnimatorSet;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.TransitionInfo;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.util.ScalingWorkspaceRevealAnim;

/**
 * Handles the transition for a task moving between displays.
 *
 * Throughout this implementation:
 * - "src" refers to the display the task is moving FROM.
 * - "dst" refers to the display the task is moving TO.
 */
public final class CrossDisplayMoveTransition {
    private static final String TAG = "CrossDisplayMoveTransition";

    private CrossDisplayMoveTransition() {}

    /**
     * Returns true if the transition involves a task moving between displays.
     */
    public static boolean isCrossDisplayMove(TransitionInfo info) {
        return CrossDisplayMoveTransitionInfo.getCrossDisplayMove(info) != null;
    }

    /**
     * Animates a task moving between displays.
     *
     * <p>This works by taking a screenshot of the task on the source display, and using the actual
     * task surface on the destination display. It then reparents the screenshot to the source
     * display's animation root, and the task surface to the destination's animation root.
     *
     * <p>The actual animation is defined by {@link CrossDisplayMoveAnimator}.
     *
     * @param launcher The launcher instance.
     * @param launchingTransitionDurationMs The duration of the launching animation.
     * @param unlaunchingTransitionDurationMs The duration of the unlaunching animation.
     * @param info The transition info.
     * @param t The surface control transaction.
     * @param finishCallback The callback to invoke when the transition is finished.
     */
    public static void startCrossDisplayMoveAnimation(
            QuickstepLauncher launcher,
            long launchingTransitionDurationMs,
            long unlaunchingTransitionDurationMs,
            TransitionInfo info,
            SurfaceControl.Transaction t,
            IRemoteTransitionFinishedCallback finishCallback) {
        // 1. Parse the transition info and setup our initial transaction.

        final CrossDisplayMoveTransitionInfo moveInfo =
                CrossDisplayMoveTransitionInfo.create(info);
        if (moveInfo == null) {
            Log.w(TAG, "Failed to parse cross display move transition info");
            try {
                finishCallback.onTransitionFinished(null /* wct */, null /* t */);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to finish cross display move transition", e);
            }
            return;
        }
        Log.v(TAG, "starting cross-display move animation for "
                + moveInfo.taskMovingBetweenDisplays);
        setupInitialAnimationState(info, moveInfo, t);

        // 2. Setup the launcher reveal, if needed.

        // We're only going to play the launcher reveal if
        //  - We have a launcher moving to the front
        //  - And the launcher is moving so that it ends up on the task SRC display.
        //
        // (If the launcher is moving on the DST, then it's going to compete with the task launch.
        // We'll likely want to add another "playLauncherHide" animation for this case)
        final boolean playLauncherReveal =
                moveInfo.launcherToFront != null
                && moveInfo.launcherToFront.getEndDisplayId() == moveInfo.srcDisplayId;
        AnimatorSet launcherRevealAnimators = null;
        if (playLauncherReveal) {
            launcherRevealAnimators = new ScalingWorkspaceRevealAnim(launcher,
                    /* siblingAnimation= */ null, /* windowTargetRect= */ null,
                    /* playAlphaReveal= */ true, /* playBlur= */ true).getAnimators();
        }

        // 3. Start the animation
        t.apply();
        new CrossDisplayMoveAnimator(moveInfo.srcTaskLeash, moveInfo.dstTaskLeash,
                moveInfo.srcBounds,
                moveInfo.dstBounds, launchingTransitionDurationMs,
                unlaunchingTransitionDurationMs, launcherRevealAnimators, finishCallback)
                .start();
    }

    private static void initializeLeashAsVisible(SurfaceControl.Transaction t, Rect bounds,
            SurfaceControl leash) {
        t.setAlpha(leash, 1f);
        t.setPosition(leash, bounds.left, bounds.top);
        t.show(leash);
    }

    /**
     * Sets up the initial animation state. (Including re-rooting leashes to the correct display.)
     */
    @VisibleForTesting
    public static void setupInitialAnimationState(TransitionInfo info,
            CrossDisplayMoveTransitionInfo move, SurfaceControl.Transaction t) {
        // Our strategy for animation setup is as follows:
        //  1. Set up the state of the animation to match the final state of the transition.
        //    - This setup is independent of the knowledge of the actual animation implementation
        //      EG this will end up making DST task alpha 1, though to actually animate we want 0
        //  Then
        //  2. Set up the state of the animation to match the concrete implementation we expect for
        //    the effect we're going to produce. This means:
        //    - Reparent leashes to support a parallel display effect.
        //    - Apply a frame-0 animation state to each leash, defined by the concrete
        //      implementation we expect to use.
        //
        // This does mean that changes in the first step may be overridden in the second step.
        // But, in trade we'll be a bit more robust to changes in the underlying animation
        // implementation.

        // 1. Set up the state of the animation to match the final state of the transition.
        for (TransitionInfo.Change change : info.getChanges()) {
            final int mode = change.getMode();
            if (isOpeningMode(mode)) {
                initializeLeashAsVisible(t, change.getEndAbsBounds(), change.getLeash());
            } else if (isClosingMode(mode) && isEmbeddedActivityInMovingTask(change, move)) {
                // If the change is a closing Activity embedded under the moving task, the Activity
                // doesn't have a dedicated space to be shown on the destination display and should
                // not be shown.
                Log.v(TAG, "Hiding closing ActivityEmbedding: " + change);
                t.hide(change.getLeash());
            }
        }

        // 2. Setup to match the concrete implementation we expect.
        //
        //  - Reparenting special cases
        if (move.srcTaskLeash != null) {
            if (move.srcRoot != null) {
                t.reparent(move.srcTaskLeash, move.srcRoot.getLeash());
                initializeLeashAsVisible(t, move.srcBounds, move.srcTaskLeash);
            } else {
                // We should be able to always find the srcRoot. But, we don't if src is an
                // external display.
                // See b/442301974
                //
                // As srcScreenshotLeash comes in parented to dst, not having srcRoot means we don't
                // have a good way to reparent to the correct display. Given that, reparenting to
                // null effectively releases it (our best option).
                Log.w(TAG, "No srcRoot found for cross display move; releasing src effect.");
                t.reparent(move.srcTaskLeash, null);
            }
        }
        t.reparent(move.dstTaskLeash, move.dstRoot.getLeash());
        initializeLeashAsVisible(t, move.dstBounds, move.dstTaskLeash);

        //  - Apply a frame-0 animation state
        if (move.srcTaskLeash != null) {
            CrossDisplayMoveAnimator.applyUnlaunchState(t, move.srcTaskLeash,
                    move.srcBounds, /* progress= */ 0f);
        }
        CrossDisplayMoveAnimator.applyLaunchState(t, move.dstTaskLeash,
                move.dstBounds,
                /* progress= */ 0f);
    }

    private static boolean isEmbeddedActivityInMovingTask(
            TransitionInfo.Change change, CrossDisplayMoveTransitionInfo move) {
        if (!change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY)) {
            return false;
        }
        if (change.getParent() == null) {
            return false;
        }
        Log.v(TAG, "isEmbeddedActivityInMovingTask, parent=" + change.getParent()
                + ", moving task=" + move.taskMovingBetweenDisplays.getContainer());
        if (change.getParent().equals(move.taskMovingBetweenDisplays.getContainer())) {
            return true;
        }
        return false;
    }
}
