/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.app.WallpaperColors;
import android.content.Context;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.inject.Inject;

/** Extracts the colors we need from the wallpaper at given locations. */
public class LocalColorExtractor {

    @Inject
    public LocalColorExtractor() {
    }

    /**
     * Updates the base context to contain the colors override
     */
    @Nullable
    public ColorsOverride applyColorsOverride(Context base, WallpaperColors colors) {
        return null;
    }

    /**
     * Generates color resource overrides from {@link WallpaperColors}.
     */
    @Nullable
    public SparseIntArray generateColorsOverride(WallpaperColors colors) {
        return null;
    }

    /**
     * Updates the base context to contain the colors override
     */
    @Nullable
    public ColorsOverride applyColorsOverride(Context base, SparseIntArray override) {
        return null;
    }

    /**
     * Updates the base context with a color overlay generated using the seeds colors and style, and
     * returns a {@link ColorsOverride} for using the override
     */
    @Nullable
    public ColorsOverride applyColorOverlay(@NonNull Context base, @NonNull int[] seedColors,
            int style) {
        return null;
    }
}
