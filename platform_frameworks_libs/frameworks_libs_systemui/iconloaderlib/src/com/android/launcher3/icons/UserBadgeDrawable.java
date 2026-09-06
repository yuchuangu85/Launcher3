/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.icons;

import static com.android.launcher3.icons.GraphicsUtils.getColorMultipliedFilter;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * A drawable used for drawing user badge. It draws a circle around the actual badge,
 * and has support for theming.
 */
public class UserBadgeDrawable extends DrawableWrapper {

    public static final float VIEWPORT_SIZE = 24;
    private static final float CENTER = VIEWPORT_SIZE / 2;

    public static final float BG_RADIUS = 11;
    public static final float SHADOW_RADIUS = 11.5f;
    public static final float SHADOW_OFFSET_Y = 0.25f;

    public static final int SHADOW_COLOR = 0x11000000;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int mBgColor;
    private final int mBaseColor;

    public UserBadgeDrawable(Drawable base, int bgColor, int baseColor) {
        super(base);
        mutate();
        mBgColor = bgColor;
        mBaseColor = baseColor;
        setTint(mBaseColor);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        int saveCount = canvas.save();
        canvas.translate(b.left, b.top);
        canvas.scale(b.width() / VIEWPORT_SIZE, b.height() / VIEWPORT_SIZE);

        mPaint.setColor(blendDrawableAlpha(SHADOW_COLOR));
        canvas.drawCircle(CENTER, CENTER + SHADOW_OFFSET_Y, SHADOW_RADIUS, mPaint);
        mPaint.setColor(blendDrawableAlpha(mBgColor));
        canvas.drawCircle(CENTER, CENTER, BG_RADIUS, mPaint);
        canvas.restoreToCount(saveCount);

        super.draw(canvas);
    }

    private @ColorInt int blendDrawableAlpha(@ColorInt int color) {
        int alpha = (int) (Color.valueOf(color).alpha() * getAlpha());
        return ColorUtils.setAlphaComponent(color, alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        super.setColorFilter(getColorMultipliedFilter(mBaseColor, filter));
    }

    @Override
    public ConstantState getConstantState() {
        return new MyConstantState(
                getDrawable().getConstantState(), mBgColor, mBaseColor);
    }

    private static class MyConstantState extends ConstantState {

        private final ConstantState mBase;
        private final int mBgColor;
        private final int mBaseColor;

        MyConstantState(ConstantState base, int bgColor, int baseColor) {
            mBase = base;
            mBgColor = bgColor;
            mBaseColor = baseColor;
        }

        @Override
        public int getChangingConfigurations() {
            return mBase.getChangingConfigurations();
        }

        @Override
        @NonNull
        public Drawable newDrawable() {
            return new UserBadgeDrawable(
                    mBase.newDrawable(), mBgColor, mBaseColor);
        }

        @Override
        @NonNull
        public Drawable newDrawable(Resources res) {
            return new UserBadgeDrawable(
                    mBase.newDrawable(res), mBgColor, mBaseColor);
        }

        @Override
        @NonNull
        public Drawable newDrawable(Resources res, Theme theme) {
            return new UserBadgeDrawable(
                    mBase.newDrawable(res, theme), mBgColor, mBaseColor);
        }
    }
}
