package com.android.wm.shell.shared.compat;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.window.WindowAnimationState;

/** Hand-written compile stub for AOSP AnimatedSurface (typed-parcelable aidl). */
public class AnimatedSurface {
    public static final class Mode {
        public static final int CLOSING = 0;
        public static final int OPENING = 1;
        public static final int OTHER = 2;
        private Mode() {}
    }

    public SurfaceControl leash;
    public SurfaceControl startLeash;
    public WindowAnimationState startState;
    public WindowAnimationState endState;
    public int backgroundColor;
    public boolean isTranslucent;
    public RunningTaskInfo taskInfo;
    public int mode;
    public Rect screenSpaceBounds;
    public Rect localBounds;
    public Rect startBounds;
    public Rect contentInsets;
    public Point position;
    public int rotationChange;
    public WindowConfiguration windowConfiguration;
    public int taskId;
    public int windowType;
    public boolean willShowImeOnTarget;
    public boolean isNotInRecents;
    public boolean allowEnterPip;
    public int order;
}
