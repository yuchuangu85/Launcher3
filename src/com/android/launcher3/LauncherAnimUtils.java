/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.util.MultiScalePropertyFactory;
import com.android.launcher3.views.ScrimColors;
import com.android.launcher3.views.ScrimView;

import java.util.function.Function;

public class LauncherAnimUtils {
    /**
     * Durations for various state animations. These are not defined in resources to allow
     * easier access from static classes and enums
     */
    public static final int SPRING_LOADED_EXIT_DELAY = 500;

    // Progress after which the transition is assumed to be a success
    public static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;
    public static final float TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS = 0.3f;

    public static final IntProperty<Drawable> DRAWABLE_ALPHA =
            new IntProperty<Drawable>("drawableAlpha") {
                @Override
                public Integer get(Drawable drawable) {
                    return drawable.getAlpha();
                }

                @Override
                public void setValue(Drawable drawable, int alpha) {
                    drawable.setAlpha(alpha);
                }
            };

    public static final FloatProperty<View> SCALE_PROPERTY = getScaleProperty();

    /** Used to debug b/489475544, after the crash is fixed, caller can just use SCALE_PROPERTY t*/
    public static FloatProperty<View> getScaleProperty() {
        return new ScaleProperty();
    }

    /**
     * FloatProperty that saves the creation call stacktrace, so that during animation frame if
     * {@link android.view.ViewRootImpl$CalledFromWrongThreadException} is detected, the crash
     * will contain creation call stacktrace.
     */
    private static class ScaleProperty extends FloatProperty<View> {

        private final Exception mException;

        ScaleProperty() {
            super("scale");
            mException = new Exception();
        }

        @Override
        public Float get(View view) {
            return view.getScaleX();
        }

        @Override
        public void setValue(View view, float scale) {
            try {
                if (view.getHandler() != null
                        && view.getHandler().getLooper() != android.os.Looper.myLooper()) {
                    view.post(() -> {
                        view.setScaleX(scale);
                        view.setScaleY(scale);
                    });
                } else {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                }
            } catch (RuntimeException e) {
                // Check if the class name matches the hidden Android exception
                if (e.getClass().getName().contains("CalledFromWrongThreadException")) {
                    RuntimeException diagnostic = new RuntimeException(
                            "Thread Mismatch! Creator trace attached as cause.", e);
                    diagnostic.initCause(mException);
                    throw diagnostic;
                }
                throw e;
            }
        }
    }

    /**
     * Property to set the scale of workspace. The value is based on a combination
     * of all the ones set, to have a smooth experience even in the case of overlapping scaling
     * animation.
     */
    public static final MultiScalePropertyFactory<Workspace<?>> WORKSPACE_SCALE_PROPERTY_FACTORY =
            new MultiScalePropertyFactory<Workspace<?>>("workspace_scale_property");

    /** Property to set the scale of hotseat. */
    public static final MultiScalePropertyFactory<Hotseat> HOTSEAT_SCALE_PROPERTY_FACTORY =
            new MultiScalePropertyFactory<Hotseat>("hotseat_scale_property");

    public static final int SCALE_INDEX_UNFOLD_ANIMATION = 1;
    public static final int SCALE_INDEX_WORKSPACE_STATE = 2;
    public static final int SCALE_INDEX_REVEAL_ANIM = 3;
    public static final int SCALE_INDEX_WIDGET_TRANSITION = 4;
    public static final int SCALE_INDEX_FOLDER_ANIM = 5;

    /** Increase the duration if we prevented the fling, as we are going against a high velocity. */
    public static int blockedFlingDurationFactor(float velocity) {
        return (int) Utilities.boundToRange(Math.abs(velocity) / 2, 2f, 6f);
    }

    public static final IntProperty<LayoutParams> LAYOUT_WIDTH =
            new IntProperty<LayoutParams>("width") {
                @Override
                public Integer get(LayoutParams lp) {
                    return lp.width;
                }

                @Override
                public void setValue(LayoutParams lp, int width) {
                    lp.width = width;
                }
            };

    public static final IntProperty<LayoutParams> LAYOUT_HEIGHT =
            new IntProperty<LayoutParams>("height") {
                @Override
                public Integer get(LayoutParams lp) {
                    return lp.height;
                }

                @Override
                public void setValue(LayoutParams lp, int height) {
                    lp.height = height;
                }
            };

    public static final IntProperty<TextView> TEXT_COLOR =
            new IntProperty<TextView>("textColor") {
                @Override
                public Integer get(TextView view) {
                    return view.getTextColors().getDefaultColor();
                }

                @Override
                public void setValue(TextView view, int color) {
                    view.setTextColor(color);
                }
            };

