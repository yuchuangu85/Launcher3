/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.preview;

import static android.content.res.Configuration.UI_MODE_NIGHT_NO;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.LauncherPrefs.GRID_NAME;
import static com.android.launcher3.LauncherPrefs.NON_FIXED_LANDSCAPE_GRID_NAME;
import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.widget.LauncherWidgetHolder.APPWIDGET_HOST_ID;

import android.app.WallpaperColors;
import android.appwidget.AppWidgetHost;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.graphics.GridCustomizationsProxy;
import com.android.launcher3.preview.PreviewContext.PreviewAppComponent;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.Themes;
import com.android.launcher3.widget.ColorsOverride;
import com.android.launcher3.widget.LocalColorExtractor;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Render preview using surface view. */
@SuppressWarnings("NewApi")
public class PreviewSurfaceRenderer {

    public static final long FADE_IN_ANIMATION_DURATION = 200;
    public static final String KEY_HOST_TOKEN = "host_token";
    public static final String KEY_VIEW_WIDTH = "width";
    public static final String KEY_VIEW_HEIGHT = "height";
    public static final String KEY_DISPLAY_ID = "display_id";
    private static final String KEY_WORKSPACE_ITEMS_LABELS_HIDDEN = "workspace_items_label_hidden";
    public static final String KEY_COLORS = "wallpaper_colors";
    public static final String KEY_COLOR_RESOURCE_IDS = "color_resource_ids";
    public static final String KEY_COLOR_VALUES = "color_values";
    public static final String KEY_SEED_COLOR_LIST = "seed_color_list";
    public static final String KEY_THEME_STYLE = "theme_style";
    public static final String KEY_DARK_MODE = "use_dark_mode";
    public static final String KEY_LAYOUT_XML = "layout_xml";
    public static final String KEY_BITMAP_GENERATION_DELAY_MS = "bitmap_delay_ms";
    // Wait for some time before capturing screenshot to allow the surface to be laid out
    public static final long MIN_BITMAP_GENERATION_DELAY_MS = 100L;

    public static final String KEY_WORKSPACE_PAGE_ID = "workspace_page_id";
    public static final String FIXED_LANDSCAPE_GRID = "fixed_landscape_mode";

    private final Context mContext;
    private final int mWorkspacePageId;

    private final PreviewContext mPreviewContext;
    private final PreviewAppComponent mAppComponent;


    private SparseIntArray mPreviewColorOverride;

    @Nullable private Boolean mDarkMode;
    private boolean mDestroyed = false;
    private boolean mHideQsb;

    private final FrameLayout mViewRoot;
    private final IBinder mHostToken;
    private final int mWidth;
    private final int mHeight;
    private final boolean mSkipAnimations;
    private final int mDisplayId;
    private final WallpaperColors mWallpaperColors;
    private final RunnableList mLifeCycleTracker;
    private final SurfaceControlViewHost mSurfaceControlViewHost;

    private final InvariantDeviceProfile.OnIDPChangeListener mOnIDPChangeListener =
            modelPropertiesChanged -> recreatePreviewRenderer();

    private LauncherPreviewRenderer mCurrentRenderer;

    @Nullable private SurfaceControlViewHost.SurfacePackage mSurfacePackage;

    private int[] mSeedColorList;
    private int mThemeStyle = -1;
    private ColorsOverride mColorsOverride;

