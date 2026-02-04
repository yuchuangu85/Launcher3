/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import android.view.ViewDebug;
import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

/**
 * Used to draw a notification dot on top of an icon.
 */
public class DotRenderer {

    private static final String TAG = "DotRenderer";

    // The dot size is defined as a percentage of the app icon size.
    private static final float SIZE_PERCENTAGE = 0.228f;
    
    // Lawnchair
    private static final float SIZE_PERCENTAGE_WITH_COUNT = 0.348f;
    private static final int MAX_COUNT = 99; // The max number to draw on dots

    private final float mCircleRadius;
    private final Paint mCirclePaint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);

    // Lawnchair
    private final Paint mTextPaint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);

    private final Bitmap mBackgroundWithShadow;
    private final float mBitmapOffset;

    // Stores the center x and y position as a percentage (0 to 1) of the icon size
    private final float[] mRightDotPosition;
    private final float[] mLeftDotPosition;

    private boolean mDisplayCount;
    
    // Lawnchair
    @ColorInt
    private int mColor;
    @ColorInt
    private int mCounterColor;
    private final Rect mTextRect = new Rect();

    private static final int MIN_DOT_SIZE = 1;
    
    /**
     * AOSP's dot renderer with Lawnchair related change to show notification count on a dot.
     *
     * @param iconSizePx
     * @param iconShapePath
     * @param pathSize
     */
    public DotRenderer(int iconSizePx, Path iconShapePath, int pathSize, Boolean displayCount, Typeface typeface, @ColorInt int color, @ColorInt int counterColor) {
        mDisplayCount = displayCount;
        mColor = color;
        mCounterColor = counterColor;
        int size = Math.round((displayCount ? SIZE_PERCENTAGE_WITH_COUNT : SIZE_PERCENTAGE) * iconSizePx);
        if (size <= 0) {
            size = MIN_DOT_SIZE;
        }
        ShadowGenerator.Builder builder = new ShadowGenerator.Builder(Color.TRANSPARENT);
        builder.ambientShadowAlpha = 88;
        mBackgroundWithShadow = builder.setupBlurForSize(size).createPill(size, size);
        mCircleRadius = builder.radius;

        mBitmapOffset = -mBackgroundWithShadow.getHeight() * 0.5f; // Same as width.

        // Find the points on the path that are closest to the top left and right corners.
        mLeftDotPosition = getPathPoint(iconShapePath, pathSize, -1);
        mRightDotPosition = getPathPoint(iconShapePath, pathSize, 1);
        
        mTextPaint.setTextSize(size * 0.65f);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setTypeface(typeface);
    }


    /**
     * AOSP's dot renderer. To use notification count on the dot see {@link #DotRenderer(int, Path, int, Boolean, Typeface, int, int)}
     * 
     * @param iconSizePx 
     * @param iconShapePath
     * @param pathSize
     */
    public DotRenderer(int iconSizePx, Path iconShapePath, int pathSize) {
        int size = Math.round(SIZE_PERCENTAGE * iconSizePx);
        if (size <= 0) {
            size = MIN_DOT_SIZE;
        }
        ShadowGenerator.Builder builder = new ShadowGenerator.Builder(Color.TRANSPARENT);
        builder.ambientShadowAlpha = 88;
        mBackgroundWithShadow = builder.setupBlurForSize(size).createPill(size, size);
        mCircleRadius = builder.radius;

        mBitmapOffset = -mBackgroundWithShadow.getHeight() * 0.5f; // Same as width.

        // Find the points on the path that are closest to the top left and right corners.
        mLeftDotPosition = getPathPoint(iconShapePath, pathSize, -1);
        mRightDotPosition = getPathPoint(iconShapePath, pathSize, 1);
    }

    private static float[] getPathPoint(Path path, float size, float direction) {
        float halfSize = size / 2;
        // Small delta so that we don't get a zero size triangle
        float delta = 1;

        float x = halfSize + direction * halfSize;
        Path trianglePath = new Path();
        trianglePath.moveTo(halfSize, halfSize);
        trianglePath.lineTo(x + delta * direction, 0);
        trianglePath.lineTo(x, -delta);
        trianglePath.close();

        trianglePath.op(path, Path.Op.INTERSECT);
        float[] pos = new float[2];
        new PathMeasure(trianglePath, false).getPosTan(0, pos, null);

        pos[0] = pos[0] / size;
        pos[1] = pos[1] / size;
        return pos;
    }

    public float[] getLeftDotPosition() {
        return mLeftDotPosition;
    }

    public float[] getRightDotPosition() {
        return mRightDotPosition;
    }

    /**
     * LC: Draw a circle on top of the canvas according to the given params.
     * 
     * Include: notification number counter
     */
    public void draw(Canvas canvas, DrawParams params, int numNotifications) {
        if (params == null) {
            Log.e(TAG, "Invalid null argument(s) passed in call to draw.");
            return;
        }
        canvas.save();

        Rect iconBounds = params.iconBounds;
        float[] dotPosition = params.leftAlign ? mLeftDotPosition : mRightDotPosition;
        float dotCenterX = iconBounds.left + iconBounds.width() * dotPosition[0];
        float dotCenterY = iconBounds.top + iconBounds.height() * dotPosition[1];

        // Ensure dot fits entirely in canvas clip bounds.
        Rect canvasBounds = canvas.getClipBounds();
        float offsetX = params.leftAlign
            ? Math.max(0, canvasBounds.left - (dotCenterX + mBitmapOffset))
            : Math.min(0, canvasBounds.right - (dotCenterX - mBitmapOffset));
        float offsetY = Math.max(0, canvasBounds.top - (dotCenterY + mBitmapOffset));

        // We draw the dot relative to its center.
        canvas.translate(dotCenterX + offsetX, dotCenterY + offsetY);
        canvas.scale(params.scale, params.scale);

        mCirclePaint.setColor(Color.BLACK);
        canvas.drawBitmap(mBackgroundWithShadow, mBitmapOffset, mBitmapOffset, mCirclePaint);
        mCirclePaint.setColor(params.dotColor);
        canvas.drawCircle(0, 0, mCircleRadius, mCirclePaint);

        if (mDisplayCount && numNotifications > 0) {
            // Draw the numNotifications text
            final int counterColor;
            if (mCounterColor != 0) {
                counterColor = mCounterColor;
            } else {
                counterColor = getCounterTextColor(params.dotColor);
            }
            mTextPaint.setColor(counterColor);
            String text = String.valueOf(Math.min(numNotifications, MAX_COUNT));
            mTextPaint.getTextBounds(text, 0, text.length(), mTextRect);
            float x = (-mTextRect.width() / 2f - mTextRect.left) * getAdjustment(numNotifications);
            float y = mTextRect.height() / 2f - mTextRect.bottom;
            canvas.drawText(text, x, y, mTextPaint);
        }

        canvas.restore();
    }

    /**
     * Draw a circle on top of the canvas according to the given params.
     * 
     * This is the original AOSP method without notification count feature. To use it with count see {@link #draw(Canvas, DrawParams, int)}
     */
    public void draw(Canvas canvas, DrawParams params) {
        if (params == null) {
            Log.e(TAG, "Invalid null argument(s) passed in call to draw.");
            return;
        }
        canvas.save();

        Rect iconBounds = params.iconBounds;
        float[] dotPosition = params.leftAlign ? mLeftDotPosition : mRightDotPosition;
        float dotCenterX = iconBounds.left + iconBounds.width() * dotPosition[0];
        float dotCenterY = iconBounds.top + iconBounds.height() * dotPosition[1];

        // Ensure dot fits entirely in canvas clip bounds.
        Rect canvasBounds = canvas.getClipBounds();
        float offsetX = params.leftAlign
                ? Math.max(0, canvasBounds.left - (dotCenterX + mBitmapOffset))
                : Math.min(0, canvasBounds.right - (dotCenterX - mBitmapOffset));
        float offsetY = Math.max(0, canvasBounds.top - (dotCenterY + mBitmapOffset));

        // We draw the dot relative to its center.
        canvas.translate(dotCenterX + offsetX, dotCenterY + offsetY);
        canvas.scale(params.scale, params.scale);

        mCirclePaint.setColor(Color.BLACK);
        canvas.drawBitmap(mBackgroundWithShadow, mBitmapOffset, mBitmapOffset, mCirclePaint);
        mCirclePaint.setColor(params.dotColor);
        canvas.drawCircle(0, 0, mCircleRadius, mCirclePaint);
        canvas.restore();
    }

    /**
     * LC: An attempt to adjust digits to their perceived center, they were tuned with Roboto but should
     * (hopefully) work with other OEM fonts as well.
     */
    private float getAdjustment(int number) {
        return switch (number) {
            case 1 -> 1.01f;
            case 2 -> 0.99f;
            case 3, 4, 6 -> 0.98f;
            case 7 -> 1.02f;
            case 9 -> 0.9f;
            default -> 1f;
        };
    }

    /**
     * LC: Returns the color to use for the counter text based on the dot's background color.
     *
     * @param dotBackgroundColor The color of the dot background.
     * @return The color to use on the counter text.
     */
    private int getCounterTextColor(int dotBackgroundColor) {
        return new Palette.Swatch(ColorUtils.setAlphaComponent(dotBackgroundColor, 0xFF), 1).getBodyTextColor();
    }

    public static class DrawParams {
        /** The color (possibly based on the icon) to use for the dot. */
        @ViewDebug.ExportedProperty(category = "notification dot", formatToHexString = true)
        public int dotColor;
        /** The color (possibly based on the icon) to use for a predicted app. */
        @ViewDebug.ExportedProperty(category = "notification dot", formatToHexString = true)
        public int appColor;
        /** The bounds of the icon that the dot is drawn on top of. */
        @ViewDebug.ExportedProperty(category = "notification dot")
        public Rect iconBounds = new Rect();
        /** The progress of the animation, from 0 to 1. */
        @ViewDebug.ExportedProperty(category = "notification dot")
        public float scale;
        /** Whether the dot should align to the top left of the icon rather than the top right. */
        @ViewDebug.ExportedProperty(category = "notification dot")
        public boolean leftAlign;
    }
}
