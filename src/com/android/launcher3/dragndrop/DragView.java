/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.icons.FastBitmapDrawable.getDisabledColorFilter;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.appwidget.AppWidgetHostView;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.folder.ClippedFolderIconLayoutRule;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.ViewEx;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** A custom view for rendering an icon, folder, shortcut or widget during drag-n-drop. */
@SuppressLint("ViewConstructor")
public class DragView extends FrameLayout {

    public static final int VIEW_ZOOM_DURATION = 150;
    private static final int PARALLAX_MAX_IN_DP = 8;

    private final View mContent;
    // The following are only used for rendering mContent directly during drag-n-drop.
    @Nullable private final ViewGroup.LayoutParams mContentViewLayoutParams;
    @Nullable private ViewGroup mContentViewParent;
    private int mContentViewInParentViewIndex = -1;
    private final int mWidth;
    private final int mHeight;

    private final int mBlurSizeOutline;
    protected final int mRegistrationX;
    protected final int mRegistrationY;
    private final float mInitialScale;
    private final float mEndScale;
    protected final float mScaleOnDrop;
    protected final int[] mTempLoc = new int[2];

    private final RunnableList mOnDragStartCallback = new RunnableList();

    private boolean mHasDragOffset;
    private Rect mDragRegion = null;
    protected final ActivityContext mActivity;
    private final BaseDragLayer<?> mDragLayer;
    private boolean mHasDrawn = false;

    final ValueAnimator mScaleAnim;
    final ValueAnimator mShiftAnim;

    // Whether mAnim has started. Unlike mAnim.isStarted(), this is true even after mAnim ends.
    private boolean mScaleAnimStarted;
    private boolean mShiftAnimStarted;
    private Runnable mOnScaleAnimEndCallback;
    private Runnable mOnShiftAnimEndCallback;

    private int mLastTouchX;
    private int mLastTouchY;
    private int mAnimatedShiftX;
    private int mAnimatedShiftY;

    // Below variable only needed IF FeatureFlags.LAUNCHER3_SPRING_ICONS is {@code true}
    private Drawable mBgSpringDrawable, mFgSpringDrawable;

    // Indicates whether spring animated drawable should be hidden during the drag and drop
    // sequence, for example, when a system drag shadow is used for the drag.
    private final boolean mAllowSpringDrawable;

    private SpringFloatValue mTranslateX, mTranslateY;
    private Path mScaledMaskPath;
    private Drawable mBadge;
    private int mItemType;

    private final Set<Consumer<Float>> mOnAlphaChangeListeners = new HashSet<>();

    public DragView(ActivityContext launcher, Drawable drawable, int registrationX,
            int registrationY, final float initialScale, final float scaleOnDrop,
            final float finalScaleDps, boolean allowSpringDrawable) {
        this(launcher, getViewFromDrawable(launcher, drawable),
                drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
                registrationX, registrationY, initialScale, scaleOnDrop, finalScaleDps,
                allowSpringDrawable);
    }