    public PreviewSurfaceRenderer(Context context, RunnableList lifecycleTracker, Bundle bundle,
            int callingPid, boolean skipAnimations) throws Exception {
        mContext = context;
        mLifeCycleTracker = lifecycleTracker;

        mWallpaperColors = bundle.getParcelable(KEY_COLORS);
        updateColorOverrides(bundle);

        mHideQsb = bundle.getBoolean(GridCustomizationsProxy.KEY_HIDE_BOTTOM_ROW);

        mHostToken = bundle.getBinder(KEY_HOST_TOKEN);
        mWidth = bundle.getInt(KEY_VIEW_WIDTH);
        mHeight = bundle.getInt(KEY_VIEW_HEIGHT);
        mSkipAnimations = skipAnimations;
        mDisplayId = bundle.getInt(KEY_DISPLAY_ID);
        Display display = context.getSystemService(DisplayManager.class)
                .getDisplay(mDisplayId);
        mWorkspacePageId = bundle.getInt(KEY_WORKSPACE_PAGE_ID, FIRST_SCREEN_ID);
        if (display == null) {
            throw new IllegalArgumentException("Display ID does not match any displays.");
        }

        mSurfaceControlViewHost = MAIN_EXECUTOR.submit(() -> new MySurfaceControlViewHost(
                        mContext,
                        context.getSystemService(DisplayManager.class).getDisplay(DEFAULT_DISPLAY),
                        mHostToken,
                        mLifeCycleTracker))
                .get(5, TimeUnit.SECONDS);
        mLifeCycleTracker.add(this::destroy);

        // Create the preview context
        String layoutXml = bundle.getString(KEY_LAYOUT_XML);
        boolean isCustomLayout = !TextUtils.isEmpty(layoutXml);
        int widgetHostId = isCustomLayout ? APPWIDGET_HOST_ID + callingPid : APPWIDGET_HOST_ID;


        String gridName = bundle.getString("name");
        bundle.remove("name");
        if (gridName == null) {
            gridName = LauncherPrefs.get(context).get(GRID_NAME);
        }
        if (Objects.equals(gridName, FIXED_LANDSCAPE_GRID)) {
            gridName = LauncherPrefs.get(context).get(NON_FIXED_LANDSCAPE_GRID_NAME);
        }

        mPreviewContext = new PreviewContext(
                context.createDisplayContext(display),
                gridName,
                widgetHostId,
                layoutXml,
                mWorkspacePageId,
                bundle.getBoolean(KEY_WORKSPACE_ITEMS_LABELS_HIDDEN, false)
        );

        mViewRoot = new FrameLayout(mPreviewContext);
        mAppComponent = (PreviewAppComponent) LauncherComponentProvider.get(mPreviewContext);
        mLifeCycleTracker.add(mPreviewContext::onDestroy);

        // Initialize events related to display of existing items
        mAppComponent.getModelInitializer().initializeDisplayEvents(mAppComponent.getModel());

        // When using a custom layout, reset the widget host on destroy
        if (isCustomLayout) {
            mLifeCycleTracker.add(() -> {
                AppWidgetHost host = new AppWidgetHost(mContext, widgetHostId);
                // Start listening here, so that any previous active host is disabled
                host.startListening();
                host.stopListening();
                host.deleteHost();
            });
        }

        MAIN_EXECUTOR.submit(() -> {
            mSurfaceControlViewHost.setView(mViewRoot, mWidth, mHeight);
            if (!skipAnimations) mViewRoot.setAlpha(0);

            mAppComponent.getIDP().addOnChangeListener(mOnIDPChangeListener);
            recreatePreviewRenderer();
        }).get();
    }

    public int getDisplayId() {
        return mDisplayId;
    }

    public IBinder getHostToken() {
        return mHostToken;
    }

    public SurfaceControlViewHost getHost() {
        return mSurfaceControlViewHost;
    }

    private void destroy() {
        mDestroyed = true;
        if (mSurfacePackage != null) {
            mSurfacePackage.release();
            mSurfacePackage = null;
        }
        mSurfaceControlViewHost.release();
        MAIN_EXECUTOR.execute(() -> {
            mAppComponent.getIDP().removeOnChangeListener(mOnIDPChangeListener);
            if (mCurrentRenderer != null) {
                mCurrentRenderer.onViewDestroyed();
            }
            mCurrentRenderer = null;
        });
    }

    /**
     * Generates the preview in background and returns the generated view
     */
    public CompletableFuture<View> loadAsync() {
        LauncherPreviewRenderer renderer = mCurrentRenderer;
        return renderer != null ? renderer.initialRender : CompletableFuture.completedFuture(null);
    }

    /**
     * Hides the components in the bottom row.
     *
     * @param hide True to hide and false to show.
     */
    public void hideBottomRow(boolean hide) {
        mHideQsb = hide;
        MAIN_EXECUTOR.execute(() -> {
            if (mCurrentRenderer != null) mCurrentRenderer.hideBottomRow(hide);
        });
    }

    /**
     * Updates the colors of the preview.
     *
     * @param bundle Bundle with an int array of color ids and an int array of overriding colors.
     */
    public void previewColor(Bundle bundle) {
        if (!updateColorOverrides(bundle)) return;
        MAIN_EXECUTOR.execute(this::recreatePreviewRenderer);
    }

    /**
     * Returns the [GridCustomizationsProxy] tried to this preview instance
     */
    public GridCustomizationsProxy getCustomizationDelegate() {
        return mAppComponent.getGridCustomizationsProxy();
    }

