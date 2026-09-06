/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.graphics.Rect;
import android.view.HapticFeedbackConstants;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DropTarget;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;

/**
 * Drag controller for Secondary Launcher activity
 */
public class SecondaryDragController extends DragController {

    private final SecondaryDisplayLauncher mActivity;

    public SecondaryDragController(SecondaryDisplayLauncher secondaryLauncher) {
        super(secondaryLauncher);
        mActivity = secondaryLauncher;
    }

    @Override
    protected void onDragViewInitialized() {
        mActivity.getDragLayer().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (!isItemPinnable()) {
            MAIN_EXECUTOR.post(this:: cancelDrag);
        }
    }

    @Override
    protected DropTarget getDefaultDropTarget(int[] dropCoordinates) {
        DropTarget target = new DropTarget() {
            @Override
            public boolean isDropEnabled() {
                return true;
            }

            @Override
            public void onDrop(DragObject dragObject, DragOptions options) {
                ((SecondaryDragLayer) mActivity.getDragLayer()).getPinnedAppsAdapter().addPinnedApp(
                        dragObject.dragInfo);
                dragObject.dragView.remove();
            }

            @Override
            public void onDragEnter(DragObject dragObject) {
                if (getDistanceDragged() > mActivity.getResources().getDimensionPixelSize(
                        R.dimen.drag_distanceThreshold)) {
                    mActivity.showAppDrawer(false);
                    AbstractFloatingView.closeAllOpenViews(mActivity);
                }
            }

            @Override
            public void onDragOver(DragObject dragObject) { }

            @Override
            public void onDragExit(DragObject dragObject) { }

            @Override
            public boolean acceptDrop(DragObject dragObject) {
                return true;
            }

            @Override
            public void prepareAccessibilityDrop() { }

            @Override
            public void getHitRectRelativeToDragLayer(Rect outRect) { }
        };
        return target;
    }
}
