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

package com.android.launcher3;

import static com.android.launcher3.LauncherPrefs.DB_FILE;
import static com.android.launcher3.LauncherPrefs.ENABLE_TWOLINE_ALLAPPS_TOGGLE;
import static com.android.launcher3.LauncherPrefs.FIXED_LANDSCAPE_MODE;
import static com.android.launcher3.LauncherPrefs.GRID_NAME;
import static com.android.launcher3.LauncherPrefs.NON_FIXED_LANDSCAPE_GRID_NAME;
import static com.android.launcher3.LauncherPrefs.WORKSPACE_ITEMS_LABEL_HIDDEN;
import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.deviceprofile.parser.DeviceTypedMap.COUNT_SIZES;
import static com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_DEFAULT;
import static com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_LANDSCAPE;
import static com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_TWO_PANEL_LANDSCAPE;
import static com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_TWO_PANEL_PORTRAIT;
import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_DENSITY;
import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_SUPPORTED_BOUNDS;
import static com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.SimpleBroadcastReceiver.actionsFilter;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Trace;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.DimenRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.annotation.XmlRes;

import com.android.launcher3.concurrent.annotations.Ui;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.deviceprofile.parser.DisplayOption;
import com.android.launcher3.deviceprofile.parser.GridOption;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.display.LauncherDisplayInfo;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.DeviceGridState;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.ListenableDiffAwareRef;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.Partner;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.launcher3.util.TaskbarModeUtil;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.inject.Inject;

@LauncherAppSingleton
public class InvariantDeviceProfile {

    public static final String TAG = "IDP";
    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final DaggerSingletonObject<InvariantDeviceProfile> INSTANCE =
            new DaggerSingletonObject<>(LauncherAppComponent::getIDP);

