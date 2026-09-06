// Minimal compile stub of AOSP AnimatedSurfaceUtils (original has deep framework deps).
package com.android.wm.shell.shared.compat;

import android.annotation.IntDef;
import android.view.RemoteAnimationTarget;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AnimatedSurfaceUtils {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AnimatedSurface.Mode.CLOSING, AnimatedSurface.Mode.OPENING, AnimatedSurface.Mode.OTHER})
    public @interface AnimatedSurfaceMode {}

    public static AnimatedSurface from(RemoteAnimationTarget target) {
        AnimatedSurface s = new AnimatedSurface();
        if (target != null) {
            s.leash = target.leash;
            s.mode = mappedModeFromTarget(target.mode);
            s.taskId = target.taskId;
            if (target.screenSpaceBounds != null) {
                s.screenSpaceBounds = new android.graphics.Rect(target.screenSpaceBounds);
            }
        }
        return s;
    }

    public static AnimatedSurface[] mapFromTargets(RemoteAnimationTarget[] targets) {
        if (targets == null) return null;
        AnimatedSurface[] out = new AnimatedSurface[targets.length];
        for (int i = 0; i < targets.length; i++) out[i] = from(targets[i]);
        return out;
    }

    public static int mappedModeFromTarget(int targetMode) {
        return targetMode == RemoteAnimationTarget.MODE_CLOSING
                ? AnimatedSurface.Mode.CLOSING
                : targetMode == RemoteAnimationTarget.MODE_OPENING
                        ? AnimatedSurface.Mode.OPENING : AnimatedSurface.Mode.OTHER;
    }

    public static boolean isOpening(int mode) { return mode == AnimatedSurface.Mode.OPENING; }
    public static boolean isClosing(int mode) { return mode == AnimatedSurface.Mode.CLOSING; }
}
