/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_ACTIVE_SCREEN;
import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_ALL;
import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_ROTATION;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.view.MotionEvent;

import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.display.LauncherDisplayInfo;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.ListenableDiffAwareRef;
import com.android.quickstep.dagger.QuickstepBaseAppComponent;

import javax.inject.Inject;

@LauncherAppSingleton
public class SimpleOrientationTouchTransformer {

    public static final DaggerSingletonObject<SimpleOrientationTouchTransformer> INSTANCE =
            new DaggerSingletonObject<>(
                    QuickstepBaseAppComponent::getSimpleOrientationTouchTransformer);

    private OrientationRectF mOrientationRectF;
    private OrientationRectF mTouchingOrientationRectF;
    private int mViewRotation;
    private final int mDisplayId;

    @Inject
    public SimpleOrientationTouchTransformer(
            DisplayController displayController, DaggerSingletonTracker tracker) {
        // TODO (b/398195845): make sure non-default displays don't get affected by default display
        // changes.
        mDisplayId = DEFAULT_DISPLAY;
        ListenableDiffAwareRef<LauncherDisplayInfo, Integer> listenable =
                displayController.getListenable(mDisplayId);
        if (listenable != null) {
            tracker.addCloseable(listenable.forEachChange(
                    MAIN_EXECUTOR, this::onDisplayInfoChanged));
        }

        LauncherDisplayInfo displayInfo = displayController.getInfoForDisplay(mDisplayId);
        if (displayInfo != null) {
            onDisplayInfoChanged(displayInfo, CHANGE_ALL);
        }
    }


    private void onDisplayInfoChanged(LauncherDisplayInfo info, int flags) {
        if ((flags & (CHANGE_ROTATION | CHANGE_ACTIVE_SCREEN)) == 0) {
            return;
        }
        mOrientationRectF = new OrientationRectF(0, 0, info.currentSize.y, info.currentSize.x,
                info.rotation);
    }

    /**
     * Called when the touch is started. This preserves the touching orientation until the touch is
     * done (i.e. ACTION_CANCEL or ACTION_UP). So the transform won't produce inconsistent position
     * if display is changed during the touch.
     */
    public void updateTouchingOrientation(int viewRotation) {
        mViewRotation = viewRotation;
        mTouchingOrientationRectF = new OrientationRectF(mOrientationRectF.left,
                mOrientationRectF.top, mOrientationRectF.right, mOrientationRectF.bottom,
                mOrientationRectF.getRotation());
    }

    /** Called when the touch is finished. */
    public void clearTouchingOrientation() {
        mTouchingOrientationRectF = null;
    }

    public void transform(MotionEvent ev, int rotation) {
        if (mTouchingOrientationRectF != null) {
            mTouchingOrientationRectF.applyTransformToRotation(ev, mViewRotation,
                    true /* forceTransform */);
            return;
        }
        mOrientationRectF.applyTransformToRotation(ev, rotation, true /* forceTransform */);
    }
}