    /**
     * Construct the drag view.
     * <p>
     * The registration point is the point inside our view that the touch events should
     * be centered upon.
     * @param activity      The Launcher instance/ActivityContext this DragView is in.
     * @param content       the view content that is attached to the drag view.
     * @param width         the width of the dragView
     * @param height        the height of the dragView
     * @param initialScale  The view that we're dragging around.  We scale it up when we draw it.
     * @param registrationX The x coordinate of the registration point.
     * @param registrationY The y coordinate of the registration point.
     * @param scaleOnDrop   the scale used in the drop animation.
     * @param finalScaleDps the scale used in the zoom out animation when the drag view is shown.
     * @param allowSpringDrawable whether the spring animated drag image should be shown.
     */
    public DragView(ActivityContext activity, View content, int width, int height,
            int registrationX, int registrationY, final float initialScale, final float scaleOnDrop,
            final float finalScaleDps, boolean allowSpringDrawable) {
        super(activity.asContext());
        mActivity = activity;
        mDragLayer = activity.getDragLayer();
        mAllowSpringDrawable = allowSpringDrawable;

        mContent = content;
        mWidth = width;
        mHeight = height;
        mContentViewLayoutParams = mContent.getLayoutParams();
        if (mContent.getParent() instanceof ViewGroup vg) {
            mContentViewParent = vg;
            mContentViewInParentViewIndex = mContentViewParent.indexOfChild(mContent);
            mContentViewParent.removeView(mContent);
        }

        // During a drag, we don't want to expose the descendants of drag view to a11y users,
        // as those descendants are not a valid position in the workspace.
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(content, new LayoutParams(width, height));

        // If there is already a scale set on the content, we don't want to clip the children.
        if (content.getScaleX() != 1 || content.getScaleY() != 1) {
            setClipChildren(false);
            setClipToPadding(false);
        }

        mEndScale = (width + finalScaleDps) / width;

        // Set the initial scale to avoid any jumps
        setScaleX(initialScale);
        setScaleY(initialScale);

        // Animate the view into the correct position
        mScaleAnim = ValueAnimator.ofFloat(0f, 1f);
        mScaleAnim.setDuration(VIEW_ZOOM_DURATION);
        mScaleAnim.addUpdateListener(animation -> {
            final float value = (Float) animation.getAnimatedValue();
            setScaleX(Utilities.mapRange(value, initialScale, mEndScale));
            setScaleY(Utilities.mapRange(value, initialScale, mEndScale));
            if (!isAttachedToWindow()) {
                animation.cancel();
            }
        });
        mScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mScaleAnimStarted = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mOnScaleAnimEndCallback != null) {
                    mOnScaleAnimEndCallback.run();
                }
            }
        });
        // Set up the shift animator.
        mShiftAnim = ValueAnimator.ofFloat(0f, 1f);
        mShiftAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mShiftAnimStarted = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mOnShiftAnimEndCallback != null) {
                    mOnShiftAnimEndCallback.run();
                }
            }
        });

        setDragRegion(new Rect(0, 0, width, height));

        // The point in our scaled bitmap that the touch events are located
        mRegistrationX = registrationX;
        mRegistrationY = registrationY;

        mInitialScale = initialScale;
        mScaleOnDrop = scaleOnDrop;

        // Force a measure, because Workspace uses getMeasuredHeight() before the layout pass
        measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));

        mBlurSizeOutline = getResources().getDimensionPixelSize(R.dimen.blur_size_medium_outline);
        setWillNotDraw(false);
    }

    /** Callback invoked when the scale animation ends. */
    public void setOnScaleAnimEndCallback(Runnable callback) {
        mOnScaleAnimEndCallback = callback;
    }

    /** Callback invoked when the shift animation ends. */
    public void setOnShiftAnimEndCallback(Runnable callback) {
        mOnShiftAnimEndCallback = callback;
    }

    /**
     * Initialize {@code #mIconDrawable} if the item can be represented using
     * an {@link AdaptiveIconDrawable} or {@link FolderAdaptiveIcon}.
     */
    @TargetApi(Build.VERSION_CODES.O)
    public void setItemInfo(final ItemInfo info) {
        mItemType = info.itemType;
        if (!mAllowSpringDrawable) {
            return;
        }

        // Load the adaptive icon on a background thread and add the view in ui thread.
        MODEL_EXECUTOR.getHandler().postAtFrontOfQueue(() -> {
            ThemeManager themeManager = ThemeManager.INSTANCE.get(getContext());
            int w = mWidth;
            int h = mHeight;
            var fullDrawable = mActivity.getActivityComponent().getIconLoader().getFullDrawable(
                    info, w, h, themeManager.isIconThemeEnabled());
            if (fullDrawable != null) {
                AdaptiveIconDrawable adaptiveIcon = fullDrawable.icon;
                int blurMargin = (int) getContext().getResources()
                        .getDimension(R.dimen.blur_size_medium_outline) / 2;

                Rect bounds = new Rect(0, 0, w, h);
                bounds.inset(blurMargin, blurMargin);
                // Badge is applied after icon normalization so the bounds for badge should not
                // be scaled down due to icon normalization.
                mBadge = fullDrawable.badge;
                FastBitmapDrawable.setBadgeBounds(mBadge, bounds);
                Utilities.scaleRectAboutCenter(bounds, IconNormalizer.ICON_VISIBLE_AREA_FACTOR);

                // Shrink very tiny bit so that the clip path is smaller than the original bitmap
                // that has anti aliased edges and shadows.
                Rect shrunkBounds = new Rect(bounds);
                Utilities.scaleRectAboutCenter(shrunkBounds, 0.98f);
                adaptiveIcon.setBounds(shrunkBounds);

                final Path mask;
                if (adaptiveIcon instanceof FolderAdaptiveIcon) {
                    // In order to not clip preview icons, need to account for both parallax
                    // as well padding for the icon overlap outside of the folder icon.
                    Rect maskBounds = new Rect(shrunkBounds);
                    Utilities.scaleRectAboutCenter(maskBounds,
                            ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR);
                    final int maxParallax =
                            (int) (getResources().getDisplayMetrics().density * PARALLAX_MAX_IN_DP);
                    maskBounds.inset(-maxParallax, -maxParallax);
                    mask = themeManager.getFolderShape().getPath(maskBounds);
                } else {
                    mask = themeManager.getIconShape().getPath(shrunkBounds);
                }

                mTranslateX = new SpringFloatValue(DragView.this,
                        w * AdaptiveIconDrawable.getExtraInsetFraction());
                mTranslateY = new SpringFloatValue(DragView.this,
                        h * AdaptiveIconDrawable.getExtraInsetFraction());

                bounds.inset(
                        (int) (-bounds.width() * AdaptiveIconDrawable.getExtraInsetFraction()),
                        (int) (-bounds.height() * AdaptiveIconDrawable.getExtraInsetFraction())
                );
                mBgSpringDrawable = adaptiveIcon.getBackground();
                if (mBgSpringDrawable == null) {
                    mBgSpringDrawable = new ColorDrawable(Color.TRANSPARENT);
                }
                mBgSpringDrawable.setBounds(bounds);
                mFgSpringDrawable = adaptiveIcon.getForeground();
                if (mFgSpringDrawable == null) {
                    mFgSpringDrawable = new ColorDrawable(Color.TRANSPARENT);
                }
                mFgSpringDrawable.setBounds(bounds);

                new Handler(mActivity.asContext().getMainLooper()).post(() ->
                        mOnDragStartCallback.add(() -> {
                            // TODO: Consider fade-in animation
                            // Assign the variable on the UI thread to avoid race conditions.
                            mScaledMaskPath = mask;
                            // Avoid relayout as we do not care about children affecting layout
                            removeAllViewsInLayout();

                            if (info.isDisabled()) {
                                ColorFilter filter = getDisabledColorFilter();
                                mBgSpringDrawable.setColorFilter(filter);
                                mFgSpringDrawable.setColorFilter(filter);
                                mBadge.setColorFilter(filter);
                            }
                            invalidate();
                }));
            }
        });
    }

    /**
     * Called when pre-drag finishes for an icon
     */
    public void onDragStart() {
        mOnDragStartCallback.executeAllAndDestroy();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(makeMeasureSpec(mWidth, EXACTLY), makeMeasureSpec(mHeight, EXACTLY));
    }

    public int getDragRegionWidth() {
        return mDragRegion.width();
    }

    public int getDragRegionHeight() {
        return mDragRegion.height();
    }

    public void setHasDragOffset(boolean hasDragOffset) {
        mHasDragOffset = hasDragOffset;
    }

    public boolean getHasDragOffset() {
        return mHasDragOffset;
    }

    public void setDragRegion(Rect r) {
        mDragRegion = r;
    }

    public Rect getDragRegion() {
        return mDragRegion;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        // Draw after the content
        mHasDrawn = true;
        if (mScaledMaskPath != null) {
            int cnt = canvas.save();
            canvas.clipPath(mScaledMaskPath);
            mBgSpringDrawable.draw(canvas);
            canvas.translate(mTranslateX.mValue, mTranslateY.mValue);
            mFgSpringDrawable.draw(canvas);
            canvas.restoreToCount(cnt);
            mBadge.draw(canvas);
        }
    }

    public void crossFadeContent(Drawable crossFadeDrawable, int duration) {
        if (mContent.getParent() == null) {
            // If the content is already removed, ignore
            return;
        }
        ImageView newContent = getViewFromDrawable(mActivity, crossFadeDrawable);
        // We need to fill the ImageView with the content, otherwise the shapes of the final view
        // and the drag view might not match exactly
        newContent.setScaleType(ImageView.ScaleType.FIT_XY);
        newContent.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        newContent.measure(makeMeasureSpec(mWidth, EXACTLY), makeMeasureSpec(mHeight, EXACTLY));
        newContent.layout(0, 0, mWidth, mHeight);
        addViewInLayout(newContent, 0, new LayoutParams(mWidth, mHeight));

        AnimatorSet anim = new AnimatorSet();
        anim.play(ObjectAnimator.ofFloat(newContent, VIEW_ALPHA, 0, 1));
        anim.play(ObjectAnimator.ofFloat(mContent, VIEW_ALPHA, 0));
        anim.setDuration(duration).setInterpolator(Interpolators.DECELERATE_1_5);
        anim.start();
    }

    public boolean hasDrawn() {
        return mHasDrawn;
    }

    /**
     * Create a window containing this view and show it.
     *
     * @param touchX the x coordinate the user touched in DragLayer coordinates
     * @param touchY the y coordinate the user touched in DragLayer coordinates
     */
    public void show(int touchX, int touchY) {
        mDragLayer.addView(this);

        // Start the pick-up animation
        BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(mWidth, mHeight);
        lp.customPosition = true;
        setLayoutParams(lp);

        if (mContent != null) {
            // At the drag start, the source view visibility is set to invisible.
            if (getHasDragOffset()) {
                // If there is any dragOffset, this means the content will show away of the original
                // icon location, otherwise it's fine since original content would just show at the
                // same spot.
                mContent.setVisibility(INVISIBLE);
            } else {
                mContent.setVisibility(VISIBLE);
            }
        }

        move(touchX, touchY);
        // Post the animation to skip other expensive work happening on the first frame
        post(mScaleAnim::start);
    }

    public void cancelAnimation() {
        if (mScaleAnim != null && mScaleAnim.isRunning()) {
            mScaleAnim.cancel();
        }
    }

    /** {@code true} if the scale animation has finished. */
    public boolean isScaleAnimationFinished() {
        return mScaleAnimStarted && !mScaleAnim.isRunning();
    }

    /** {@code true} if the shift animation has finished. */
    public boolean isShiftAnimationFinished() {
        return mShiftAnimStarted && !mShiftAnim.isRunning();
    }

    /**
     * Move the window containing this view.
     *
     * @param touchX the x coordinate the user touched in DragLayer coordinates
     * @param touchY the y coordinate the user touched in DragLayer coordinates
     */
    public void move(int touchX, int touchY) {
        if (touchX > 0 && touchY > 0 && mLastTouchX > 0 && mLastTouchY > 0
                && mScaledMaskPath != null) {
            mTranslateX.animateToPos(mLastTouchX - touchX);
            mTranslateY.animateToPos(mLastTouchY - touchY);
        }
        mLastTouchX = touchX;
        mLastTouchY = touchY;
        applyTranslation();
    }

    /**
     * Animate this DragView to the given DragLayer coordinates and then remove it.
     */
    public void animateTo(int toTouchX, int toTouchY, Runnable onCompleteRunnable,
            int duration) {
        Runnable onAnimationEnd = () -> {
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
            mActivity.getDragLayer().removeView(this);
        };

        duration = Math.max(duration,
                getResources().getInteger(R.integer.config_dropAnimMinDuration));

        animate()
                .translationX(toTouchX - mRegistrationX)
                .translationY(toTouchY - mRegistrationY)
                .scaleX(mScaleOnDrop)
                .scaleY(mScaleOnDrop)
                .withEndAction(onAnimationEnd)
                .setDuration(duration)
                .start();
    }

    public void animateShift(final int shiftX, final int shiftY) {
        if (mShiftAnim.isStarted()) return;

        // Set mContent visibility to visible to show icon regardless in case it is INVISIBLE.
        if (mContent != null) mContent.setVisibility(VISIBLE);

        mAnimatedShiftX = shiftX;
        mAnimatedShiftY = shiftY;
        applyTranslation();
        mShiftAnim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = 1 - animation.getAnimatedFraction();
                mAnimatedShiftX = (int) (fraction * shiftX);
                mAnimatedShiftY = (int) (fraction * shiftY);
                applyTranslation();
            }
        });
        mShiftAnim.start();
    }

    private void applyTranslation() {
        setTranslationX(mLastTouchX - mRegistrationX + mAnimatedShiftX);
        setTranslationY(mLastTouchY - mRegistrationY + mAnimatedShiftY);
    }

    /**
     * Detaches {@link #mContent}, if previously attached, from this view.
     *
     * <p>In the case of no change in the drop position, sets {@code reattachToPreviousParent} to
     * {@code true} to attach the {@link #mContent} back to its previous parent.
     */
    public void detachContentView(boolean reattachToPreviousParent) {
        if (mContent != null && mContentViewParent != null && mContentViewInParentViewIndex >= 0) {
            Drawable snapshotDrawable = ViewEx.captureSnapshotAsDrawable(mContent,
                    /*debugString=*/ "DragViewDrop", mWidth, mHeight);

            View view = new View(mActivity.asContext());
            view.setBackground(snapshotDrawable);
            view.measure(makeMeasureSpec(mWidth, EXACTLY), makeMeasureSpec(mHeight, EXACTLY));
            view.layout(mContent.getLeft(), mContent.getTop(),
                    mContent.getRight(), mContent.getBottom());
            setClipToOutline(mContent.getClipToOutline());
            setOutlineProvider(mContent.getOutlineProvider());
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addViewInLayout(view, indexOfChild(mContent), mContent.getLayoutParams(), true);

            removeViewInLayout(mContent);
            mContent.setVisibility(INVISIBLE);
            mContent.setLayoutParams(mContentViewLayoutParams);
            if (reattachToPreviousParent) {
                mContentViewParent.addView(mContent, mContentViewInParentViewIndex);
            }
            mContent.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            mContentViewParent = null;
            mContentViewInParentViewIndex = -1;
        }
    }

    /**
     * Removes this view from the {@link DragLayer}.
     *
     * <p>If the drag content is a {@link #mContent}, this call doesn't reattach the
     * {@link #mContent} back to its previous parent. To reattach to previous parent, the caller
     * should call {@link #detachContentView} with {@code reattachToPreviousParent} sets to true
     * before this call.
     */
    public void remove() {
        if (getParent() != null) {
            mDragLayer.removeView(DragView.this);
        }
    }

    public int getBlurSizeOutline() {
        return mBlurSizeOutline;
    }

    public int getRegistrationX() {
        return mRegistrationX;
    }

    public int getRegistrationY() {
        return mRegistrationY;
    }

    public float getInitialScale() {
        return mInitialScale;
    }

    public float getEndScale() {
        return mEndScale;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Returns the current content view that is rendered in the drag view. */
    public View getContentView() {
        return mContent;
    }

    /**
     * Returns the previous {@link ViewGroup} parent of the {@link #mContent} before the drag
     * content is attached to this view.
     */
    @Nullable
    public ViewGroup getContentViewParent() {
        return mContentViewParent;
    }

    /** Return true if {@link #mContent} is a {@link AppWidgetHostView}. */
    public boolean containsAppWidgetHostView() {
        return mContent instanceof AppWidgetHostView;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mOnAlphaChangeListeners.clear();
    }

    @Override
    public void setAlpha(@FloatRange(from = 0.0, to = 1.0) float alpha) {
        final float oldAlpha = getAlpha();
        super.setAlpha(alpha);
        if (oldAlpha != alpha) {
            for (final Consumer<Float> listener : mOnAlphaChangeListeners) {
                listener.accept(alpha);
            }
        }
    }

    /**
     * Registers a listener to be notified of alpha change events. Note that listeners are
     * automatically removed in {@link #onDetachedFromWindow()}.
     */
    public void addOnAlphaChangeListener(Consumer<Float> listener) {
        mOnAlphaChangeListeners.add(listener);
    }

    private static class SpringFloatValue {

        private static final FloatPropertyCompat<SpringFloatValue> VALUE =
                new FloatPropertyCompat<SpringFloatValue>("value") {
                    @Override
                    public float getValue(SpringFloatValue object) {
                        return object.mValue;
                    }

                    @Override
                    public void setValue(SpringFloatValue object, float value) {
                        object.mValue = value;
                        object.mView.invalidate();
                    }
                };

        // Following three values are fine tuned with motion ux designer
        private static final int STIFFNESS = 4000;
        private static final float DAMPENING_RATIO = 1f;

        private final View mView;
        private final SpringAnimation mSpring;
        private final float mDelta;

        private float mValue;

        public SpringFloatValue(View view, float range) {
            mView = view;
            mSpring = new SpringAnimation(this, VALUE, 0)
                    .setMinValue(-range).setMaxValue(range)
                    .setSpring(new SpringForce(0)
                            .setDampingRatio(DAMPENING_RATIO)
                            .setStiffness(STIFFNESS));
            mDelta = Math.min(
                    range, view.getResources().getDisplayMetrics().density * PARALLAX_MAX_IN_DP);
        }

        public void animateToPos(float value) {
            mSpring.animateToFinalPosition(Utilities.boundToRange(value, -mDelta, mDelta));
        }
    }

    private static ImageView getViewFromDrawable(ActivityContext context, Drawable drawable) {
        ImageView iv = new ImageView(context.asContext());
        iv.setImageDrawable(drawable);
        return iv;
    }

    /**
     * Removes any stray DragView from the DragLayer.
     */
    public static void removeAllViews(@NonNull ActivityContext activity) {
        BaseDragLayer dragLayer = activity.getDragLayer();
        // Iterate in reverse order. DragView is added later to the dragLayer,
        // and will be one of the last views.
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof DragView d) {
                // Widgets uses a listener to remove views.
                // When widgets are dropped from another window, we don't want to remove the
                // dragView on resume of launcher.
                if (d.mItemType != ITEM_TYPE_APPWIDGET
                        && d.mItemType != ITEM_TYPE_DEEP_SHORTCUT) {
                    dragLayer.removeView(child);
                }
            }
        }
    }
}