    public static final String GRID_NAME_PREFS_KEY = "idp_grid_name";
    public static final String NON_FIXED_LANDSCAPE_GRID_NAME_PREFS_KEY =
            "idp_non_fixed_landscape_grid_name";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_PHONE, TYPE_MULTI_DISPLAY, TYPE_TABLET, TYPE_DESKTOP})
    public @interface DeviceType {
    }

    public static final int TYPE_PHONE = 0;
    public static final int TYPE_MULTI_DISPLAY = 1;
    public static final int TYPE_TABLET = 2;
    public static final int TYPE_DESKTOP = 3;

    private static final float ICON_SIZE_DEFINED_IN_APP_DP = 48;

    // Constants that affects the interpolation curve between statically defined device profile
    // buckets.
    private static final float KNEARESTNEIGHBOR = 3;
    private static final float WEIGHT_POWER = 5;

    // used to offset float not being able to express extremely small weights in extreme cases.
    private static final float WEIGHT_EFFICIENT = 100000f;

    /** These resources are used to override the device profile */
    private static final String RES_GRID_NUM_ROWS = "grid_num_rows";
    private static final String RES_GRID_NUM_COLUMNS = "grid_num_columns";
    private static final String RES_GRID_ICON_SIZE_DP = "grid_icon_size_dp";

    private final DisplayController mDisplayController;
    private final WindowManagerProxy mWMProxy;
    private final LauncherPrefs mPrefs;
    private final ThemeManager mThemeManager;

    /**
     * Number of icons per row and column in the workspace.
     */
    public int numRows;
    public int numColumns;
    public int numSearchContainerColumns;

    /**
     * Number of icons per row and column in the folder.
     */
    public int[] numFolderRows;
    public int[] numFolderColumns;
    public float[] iconSize;
    public float[] iconTextSize;
    public int iconBitmapSize;
    public int fillResIconDpi;
    public @DeviceType int deviceType;
    public LauncherDisplayInfo displayInfo;

    public PointF[] minCellSize;

    public PointF[] borderSpaces;
    public @DimenRes int inlineNavButtonsEndSpacing;

    public @StyleRes int folderStyle;

    public @StyleRes int cellStyle;

    public float[] horizontalMargin;

    public PointF[] allAppsCellSize;
    public float[] allAppsIconSize;
    public float[] allAppsIconTextSize;
    public PointF[] allAppsBorderSpaces;

    public float[] transientTaskbarIconSize;

    public boolean[] startAlignTaskbar;

    /**
     * Number of icons inside the hotseat area.
     */
    public int numShownHotseatIcons;

    /**
     * Number of icons inside the hotseat area that is stored in the database. This is greater than
     * or equal to numnShownHotseatIcons, allowing for a seamless transition between two hotseat
     * sizes that share the same DB.
     */
    public int numDatabaseHotseatIcons;

    public float[] hotseatBarBottomSpace;
    public float[] hotseatQsbSpace;

    /**
     * Number of columns in the all apps list.
     */
    public int numAllAppsColumns;
    public int numAllAppsRowsForCellHeightCalculation;
    public int numDatabaseAllAppsColumns;
    public @StyleRes int allAppsStyle;

    /**
     * Do not query directly. see {@link DeviceProfile#isScalableGrid}.
     */
    protected boolean isScalable;
    @XmlRes
    public int devicePaddingId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int workspaceSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int workspaceSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int allAppsSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int allAppsSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int folderSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int folderSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int hotseatSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int hotseatSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int workspaceCellSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int workspaceCellSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int allAppsCellSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int allAppsCellSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;

    private String mLocale = "";
    public boolean enableTwoLinesInAllApps = false;

    // If non-negative, the workspace row with which top of the all apps container is to be aligned
    // with.
    public int appListAlignedWithWorkspaceRow = -1;

    /**
     * Fixed landscape mode is the landscape on the phones.
     */
    public boolean isFixedLandscape = false;

    @GridType
    public int gridType;
    public String dbFile;
    public int defaultLayoutId;
    public boolean[] inlineQsb = new boolean[COUNT_SIZES];

    /**
     * An immutable list of supported profiles.
     */
    public List<DeviceProfile> supportedProfiles = Collections.emptyList();

    public Point defaultWallpaperSize;

    private final List<OnIDPChangeListener> mChangeListeners = new CopyOnWriteArrayList<>();

    public TaskbarModeUtil taskbarModeUtil;
    private final LooperExecutor mMainExecutor;

    @Inject
    InvariantDeviceProfile(
            @ApplicationContext Context context,
            LauncherPrefs prefs,
            DisplayController dc,
            WindowManagerProxy wmProxy,
            ThemeManager themeManager,
            DaggerSingletonTracker lifeCycle,
            TaskbarModeUtil taskbarModeUtil,
            @Ui final LooperExecutor mainExecutor) {
        mDisplayController = dc;
        mWMProxy = wmProxy;
        this.taskbarModeUtil = taskbarModeUtil;
        mPrefs = prefs;
        mThemeManager = themeManager;
        mMainExecutor = mainExecutor;

        String gridName = prefs.get(GRID_NAME);
        initGrid(gridName);
        mThemeManager.generateIconShape(iconBitmapSize);

        ListenableDiffAwareRef<LauncherDisplayInfo, Integer> listenable = dc.getListenable();
        if (listenable != null) {
            lifeCycle.addCloseable(listenable.getChanges().forEach(MAIN_EXECUTOR, (flags) -> {
                if ((flags & (CHANGE_DENSITY | CHANGE_SUPPORTED_BOUNDS
                        | CHANGE_NAVIGATION_MODE)) != 0) {
                    onConfigChanged();
                }
                return null;
            }));
        }

        LauncherPrefChangeListener prefListener = key -> {
            if (FIXED_LANDSCAPE_MODE.getSharedPrefKey().equals(key)
                    && isFixedLandscape != prefs.get(FIXED_LANDSCAPE_MODE)) {
                Trace.beginSection("InvariantDeviceProfile#setFixedLandscape");
                if (isFixedLandscape) {
                    setCurrentGrid(prefs.get(NON_FIXED_LANDSCAPE_GRID_NAME));
                } else {
                    prefs.put(NON_FIXED_LANDSCAPE_GRID_NAME, mPrefs.get(GRID_NAME));
                    onConfigChanged();
                }
                Trace.endSection();
            } else if (ENABLE_TWOLINE_ALLAPPS_TOGGLE.getSharedPrefKey().equals(key)
                    && enableTwoLinesInAllApps != prefs.get(ENABLE_TWOLINE_ALLAPPS_TOGGLE)) {
                onConfigChanged();
            } else if (WORKSPACE_ITEMS_LABEL_HIDDEN.getSharedPrefKey().equals(key)
                    && com.android.systemui.shared.Flags.workspaceItemsLabelHidden()) {
                onConfigChanged();
            }
        };
        prefs.addListener(prefListener, FIXED_LANDSCAPE_MODE, ENABLE_TWOLINE_ALLAPPS_TOGGLE);
        lifeCycle.addCloseable(() -> prefs.removeListener(prefListener,
                FIXED_LANDSCAPE_MODE, ENABLE_TWOLINE_ALLAPPS_TOGGLE));

        SimpleBroadcastReceiver localeReceiver = new SimpleBroadcastReceiver(context,
                mMainExecutor, i -> onConfigChanged());
        localeReceiver.register(actionsFilter(Intent.ACTION_LOCALE_CHANGED));
        lifeCycle.addCloseable(localeReceiver);
    }

    private void initGrid(String gridName) {
        LauncherDisplayInfo displayInfo = mDisplayController.getInfo();
        List<DisplayOption> allOptions = getPredefinedDeviceProfiles(
                displayInfo,
                gridName,
                mPrefs.get(FIXED_LANDSCAPE_MODE)
        );

        FileLog.d(
                "b/475447538",
                "Fixed Landscape pref = " + mPrefs.get(FIXED_LANDSCAPE_MODE)
                        + " all grids = " + allOptions
                        .stream()
                        .map(opt -> opt.grid)
                        .collect(Collectors.toList())
        );

        // Filter out options that don't have the same number of columns as the grid
        DeviceGridState deviceGridState = new DeviceGridState(mPrefs);
        List<DisplayOption> allOptionsFilteredByColCount =
                filterByColumnCount(allOptions, deviceGridState.getColumns());

        DisplayOption displayOption =
                invDistWeightedInterpolate(displayInfo, allOptionsFilteredByColCount.isEmpty()
                                ? new ArrayList<>(allOptions)
                                : new ArrayList<>(allOptionsFilteredByColCount),
                        displayInfo.getDeviceType());

        if (!displayOption.grid.name.equals(gridName)) {
            mPrefs.put(GRID_NAME, displayOption.grid.name);
        }

        initGridForDisplayOption(displayInfo, displayOption);
        FileLog.d(TAG, "After initGrid:"
                + "gridName:" + gridName
                + ", dbFile:" + dbFile
                + ", LauncherPrefs GRID_NAME:" + mPrefs.get(GRID_NAME)
                + ", LauncherPrefs DB_FILE:" + mPrefs.get(DB_FILE));
    }

    private List<DisplayOption> filterByColumnCount(
            List<DisplayOption> allOptions, int numColumns) {
        return allOptions.stream()
                .filter(option -> option.grid.numColumns == numColumns)
                .collect(Collectors.toList());
    }

    /**
     * @deprecated This is a temporary solution because on the backup and restore case we modify the
     * IDP, this resets it. b/332974074
     */
    @Deprecated
    public void reset() {
        initGrid(mPrefs.get(GRID_NAME));
    }

    private void initGridForDisplayOption(
            LauncherDisplayInfo displayInfo, DisplayOption displayOption) {
        Context context = displayInfo.context;
        enableTwoLinesInAllApps = Flags.enableTwolineToggle()
                && Utilities.isEnglishLanguage(context)
                && mPrefs.get(ENABLE_TWOLINE_ALLAPPS_TOGGLE);
        mLocale = context.getResources().getConfiguration().locale.toString();

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        GridOption closestProfile = displayOption.grid;
        numRows = closestProfile.numRows;
        numColumns = closestProfile.numColumns;
        numSearchContainerColumns = closestProfile.numSearchContainerColumns;
        dbFile = closestProfile.dbFile;
        gridType = closestProfile.gridType;
        defaultLayoutId = closestProfile.defaultLayoutId;

        numFolderRows = closestProfile.numFolderRows;
        numFolderColumns = closestProfile.numFolderColumns;
        folderStyle = closestProfile.folderStyle;

        cellStyle = closestProfile.cellStyle;

        isScalable = closestProfile.isScalable;
        devicePaddingId = closestProfile.devicePaddingId;
        workspaceSpecsId = closestProfile.workspaceSpecsId;
        workspaceSpecsTwoPanelId = closestProfile.workspaceSpecsTwoPanelId;
        allAppsSpecsId = closestProfile.allAppsSpecsId;
        allAppsSpecsTwoPanelId = closestProfile.allAppsSpecsTwoPanelId;
        folderSpecsId = closestProfile.folderSpecsId;
        folderSpecsTwoPanelId = closestProfile.folderSpecsTwoPanelId;
        hotseatSpecsId = closestProfile.hotseatSpecsId;
        hotseatSpecsTwoPanelId = closestProfile.hotseatSpecsTwoPanelId;
        workspaceCellSpecsId = closestProfile.workspaceCellSpecsId;
        workspaceCellSpecsTwoPanelId = closestProfile.workspaceCellSpecsTwoPanelId;
        allAppsCellSpecsId = closestProfile.allAppsCellSpecsId;
        allAppsCellSpecsTwoPanelId = closestProfile.allAppsCellSpecsTwoPanelId;
        numAllAppsRowsForCellHeightCalculation =
                closestProfile.numAllAppsRowsForCellHeightCalculation;
        appListAlignedWithWorkspaceRow = closestProfile.allAppsAlignedWithWorkspaceRow;
        this.deviceType = displayInfo.getDeviceType();
        this.displayInfo = displayInfo;

        inlineNavButtonsEndSpacing = closestProfile.inlineNavButtonsEndSpacing;

        iconSize = displayOption.iconSizes;
        float maxIconSize = iconSize[0];
        for (int i = 1; i < iconSize.length; i++) {
            maxIconSize = Math.max(maxIconSize, iconSize[i]);
        }
        iconBitmapSize = ResourceUtils.pxFromDp(maxIconSize, metrics);

        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);

        iconTextSize = displayOption.textSizes;

        minCellSize = displayOption.minCellSize;

        borderSpaces = displayOption.borderSpaces;

        horizontalMargin = displayOption.horizontalMargin;

        numShownHotseatIcons = closestProfile.numHotseatIcons;
        numDatabaseHotseatIcons = deviceType == TYPE_MULTI_DISPLAY || deviceType == TYPE_DESKTOP
                ? closestProfile.numDatabaseHotseatIcons : closestProfile.numHotseatIcons;

        hotseatBarBottomSpace = displayOption.hotseatBarBottomSpace;
        hotseatQsbSpace = displayOption.hotseatQsbSpace;

        allAppsStyle = closestProfile.allAppsStyle;

        numAllAppsColumns = closestProfile.numAllAppsColumns;

        numDatabaseAllAppsColumns = deviceType == TYPE_MULTI_DISPLAY
                ? closestProfile.numDatabaseAllAppsColumns : closestProfile.numAllAppsColumns;

        allAppsCellSize = displayOption.allAppsCellSize;
        allAppsBorderSpaces = displayOption.allAppsBorderSpaces;
        allAppsIconSize = displayOption.allAppsIconSizes;
        allAppsIconTextSize = displayOption.allAppsIconTextSizes;

        inlineQsb = closestProfile.inlineQsb;

        transientTaskbarIconSize = displayOption.transientTaskbarIconSize;

        startAlignTaskbar = displayOption.startAlignTaskbar;

        // Fixed Landscape mode
        isFixedLandscape = closestProfile.isFixedLandscape;

        // If the partner customization apk contains any grid overrides, apply them
        // Supported overrides: numRows, numColumns, iconSize
        applyPartnerDeviceProfileOverrides(context, metrics);

        final List<DeviceProfile> localSupportedProfiles = new ArrayList<>();
        defaultWallpaperSize = new Point(displayInfo.currentSize);
        for (WindowBounds bounds : displayInfo.supportedBounds) {
            DeviceProfile.Builder builder = newDPBuilder(displayInfo)
                    .setIsMultiDisplay(deviceType == TYPE_MULTI_DISPLAY)
                    .setWindowBounds(bounds);
            if (com.android.systemui.shared.Flags.workspaceItemsLabelHidden()) {
                builder.setIsWorkspaceItemsLabelHidden(mPrefs.get(WORKSPACE_ITEMS_LABEL_HIDDEN));
            }
            localSupportedProfiles.add(builder.build());

            // Wallpaper size should be the maximum of the all possible sizes Launcher expects
            int displayWidth = bounds.bounds.width();
            int displayHeight = bounds.bounds.height();
            defaultWallpaperSize.y = Math.max(defaultWallpaperSize.y, displayHeight);

            // We need to ensure that there is enough extra space in the wallpaper
            // for the intended parallax effects
            float parallaxFactor =
                    dpiFromPx(Math.min(displayWidth, displayHeight), displayInfo.getDensityDpi())
                            < 720
                            ? 2
                            : wallpaperTravelToScreenWidthRatio(displayWidth, displayHeight);
            defaultWallpaperSize.x =
                    Math.max(defaultWallpaperSize.x, Math.round(parallaxFactor * displayWidth));
        }
        supportedProfiles = Collections.unmodifiableList(localSupportedProfiles);

        int numMinShownHotseatIconsForTablet = supportedProfiles
                .stream()
                .filter(deviceProfile -> deviceProfile.getDeviceProperties().isLargeScreen())
                .mapToInt(
                        deviceProfile -> deviceProfile.getHotseatProfile().getNumShownIcons()
                )
                .min()
                .orElse(0);

        supportedProfiles
                .stream()
                .filter(deviceProfile -> deviceProfile.getDeviceProperties().isLargeScreen())
                .forEach(deviceProfile ->
                    deviceProfile.recalculateHotseatWidthAndBorderSpace(
                            numMinShownHotseatIconsForTablet
                    )
                );
    }

    DeviceProfile.Builder newDPBuilder(LauncherDisplayInfo info) {
        return new DeviceProfile.Builder(this, info, mWMProxy);
    }

    public void addOnChangeListener(OnIDPChangeListener listener) {
        mChangeListeners.add(listener);
    }

    public void removeOnChangeListener(OnIDPChangeListener listener) {
        mChangeListeners.remove(listener);
    }

    /**
     * Updates the current grid, this triggers a new IDP, reloads the database and triggers a grid
     * migration.
     */
    public void setCurrentGrid(String newGridName) {
        if (TextUtils.equals(mPrefs.get(GRID_NAME), newGridName)) return;
        mPrefs.put(GRID_NAME, newGridName);
        mMainExecutor.execute(() -> {
            Trace.beginSection("InvariantDeviceProfile#setCurrentGrid");
            onConfigChanged();
            Trace.endSection();
        });
    }

    private Object[] toModelState() {
        return new Object[]{
                numColumns, numRows, numSearchContainerColumns, numDatabaseHotseatIcons,
                iconBitmapSize, fillResIconDpi, numDatabaseAllAppsColumns, dbFile, mLocale};
    }

    /** Updates IDP using the provided context. Notifies listeners of change. */
    private void onConfigChanged() {
        Object[] oldState = toModelState();

        // Re-init grid
        initGrid(mPrefs.get(GRID_NAME));

        boolean modelPropsChanged = !Arrays.equals(oldState, toModelState());
        for (OnIDPChangeListener listener : mChangeListeners) {
            listener.onIdpChanged(modelPropsChanged);
        }

        // Generate new Icon Shape info
        mThemeManager.generateIconShape(iconBitmapSize);
    }

    private static List<DisplayOption> getPredefinedDeviceProfiles(
            @NonNull LauncherDisplayInfo displayInfo,
            @Nullable String gridName,
            boolean isFixedLandscapeMode
    ) {
        List<DisplayOption> profiles = DisplayOption.getPredefinedDisplayOptions(
                displayInfo, isFixedLandscapeMode);

        ArrayList<DisplayOption> filteredProfiles = new ArrayList<>();
        if (!TextUtils.isEmpty(gridName)) {
            for (DisplayOption option : profiles) {
                if (gridName.equals(option.grid.name)
                        && (option.grid.isEnabled(displayInfo.getDeviceType()))) {
                    filteredProfiles.add(option);
                }
            }
        }
        if (filteredProfiles.isEmpty() && TextUtils.isEmpty(gridName)) {
            // Use the default options since gridName is empty and there's no valid grids.
            for (DisplayOption option : profiles) {
                if (option.canBeDefault) {
                    filteredProfiles.add(option);
                }
            }
        } else if (filteredProfiles.isEmpty()) {
            // In this case we had a grid selected but we couldn't find it.
            filteredProfiles.addAll(profiles);
        }
        if (filteredProfiles.isEmpty()) {
            throw new RuntimeException("No display option with canBeDefault=true");
        }
        return filteredProfiles;
    }

    /**
     * @return all the grid options that can be shown on the device
     */
    public List<GridOption> parseAllGridOptions(Context context) {
        return GridOption.parseAllValid(context, displayInfo, deviceType, isFixedLandscape);
    }

    private int getLauncherIconDensity(int requiredSize) {
        // Densities typically defined by an app.
        int[] densityBuckets = new int[]{
                DisplayMetrics.DENSITY_LOW,
                DisplayMetrics.DENSITY_MEDIUM,
                DisplayMetrics.DENSITY_TV,
                DisplayMetrics.DENSITY_HIGH,
                DisplayMetrics.DENSITY_XHIGH,
                DisplayMetrics.DENSITY_XXHIGH,
                DisplayMetrics.DENSITY_XXXHIGH
        };

        int density = DisplayMetrics.DENSITY_XXXHIGH;
        for (int i = densityBuckets.length - 1; i >= 0; i--) {
            float expectedSize = ICON_SIZE_DEFINED_IN_APP_DP * densityBuckets[i]
                    / DisplayMetrics.DENSITY_DEFAULT;
            if (expectedSize >= requiredSize) {
                density = densityBuckets[i];
            }
        }

        return density;
    }

    /**
     * Apply any Partner customization grid overrides.
     *
     * Currently we support: all apps row / column count.
     */
    private void applyPartnerDeviceProfileOverrides(Context context, DisplayMetrics dm) {
        Partner p = Partner.get(context.getPackageManager());
        if (p == null) {
            return;
        }
        try {
            int numRows = p.getIntValue(RES_GRID_NUM_ROWS, -1);
            int numColumns = p.getIntValue(RES_GRID_NUM_COLUMNS, -1);
            float iconSizePx = p.getDimenValue(RES_GRID_ICON_SIZE_DP, -1);

            if (numRows > 0 && numColumns > 0) {
                this.numRows = numRows;
                this.numColumns = numColumns;
            }
            if (iconSizePx > 0) {
                this.iconSize[INDEX_DEFAULT] = Utilities.dpiFromPx(iconSizePx, dm.densityDpi);
            }
        } catch (Resources.NotFoundException ex) {
            Log.e(TAG, "Invalid Partner grid resource!", ex);
        }
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    private static DisplayOption invDistWeightedInterpolate(
            LauncherDisplayInfo displayInfo, List<DisplayOption> points,
            @DeviceType int deviceType) {
        int minWidthPx = Integer.MAX_VALUE;
        int minHeightPx = Integer.MAX_VALUE;
        for (WindowBounds bounds : displayInfo.supportedBounds) {
            boolean isLargeScreen = displayInfo.isLargeScreen(bounds);
            if (isLargeScreen && deviceType == TYPE_MULTI_DISPLAY) {
                // For split displays, take half width per page
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.x / 2);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.y);

            } else if (!isLargeScreen && bounds.isLandscape()) {
                // We will use transposed layout in this case
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.y);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.x);
            } else {
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.x);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.y);
            }
        }

        float width = dpiFromPx(minWidthPx, displayInfo.getDensityDpi());
        float height = dpiFromPx(minHeightPx, displayInfo.getDensityDpi());

        // Sort the profiles based on the closeness to the device size
        points.sort((a, b) ->
                Float.compare(dist(width, height, a.minWidthDps, a.minHeightDps),
                        dist(width, height, b.minWidthDps, b.minHeightDps)));

        DisplayOption closestPoint = points.get(0);
        GridOption closestOption = closestPoint.grid;
        float weights = 0;

        if (dist(width, height, closestPoint.minWidthDps, closestPoint.minHeightDps) == 0) {
            return closestPoint;
        }

        // Calculate the weighted average as
        // out = (w1 * p1 + w2 * p2 + w3 * p3...) / (w1 + w2 + w3...)
        DisplayOption out = DisplayOption.createEmpty(displayInfo.context, closestOption);
        for (int i = 0; i < points.size() && i < KNEARESTNEIGHBOR; ++i) {
            DisplayOption p = points.get(i);
            float w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER);
            weights += w;
            // out = out + w * p, for any boolean properties, we just take their union
            out.merge(p, (b1, b2) -> b1 || b2, (f1, f2) -> f1 + w * f2);
        }
        final float combinedWeight = weights;
        // out = out / combinedWeight
        out.merge(out, (b1, b2) -> b1, (f1, f2) -> f1 / combinedWeight);

        // Since the bitmaps are persisted, ensure that all bitmap sizes are not larger than
        // predefined size to avoid cache invalidation
        for (int i = 0; i < out.iconSizes.length; i++) {
            out.iconSizes[i] = Math.min(out.iconSizes[i], closestPoint.iconSizes[i]);
        }

        return out;
    }

    public DeviceProfile createDeviceProfileForSecondaryDisplay(Context displayContext) {
        // Disable transpose layout and use external display so that the icons are scaled properly
        return newDPBuilder(new LauncherDisplayInfo(displayContext, mWMProxy))
                .setIsMultiDisplay(false)
                .setExternalDisplay(true)
                .setWindowBounds(mWMProxy.getRealBounds(
                        displayContext, mWMProxy.getDisplayInfo(displayContext)))
                .setTransposeLayoutWithOrientation(false)
                .setSecondaryDisplayOptionSpec()
                .build();
    }

    public DeviceProfile getDeviceProfile(Context context) {
        Rect bounds = mWMProxy.getCurrentBounds(context);
        int rotation = mWMProxy.getRotation(context);
        return getBestMatch(bounds.width(), bounds.height(), rotation);
    }

    /**
     * Returns the device profile matching the provided screen configuration
     */
    public DeviceProfile getBestMatch(float screenWidth, float screenHeight, int rotation) {
        DeviceProfile bestMatch = supportedProfiles.get(0);
        float minDiff = Float.MAX_VALUE;

        for (DeviceProfile profile : supportedProfiles) {
            float diff = Math.abs(profile.getDeviceProperties().getWidthPx() - screenWidth)
                    + Math.abs(profile.getDeviceProperties().getHeightPx() - screenHeight);
            if (diff < minDiff) {
                minDiff = diff;
                bestMatch = profile;
            } else if (diff == minDiff && profile.getDeviceProperties().getRotationHint() == rotation) {
                bestMatch = profile;
            }
        }
        return bestMatch;
    }

    private static float weight(float x0, float y0, float x1, float y1, float pow) {
        float d = dist(x0, y0, x1, y1);
        if (Float.compare(d, 0f) == 0) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) (WEIGHT_EFFICIENT / Math.pow(d, pow));
    }

    /**
     * As a ratio of screen height, the total distance we want the parallax effect to span
     * horizontally
     */
    private static float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16 / 10f;
        final float ASPECT_RATIO_PORTRAIT = 10 / 16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
                (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE
                        - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
                        (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    public interface OnIDPChangeListener {

        /**
         * Called when the device provide changes
         */
        void onIdpChanged(boolean modelPropertiesChanged);
    }

    /** Returns {@link DisplayOptionSpec} for the provided displayInfo. */
    static DisplayOptionSpec createDisplayOptionSpec(
            LauncherDisplayInfo displayInfo, boolean isLandscape) {
        // Get predefined profiles for provided displayInfo without using any main device's pref.
        List<DisplayOption> allOptions = getPredefinedDeviceProfiles(displayInfo,
                /* gridName= */ null,
                /* isFixedLandscapeMode= */ false);
        return new DisplayOptionSpec(
                invDistWeightedInterpolate(displayInfo, allOptions,
                        displayInfo.getDeviceType()), isLandscape);
    }

    /** Class to expose properties required for external displays to {@link DeviceProfile} */
    public static final class DisplayOptionSpec {
        public final int typeIndex;
        public final int numShownHotseatIcons;
        public final int numAllAppsColumns;
        @XmlRes public final int hotseatSpecsId;
        @XmlRes public final int workspaceCellSpecsId;
        @XmlRes public final int workspaceSpecsId;
        @XmlRes public final int allAppsSpecsId;
        @XmlRes public final int folderSpecsId;
        @XmlRes public final int allAppsCellSpecsId;
        public final boolean startAlignTaskbar;

        DisplayOptionSpec(DisplayOption displayOption, boolean isLandscape) {
            typeIndex = isLandscape ? INDEX_LANDSCAPE : INDEX_DEFAULT;
            numShownHotseatIcons = displayOption.grid.numHotseatIcons;
            numAllAppsColumns = displayOption.grid.numAllAppsColumns;
            hotseatSpecsId = displayOption.grid.hotseatSpecsId;
            workspaceCellSpecsId = displayOption.grid.workspaceCellSpecsId;
            workspaceSpecsId = displayOption.grid.workspaceSpecsId;
            allAppsSpecsId = displayOption.grid.allAppsSpecsId;
            folderSpecsId = displayOption.grid.folderSpecsId;
            allAppsCellSpecsId = displayOption.grid.allAppsCellSpecsId;
            startAlignTaskbar = displayOption.startAlignTaskbar[typeIndex];
        }

        DisplayOptionSpec(InvariantDeviceProfile inv, boolean isTwoPanels, boolean isLandscape) {
            if (isTwoPanels) {
                if (isLandscape) {
                    typeIndex = INDEX_TWO_PANEL_LANDSCAPE;
                } else {
                    typeIndex = INDEX_TWO_PANEL_PORTRAIT;
                }
            } else {
                if (isLandscape) {
                    typeIndex = INDEX_LANDSCAPE;
                } else {
                    typeIndex = INDEX_DEFAULT;
                }
            }
            numShownHotseatIcons =
                    isTwoPanels ? inv.numDatabaseHotseatIcons : inv.numShownHotseatIcons;
            numAllAppsColumns = isTwoPanels ? inv.numDatabaseAllAppsColumns : inv.numAllAppsColumns;
            hotseatSpecsId = isTwoPanels ? inv.hotseatSpecsTwoPanelId : inv.hotseatSpecsId;
            workspaceCellSpecsId = isTwoPanels ? inv.workspaceCellSpecsTwoPanelId
                    : inv.workspaceCellSpecsId;
            workspaceSpecsId = isTwoPanels ? inv.workspaceSpecsTwoPanelId : inv.workspaceSpecsId;
            allAppsSpecsId = isTwoPanels ? inv.allAppsSpecsTwoPanelId : inv.allAppsSpecsId;
            folderSpecsId = isTwoPanels ? inv.folderSpecsTwoPanelId : inv.folderSpecsId;
            allAppsCellSpecsId =
                    isTwoPanels ? inv.allAppsCellSpecsTwoPanelId : inv.allAppsCellSpecsId;
            startAlignTaskbar = inv.startAlignTaskbar[typeIndex];
        }
    }

}
