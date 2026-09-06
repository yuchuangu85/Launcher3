/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep;

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

import android.animation.ObjectAnimator;
import android.app.ActivityOptions;
import android.app.TaskInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
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

import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.ObjectWrapper;
import com.android.launcher3.util.StableViewInfo;
import com.android.quickstep.fallback.FallbackActivityRecentsView;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;
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
 */
public class FallbackSwipeHandler extends
        AbsSwipeUpHandler<RecentsActivity, FallbackActivityRecentsView, RecentsState> {

    private static final String TAG = "FallbackSwipeHandler";

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
    private final boolean mRunningOverHome;

    private final Matrix mTmpMatrix = new Matrix();

    private boolean mAppCanEnterPip;

    public FallbackSwipeHandler(Context context,
            TaskAnimationManager taskAnimationManager,
            RecentsAnimationDeviceState deviceState,
            RotationTouchHelper rotationTouchHelper,
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

        mRunningOverHome = mGestureState.getRunningTask() != null
                && mGestureState.getRunningTask().isHomeTask();
        if (mRunningOverHome) {
            runActionOnRemoteHandles(remoteTargetHandle ->
                    remoteTargetHandle.getTransformParams().setHomeBuilderProxy(
                    FallbackSwipeHandler.this::updateHomeActivityTransformDuringSwipeUp));
        }
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
                "FallbackSwipeHandler-home");
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
    public void onTasksAppeared(@NonNull RemoteAnimationTarget[] appearedTaskTargets,
            @Nullable TransitionInfo transitionInfo) {
        if (mActiveAnimationFactory != null && mActiveAnimationFactory.handleHomeTaskAppeared(
                appearedTaskTargets)) {
            mActiveAnimationFactory = null;
            return;
        }

        super.onTasksAppeared(appearedTaskTargets, transitionInfo);
    }

    @Override
    protected void finishRecentsControllerToHome(Runnable callback) {
        final Runnable recentsCallback;
        if (mAppCanEnterPip) {
            // Make sure Launcher is resumed after auto-enter-pip transition to actually trigger
            // the PiP task appearing.
            recentsCallback = () -> {
                callback.run();
                startHomeIntent(
                        /* gestureContractAnimationFactory= */ null,
                        /* runningTaskTarget= */ null,
                        /* targetTaskView= */ null,
                        /* launchCookie= */ null,
                        /* reason= */ "FallbackSwipeHandler-resumeLauncher");
            };
        } else {
            recentsCallback = callback;
        }
        if (mRecentsView != null) {
            mRecentsView.cleanupRemoteTargets();
        }
        mRecentsAnimationController.finish(
                /* toHome= */mAppCanEnterPip,
                recentsCallback,
                /* sendUserLeaveHint= */ true,
                /* reason= */ new ActiveGestureLog.CompoundString(
                        "FallbackSwipeHandler.finishRecentsControllerToHome"));
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
            long accuracy = 2 * Math.max(mDp.getDeviceProperties().getWidthPx(), mDp.getDeviceProperties().getHeightPx());
            return mContainer.getStateManager().createAnimationToNewWorkspace(
                    RecentsState.HOME, accuracy, StateAnimationConfig.SKIP_ALL_ANIMATIONS);
        }
    }

    private class FallbackHomeAnimationFactory extends HomeAnimationFactory
            implements Consumer<Message> {
        private final Rect mTempRect = new Rect();
        private final TransformParams mHomeAlphaParams = new TransformParams();
        private final AnimatedFloat mHomeAlpha;

        private final AnimatedFloat mVerticalShiftForScale = new AnimatedFloat();
        private final AnimatedFloat mRecentsAlpha = new AnimatedFloat();

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
                float currentShift = getCurrentShiftValue();
                mHomeAlpha = new AnimatedFloat();
                mHomeAlpha.value = Utilities.boundToRange(1 - currentShift, 0, 1);
                mVerticalShiftForScale.value = currentShift;
                runActionOnRemoteHandles(remoteTargetHandle ->
                        remoteTargetHandle.getTransformParams().setHomeBuilderProxy(
                                FallbackHomeAnimationFactory.this
                                        ::updateHomeActivityTransformDuringHomeAnim));
            } else {
                mHomeAlpha = new AnimatedFloat(this::updateHomeAlpha);
                mHomeAlpha.value = 0;
                mHomeAlphaParams.setHomeBuilderProxy(
                        this::updateHomeActivityTransformDuringHomeAnim);
            }

            mRecentsAlpha.value = 1;
            runActionOnRemoteHandles(remoteTargetHandle ->
                    remoteTargetHandle.getTransformParams().setBaseBuilderProxy(
                            FallbackHomeAnimationFactory.this
                                    ::updateRecentsActivityTransformDuringHomeAnim));
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

        private void updateRecentsActivityTransformDuringHomeAnim(SurfaceProperties builder,
                RemoteAnimationTarget app, TransformParams params) {
            builder.setAlpha(mRecentsAlpha.value);
        }

        private void updateHomeActivityTransformDuringHomeAnim(SurfaceProperties builder,
                RemoteAnimationTarget app, TransformParams params) {
            setHomeScaleAndAlpha(builder, app, mVerticalShiftForScale.value, mHomeAlpha.value);
        }

        @NonNull
        @Override
        public AnimatorPlaybackController createActivityAnimationToHome() {
            PendingAnimation pa = new PendingAnimation(mDuration);
            pa.setFloat(mRecentsAlpha, AnimatedFloat.VALUE, 0, ACCELERATE);
            return pa.createPlaybackController();
        }

        private void updateHomeAlpha() {
            if (mHomeAlphaParams.getTargetSet() != null) {
                mHomeAlphaParams.applySurfaceParams(
                        mHomeAlphaParams.createSurfaceParams(BuilderProxy.NO_OP));
            }
        }

        public boolean handleHomeTaskAppeared(RemoteAnimationTarget[] appearedTaskTargets) {
            RemoteAnimationTarget appearedTaskTarget = appearedTaskTargets[0];
            if (appearedTaskTarget.windowConfiguration.getActivityType() == ACTIVITY_TYPE_HOME) {
                RemoteAnimationTargets targets = new RemoteAnimationTargets(
                        new RemoteAnimationTarget[] {appearedTaskTarget},
                        new RemoteAnimationTarget[0], new RemoteAnimationTarget[0],
                        appearedTaskTarget.mode);
                mHomeAlphaParams.setTargetSet(targets);
                updateHomeAlpha();
                return true;
            }
            return false;
        }

        @Override
        public void playAtomicAnimation(float velocity) {
            ObjectAnimator alphaAnim = mHomeAlpha.animateToValue(mHomeAlpha.value, 1);
            alphaAnim.setDuration(mDuration).setInterpolator(ACCELERATE);
            alphaAnim.start();

            if (mRunningOverHome) {
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
        }

        @Override
        public void setAnimation(RectFSpringAnim anim) {
            mSpringAnim = anim;
            mSpringAnim.addAnimatorListener(forEndCallback(this::onRectAnimationEnd));
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
