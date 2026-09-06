/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.service.personalcontext.embedded;

import static android.view.View.SCROLL_AXIS_NONE;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.StyleRes;
import android.annotation.SystemApi;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * This class is used to publish updates from an {@link InsightSurfaceClient} to a connected
 * {@link InsightSurfaceVisualizerService}. It is essentially a wrapper around a collection of
 * updatable client properties.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightSurfaceClientUpdate implements Parcelable {
    /** Key for a {@link android.view.View.MeasureSpec} width update. */
    public static final String KEY_MEASURE_SPEC_WIDTH = "key_measure_spec_width";

    /** Key for a {@link android.view.View.MeasureSpec} height update. */
    public static final String KEY_MEASURE_SPEC_HEIGHT = "key_measure_spec_height";

    /** Key for a background {@link Color} update. */
    public static final String KEY_BACKGROUND_COLOR = "key_background_color";

    /** Key for a nested scroll axes update. */
    public static final String KEY_NESTED_SCROLL_AXES = "key_nested_scroll_axes";

    /** Key for a nested axis locked update. */
    public static final String KEY_NESTED_SCROLL_AXIS_LOCKED = "key_nested_scroll_axis_locked";

    /** Key for should blur update. */
    public static final String KEY_SHOULD_BLUR = "key_should_blur";

    /** Key for a theme resource name update. */
    public static final String KEY_THEME_RESOURCE_NAME = "key_theme_resource_name";

    /** Key for a {@link Configuration} update. */
    public static final String KEY_CONFIGURATION = "key_configuration";

    private final Bundle mUpdateValues;

    private InsightSurfaceClientUpdate(Bundle updateValues) {
        mUpdateValues = updateValues;
    }

    private InsightSurfaceClientUpdate(Parcel in) {
        mUpdateValues = in.readBundle();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeBundle(mUpdateValues);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<InsightSurfaceClientUpdate> CREATOR =
            new Creator<>() {
                @Override
                public InsightSurfaceClientUpdate createFromParcel(Parcel in) {
                    return new InsightSurfaceClientUpdate(in);
                }

                @Override
                public InsightSurfaceClientUpdate[] newArray(int size) {
                    return new InsightSurfaceClientUpdate[size];
                }
            };

    /**
     * Returns whether the update contains the value with the given key.
     * @param key the update key to be queried
     * @return {@code true} if the update contains a value for the given key
     */
    public boolean hasUpdate(@NonNull String key) {
        return mUpdateValues.containsKey(key);
    }

    /**
     * Return the width {@link android.view.View.MeasureSpec} for this update, or
     * {@link View.MeasureSpec#UNSPECIFIED} if the update doesn't contain such a value.
     */
    public int getMeasureSpecWidth() {
        return hasUpdate(KEY_MEASURE_SPEC_WIDTH)
                ? mUpdateValues.getInt(KEY_MEASURE_SPEC_WIDTH)
                : View.MeasureSpec.UNSPECIFIED;
    }

    /**
     * Return the height {@link android.view.View.MeasureSpec} for this update, or
     * {@link View.MeasureSpec#UNSPECIFIED} if the update doesn't contain such a value.
     */
    public int getMeasureSpecHeight() {
        return hasUpdate(KEY_MEASURE_SPEC_HEIGHT)
                ? mUpdateValues.getInt(KEY_MEASURE_SPEC_HEIGHT)
                : View.MeasureSpec.UNSPECIFIED;
    }

    /**
     * Return the background {@link Color} for this update, or {@code null} if the update doesn't
     * contain such a value.
     */
    @Nullable
    public Color getBackgroundColor() {
        return hasUpdate(KEY_BACKGROUND_COLOR)
                ? Color.valueOf(mUpdateValues.getInt(KEY_BACKGROUND_COLOR))
                : null;
    }

    /**
     * Return the nested scroll axes for this update, or {@link android.view.View#SCROLL_AXIS_NONE}
     * if the update doesn't contain such a value.
     */
    public int getNestedScrollAxes() {
        return hasUpdate(KEY_NESTED_SCROLL_AXES)
                ? mUpdateValues.getInt(KEY_NESTED_SCROLL_AXES)
                : SCROLL_AXIS_NONE;
    }

    /**
     * Return the lock nested scrolling value for this update, or {@code false} if the update
     * doesn't contain such a value.
     */
    public boolean isNestedScrollAxisLocked() {
        return hasUpdate(KEY_NESTED_SCROLL_AXIS_LOCKED) && mUpdateValues.getBoolean(
                KEY_NESTED_SCROLL_AXIS_LOCKED);
    }

    /**
     * Return whether the embedded surface should apply a blur. This should be {@code true} when the
     * client view is blurred so that the embedded surface can also apply a blur to match it.
     */
    public boolean shouldBlur() {
        return hasUpdate(KEY_SHOULD_BLUR) && mUpdateValues.getBoolean(KEY_SHOULD_BLUR);
    }

    /**
     * Return the theme resource name update for this update, or {@link Resources#ID_NULL} if the
     * update doesn't contain such a value.
     *
     * @see InsightSurfaceClientInfo#getThemeResourceId()
     */
    @StyleRes
    public int getThemeResourceId() {
        return hasUpdate(KEY_THEME_RESOURCE_NAME)
                ? mUpdateValues.getInt(KEY_THEME_RESOURCE_NAME)
                : Resources.ID_NULL;
    }

    /**
     * Return the {@link Configuration} for this update, or {@code null} if the update doesn't
     * contain a configuration.
     */
    @Nullable
    public Configuration getConfiguration() {
        return hasUpdate(KEY_CONFIGURATION)
                ? mUpdateValues.getParcelable(KEY_CONFIGURATION, Configuration.class)
                : null;
    }

    /** Builder for {@link InsightSurfaceClientUpdate}. */
    public static final class Builder {
        private final Bundle mValues = new Bundle();

        public Builder() {
        }

        /**
         * Set the width {@link View.MeasureSpec} for the update.
         * @param measureSpecWidth the width {@link android.view.View.MeasureSpec}
         */
        @NonNull
        public Builder setMeasureSpecWidth(int measureSpecWidth) {
            mValues.putInt(KEY_MEASURE_SPEC_WIDTH, measureSpecWidth);
            return this;
        }

        /**
         * Set the height {@link View.MeasureSpec} for the update.
         * @param measureSpecHeight the height {@link android.view.View.MeasureSpec}
         */
        @NonNull
        public Builder setMeasureSpecHeight(int measureSpecHeight) {
            mValues.putInt(KEY_MEASURE_SPEC_HEIGHT, measureSpecHeight);
            return this;
        }

        /**
         * Set the nested scroll axes for the update.
         * @param nestedScrollAxes the nested scroll axes
         */
        @NonNull
        public Builder setNestedScrollAxes(int nestedScrollAxes) {
            mValues.putInt(KEY_NESTED_SCROLL_AXES, nestedScrollAxes);
            return this;
        }

        /**
         * Set whether nested scrolling is locked to the nested scroll axes for the update. Note
         * that this value can be set for the update even if the nested scroll axes aren't also
         * updated.
         * @param isLocked {@code true} if nested scrolling is locked
         */
        @NonNull
        public Builder setNestedScrollAxisLocked(boolean isLocked) {
            mValues.putBoolean(KEY_NESTED_SCROLL_AXIS_LOCKED, isLocked);
            return this;
        }

        /**
         * Set whether the embedded surface should apply a blur. Clients would set this to
         * {@code true} when the client view is blurred so that the embedded surface can also apply
         * a blur to match it.
         * @param shouldBlur {@code true} if the embedded surface should apply a blur
         */
        @NonNull
        public Builder setShouldBlur(boolean shouldBlur) {
            mValues.putBoolean(KEY_SHOULD_BLUR, shouldBlur);
            return this;
        }

        /**
         * Set the theme resource id for this update.
         * @see InsightSurfaceClient.Builder#setThemeResourceId(int)
         * @param themeResourceId the id of the theme resource, or {@code null} to clear it
         */
        @NonNull
        public Builder setThemeResourceId(@StyleRes int themeResourceId) {
            mValues.putInt(KEY_THEME_RESOURCE_NAME, themeResourceId);
            return this;
        }

        /**
         * Set the background color for the update.
         * @param backgroundColor the background color
         */
        @NonNull
        public Builder setBackgroundColor(@NonNull Color backgroundColor) {
            mValues.putInt(KEY_BACKGROUND_COLOR, backgroundColor.toArgb());
            return this;
        }

        /**
         * Set the {@link Configuration} for the update.
         * @param configuration the {@link Configuration}
         */
        @NonNull
        public Builder setConfiguration(@NonNull Configuration configuration) {
            mValues.putParcelable(KEY_CONFIGURATION, configuration);
            return this;
        }

        /**
         * Build and return a new {@link InsightSurfaceClientUpdate}.
         */
        @NonNull
        public InsightSurfaceClientUpdate build() {
            return new InsightSurfaceClientUpdate(mValues);
        }
    }
}
