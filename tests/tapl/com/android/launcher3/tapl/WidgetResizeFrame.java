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
package com.android.launcher3.tapl;

import static com.android.launcher3.tapl.Launchable.DEFAULT_DRAG_STEPS;

import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.graphics.Rect;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

/** The resize frame that is shown for a widget on the workspace. */
public class WidgetResizeFrame {
    private static final String WIDGET_HOST_VIEW_CLASS =
            "com.android.launcher3.widget.LauncherAppWidgetHostView";
    private final LauncherInstrumentation mLauncher;

    WidgetResizeFrame(LauncherInstrumentation launcher) {
        mLauncher = launcher;
        launcher.waitForLauncherObject("widget_resize_frame");
    }

    /** Dismisses the resize frame. */
    public void dismiss() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to dismiss widget resize frame")) {
            // Dismiss the resize frame by pressing the home button.
            mLauncher.getDevice().pressHome();
        }
    }

    /** Resizes the widget to double its height, and returns the resize frame. */
    public WidgetResizeFrame resize(CharSequence label) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to resize the widget frame.")) {
            UiObject2 frame = mLauncher.waitForLauncherObject("widget_resize_frame");
            UiObject2 bottomResizeHandle =
                    mLauncher.waitForLauncherObject("widget_resize_bottom_handle");

            UiObject2 widgetView = mLauncher.getDevice().wait(
                    Until.findObject(By.clazz(WIDGET_HOST_VIEW_CLASS).desc(label.toString())),
                    LauncherInstrumentation.WAIT_TIME_MS);
            float originalWidgetSize = widgetView.getVisibleBounds().height();

            Rect frameSize = frame.getVisibleBounds();
            Point targetStart = bottomResizeHandle.getVisibleCenter();
            Point targetDest = bottomResizeHandle.getVisibleCenter();
            targetDest.offset(0,
                    frameSize.height() + mLauncher.getCellLayoutBoarderHeight());

            mLauncher.getDevice().drag(targetStart.x, targetStart.y, targetDest.x, targetDest.y,
                    DEFAULT_DRAG_STEPS);

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                    "want to return resized widget resize frame")) {
                float newWidgetSize = widgetView.getVisibleBounds().height();
                assertTrue("Widget not resized.", newWidgetSize >= originalWidgetSize * 2);
                return this;
            }
        }
    }
}
