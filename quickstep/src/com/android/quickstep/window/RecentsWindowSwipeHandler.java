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
package com.android.quickstep.window;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.content.Intent.EXTRA_COMPONENT_NAME;
import static android.content.Intent.EXTRA_USER;

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.app.animation.Interpolators.DECELERATE;
import static com.android.launcher3.GestureNavContract.EXTRA_ENABLE_GESTURE_CONTRACT;
import static com.android.launcher3.GestureNavContract.EXTRA_GESTURE_CONTRACT;
import static com.android.launcher3.GestureNavContract.EXTRA_ICON_POSITION;
import static com.android.launcher3.GestureNavContract.EXTRA_ICON_SURFACE;
import static com.android.launcher3.GestureNavContract.EXTRA_LAUNCH_COOKIE;
import static com.android.launcher3.GestureNavContract.EXTRA_ON_FINISH_CALLBACK;
import static com.android.launcher3.GestureNavContract.EXTRA_REMOTE_CALLBACK;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;
import static com.android.quickstep.fallback.RecentsState.HIDDEN;

import android.animation.Animator;
import android.app.ActivityOptions;
import android.app.TaskInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.RemoteAnimationTarget;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.animation.Interpolator;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.ObjectWrapper;
import com.android.launcher3.util.StableViewInfo;
import com.android.quickstep.AbsSwipeUpHandler;
import com.android.quickstep.GestureState;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RotationTouchHelper;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.fallback.FallbackWindowRecentsView;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.InputConsumerController;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles the navigation gestures when a 3rd party launcher is the default home activity.
 *
 * Bugs: b/365775417
 */