    /**
     * Updates the color overrides and returns true if something has changed
     */
    private boolean updateColorOverrides(Bundle bundle) {
        Boolean oldDarkMode = mDarkMode;
        SparseIntArray oldColorsOverride = mPreviewColorOverride;
        int[] oldSeedColorList = mSeedColorList;
        int oldThemeStyle = mThemeStyle;

        mDarkMode =
                bundle.containsKey(KEY_DARK_MODE) ? bundle.getBoolean(KEY_DARK_MODE) : null;

        mSeedColorList = bundle.getIntArray(KEY_SEED_COLOR_LIST);
        mThemeStyle = bundle.getInt(KEY_THEME_STYLE, -1);

        int[] ids = bundle.getIntArray(KEY_COLOR_RESOURCE_IDS);
        int[] colors = bundle.getIntArray(KEY_COLOR_VALUES);
        if (ids != null && colors != null) {
            mPreviewColorOverride = new SparseIntArray();
            for (int i = 0; i < ids.length; i++) {
                mPreviewColorOverride.put(ids[i], colors[i]);
            }
        } else {
            mPreviewColorOverride = null;
        }
        return  !Objects.equals(oldDarkMode, mDarkMode)
                || !Arrays.equals(oldSeedColorList, mSeedColorList)
                || oldThemeStyle != mThemeStyle
                || mPreviewColorOverride != oldColorsOverride;
    }

    /***
     * Generates a new context overriding the theme color and the display size without affecting the
     * main application context
     */
    @UiThread
    private void recreatePreviewRenderer() {
        if (mDestroyed) return;
        ContextThemeWrapper context = new ContextThemeWrapper(
                mPreviewContext, Themes.getActivityThemeRes(mPreviewContext));
        if (mDarkMode != null) {
            Configuration configuration = new Configuration(
                    mPreviewContext.getResources().getConfiguration());
            if (mDarkMode) {
                configuration.uiMode &= ~UI_MODE_NIGHT_NO;
                configuration.uiMode |= UI_MODE_NIGHT_YES;
            } else {
                configuration.uiMode &= ~UI_MODE_NIGHT_YES;
                configuration.uiMode |= UI_MODE_NIGHT_NO;
            }
            context.applyOverrideConfiguration(configuration);
        }

        final int themeRes = mWallpaperColors == null
                ? Themes.getActivityThemeRes(context)
                : Themes.getActivityThemeRes(context, mWallpaperColors.getColorHints());

        LocalColorExtractor localColorExtractor = mAppComponent.getLocalColorExtractor();

        if (mSeedColorList != null) {
            mColorsOverride = localColorExtractor
                    .applyColorOverlay(context, mSeedColorList, mThemeStyle);
        } else if (mPreviewColorOverride != null) {
            mColorsOverride = localColorExtractor
                    .applyColorsOverride(context, mPreviewColorOverride);
        } else if (mWallpaperColors != null) {
            mColorsOverride = localColorExtractor
                    .applyColorsOverride(context, mWallpaperColors);
        } else {
            mColorsOverride = null;
        }

        final LauncherPreviewRenderer oldRenderer = mCurrentRenderer;
        LauncherPreviewRenderer renderer = new LauncherPreviewRenderer(
                context, mWorkspacePageId,
                mColorsOverride, mAppComponent.getModel(), themeRes);
        renderer.hideBottomRow(mHideQsb);

        CompletableFuture<Void> renderTask = renderer.initialRender
                .thenAcceptAsync(this::setContentRoot, MAIN_EXECUTOR);
        if (oldRenderer != null) {
            Future<?> unused = CompletableFuture.anyOf(renderTask)
                    .completeOnTimeout(null, 10, TimeUnit.SECONDS)
                    .thenRunAsync(oldRenderer::onViewDestroyed, MAIN_EXECUTOR);
        }
        mCurrentRenderer = renderer;
    }

    private void setContentRoot(View view) {
        // This aspect scales the view to fit in the surface and centers it
        final float scale = Math.min(mWidth / (float) view.getMeasuredWidth(),
                mHeight / (float) view.getMeasuredHeight());
        view.setScaleX(scale);
        view.setScaleY(scale);

        LayoutParams lp = new LayoutParams(view.getMeasuredWidth(), view.getMeasuredHeight());
        lp.gravity = Gravity.CENTER;
        view.setLayoutParams(lp);
        if (mViewRoot.getChildCount() == 0) {
            mViewRoot.addView(view);
            mViewRoot.animate().alpha(1)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setDuration(FADE_IN_ANIMATION_DURATION)
                    .start();
        } else {
            mViewRoot.removeAllViews();
            mViewRoot.addView(view);
        }
    }

    private static class MySurfaceControlViewHost extends SurfaceControlViewHost {

        private final RunnableList mLifecycleTracker;

        MySurfaceControlViewHost(Context context, Display display, IBinder hostToken,
                RunnableList lifeCycleTracker) {
            super(context, display, hostToken);
            mLifecycleTracker = lifeCycleTracker;
            mLifecycleTracker.add(this::release);
        }

        @Override
        public void release() {
            super.release();
            // RunnableList ensures that the callback is only called once
            MAIN_EXECUTOR.execute(mLifecycleTracker::executeAllAndDestroy);
        }
    }
}
