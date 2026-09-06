/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.statehandlers;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;

import android.content.Context;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.ListenableRef;
import com.android.quickstep.util.BaseDepthControllerImpl;

import java.io.PrintWriter;

/**
 * Controls blur and wallpaper zoom.
 * @param <STATE> state associated with the container.
 * @param <CONTAINER> the StatefulContainer.
 */
public class DepthController<
        STATE extends BaseState<STATE>,
        CONTAINER extends Context & StatefulContainer<STATE>>
        extends BaseDepthControllerImpl<STATE, CONTAINER>
        implements StateHandler<STATE> {
    public static final float DEPTH_0_PERCENT = 0f;
    public static final float DEPTH_70_PERCENT = 0.7f;

    @VisibleForTesting
    final ViewTreeObserver.OnDrawListener mOnDrawListener = this::onContainerDraw;

    private final Runnable mOpaquenessListener = this::applyDepthAndBlur;

    private View.OnAttachStateChangeListener mOnAttachListener;

    // Ensure {@link mOnDrawListener} is added only once to avoid spamming DragLayer's mRunQueue
    // via {@link View#post(Runnable)}
    private boolean mIsOnDrawListenerAdded = false;
    private boolean mRemoveOnDrawListenerCancelled = false;

    public DepthController(CONTAINER container, ListenableRef<Boolean> blurState) {
        super(container, blurState);
    }

    private void onContainerDraw() {
        View view = mContainer.getDragLayer();
        ViewRootImpl viewRootImpl = view.getViewRootImpl();
        setBaseSurface(viewRootImpl != null ? viewRootImpl.getSurfaceControl() : null);
        mRemoveOnDrawListenerCancelled = false;
        view.post(() -> {
            if (!mRemoveOnDrawListenerCancelled) {
                removeOnDrawListener();
            }
        });
    }

    private void ensureDependencies() {
        View rootView = mContainer.getRootView();
        if (rootView == null) {
            return;
        }
        if (mOnAttachListener != null) {
            return;
        }
        mOnAttachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                mContainer.getScrimView().addOpaquenessListener(mOpaquenessListener);

                // To handle the case where window token is invalid during last setDepth call.
                applyDepthAndBlur();
            }

            @Override
            public void onViewDetachedFromWindow(View view) { }
        };
        rootView.addOnAttachStateChangeListener(mOnAttachListener);
        if (rootView.isAttachedToWindow()) {
            mOnAttachListener.onViewAttachedToWindow(rootView);
        }
    }

    /**
     * Sets if the underlying activity is started or not
     */
    public void setActivityStarted(boolean isStarted) {
        if (isStarted) {
            addOnDrawListener();
        } else {
            removeOnDrawListener();
            setBaseSurface(null);
            setEarlyWakeup(false);
        }
    }

    @Override
    public void setState(STATE toState) {
        stateDepth.setValue(toState.getDepth(mContainer));
        if (toState == mContainer.getBackgroundAppState()) {
            addOnDrawListener();
        }
    }

    @Override
    public void setStateWithAnimation(STATE toState, StateAnimationConfig config,
            PendingAnimation animation) {
        if (config.hasAnimationFlag(SKIP_DEPTH_CONTROLLER)) {
            return;
        }

        float toDepth = toState.getDepth(mContainer);
        animation.setFloat(stateDepth, MULTI_PROPERTY_VALUE, toDepth,
                config.getInterpolator(ANIM_DEPTH, LINEAR));
    }

    @Override
    protected void applyDepthAndBlur() {
        ensureDependencies();
        super.applyDepthAndBlur();
    }

    @Override
    protected void onInvalidSurface() {
        // Lets wait for surface to become valid again
        addOnDrawListener();
    }

    private void addOnDrawListener() {
        mRemoveOnDrawListenerCancelled = true;
        if (mIsOnDrawListenerAdded) {
            return;
        }
        mContainer.getDragLayer().getViewTreeObserver().addOnDrawListener(mOnDrawListener);
        mIsOnDrawListenerAdded = true;
    }

    private void removeOnDrawListener() {
        mRemoveOnDrawListenerCancelled = true;
        if (!mIsOnDrawListenerAdded) {
            return;
        }
        mContainer.getDragLayer().getViewTreeObserver().removeOnDrawListener(mOnDrawListener);
        mIsOnDrawListenerAdded = false;
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "DepthController");
        writer.println(prefix + "\tmMaxBlurRadius=" + mMaxBlurRadius);
        writer.println(prefix + "\tmCrossWindowBlursEnabled=" + mCrossWindowBlursEnabled);
        writer.println(prefix + "\tmBaseSurface=" + mBaseSurface);
        writer.println(prefix + "\tmBaseSurfaceOverride=" + mBaseSurfaceOverride);
        writer.println(prefix + "\tmStateDepth=" + stateDepth.getValue());
        writer.println(prefix + "\tmWidgetDepth=" + widgetDepth.getValue());
        writer.println(prefix + "\tmCurrentBlur=" + mCurrentBlur);
        writer.println(prefix + "\tmInEarlyWakeUp=" + mInEarlyWakeUp);
        writer.println(prefix + "\tmPauseBlurs=" + mPauseBlurs);
        writer.println(prefix + "\tmWaitingOnSurfaceValidity=" + mWaitingOnSurfaceValidity);
    }
}