public class RecentsWindowSwipeHandler extends AbsSwipeUpHandler<RecentsWindowManager,
        FallbackWindowRecentsView, RecentsState> {

    private static final String TAG = "RecentsWindowSwipeHandler";

    private static final float HOME_SWIPE_UP_SCALE = 0.9f;
    private static final float HOME_SWIPE_UP_ALPHA = 0.75f;
    private static final Interpolator HOME_SWIPE_UP_INTERPOLATOR = DECELERATE;

    /**
     * Message used for receiving gesture nav contract information. We use a static messenger to
     * avoid leaking too make binders in case the receiving launcher does not handle the contract
     * properly.
     */
    private static StaticMessageReceiver sMessageReceiver = null;

    private FallbackHomeAnimationFactory mActiveAnimationFactory;
    private final RecentsWindowManager mRecentsWindowManager;

    private final boolean mRunningOverHome;

    private final Matrix mTmpMatrix = new Matrix();

    private boolean mAppCanEnterPip;

    public RecentsWindowSwipeHandler(Context context,
            TaskAnimationManager taskAnimationManager,
            RecentsAnimationDeviceState deviceState,
            RotationTouchHelper rotationTouchHelper,
            RecentsWindowManager recentsWindowManager,
            GestureState gestureState,
            boolean continuingLastGesture,
            InputConsumerController inputConsumer,
            MSDLPlayerWrapper msdlPlayerWrapper,
            int displayId) {
        super(context,
                taskAnimationManager,
                deviceState,
                rotationTouchHelper,
                gestureState,
                continuingLastGesture,
                inputConsumer,
                msdlPlayerWrapper,
                displayId);

        mRecentsWindowManager = recentsWindowManager;
        mRunningOverHome = mGestureState.getRunningTask() != null
                && mGestureState.getRunningTask().isHomeTask();

        initTransformParams();
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets, @Nullable TransitionInfo transitionInfo) {
        super.onRecentsAnimationStart(controller, targets, transitionInfo);
        initTransformParams();
    }

    private void initTransformParams() {
        if (mActiveAnimationFactory != null) {
            mActiveAnimationFactory.initTransformParams();
            return;
        }
        runActionOnRemoteHandles(remoteTargetHandle ->
                remoteTargetHandle.getTransformParams().setHomeBuilderProxy(
                        RecentsWindowSwipeHandler.this::updateHomeActivityTransformDuringSwipeUp));
    }

    @UiThread
    @Override
    protected void animateGestureEnd(
            float startShift,
            float endShift,
            long duration,
            @NonNull Interpolator interpolator,
            @NonNull GestureState.GestureEndTarget endTarget,
            @NonNull PointF velocityPxPerMs) {
        boolean fromHomeToHome = mRunningOverHome
                && endTarget == GestureState.GestureEndTarget.HOME;
        if (fromHomeToHome) {
            mRecentsWindowManager.startHomeWithRemoteAnimation(/* finishRecentsAnimation= */ false);
        }
        super.animateGestureEnd(
                startShift,
                endShift,
                fromHomeToHome ? 0 : duration,
                interpolator,
                endTarget,
                velocityPxPerMs);
    }

    private void updateHomeActivityTransformDuringSwipeUp(
            SurfaceProperties builder, RemoteAnimationTarget app, TransformParams params) {
        if (mActiveAnimationFactory != null) {
            return;
        }
        // The currentShift is already interpolated by the magnetic swipe detach effect
        float currentShift = getCurrentShiftValue();
        setHomeScaleAndAlpha(
                builder,
                app,
                currentShift,
                mRunningOverHome
                        ? Utilities.mapBoundToRange(
                                currentShift,
                                /* lowerBound= */ 0f,
                                /* upperBound= */ mDragLengthFactor,
                                /* toMin= */ 1f,
                                /* toMax= */ HOME_SWIPE_UP_ALPHA,
                                /* interpolator= */ HOME_SWIPE_UP_INTERPOLATOR)
                        : 0f);
    }

    private void setHomeScaleAndAlpha(
            SurfaceProperties builder,
            RemoteAnimationTarget app,
            float verticalShift,
            float alpha) {
        if (app.windowConfiguration.getActivityType() != ACTIVITY_TYPE_HOME) {
            return;
        }
        float scale = mRunningOverHome
                ? Utilities.mapBoundToRange(
                        verticalShift,
                        /* lowerBound= */ 0f,
                        /* upperBound= */ mDragLengthFactor,
                        /* toMin= */ 1f,
                        /* toMax= */ HOME_SWIPE_UP_SCALE,
                        /* interpolator= */ HOME_SWIPE_UP_INTERPOLATOR)
                : 1f;
        mTmpMatrix.setScale(scale, scale,
                app.localBounds.exactCenterX(), app.localBounds.exactCenterY());
        builder.setMatrix(mTmpMatrix).setAlpha(alpha);
        builder.setShow();
    }

    @Override
    protected HomeAnimationFactory createHomeAnimationFactory(
            List<IBinder> launchCookies,
            long duration,
            boolean isTargetTranslucent,
            boolean appCanEnterPip,
            RemoteAnimationTarget runningTaskTarget,
            @Nullable TaskView targetTaskView) {
        mAppCanEnterPip = appCanEnterPip;
        if (appCanEnterPip) {
            return new FallbackPipToHomeAnimationFactory();
        }
        mActiveAnimationFactory = new FallbackHomeAnimationFactory(duration, targetTaskView);
        startHomeIntent(
                mActiveAnimationFactory,
                runningTaskTarget,
                targetTaskView,
                targetTaskView != null
                        ? null
                        : launchCookies.stream()
                                .filter(launchCookie ->
                                        ObjectWrapper.<StableViewInfo>unwrap(launchCookie) != null)
                                .findFirst()
                                .orElse(null),
                "RecentsWindowSwipeHandler-home");
        return mActiveAnimationFactory;
    }

    private void startHomeIntent(
            @Nullable FallbackHomeAnimationFactory gestureContractAnimationFactory,
            @Nullable RemoteAnimationTarget runningTaskTarget,
            @Nullable TaskView targetTaskView,
            @Nullable IBinder launchCookie,
            @NonNull String reason) {
        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
        Intent intent = new Intent(mGestureState.getHomeIntent());
        if (gestureContractAnimationFactory != null) {
            TaskKey taskKey = null;
            if (targetTaskView != null) {
                Task firstTask = targetTaskView.getFirstTask();

                taskKey = firstTask == null ? null : firstTask.key;
            } else if (runningTaskTarget != null) {
                TaskInfo taskInfo = runningTaskTarget.taskInfo;

                taskKey = taskInfo == null ? null : new TaskKey(taskInfo);
            }
            if (taskKey != null) {
                gestureContractAnimationFactory.addGestureContract(intent, taskKey, launchCookie);
            }
        }
        startHomeIntentSafely(mContext, intent, options.toBundle(), reason);
    }

    @Override
    protected void finishRecentsControllerToHome(Runnable callback) {
        final Runnable recentsCallback;
        // TODO(b/404866791): check if this is actually necessary for this recents-in-window class
        if (mAppCanEnterPip) {
            // Make sure Launcher is resumed after auto-enter-pip transition to actually trigger
            // the PiP task appearing.
            recentsCallback = () -> mRecentsWindowManager.startHomeWithRemoteAnimation(
                    /* finishRecentsAnimation= */ true, /* onHomeAnimationComplete= */ callback);
        } else {
            recentsCallback = callback;
        }
        if (mRecentsView != null) {
            mRecentsView.cleanupRemoteTargets();
        }
        mRecentsAnimationController.finish(
                /* toHome= */ true,
                recentsCallback,
                /* sendUserLeaveHint= */ true,
                /* reason= */ new ActiveGestureLog.CompoundString(
                        "RecentsWindowSwipeHandler.finishRecentsControllerToHome"));
    }

    @Override
    protected void switchToScreenshot() {
        if (mRunningOverHome) {
            // When the current task is home, then we don't need to capture anything
            mStateCallback.setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        } else {
            super.switchToScreenshot();
        }
    }

    @Override
    protected void notifyGestureAnimationStartToRecents() {
        if (!mRunningOverHome) {
            super.notifyGestureAnimationStartToRecents();
            return;
        }
        if ((DisplayController.getNavigationMode(mContext).hasGestures
                || mGestureState.isTrackpadGesture())
                && mRecentsView != null) {
            mRecentsView.onGestureAnimationStartOnHome(
                    mGestureState.getRunningTask().getPlaceholderGroupedTaskInfo(
                            /* splitTaskIds = */ null));
        }
    }

    private class FallbackPipToHomeAnimationFactory extends HomeAnimationFactory {
        @NonNull
        @Override
        public AnimatorPlaybackController createActivityAnimationToHome() {
            // copied from {@link LauncherSwipeHandlerV2.LauncherHomeAnimationFactory}
            long accuracy = 2 * Math.max(mDp.getDeviceProperties().getWidthPx(),
                    mDp.getDeviceProperties().getHeightPx());
            return mRecentsWindowManager
                    .getStateManager()
                    .createAnimationToNewWorkspace(
                            HIDDEN,
                            accuracy,
                            StateAnimationConfig.SKIP_ALL_ANIMATIONS);
        }
    }

    private class FallbackHomeAnimationFactory extends HomeAnimationFactory
            implements Consumer<Message> {
        private final Rect mTempRect = new Rect();

        private final TransformParams mTransformParams = new TransformParams();
        private final AnimatedFloat mHomeAlpha = new AnimatedFloat(this::updateAppTransforms);
        private final AnimatedFloat mVerticalShiftForScale =
                new AnimatedFloat(this::updateAppTransforms);
        private final AnimatedFloat mRecentsAlpha = new AnimatedFloat(this::updateAppTransforms);

        private final RectF mTargetRect = new RectF();
        private SurfaceControl mSurfaceControl;

        private boolean mAnimationFinished;
        private Message mOnFinishCallback;

        private final long mDuration;
        @Nullable private final TaskView mTargetTaskView;

        private RectFSpringAnim mSpringAnim;
        FallbackHomeAnimationFactory(long duration, @Nullable TaskView targetTaskView) {
            mDuration = duration;
            mTargetTaskView = targetTaskView;

            if (mRunningOverHome) {
                mVerticalShiftForScale.value = getCurrentShiftValue();
            }
            mRecentsAlpha.value = 1;
            mHomeAlpha.value = 0;

            initTransformParams();
        }

        @Nullable
        @Override
        public TaskView getTargetTaskView() {
            return mTargetTaskView;
        }

        @NonNull
        @Override
        public RectF getWindowTargetRect() {
            if (mTargetRect.isEmpty()) {
                mTargetRect.set(super.getWindowTargetRect());
            }
            return mTargetRect;
        }

        @NonNull
        @Override
        public AnimatorPlaybackController createActivityAnimationToHome() {
            // TODO(b/377678992): Implement a new AtomicAnimationFactory for RecentsWindowManager
            PendingAnimation pa = new PendingAnimation(mDuration);
            pa.setFloat(mRecentsAlpha, AnimatedFloat.VALUE, 0, ACCELERATE);
            pa.setFloat(mHomeAlpha, AnimatedFloat.VALUE, 1, ACCELERATE);
            pa.addStartListener(() -> {
                // Cancel any ongoing state manager animations since this is a non-user
                // controlled animation to home.
                mRecentsWindowManager.getStateManager().cancelAnimation();
            });
            pa.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    mRecentsWindowManager
                            .getStateManager()
                            .moveToRestState(/* isAnimated= */ false);
                }
            });
            return pa.createPlaybackController();
        }

        @Override
        public void playAtomicAnimation(float velocity) {
            if (!mRunningOverHome) {
                return;
            }
            // Spring back launcher scale
            new SpringAnimationBuilder(mContext)
                    .setStartValue(mVerticalShiftForScale.value)
                    .setEndValue(0)
                    .setStartVelocity(-velocity / mTransitionDragLength)
                    .setMinimumVisibleChange(1f / mDp.getDeviceProperties().getHeightPx())
                    .setDampingRatio(0.6f)
                    .setStiffness(800)
                    .build(mVerticalShiftForScale, AnimatedFloat.VALUE)
                    .start();
        }

        @Override
        public void setAnimation(RectFSpringAnim anim) {
            mSpringAnim = anim;
            mSpringAnim.addAnimatorListener(forEndCallback(this::onRectAnimationEnd));
        }

        private void initTransformParams() {
            runActionOnRemoteHandles(remoteTargetHandle ->
                    remoteTargetHandle.getTransformParams().setHomeBuilderProxy(
                            FallbackHomeAnimationFactory.this
                                    ::updateHomeActivityTransformDuringHomeAnim));

            mTransformParams.setHomeBuilderProxy(FallbackHomeAnimationFactory.this
                    ::updateHomeActivityTransformDuringHomeAnim);
            mTransformParams.setTargetSet(mRecentsAnimationTargets);
        }

        private void updateRecentsActivityTransformDuringHomeAnim(SurfaceProperties builder,
                RemoteAnimationTarget app, TransformParams params) {
            if (mRecentsAnimationTargets != null
                    && app.mode != mRecentsAnimationTargets.targetMode) {
                return;
            }
            builder.setAlpha(mRecentsAlpha.value);
        }

        private void updateAppTransforms() {
            mTransformParams.applySurfaceParams(
                    mTransformParams.createSurfaceParams(FallbackHomeAnimationFactory.this
                            ::updateRecentsActivityTransformDuringHomeAnim));
        }

        private void updateHomeActivityTransformDuringHomeAnim(SurfaceProperties builder,
                RemoteAnimationTarget app, TransformParams params) {
            setHomeScaleAndAlpha(builder, app, mVerticalShiftForScale.value, mHomeAlpha.value);
        }

        private void onRectAnimationEnd() {
            mAnimationFinished = true;
            maybeSendEndMessage();
        }

        private void maybeSendEndMessage() {
            if (mAnimationFinished && mOnFinishCallback != null) {
                try {
                    mOnFinishCallback.replyTo.send(mOnFinishCallback);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error sending icon position", e);
                }
            }
        }

        @Override
        public void accept(Message msg) {
            try {
                Bundle data = msg.getData();
                RectF position = data.getParcelable(EXTRA_ICON_POSITION);
                if (!position.isEmpty()) {
                    mSurfaceControl = data.getParcelable(EXTRA_ICON_SURFACE);
                    mTargetRect.set(position);
                    if (mSpringAnim != null) {
                        mSpringAnim.onTargetPositionChanged();
                    }
                }
                mOnFinishCallback = data.getParcelable(EXTRA_ON_FINISH_CALLBACK);
                maybeSendEndMessage();
            } catch (Exception e) {
                // Ignore
            }
        }

        @Override
        public void update(RectF currentRect, float progress, float radius, int overlayAlpha) {
            if (mSurfaceControl != null) {
                currentRect.roundOut(mTempRect);
                Transaction t = new Transaction();
                try {
                    t.setGeometry(mSurfaceControl, null, mTempRect, Surface.ROTATION_0);
                    t.apply();
                } catch (RuntimeException e) {
                    // Ignore
                }
            }
        }

        private void addGestureContract(
                @NonNull Intent intent,
                @NonNull TaskKey key,
                @Nullable IBinder launchCookie) {
            if (mRunningOverHome) {
                return;
            }
            if (key.getComponent() == null) {
                return;
            }
            if (sMessageReceiver == null) {
                sMessageReceiver = new StaticMessageReceiver();
            }
            Bundle gestureNavContract = new Bundle();

            gestureNavContract.putBoolean(
                    EXTRA_ENABLE_GESTURE_CONTRACT, mRemoteTargetHandles.length <= 1);
            gestureNavContract.putBinder(EXTRA_LAUNCH_COOKIE, launchCookie);
            gestureNavContract.putParcelable(EXTRA_COMPONENT_NAME, key.getComponent());
            gestureNavContract.putParcelable(EXTRA_USER, UserHandle.of(key.userId));
            gestureNavContract.putParcelable(
                    EXTRA_REMOTE_CALLBACK, sMessageReceiver.newCallback(this));

            intent.putExtra(EXTRA_GESTURE_CONTRACT, gestureNavContract);
        }
    }

    private static class StaticMessageReceiver implements Handler.Callback {

        private final Messenger mMessenger =
                new Messenger(new Handler(Looper.getMainLooper(), this));

        private ParcelUuid mCurrentUID = new ParcelUuid(UUID.randomUUID());
        private WeakReference<Consumer<Message>> mCurrentCallback = new WeakReference<>(null);

        public Message newCallback(Consumer<Message> callback) {
            mCurrentUID = new ParcelUuid(UUID.randomUUID());
            mCurrentCallback = new WeakReference<>(callback);

            Message msg = Message.obtain();
            msg.replyTo = mMessenger;
            msg.obj = mCurrentUID;
            return msg;
        }

        @Override
        public boolean handleMessage(@NonNull Message message) {
            if (mCurrentUID.equals(message.obj)) {
                Consumer<Message> consumer = mCurrentCallback.get();
                if (consumer != null) {
                    consumer.accept(message);
                    return true;
                }
            }
            return false;
        }
    }
}