    public static final IntProperty<TextView> HINT_TEXT_COLOR =
            new IntProperty<TextView>("hintTextColor") {
                @Override
                public Integer get(TextView view) {
                    return view.getHintTextColors().getDefaultColor();
                }

                @Override
                public void setValue(TextView view, int color) {
                    view.setHintTextColor(color);
                }
            };

    public static final FloatProperty<View> VIEW_TRANSLATE_X = convert(View.TRANSLATION_X);
    public static final FloatProperty<View> VIEW_TRANSLATE_Y = convert(View.TRANSLATION_Y);
    public static final FloatProperty<View> VIEW_TRANSLATE_Z = convert(View.TRANSLATION_Z);

    public static final FloatProperty<View> VIEW_ALPHA = convert(View.ALPHA);

    private static FloatProperty<View> convert(Property<View, Float> property) {
        if (property instanceof FloatProperty<View> f) return f;
        return new FloatProperty<>(property.getName()) {
            @Override
            public void setValue(View view, float v) {
                property.set(view, v);
            }

            @Override
            public Float get(View view) {
                return property.get(view);
            }
        };
    }

    public static final IntProperty<View> VIEW_BACKGROUND_COLOR =
            new IntProperty<View>("backgroundColor") {
                @Override
                public void setValue(View view, int color) {
                    view.setBackgroundColor(color);
                }

                @Override
                public Integer get(View view) {
                    if (!(view.getBackground() instanceof ColorDrawable)) {
                        return Color.TRANSPARENT;
                    }
                    return ((ColorDrawable) view.getBackground()).getColor();
                }
            };

    /**
     * Returns a function to fetch the coordinate (centerX or centerY) for the primary axis
     * of movement between the start and target Rect.
     */
    public static Function<RectF, Float> getPosProviderForRect(RectF start, RectF target) {
        float totalXDiff = Math.abs(start.centerX() - target.centerX());
        float totalYDiff = Math.abs(start.centerY() - target.centerY());
        return totalYDiff > totalXDiff ? RectF::centerY : RectF::centerX;
    }

    public static final Property<ScrimView, ScrimColors> SCRIM_COLORS =
            new Property<ScrimView, ScrimColors>(ScrimColors.class, "scrimColors") {
                @Override
                public void set(ScrimView scrimView, ScrimColors scrimColors) {
                    scrimView.setScrimColors(scrimColors);
                }

                @Override
                public ScrimColors get(ScrimView scrimView) {
                    return scrimView.getScrimColors();
                }
            };

    public static final FloatProperty<ImageView> ROTATION_DRAWABLE_PERCENT =
            new FloatProperty<ImageView>("drawableRotationPercent") {
                // RotateDrawable linearly interpolates the rotation degrees between fromDegrees
                // and toDegrees using the drawable level as a percent of its MAX_LEVEL.
                private static final int MAX_LEVEL = 10000;

                @Override
                public void setValue(ImageView view, float percent) {
                    view.setImageLevel((int) (percent * MAX_LEVEL));
                }

                @Override
                public Float get(ImageView view) {
                    return view.getDrawable().getLevel() / (float) MAX_LEVEL;
                }
            };

    /**
     * Utility method to create an {@link AnimatorListener} which executes a callback on animation
     * cancel. Once the cancel has been dispatched, this listener will no longer be called.
     */
    public static AnimatorListener newSingleUseCancelListener(Runnable callback) {
        return newCancelListener(callback, true);
    }

    /**
     * Utility method to create an {@link AnimatorListener} which executes a callback on animation
     * cancel.
     *
     * @param isSingleUse {@code true} means the callback will be called at most once
     */
    public static AnimatorListener newCancelListener(Runnable callback, boolean isSingleUse) {
        return new AnimatorListenerAdapter() {
            boolean mDispatched = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                if (!mDispatched) {
                    if (isSingleUse) {
                        mDispatched = true;
                    }
                    callback.run();
                }
            }
        };
    }

    /**
     * A property that updates the specified property within a given range of values (ie. even if
     * the animator goes beyond 0..1, the interpolated value will still be bounded).
     * @param <T> the specified property
     */
    public static class ClampedProperty<T> extends FloatProperty<T> {
        private final FloatProperty<T> mProperty;
        private final float mMinValue;
        private final float mMaxValue;

        public ClampedProperty(FloatProperty<T> property, float minValue, float maxValue) {
            super(property.getName() + "Clamped");
            mProperty = property;
            mMinValue = minValue;
            mMaxValue = maxValue;
        }

        @Override
        public void setValue(T t, float v) {
            mProperty.set(t, Utilities.boundToRange(v, mMinValue, mMaxValue));
        }

        @Override
        public Float get(T t) {
            return mProperty.get(t);
        }
    }
}
