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

package com.android.launcher3;

import static com.android.launcher3.InvariantDeviceProfile.createDisplayOptionSpec;
import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE;
import static com.android.launcher3.testing.shared.ResourceUtils.pxFromDp;
import static com.android.systemui.shared.Flags.enableRecentsInTaskbar;
import static com.android.wm.shell.Flags.enableBubbleBar;

import static java.lang.Math.max;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.CellLayout.ContainerType;
import com.android.launcher3.InvariantDeviceProfile.DisplayOptionSpec;
import com.android.launcher3.deviceprofile.AllAppsProfile;
import com.android.launcher3.deviceprofile.BottomSheetProfile;
import com.android.launcher3.deviceprofile.DeviceConfiguration;
import com.android.launcher3.deviceprofile.DeviceProperties;
import com.android.launcher3.deviceprofile.DropTargetProfile;
import com.android.launcher3.deviceprofile.FolderProfile;
import com.android.launcher3.deviceprofile.HotseatProfile;
import com.android.launcher3.deviceprofile.HotseatProfileInitialValues;
import com.android.launcher3.deviceprofile.OverviewProfile;
import com.android.launcher3.deviceprofile.SysuiProfile;
import com.android.launcher3.deviceprofile.TaskbarConfiguration;
import com.android.launcher3.deviceprofile.TaskbarProfile;
import com.android.launcher3.deviceprofile.WorkspaceProfile;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.display.LauncherDisplayInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.responsive.CalculatedCellSpec;
import com.android.launcher3.responsive.CalculatedHotseatSpec;
import com.android.launcher3.responsive.CalculatedResponsiveSpec;
import com.android.launcher3.responsive.HotseatSpecsProvider;
import com.android.launcher3.responsive.ResponsiveCellSpecsProvider;
import com.android.launcher3.responsive.ResponsiveSpec.Companion.ResponsiveSpecType;
import com.android.launcher3.responsive.ResponsiveSpec.DimensionType;
import com.android.launcher3.responsive.ResponsiveSpecsProvider;
import com.android.launcher3.util.IconSizeSteps;
import com.android.launcher3.util.ResourceHelper;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.inject.Inject;

@SuppressLint("NewApi")
public class DeviceProfile {

    private static final float MAX_ASPECT_RATIO_FOR_ALTERNATE_EDIT_STATE = 1.5f;

    public static final PointF DEFAULT_SCALE = new PointF(1.0f, 1.0f);
    public static final ViewScaleProvider DEFAULT_PROVIDER = itemInfo -> DEFAULT_SCALE;
    public static final Consumer<DeviceProfile> DEFAULT_DIMENSION_PROVIDER = dp -> { };

    public static final DeviceProfile DEFAULT_DEVICE_PROFILE = new DeviceProfile();

    private final DisplayOptionSpec mDisplayOptionSpec;
    private final IconSizeSteps mIconSizeSteps;

    // Device properties

    private final DeviceProperties mDeviceProperties;
    // Variables used only when creating the DeviceProfile.
    private final boolean mIsScalableGrid;
    private final int mTypeIndex;
    private final DisplayMetrics mMetrics;

    private final LauncherDisplayInfo mInfo;

    private final boolean mIsResponsiveGrid;

    // Responsive grid
    private CalculatedResponsiveSpec mResponsiveWorkspaceWidthSpec;
    private CalculatedResponsiveSpec mResponsiveWorkspaceHeightSpec;
    private CalculatedResponsiveSpec mResponsiveAllAppsWidthSpec;
    private CalculatedResponsiveSpec mResponsiveAllAppsHeightSpec;
    private CalculatedResponsiveSpec mResponsiveFolderWidthSpec;
    private CalculatedResponsiveSpec mResponsiveFolderHeightSpec;
    private CalculatedHotseatSpec mResponsiveHotseatSpec;
    private CalculatedCellSpec mResponsiveWorkspaceCellSpec;
    private CalculatedCellSpec mResponsiveAllAppsCellSpec;

    private WorkspaceProfile mWorkspaceProfile;
    public final InvariantDeviceProfile inv;
    private final BottomSheetProfile mBottomSheetProfile;
    private FolderProfile mFolderProfile;
    private AllAppsProfile mAllAppsProfile;
    private final OverviewProfile overviewProfile;

    // Hotseat
    private HotseatProfile mHotseatProfile;

    private SysuiProfile mSysuiProfile;

    // Widgets
    private final ViewScaleProvider mViewScaleProvider;

    private final DropTargetProfile mDropTargetProfile;

    // Taskbar
    private TaskbarProfile mTaskbarProfile;

    /** Used only as an alternative to mocking when null values cannot be used. */
    @VisibleForTesting
    public DeviceProfile() {
        mWorkspaceProfile = new WorkspaceProfile(0f, 0, 0, 0, 0f, 0, 0, new Point(), 0, 0, 0, false,
                0, 0f, 0, 0, 0, 0, 0, 0, new Rect(), new Rect(), 0, 0, 0, 0, 0, false, 0, 0, 0,
                new Point(0, 0), 0, 0, 0, new Rect(0, 0, 0, 0));
        mDeviceProperties = new DeviceProperties(
                0, 0,
                0,
                0, 0,
                0, 0,
                0.0f,
                false,
                false,
                false,
                false,
                new Rect(0, 0, 0, 0),
                new DeviceConfiguration(
                        false,
                        false,
                        false,
                        false,
                        false
                ),
                new TaskbarConfiguration(false)
        );
        mBottomSheetProfile = new BottomSheetProfile(0, 0, 0, 0f, 0f);
        overviewProfile = new OverviewProfile(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
        mHotseatProfile = new HotseatProfile(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, false);
        mTaskbarProfile = new TaskbarProfile(0, 0, 0, 0, 0, false, false, false);
        mFolderProfile = new FolderProfile(0, 0, 0, 0, 0, new Point(), 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0);
        inv = null;
        mDisplayOptionSpec = null;
        mInfo = null;
        mMetrics = null;
        mIconSizeSteps = null;
        mIsScalableGrid = false;
        mTypeIndex = 0;
        mIsResponsiveGrid = false;
        mDropTargetProfile = new DropTargetProfile(0, 0, 0, 0, 0, 0, 0, 0, 0);
        mViewScaleProvider = null;
        mAllAppsProfile = new AllAppsProfile(new Point(0, 0), 0, 0, 0f, 0, 0, 0, 0, 0, 0,
                new Rect(), 0, 0);
        mSysuiProfile = new SysuiProfile(0, 0, false);
    }

    DeviceProfile(
            InvariantDeviceProfile inv,
            LauncherDisplayInfo info,
            DeviceProperties deviceProperties,
            @NonNull final ViewScaleProvider viewScaleProvider,
            @NonNull final Consumer<DeviceProfile> dimensionOverrideProvider,
            DisplayOptionSpec displayOptionSpec
    ) {

        this.inv = inv;

        mDeviceProperties = deviceProperties;

        this.mDisplayOptionSpec = displayOptionSpec;

        // TODO(b/241386436): shouldn't change any launcher behaviour
        mIsResponsiveGrid = inv.workspaceSpecsId != INVALID_RESOURCE_HANDLE
                && inv.allAppsSpecsId != INVALID_RESOURCE_HANDLE
                && inv.folderSpecsId != INVALID_RESOURCE_HANDLE
                && inv.hotseatSpecsId != INVALID_RESOURCE_HANDLE
                && inv.workspaceCellSpecsId != INVALID_RESOURCE_HANDLE
                && inv.allAppsCellSpecsId != INVALID_RESOURCE_HANDLE;

        mIsScalableGrid = inv.isScalable
                && !isVerticalBarLayout()
                && !mDeviceProperties.getDeviceConfiguration().isExternalDisplay();
        // Determine device posture.
        mInfo = info;

        // Some more constants.
        Context context = getContext(info, isLandscapeOrientation()
                        ? Configuration.ORIENTATION_LANDSCAPE
                        : Configuration.ORIENTATION_PORTRAIT,
                mDeviceProperties.createWindowBounds());

        final Resources res = context.getResources();

        overviewProfile = OverviewProfile.Factory.createOverviewProfile(res);

        mMetrics = res.getDisplayMetrics();

        mIconSizeSteps = new IconSizeSteps(res);

        mTypeIndex = displayOptionSpec.typeIndex;

        mTaskbarProfile = TaskbarProfile.Factory.createTaskbarProfile(
                res,
                inv.taskbarModeUtil.isTransient(info),
                mDeviceProperties.getTaskbarConfiguration().isTaskbarPresent(),
                displayOptionSpec
        );

        // Some foldable portrait modes are too wide in terms of aspect ratio so we need to tweak
        // the dimensions for edit state.
        final boolean shouldApplyWidePortraitDimens = mDeviceProperties.isLargeScreen()
                && !mDeviceProperties.isLandscape()
                && mDeviceProperties.getAspectRatio() < MAX_ASPECT_RATIO_FOR_ALTERNATE_EDIT_STATE;


        if (mIsResponsiveGrid) {
            float responsiveAspectRatio =
                    (float) mDeviceProperties.getWidthPx() / mDeviceProperties.getHeightPx();
            HotseatSpecsProvider hotseatSpecsProvider =
                    HotseatSpecsProvider.create(new ResourceHelper(context,
                            displayOptionSpec.hotseatSpecsId));
            mResponsiveHotseatSpec =
                    isVerticalBarLayout() ? hotseatSpecsProvider.getCalculatedSpec(
                            responsiveAspectRatio, DimensionType.WIDTH,
                            mDeviceProperties.getWidthPx())
                            : hotseatSpecsProvider.getCalculatedSpec(responsiveAspectRatio,
                                    DimensionType.HEIGHT, mDeviceProperties.getHeightPx());

            ResponsiveCellSpecsProvider workspaceCellSpecs = ResponsiveCellSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.workspaceCellSpecsId));
            mResponsiveWorkspaceCellSpec = workspaceCellSpecs.getCalculatedSpec(
                    responsiveAspectRatio, mDeviceProperties.getHeightPx());
        }

        int qsbHeight = res.getDimensionPixelSize(R.dimen.qsb_widget_height);

        HotseatProfileInitialValues hotseatProfileInitialValues =
                HotseatProfileInitialValues.Factory.createHotseatProfileInitialValues(
                        /*deviceProperties*/ getDeviceProperties(),
                        /*res*/ res,
                        /*inv*/ inv,
                        /*shouldApplyWidePortraitDimens*/ shouldApplyWidePortraitDimens,
                        /*responsiveHotseatSpec*/ mResponsiveHotseatSpec,
                        /*typeIndex*/ mTypeIndex,
                        /*metrics*/ mMetrics,
                        /*isVerticalBarLayout*/ isVerticalBarLayout(),
                        /*workspacePageIndicatorHeight*/res.getDimensionPixelSize(
                                R.dimen.workspace_page_indicator_height
                        ),
                        /*responsiveWorkspaceCellSpec*/ mResponsiveWorkspaceCellSpec,
                        qsbHeight
                );

        int allAppsTopPadding = mDeviceProperties.getInsets().top;

        // Needs to be calculated after hotseatBarSizePx is correct,
        // for the available height to be correct
        if (mIsResponsiveGrid) {
            int numFolderRows = inv.numFolderRows[mTypeIndex];
            int numFolderColumns = inv.numFolderColumns[mTypeIndex];
            int availableResponsiveWidth =
                    mDeviceProperties.getAvailableWidthPx() - (isVerticalBarLayout()
                            ? hotseatProfileInitialValues.getBarSizePx() : 0);
            int numWorkspaceColumns = getPanelCount() * inv.numColumns;
            // don't use availableHeightPx because it subtracts getInsets().bottom
            int availableResponsiveHeight =
                    mDeviceProperties.getHeightPx()
                            - mDeviceProperties.getInsets().top
                            - (isVerticalBarLayout() ? 0
                            : hotseatProfileInitialValues.getBarSizePx());
            float responsiveAspectRatio =
                    (float) mDeviceProperties.getWidthPx() / mDeviceProperties.getHeightPx();

            ResponsiveSpecsProvider workspaceSpecs = ResponsiveSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.workspaceSpecsId),
                    ResponsiveSpecType.Workspace);
            mResponsiveWorkspaceWidthSpec = workspaceSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.WIDTH, numWorkspaceColumns, availableResponsiveWidth);
            mResponsiveWorkspaceHeightSpec = workspaceSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.HEIGHT, inv.numRows, availableResponsiveHeight);

            ResponsiveSpecsProvider allAppsSpecs = ResponsiveSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.allAppsSpecsId),
                    ResponsiveSpecType.AllApps);
            mResponsiveAllAppsWidthSpec = allAppsSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.WIDTH, displayOptionSpec.numAllAppsColumns,
                    mDeviceProperties.getAvailableWidthPx(),
                    mResponsiveWorkspaceWidthSpec);
            if (!deviceProperties.getDeviceConfiguration().isExternalDisplay()
                    && inv.appListAlignedWithWorkspaceRow >= 0) {
                allAppsTopPadding += mResponsiveWorkspaceHeightSpec.getStartPaddingPx()
                        + inv.appListAlignedWithWorkspaceRow
                        * (mResponsiveWorkspaceHeightSpec.getCellSizePx()
                        + mResponsiveWorkspaceHeightSpec.getGutterPx());
            }
            mResponsiveAllAppsHeightSpec = allAppsSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.HEIGHT, inv.numAllAppsRowsForCellHeightCalculation,
                    mDeviceProperties.getHeightPx() - allAppsTopPadding,
                    mResponsiveWorkspaceHeightSpec);

            ResponsiveSpecsProvider folderSpecs = ResponsiveSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.folderSpecsId),
                    ResponsiveSpecType.Folder);
            mResponsiveFolderWidthSpec = folderSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.WIDTH, numFolderColumns,
                    mResponsiveWorkspaceWidthSpec.getAvailableSpace(),
                    mResponsiveWorkspaceWidthSpec);
            mResponsiveFolderHeightSpec = folderSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.HEIGHT,  numFolderRows,
                    mResponsiveWorkspaceHeightSpec.getAvailableSpace(),
                    mResponsiveWorkspaceHeightSpec);

            ResponsiveCellSpecsProvider allAppsCellSpecs = ResponsiveCellSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.allAppsCellSpecsId));
            mResponsiveAllAppsCellSpec = allAppsCellSpecs.getCalculatedSpec(
                    responsiveAspectRatio,
                    mResponsiveAllAppsHeightSpec.getAvailableSpace(),
                    mResponsiveWorkspaceCellSpec);
        }

        mWorkspaceProfile = WorkspaceProfile.Factory.createWorkspaceProfile(
                /*context*/ context,
                /*res*/ context.getResources(),
                /*deviceProperties*/ mDeviceProperties,
                /*scale*/ 1f,
                /*inv*/ inv,
                /*iconSizeSteps*/ mIconSizeSteps,
                /*isVerticalLayout*/ isVerticalBarLayout(),
                /*isResponsiveGrid*/ mIsResponsiveGrid,
                /*isScalableGrid*/ mIsScalableGrid,
                /*isQsbInline*/ hotseatProfileInitialValues.isQsbInline(),
                /*mResponsiveWorkspaceWidthSpec*/ mResponsiveWorkspaceWidthSpec,
                /*mResponsiveWorkspaceHeightSpec*/ mResponsiveWorkspaceHeightSpec,
                /*mResponsiveWorkspaceCellSpec*/ mResponsiveWorkspaceCellSpec,
                /*typeIndex*/ mTypeIndex,
                /*metrics*/ mMetrics,
                /*panelCount*/ getPanelCount(),
                /*iconSizePx*/ max(1, pxFromDp(inv.iconSize[mTypeIndex], mMetrics)),
                /*isFirstPass*/ true,
                /*isSeascape*/ isSeascape(),
                /*hotseatProfile*/ hotseatProfileInitialValues
        );

        if (mIsResponsiveGrid) {
            mAllAppsProfile = AllAppsProfile.Factory.createAllAppsWithResponsive(
                    /*deviceProperties*/ mDeviceProperties,
                    /*responsiveAllAppsCellSpec*/ mResponsiveAllAppsCellSpec,
                    /*responsiveAllAppsWidthSpec*/ mResponsiveAllAppsWidthSpec,
                    /*responsiveAllAppsHeightSpec*/ mResponsiveAllAppsHeightSpec,
                    /*iconSizeSteps*/ mIconSizeSteps,
                    /*isVerticalBarLayout*/ isVerticalBarLayout(),
                    /*res*/ res,
                    /*displayOptionSpec*/ displayOptionSpec,
                    /*allAppsTopPadding*/ allAppsTopPadding
            );
        } else {
            mAllAppsProfile = AllAppsProfile.Factory.createAllAppsProfile(
                    /*res*/ context.getResources(),
                    /*inv*/ inv,
                    /*metric*/ mMetrics,
                    /*isScalableGrid*/ mIsScalableGrid,
                    /*typeIndex*/ mTypeIndex,
                    /*workspaceProfile*/ mWorkspaceProfile,
                    /*deviceProperties*/ mDeviceProperties,
                    /*context*/ context,
                    /* allAppsTopPadding */ allAppsTopPadding,
                    /* displayOptionSpec */ displayOptionSpec
            );
        }


        final boolean isVerticalLayout = isVerticalBarLayout();
        if (isVerticalLayout && !mIsResponsiveGrid) {
            hideWorkspaceLabelsIfNotEnoughSpace();
        }

        if (inv.enableTwoLinesInAllApps
                && !(mIsResponsiveGrid && getAllAppsProfile().getMaxAllAppsTextLineCount() == 2)) {
            // Add extra textHeight to the existing allAppsCellHeight.
            mAllAppsProfile = getAllAppsProfile().copyWithCellHeightPx(
                    getAllAppsProfile().getCellHeightPx()
                            + Utilities.calculateTextHeight(getAllAppsProfile().getIconTextSizePx())
            );
        }

        mBottomSheetProfile = BottomSheetProfile.Factory.createBottomSheetProfile(
                getDeviceProperties(),
                res,
                mWorkspaceProfile.getEdgeMarginPx(),
                mWorkspaceProfile
        );

        // Folder scaling requires correct workspace paddings
        mFolderProfile = updateAvailableFolderCellDimensions(res, context);

        mHotseatProfile = HotseatProfile.Factory.createHotseatProfile(
                hotseatProfileInitialValues,
                mWorkspaceProfile,
                isVerticalLayout,
                /*inv*/ inv ,
                /*displayOptionSpec*/ displayOptionSpec,
                /*deviceProperties*/ mDeviceProperties,
                /*panelCount*/ getPanelCount(),
                /*mIsScalableGrid*/ mIsScalableGrid
        );

        mDropTargetProfile = DropTargetProfile
                .Factory
                .createDropTargetProfile(res, shouldApplyWidePortraitDimens);

        mSysuiProfile = SysuiProfile.Factory.createSysuiProfile(res, deviceProperties);

        mViewScaleProvider = viewScaleProvider;

        dimensionOverrideProvider.accept(this);
    }

    /**
     * @deprecated TODO(B/477295763) Properties of an immutable object shouldn't be updated, this
     * change doesn't ensure that the update values get propagated through the system. This
     * functionality has been here since 2021, this function was created as part of a refactor and
     * is kept to prevent altering the behaviour.
     */
    @Deprecated
    public void updateIsTaskbarPresentInApps(boolean value) {
        mTaskbarProfile = mTaskbarProfile.updateIsTaskbarPresentInApps(value);
    }

    private boolean isLandscapeOrientation()  {
        return inv.isFixedLandscape
                || isVerticalBarLayout()
                || (mDeviceProperties.isLargeScreen() && mDeviceProperties.isLandscape());
    }

    public DisplayOptionSpec getDisplayOptionSpec() {
        return mDisplayOptionSpec;
    }

    public DeviceProperties getDeviceProperties() {
        return mDeviceProperties;
    }

    public OverviewProfile getOverviewProfile() {
        return overviewProfile;
    }

    public HotseatProfile getHotseatProfile() {
        return mHotseatProfile;
    }

    public WorkspaceProfile getWorkspaceProfile() {
        return mWorkspaceProfile;
    }

    public void setWorkspaceProfile(WorkspaceProfile workspaceProfile) {
        mWorkspaceProfile = workspaceProfile;
    }

    /**
     * Return maximum of all apps row count displayed on screen. Note that 1) Partially displayed
     * row is counted as 1 row, and 2) we don't exclude the space of floating search bar. This
     * method is used for calculating number of {@link BubbleTextView} we need to pre-inflate. Thus
     * reasonable over estimation is fine.
     */
    public int getMaxAllAppsRowCount() {
        return (int) (Math.ceil(
                (mDeviceProperties.getAvailableHeightPx() - mAllAppsProfile.getPadding().top)
                        / (float) getAllAppsProfile().getCellHeightPx()));
    }

    /**
     * Calculates the width of the hotseat, changing spaces between the icons and removing icons if
     * necessary.
     */
    public void recalculateHotseatWidthAndBorderSpace(int hotseatIcons) {
        mHotseatProfile.recalculateHotseatWidthAndBorderSpace(
                inv,
                this,
                hotseatIcons
        );
    }

    public LauncherDisplayInfo getDisplayInfo() {
        return mInfo;
    }

    @VisibleForTesting
    public int getHotseatColumnSpan() {
        return mHotseatProfile.getColumnSpan();
    }

    @VisibleForTesting
    public int getHotseatWidthPx() {
        return mHotseatProfile.getWidthPx();
    }

    /** Creates a builder with the current properties filled in */
    public Builder toBuilder() {
        WindowBounds bounds = mDeviceProperties.createWindowBounds();
        bounds.bounds.offsetTo(mDeviceProperties.getWindowX(), mDeviceProperties.getWindowY());
        bounds.insets.set(mDeviceProperties.getInsets());

        return inv.newDPBuilder(mInfo)
                .setWindowBounds(bounds)
                .setIsMultiDisplay(mDeviceProperties.getDeviceConfiguration().isMultiDisplay())
                .setExternalDisplay(mDeviceProperties.getDeviceConfiguration().isExternalDisplay())
                .setGestureMode(mDeviceProperties.getDeviceConfiguration().isGestureMode())
                .setDisplayOptionSpec(mDisplayOptionSpec);
    }

    /** Creates a copy of the current device profile */
    public DeviceProfile copy() {
        return toBuilder().build();
    }

    /**
     * Checks if there is enough space for labels on the workspace.
     * If there is not, labels on the Workspace are hidden.
     * It is important to call this method after the All Apps variables have been set.
     */
    private void hideWorkspaceLabelsIfNotEnoughSpace() {
        // We want enough space so that the text is closer to its corresponding icon.
        if (getWorkspaceProfile().isItemsLabelHidden()) {
            // TODO(420933882) Group all modifications of AllAppsProfile in one place
            mAllAppsProfile = AllAppsProfile.Factory.autoResizeAllAppsCells(getAllAppsProfile());
        }
    }

    /** Creates a taskbar profile based on this device profiles. */
    public TaskbarProfile updateTaskbarProfile(Resources res, Boolean isTransient) {
        return TaskbarProfile.Factory.createTaskbarProfile(
                res,
                isTransient,
                mDeviceProperties.getTaskbarConfiguration().isTaskbarPresent(),
                mDisplayOptionSpec
        );
    }

    public FolderProfile updateAvailableFolderCellDimensions(Resources res, Context context) {
        FolderProfile folderProfile = updateFolderCellSize(1f, res, context);

        // Responsive grid doesn't need to scale the folder
        if (mIsResponsiveGrid) return folderProfile;

        // For usability we can't have the folder use the whole width of the screen
        Point totalWorkspacePadding = mWorkspaceProfile.getTotalWorkspacePadding();

        // Check if the folder fit within the available height.
        float contentUsedHeight = folderProfile.getCellHeightPx() * folderProfile.getNumRows()
                + ((folderProfile.getNumRows() - 1) * folderProfile.getCellLayoutBorderSpacePx().y)
                + folderProfile.getFooterHeightPx()
                + folderProfile.getContentPaddingTop();
        int contentMaxHeight = mDeviceProperties.getAvailableHeightPx() - totalWorkspacePadding.y;
        float scaleY = contentMaxHeight / contentUsedHeight;

        // Check if the folder fit within the available width.
        float contentUsedWidth = folderProfile.getCellWidthPx() * folderProfile.getNumColumns()
                + ((folderProfile.getNumColumns() - 1)
                    * folderProfile.getCellLayoutBorderSpacePx().x)
                + folderProfile.getContentPaddingLeftRight() * 2;
        int contentMaxWidth = mDeviceProperties.getAvailableWidthPx() - totalWorkspacePadding.x;
        float scaleX = contentMaxWidth / contentUsedWidth;

        float scale = Math.min(scaleX, scaleY);
        if (scale < 1f) {
            return updateFolderCellSize(scale, res, context);
        }
        return folderProfile;
    }

    private FolderProfile updateFolderCellSize(float scale, Resources res, Context context) {
        return FolderProfile.Factory.createFolderProfile(
                context,
                mIsResponsiveGrid,
                mIsScalableGrid,
                scale,
                mMetrics,
                inv,
                mTypeIndex,
                res,
                mResponsiveFolderHeightSpec,
                mResponsiveWorkspaceCellSpec,
                mResponsiveFolderWidthSpec,
                mIconSizeSteps,
                mWorkspaceProfile
        );
    }

    public void updateInsets(Rect insets) {
        mDeviceProperties.getInsets().set(insets);
    }

    /**
     * The current device insets. This is generally same as the insets being dispatched to
     * {@link Insettable} elements, but can differ if the element is using a different profile.
     */
    public Rect getInsets() {
        return mDeviceProperties.getInsets();
    }

    /**
     * Gets the number of panels within the workspace.
     */
    public int getPanelCount() {
        return mDeviceProperties.isTwoPanels() ? 2 : 1;
    }

    /**
     * Gets the space in px from the bottom of last item in the vertical-bar hotseat to the
     * bottom of the screen.
     */
    private int getVerticalHotseatLastItemBottomOffset(Context context) {
        Rect hotseatBarPadding = getHotseatLayoutPadding(context);
        int cellHeight = calculateCellHeight(
                mDeviceProperties.getHeightPx()
                        - hotseatBarPadding.top
                        - hotseatBarPadding.bottom,
                mHotseatProfile.getBorderSpace(),
                mHotseatProfile.getNumShownIcons());
        int extraIconEndSpacing = (cellHeight - getWorkspaceProfile().getIconSizePx()) / 2;
        return extraIconEndSpacing + hotseatBarPadding.bottom;
    }

    /**
     * Gets the scaled top of the workspace in px for the spring-loaded edit state.
     */
    public float getCellLayoutSpringLoadShrunkTop() {
        return mDeviceProperties.getInsets().top + getDropTargetProfile().getBarTopMarginPx()
                + getDropTargetProfile().getBarSizePx()
                + getDropTargetProfile().getBarBottomMarginPx();
    }

    /**
     * Returns the total height of the drop target bar, including its top and bottom margins and
     * the padding below it.
     */
    public float getDropTargetBarHeight() {
        return getDropTargetProfile().getBarSizePx()
                + getDropTargetProfile().getBarTopMarginPx()
                + getDropTargetProfile().getBarBottomMarginPx()
                + getDropTargetProfile().getButtonWorkspaceEdgeGapPx();
    }

    /**
     * Gets the scaled bottom of the workspace in px for the spring-loaded edit state.
     */
    public float getCellLayoutSpringLoadShrunkBottom(Context context) {
        int topOfHotseat = mHotseatProfile.getBarSizePx()
                + getHotseatProfile().getSpringLoadedBarTopMarginPx();
        return mDeviceProperties.getHeightPx() - (isVerticalBarLayout()
                ? getVerticalHotseatLastItemBottomOffset(context) : topOfHotseat);
    }

    /**
     * Gets the scale of the workspace for the spring-loaded edit state.
     */
    public float getWorkspaceSpringLoadScale(Context context) {
        float scale =
                (getCellLayoutSpringLoadShrunkBottom(context) - getCellLayoutSpringLoadShrunkTop())
                        / getCellLayoutHeight();
        scale = Math.min(scale, 1f);

        // Reduce scale if next pages would not be visible after scaling the workspace.
        int workspaceWidth = mDeviceProperties.getAvailableWidthPx();
        float scaledWorkspaceWidth = workspaceWidth * scale;
        float maxAvailableWidth = workspaceWidth
                - (2 * mWorkspaceProfile.getWorkspaceSpringLoadedMinNextPageVisiblePx());
        if (scaledWorkspaceWidth > maxAvailableWidth) {
            scale *= maxAvailableWidth / scaledWorkspaceWidth;
        }
        return scale;
    }

    /**
     * Gets the width of a single Cell Layout, aka a single panel within a Workspace.
     *
     * <p>This is the width of a Workspace, less its horizontal padding. Note that two-panel
     * layouts have two Cell Layouts per workspace.
     */
    public int getCellLayoutWidth() {
        return (mDeviceProperties.getAvailableWidthPx()
                - mWorkspaceProfile.getTotalWorkspacePadding().x) / getPanelCount();
    }

    /**
     * Gets the height of a single Cell Layout, aka a single panel within a Workspace.
     *
     * <p>This is the height of a Workspace, less its vertical padding.
     */
    public int getCellLayoutHeight() {
        return mDeviceProperties.getAvailableHeightPx()
                - mWorkspaceProfile.getTotalWorkspacePadding().y;
    }

    /**
     * Returns the new border space that should be used between hotseat icons after adjusting it to
     * the bubble bar.
     *
     * <p>Does not check for visible bubbles persistence, so caller should call
     * {@link #shouldAdjustHotseatOrQsbForBubbleBar} first.
     *
     * <p>If there's no adjustment needed, this method returns {@code 0}.
     *
     * @see #shouldAdjustHotseatOrQsbForBubbleBar(Context, boolean)
     */
    public float getHotseatAdjustedBorderSpaceForBubbleBar(Context context) {
        if (shouldAlignBubbleBarWithQSB() || !shouldAdjustHotseatOrQsbForBubbleBar(context)) {
            return 0;
        }
        // The adjustment is shrinking the hotseat's width by 1 icon on either side.
        int iconsWidth =
                getWorkspaceProfile().getIconSizePx() * mHotseatProfile.getNumShownIcons()
                        + mHotseatProfile.getBorderSpace() * (
                        mHotseatProfile.getNumShownIcons() - 1);
        int newWidth = iconsWidth - 2 * getWorkspaceProfile().getIconSizePx();
        // Evenly space the icons within the boundaries of the new width.
        return (float) (newWidth - getWorkspaceProfile().getIconSizePx()
                * mHotseatProfile.getNumShownIcons())
                / (mHotseatProfile.getNumShownIcons() - 1);
    }

    /**
     * Returns the hotseat icon translation X for the cellX index.
     *
     * <p>Does not check for visible bubbles persistence, so caller should call
     * {@link #shouldAdjustHotseatOrQsbForBubbleBar} first.
     *
     * <p>If there's no adjustment needed, this method returns {@code 0}.
     *
     * @see #shouldAdjustHotseatOrQsbForBubbleBar(Context, boolean)
     */
    public float getHotseatAdjustedTranslation(Context context, int cellX) {
        float borderSpace = getHotseatAdjustedBorderSpaceForBubbleBar(context);
        if (borderSpace == 0) return borderSpace;
        float borderSpaceDelta = borderSpace - mHotseatProfile.getBorderSpace();
        return getWorkspaceProfile().getIconSizePx() + cellX * borderSpaceDelta;
    }

    /** Returns whether hotseat or QSB should be adjusted for the bubble bar. */
    public boolean shouldAdjustHotseatOrQsbForBubbleBar(Context context, boolean hasBubbles) {
        return hasBubbles && shouldAdjustHotseatOrQsbForBubbleBar(context);
    }

    /** Returns whether hotseat should be adjusted for the bubble bar. */
    public boolean shouldAdjustHotseatForBubbleBar(Context context, boolean hasBubbles) {
        return shouldAlignBubbleBarWithHotseat()
                && shouldAdjustHotseatOrQsbForBubbleBar(context, hasBubbles);
    }

    /** Returns whether hotseat or QSB should be adjusted for the bubble bar. */
    public boolean shouldAdjustHotseatOrQsbForBubbleBar(Context context) {
        // only need to adjust if QSB is on top of the hotseat and there's not enough space for the
        // bubble bar to either side of the hotseat.
        if (mHotseatProfile.isQsbInline()) return false;
        Rect hotseatPadding = getHotseatLayoutPadding(context);
        int hotseatMinHorizontalPadding = Math.min(hotseatPadding.left, hotseatPadding.right);
        return hotseatMinHorizontalPadding <= mSysuiProfile.mBubbleBarSpaceThresholdPx;
    }

    /**
     * Returns the padding for hotseat view
     */
    public Rect getHotseatLayoutPadding(Context context) {
        Rect hotseatBarPadding = new Rect();
        if (isVerticalBarLayout()) {
            // The hotseat icons will be placed in the middle of the hotseat cells.
            // Changing the hotseatCellHeightPx is not affecting hotseat icon positions
            // in vertical bar layout.
            int paddingTop = max(
                    (int) (mDeviceProperties.getInsets().top
                            + mWorkspaceProfile.getCellLayoutPaddingPx().top),
                    0
            );
            int paddingBottom = max(
                    (int) (mDeviceProperties.getInsets().bottom
                            + mWorkspaceProfile.getCellLayoutPaddingPx().bottom),
                    0
            );

            if (isSeascape()) {
                hotseatBarPadding.set(mDeviceProperties.getInsets().left
                                + getHotseatProfile().getBarEdgePaddingPx(),
                        paddingTop, getHotseatProfile().getBarWorkspaceSpacePx(), paddingBottom);
            } else {
                hotseatBarPadding.set(getHotseatProfile().getBarWorkspaceSpacePx(), paddingTop,
                        mDeviceProperties.getInsets().right
                                + getHotseatProfile().getBarEdgePaddingPx(), paddingBottom);
            }
        } else if (inv.isFixedLandscape) {
            // Center the QSB vertically with hotseat
            int hotseatBarBottomPadding = getHotseatBarBottomPadding();
            int hotseatPlusQSBWidth = mWorkspaceProfile
                    .getIconToIconWidthForColumns(inv.numColumns);

            // This is needed because of b/235886078 since QSB needs to span to the icon borders
            int iconExtraSpacePx = getWorkspaceProfile().getIconSizePx() - getIconVisibleSizePx(
                    getWorkspaceProfile().getIconSizePx());
            int qsbWidth = getAdditionalQsbSpace() + iconExtraSpacePx / 2;

            int availableWidthPxForHotseat = mDeviceProperties.getAvailableWidthPx() - Math.abs(
                    mWorkspaceProfile.getWorkspacePadding().width())
                    - Math.abs(mWorkspaceProfile.getCellLayoutPaddingPx().width());
            int remainingSpaceOnSide = (availableWidthPxForHotseat - hotseatPlusQSBWidth) / 2;

            hotseatBarPadding.set(
                    remainingSpaceOnSide + mDeviceProperties.getInsets().left
                            + mWorkspaceProfile.getWorkspacePadding().left
                            + mWorkspaceProfile.getCellLayoutPaddingPx().left,
                    mHotseatProfile.getBarSizePx() - hotseatBarBottomPadding
                            - mHotseatProfile.getCellHeightPx(),
                    remainingSpaceOnSide
                            + mDeviceProperties.getInsets().right
                            + mWorkspaceProfile.getWorkspacePadding().right
                            + mWorkspaceProfile.getCellLayoutPaddingPx().right,
                    hotseatBarBottomPadding
            );
            if (Utilities.isRtl(context.getResources())) {
                hotseatBarPadding.right += qsbWidth;
            } else {
                hotseatBarPadding.left += qsbWidth;
            }
        } else if (mDeviceProperties.getTaskbarConfiguration().isTaskbarPresent()) {
            // Center the QSB vertically with hotseat
            int hotseatBarBottomPadding = getHotseatBarBottomPadding();
            int hotseatBarTopPadding =
                    mHotseatProfile.getBarSizePx()
                            - hotseatBarBottomPadding
                            - mHotseatProfile.getCellHeightPx();

            int hotseatWidth = getHotseatRequiredWidth();
            int startSpacing;
            int endSpacing;
            // Hotseat aligns to the left with nav buttons
            if (getHotseatProfile().getBarEndOffset() > 0) {
                startSpacing = getHotseatProfile().getInlineNavButtonsEndSpacingPx();
                endSpacing = mDeviceProperties.getAvailableWidthPx() - hotseatWidth - startSpacing
                        + mHotseatProfile.getBorderSpace();
            } else {
                startSpacing = (mDeviceProperties.getAvailableWidthPx() - hotseatWidth) / 2;
                endSpacing = startSpacing;
            }
            startSpacing += getAdditionalQsbSpace();

            hotseatBarPadding.top = hotseatBarTopPadding;
            hotseatBarPadding.bottom = hotseatBarBottomPadding;
            boolean isRtl = Utilities.isRtl(context.getResources());
            if (isRtl) {
                hotseatBarPadding.left = endSpacing;
                hotseatBarPadding.right = startSpacing;
            } else {
                hotseatBarPadding.left = startSpacing;
                hotseatBarPadding.right = endSpacing;
            }

        } else if (mIsScalableGrid) {
            int iconExtraSpacePx = getWorkspaceProfile().getIconSizePx() - getIconVisibleSizePx(
                    getWorkspaceProfile().getIconSizePx());
            int sideSpacing =
                    (mDeviceProperties.getAvailableWidthPx() - (
                            mHotseatProfile.getQsbWidth() + iconExtraSpacePx))
                            / 2;
            hotseatBarPadding.set(sideSpacing,
                    0,
                    sideSpacing,
                    getHotseatBarBottomPadding());
        } else {
            // We want the edges of the hotseat to line up with the edges of the workspace, but the
            // icons in the hotseat are a different size, and so don't line up perfectly. To account
            // for this, we pad the left and right of the hotseat with half of the difference of a
            // workspace cell vs a hotseat cell.
            float workspaceCellWidth = (float) mDeviceProperties.getWidthPx() / inv.numColumns;
            float hotseatCellWidth = (float) mDeviceProperties.getWidthPx()
                    / mHotseatProfile.getNumShownIcons();
            int hotseatAdjustment = Math.round((workspaceCellWidth - hotseatCellWidth) / 2);
            hotseatBarPadding.set(
                    hotseatAdjustment + mWorkspaceProfile.getWorkspacePadding().left
                            + mWorkspaceProfile.getCellLayoutPaddingPx().left
                            + mDeviceProperties.getInsets().left,
                    0,
                    hotseatAdjustment + mWorkspaceProfile.getWorkspacePadding().right
                            + mWorkspaceProfile.getCellLayoutPaddingPx().right
                            + mDeviceProperties.getInsets().right,
                    getHotseatBarBottomPadding());
        }
        return hotseatBarPadding;
    }

    /** The margin between the edge of all apps and the edge of the first icon. */
    public int getAllAppsIconStartMargin(Context context) {
        int allAppsSpacing;
        if (isVerticalBarLayout()) {
            // On phones, the landscape layout uses a different setup.
            allAppsSpacing = mWorkspaceProfile.getWorkspacePadding().left
                    + mWorkspaceProfile.getWorkspacePadding().right;
        } else {
            allAppsSpacing =
                    mAllAppsProfile.getPadding().left
                            + mAllAppsProfile.getPadding().right
                            + mAllAppsProfile.getLeftRightMargin() * 2;
        }

        int cellWidth = DeviceProfile.calculateCellWidth(
                mDeviceProperties.getAvailableWidthPx() - allAppsSpacing,
                0 /* borderSpace */,
                mAllAppsProfile.getNumShownAllAppsColumns());
        int iconAlignmentMargin = (cellWidth - getIconVisibleSizePx(
                getAllAppsProfile().getIconSizePx())) / 2;

        return (Utilities.isRtl(context.getResources()) ? mAllAppsProfile.getPadding().right
                : mAllAppsProfile.getPadding().left) + iconAlignmentMargin;
    }

    /**
     * TODO(b/235886078): workaround needed because of this bug
     * Icons are 10% larger on XML than their visual size, so remove that extra space to get
     * some dimensions correct.
     *
     * When this bug is resolved this method will no longer be needed and we would be able to
     * replace all instances where this method is called with iconSizePx.
     */
    private int getIconVisibleSizePx(int iconSizePx) {
        return Math.round(ICON_VISIBLE_AREA_FACTOR * iconSizePx);
    }

    private int getAdditionalQsbSpace() {
        return mHotseatProfile.isQsbInline() ? mHotseatProfile.getQsbWidth()
                + mHotseatProfile.getBorderSpace() : 0;
    }

    /**
     * Calculate how much space the hotseat needs to be shown completely
     */
    private int getHotseatRequiredWidth() {
        int additionalQsbSpace = getAdditionalQsbSpace();
        return getWorkspaceProfile().getIconSizePx() * mHotseatProfile.getNumShownIcons()
                + mHotseatProfile.getBorderSpace() * (
                mHotseatProfile.getNumShownIcons()
                - (getHotseatProfile().getAreNavButtonsInline() ? 0 : 1))
                + additionalQsbSpace;
    }

    /**
     * Returns the number of pixels the QSB is translated from the bottom of the screen.
     */
    public int getQsbOffsetY() {
        if (mHotseatProfile.isQsbInline()) {
            return getHotseatBarBottomPadding()
                    - ((getHotseatProfile().getQsbHeight()
                    - mHotseatProfile.getCellHeightPx()) / 2);
        } else if (mDeviceProperties.getTaskbarConfiguration().isTaskbarPresent()) { // QSB on top
            return mHotseatProfile.getBarSizePx() - getHotseatProfile().getQsbHeight()
                    + getHotseatProfile().getQsbShadowHeight();
        } else {
            return mHotseatProfile.getBarBottomSpacePx() - getHotseatProfile().getQsbShadowHeight();
        }
    }

    /**
     * Returns the number of pixels the hotseat is translated from the bottom of the screen.
     */
    private int getHotseatBarBottomPadding() {
        // QSB on top or inline
        if (mDeviceProperties.getTaskbarConfiguration().isTaskbarPresent()
                || mHotseatProfile.isQsbInline()) {
            return mHotseatProfile.getBarBottomSpacePx() - (Math.abs(
                    mHotseatProfile.getCellHeightPx()
                            - getWorkspaceProfile().getIconSizePx()) / 2);
        } else {
            return mHotseatProfile.getBarSizePx() - mHotseatProfile.getCellHeightPx();
        }
    }

    /**
     * Returns the number of pixels the hotseat icons or QSB vertical center is translated from the
     * bottom of the screen.
     */
    public int getBubbleBarVerticalCenterForHome() {
        if (shouldAlignBubbleBarWithHotseat()) {
            return mHotseatProfile.getBarSizePx()
                    - (mHotseatProfile.isQsbInline() ? 0 : getHotseatProfile().getQsbVisualHeight())
                    - mHotseatProfile.getQsbSpace()
                    - (mHotseatProfile.getCellHeightPx() / 2)
                    + ((mHotseatProfile.getCellHeightPx()
                    - getWorkspaceProfile().getIconSizePx()) / 2);
        } else {
            return mHotseatProfile.getBarSizePx()
                    - (getHotseatProfile().getQsbVisualHeight() / 2);
        }
    }

    /** Returns whether bubble bar should be aligned with the hotseat. */
    public boolean shouldAlignBubbleBarWithQSB() {
        return !shouldAlignBubbleBarWithHotseat();
    }

    /** Returns whether bubble bar should be aligned with the hotseat. */
    public boolean shouldAlignBubbleBarWithHotseat() {
        return mHotseatProfile.isQsbInline()
                || mDeviceProperties.getDeviceConfiguration().isGestureMode();
    }

    /**
     * Returns the number of pixels the taskbar is translated from the bottom of the screen.
     */
    public int getTaskbarOffsetY() {
        int taskbarIconBottomSpace =
                (getTaskbarProfile().getHeight() - getWorkspaceProfile().getIconSizePx()) / 2;
        int launcherIconBottomSpace = Math.min(
                (mHotseatProfile.getCellHeightPx() - getWorkspaceProfile().getIconSizePx()
                ) / 2, mWorkspaceProfile.getGridVisualizationPaddingY());
        // Taskbar Icon Alignment Animation is On
        // We need this for taskbar icons to softly land on hotseat icons.
        if (mTaskbarProfile.isTransientTaskbar() && !enableRecentsInTaskbar()) {
            return getHotseatBarBottomPadding() + launcherIconBottomSpace - taskbarIconBottomSpace;
        } else {
            // when icon alignment animation is not on, we only use taskbar activity device profile,
            // so we need to add the hot seat bottom padding + half of the hot seat cell item and
            // subtract half of nav button container height for it to center.
            int hotSeatIconHalf = mHotseatProfile.getCellHeightPx() / 2;
            int navButtonContainerHalf = getTaskbarProfile().getHeight() / 2;
            return mHotseatProfile.getBarBottomSpacePx() + hotSeatIconHalf - navButtonContainerHalf;
        }
    }

    /** Returns the number of pixels required below OverviewActions. */
    public int getOverviewActionsClaimedSpaceBelow() {
        return mDeviceProperties.getTaskbarConfiguration().isTaskbarPresent()
                ? getTaskbarProfile().getTransientTaskbarClaimedSpace()
                : mDeviceProperties.getInsets().bottom;
    }

    /** Gets the space that the overview actions will take, including bottom margin. */
    public int getOverviewActionsClaimedSpace() {
        int overviewActionsSpace = mDeviceProperties.isLargeScreen()
                ? 0
                : (overviewProfile.getActionsTopMarginPx() + overviewProfile.getActionsHeight());
        return overviewActionsSpace + getOverviewActionsClaimedSpaceBelow();
    }

    /**
     * Takes the View and return the scales of width and height depending on the DeviceProfile
     * specifications
     *
     * @param itemInfo The tag of the widget view
     * @return A PointF instance with the x set to be the scale of width, and y being the scale of
     * height
     */
    @NonNull
    public PointF getAppWidgetScale(@Nullable final ItemInfo itemInfo) {
        return mViewScaleProvider.getScaleFromItemInfo(itemInfo);
    }

    /**
     * @return the bounds for which the open folders should be contained within
     */
    public Rect getAbsoluteOpenFolderBounds() {
        if (isVerticalBarLayout()) {
            // Folders should only appear right of the drop target bar and left of the hotseat
            return new Rect(
                    mDeviceProperties.getInsets().left + getDropTargetProfile().getBarSizePx()
                            + mWorkspaceProfile.getEdgeMarginPx(),
                    mDeviceProperties.getInsets().top,
                    mDeviceProperties.getInsets().left
                            + mDeviceProperties.getAvailableWidthPx()
                            - mHotseatProfile.getBarSizePx()
                            - mWorkspaceProfile.getEdgeMarginPx(),
                    mDeviceProperties.getInsets().top
                            + mDeviceProperties.getAvailableHeightPx()
            );
        } else {
            // Folders should only appear below the drop target bar and above the hotseat
            int hotseatTop = mDeviceProperties.getTaskbarConfiguration().isTaskbarPresent()
                    ? getTaskbarProfile().getHeight()
                    : mHotseatProfile.getBarSizePx();
            return new Rect(
                    mDeviceProperties.getInsets().left + mWorkspaceProfile.getEdgeMarginPx(),
                    mDeviceProperties.getInsets().top + getDropTargetProfile().getBarSizePx()
                            + mWorkspaceProfile.getEdgeMarginPx(),
                    mDeviceProperties.getInsets().left
                            + mDeviceProperties.getAvailableWidthPx()
                            - mWorkspaceProfile.getEdgeMarginPx(),
                    mDeviceProperties.getInsets().top
                            + mDeviceProperties.getAvailableHeightPx() - hotseatTop
                            - mWorkspaceProfile.getWorkspacePageIndicatorHeight()
                            - mWorkspaceProfile.getEdgeMarginPx()
            );
        }
    }

    public static int calculateCellWidth(int width, int borderSpacing, int countX) {
        return (width - ((countX - 1) * borderSpacing)) / countX;
    }

    public static int calculateCellHeight(int height, int borderSpacing, int countY) {
        return (height - ((countY - 1) * borderSpacing)) / countY;
    }

    /**
     * When {@code true}, the device is in landscape mode and the hotseat is on the right column.
     * When {@code false}, either device is in portrait mode or the device is in landscape mode and
     * the hotseat is on the bottom row.
     */
    public boolean isVerticalBarLayout() {
        return mDeviceProperties.isLandscape()
                && mDeviceProperties.getDeviceConfiguration().getTransposeLayoutWithOrientation();
    }

    public boolean isSeascape() {
        return mDeviceProperties.getRotationHint() == Surface.ROTATION_270
                && (isVerticalBarLayout() || inv.isFixedLandscape);
    }

    public boolean shouldFadeAdjacentWorkspaceScreens() {
        return isVerticalBarLayout();
    }

    public int getCellContentHeight(@ContainerType int containerType) {
        switch (containerType) {
            case CellLayout.WORKSPACE:
                return getWorkspaceProfile().getCellHeightPx();
            case CellLayout.FOLDER:
                return mFolderProfile.getCellHeightPx();
            case CellLayout.HOTSEAT:
                // The hotseat is the only container where the cell height is going to be
                // different from the content within that cell.
                return getWorkspaceProfile().getIconSizePx();
            default:
                // ??
                return 0;
        }
    }

    private String pxToDpStr(String name, float value) {
        return "\t" + name + ": " + value + "px (" + pxToDp(value) + "dp)";
    }

    /**
     * Converts from px to dp.
     *
     * @param value is the px value that we want to convert.
     * @return the dp of value based on the current density.
     */
    public float pxToDp(float value) {
        return dpiFromPx(value, mMetrics.densityDpi);
    }

    private String dpPointFToString(String name, PointF value) {
        return String.format(Locale.ENGLISH, "\t%s: PointF(%.1f, %.1f)dp", name, value.x, value.y);
    }

    /** Dumps various DeviceProfile variables to the specified writer. */
    public void dump(Context context, String prefix, PrintWriter writer) {
        writer.println(prefix + "DeviceProfile:");
        writer.println(prefix + "\t1 dp = " + mMetrics.density + " px");

        writer.println(prefix + "\tisTablet:" + mDeviceProperties.isLargeScreen());
        writer.println(prefix + "\tisPhone:" + mDeviceProperties.isPhone());
        writer.println(prefix + "\ttransposeLayoutWithOrientation:"
                + mDeviceProperties.getDeviceConfiguration().getTransposeLayoutWithOrientation());
        writer.println(
                prefix + "\tisGestureMode:" + mDeviceProperties.getDeviceConfiguration()
                        .isGestureMode()
        );

        writer.println(prefix + "\tisLandscape:" + mDeviceProperties.isLandscape());
        writer.println(
                prefix + "\tisExternalDisplay:"
                        + mDeviceProperties.getDeviceConfiguration().isExternalDisplay()
        );
        writer.println(prefix + "\tisTwoPanels:" + mDeviceProperties.isTwoPanels());
        writer.println(prefix + "\tisLeftRightSplit:" + mSysuiProfile.isLeftRightSplit());

        writer.println(prefix + pxToDpStr("windowX", mDeviceProperties.getWindowX()));
        writer.println(prefix + pxToDpStr("windowY", mDeviceProperties.getWindowY()));
        writer.println(prefix + pxToDpStr("widthPx", mDeviceProperties.getWidthPx()));
        writer.println(prefix + pxToDpStr("heightPx", mDeviceProperties.getHeightPx()));
        writer.println(
                prefix + pxToDpStr("availableWidthPx",
                        mDeviceProperties.getAvailableWidthPx()));
        writer.println(
                prefix + pxToDpStr("availableHeightPx",
                        mDeviceProperties.getAvailableHeightPx()));
        writer.println(
                prefix + pxToDpStr("mInsets.left", mDeviceProperties.getInsets().left)
        );
        writer.println(
                prefix + pxToDpStr("mInsets.top", mDeviceProperties.getInsets().top)
        );
        writer.println(
                prefix + pxToDpStr("mInsets.right", mDeviceProperties.getInsets().right)
        );
        writer.println(
                prefix + pxToDpStr("mInsets.bottom", mDeviceProperties.getInsets().bottom)
        );

        writer.println(prefix + "\taspectRatio:" + mDeviceProperties.getAspectRatio());

        writer.println(prefix + "\tisResponsiveGrid:" + mIsResponsiveGrid);
        writer.println(prefix + "\tisScalableGrid:" + mIsScalableGrid);

        writer.println(prefix + "\tinv.numRows: " + inv.numRows);
        writer.println(prefix + "\tinv.numColumns: " + inv.numColumns);
        writer.println(prefix + "\tinv.numSearchContainerColumns: "
                + inv.numSearchContainerColumns);

        writer.println(prefix + dpPointFToString("minCellSize", inv.minCellSize[mTypeIndex]));

        writer.println(
                prefix + pxToDpStr("cellWidthPx", getWorkspaceProfile().getCellWidthPx())
        );
        writer.println(
                prefix + pxToDpStr("cellHeightPx", getWorkspaceProfile().getCellHeightPx())
        );

        writer.println(
                prefix + pxToDpStr("getCellSize().x", mWorkspaceProfile.getCellSize().x)
        );
        writer.println(
                prefix + pxToDpStr("getCellSize().y", mWorkspaceProfile.getCellSize().y)
        );

        writer.println(prefix + pxToDpStr("cellLayoutBorderSpacePx Horizontal",
                getWorkspaceProfile().getCellLayoutBorderSpacePx().x));
        writer.println(prefix + pxToDpStr("cellLayoutBorderSpacePx Vertical",
                getWorkspaceProfile().getCellLayoutBorderSpacePx().y));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.left",
                        mWorkspaceProfile.getCellLayoutPaddingPx().left));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.top",
                        mWorkspaceProfile.getCellLayoutPaddingPx().top));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.right",
                        mWorkspaceProfile.getCellLayoutPaddingPx().right));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.bottom",
                        mWorkspaceProfile.getCellLayoutPaddingPx().bottom));

        writer.println(prefix + pxToDpStr("iconSizePx", getWorkspaceProfile().getIconSizePx()));
        writer.println(prefix + pxToDpStr("iconTextSizePx",
                getWorkspaceProfile().getIconTextSizePx()));
        writer.println(prefix + pxToDpStr("iconDrawablePaddingPx",
                getWorkspaceProfile().getIconDrawablePaddingPx()));

        writer.println(prefix + "\tnumFolderRows: " + mFolderProfile.getNumRows());
        writer.println(prefix + "\tnumFolderColumns: " + mFolderProfile.getNumColumns());
        writer.println(prefix + pxToDpStr("folderCellWidthPx",
                mFolderProfile.getCellWidthPx()));
        writer.println(prefix + pxToDpStr("folderCellHeightPx",
                mFolderProfile.getCellHeightPx()));
        writer.println(prefix + pxToDpStr("folderChildIconSizePx",
                mFolderProfile.getChildIconSizePx()));
        writer.println(prefix + pxToDpStr("folderChildTextSizePx",
                mFolderProfile.getChildTextSizePx()));
        writer.println(prefix + pxToDpStr("folderChildDrawablePaddingPx",
                mFolderProfile.getChildDrawablePaddingPx()));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpacePx.x",
                mFolderProfile.getCellLayoutBorderSpacePx().x));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpacePx.y",
                mFolderProfile.getCellLayoutBorderSpacePx().y));
        writer.println(prefix + pxToDpStr("folderContentPaddingLeftRight",
                mFolderProfile.getContentPaddingLeftRight()));
        writer.println(prefix + pxToDpStr("folderTopPadding",
                mFolderProfile.getContentPaddingTop()));
        writer.println(prefix + pxToDpStr("folderFooterHeight",
                mFolderProfile.getFooterHeightPx()));

        writer.println(prefix + pxToDpStr("bottomSheetTopPadding",
                getBottomSheetProfile().getBottomSheetTopPadding()));
        writer.println(prefix + "\tbottomSheetOpenDuration: "
                + getBottomSheetProfile().getBottomSheetOpenDuration());
        writer.println(prefix + "\tbottomSheetCloseDuration: "
                + getBottomSheetProfile().getBottomSheetCloseDuration());
        writer.println(prefix + "\tbottomSheetWorkspaceScale: "
                + getBottomSheetProfile().getBottomSheetWorkspaceScale());
        writer.println(prefix + "\tbottomSheetDepth: "
                + getBottomSheetProfile().getBottomSheetDepth());

        writer.println(prefix + pxToDpStr("allAppsShiftRange",
                mAllAppsProfile.getShiftRange()));
        writer.println(prefix + "\tallAppsOpenDuration: " + mAllAppsProfile.getOpenDuration());
        writer.println(prefix + "\tallAppsCloseDuration: " + mAllAppsProfile.getCloseDuration());
        writer.println(prefix + pxToDpStr("allAppsIconSizePx",
                getAllAppsProfile().getIconSizePx()));
        writer.println(prefix + pxToDpStr("allAppsIconTextSizePx",
                getAllAppsProfile().getIconTextSizePx()));
        writer.println(prefix + pxToDpStr("allAppsIconDrawablePaddingPx",
                getAllAppsProfile().getIconDrawablePaddingPx()));
        writer.println(prefix + pxToDpStr("allAppsCellHeightPx",
                getAllAppsProfile().getCellHeightPx()));
        writer.println(prefix + pxToDpStr("allAppsCellWidthPx",
                getAllAppsProfile().getCellWidthPx()));
        writer.println(prefix + pxToDpStr("allAppsBorderSpacePxX",
                getAllAppsProfile().getBorderSpacePx().x));
        writer.println(prefix + pxToDpStr("allAppsBorderSpacePxY",
                getAllAppsProfile().getBorderSpacePx().y));
        writer.println(prefix + "\tnumShownAllAppsColumns: "
                + mAllAppsProfile.getNumShownAllAppsColumns());
        writer.println(
                prefix + pxToDpStr("allAppsPadding.top", mAllAppsProfile.getPadding().top)
        );
        writer.println(prefix + pxToDpStr("allAppsPadding.left",
                mAllAppsProfile.getPadding().left));
        writer.println(prefix + pxToDpStr("allAppsPadding.right",
                mAllAppsProfile.getPadding().right));
        writer.println(prefix + pxToDpStr("allAppsLeftRightMargin",
                mAllAppsProfile.getLeftRightMargin()));

        writer.println(prefix + pxToDpStr("hotseatBarSizePx",
                mHotseatProfile.getBarSizePx()));
        writer.println(prefix + "\tmHotseatColumnSpan: " + mHotseatProfile.getColumnSpan());
        writer.println(
                prefix + pxToDpStr("mHotseatWidthPx", mHotseatProfile.getWidthPx())
        );
        writer.println(prefix + pxToDpStr("hotseatCellHeightPx",
                mHotseatProfile.getCellHeightPx()));
        writer.println(prefix + pxToDpStr("hotseatBarBottomSpacePx",
                mHotseatProfile.getBarBottomSpacePx()));
        writer.println(prefix + pxToDpStr("mHotseatBarEdgePaddingPx",
                getHotseatProfile().getBarEdgePaddingPx()));
        writer.println(prefix + pxToDpStr("mHotseatBarWorkspaceSpacePx",
                getHotseatProfile().getBarWorkspaceSpacePx()));
        writer.println(prefix + pxToDpStr("inlineNavButtonsEndSpacingPx",
                getHotseatProfile().getInlineNavButtonsEndSpacingPx()));
        writer.println(prefix + pxToDpStr("navButtonsLayoutWidthPx",
                getHotseatProfile().getNavButtonsLayoutWidthPx()));
        writer.println(prefix + pxToDpStr("hotseatBarEndOffset",
                getHotseatProfile().getBarEndOffset()));
        writer.println(prefix + pxToDpStr("hotseatQsbSpace", mHotseatProfile.getQsbSpace()));
        writer.println(
                prefix + pxToDpStr("hotseatQsbHeight", getHotseatProfile().getQsbHeight())
        );
        writer.println(prefix + pxToDpStr("springLoadedHotseatBarTopMarginPx",
                getHotseatProfile().getSpringLoadedBarTopMarginPx()));
        Rect hotseatLayoutPadding = getHotseatLayoutPadding(context);
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).top",
                hotseatLayoutPadding.top));
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).bottom",
                hotseatLayoutPadding.bottom));
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).left",
                hotseatLayoutPadding.left));
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).right",
                hotseatLayoutPadding.right));
        writer.println(
                prefix + "\tnumShownHotseatIcons: " + mHotseatProfile.getNumShownIcons()
        );
        writer.println(prefix + pxToDpStr("hotseatBorderSpace",
                mHotseatProfile.getBorderSpace()));
        writer.println(prefix + "\tisQsbInline: " + mHotseatProfile.isQsbInline());
        writer.println(
                prefix + pxToDpStr("hotseatQsbWidth", mHotseatProfile.getQsbWidth())
        );

        writer.println(
                prefix + "\tisTaskbarPresent:"
                        + mDeviceProperties.getTaskbarConfiguration().isTaskbarPresent()
        );
        writer.println(
                prefix + "\tisTaskbarPresentInApps:" + mTaskbarProfile.isTaskbarPresentInApps()
        );
        writer.println(prefix + pxToDpStr("taskbarHeight", getTaskbarProfile().getHeight()));
        writer.println(prefix + pxToDpStr("stashedTaskbarHeight",
                getTaskbarProfile().getStashedTaskbarHeight()));
        writer.println(prefix + pxToDpStr("taskbarBottomMargin",
                getTaskbarProfile().getBottomMargin()));
        writer.println(prefix + pxToDpStr("taskbarIconSize", getTaskbarProfile().getIconSize()));

        writer.println(prefix + pxToDpStr("desiredWorkspaceHorizontalMarginPx",
                getWorkspaceProfile().getDesiredWorkspaceHorizontalMarginPx()));
        writer.println(prefix + pxToDpStr("workspacePadding.left",
                mWorkspaceProfile.getWorkspacePadding().left));
        writer.println(prefix + pxToDpStr("workspacePadding.top",
                mWorkspaceProfile.getWorkspacePadding().top));
        writer.println(prefix + pxToDpStr("workspacePadding.right",
                mWorkspaceProfile.getWorkspacePadding().right));
        writer.println(prefix + pxToDpStr("workspacePadding.bottom",
                mWorkspaceProfile.getWorkspacePadding().bottom));

        writer.println(prefix + pxToDpStr("iconScale", getWorkspaceProfile().getIconScale()));
        writer.println(prefix + pxToDpStr("cellScaleToFit ",
                getWorkspaceProfile().getCellScaleToFit()));
        writer.println(prefix + pxToDpStr("extraSpace", mWorkspaceProfile.getExtraSpace()));
        writer.println(prefix + pxToDpStr("unscaled extraSpace",
                mWorkspaceProfile.getExtraSpace() / getWorkspaceProfile().getIconScale()));

        writer.println(prefix + pxToDpStr("maxEmptySpace", mWorkspaceProfile.getMaxEmptySpace()));
        writer.println(prefix + pxToDpStr("workspaceTopPadding",
                mWorkspaceProfile.getWorkspaceTopPadding()));
        writer.println(prefix + pxToDpStr("workspaceBottomPadding",
                mWorkspaceProfile.getWorkspaceBottomPadding()));

        writer.println(prefix + pxToDpStr("overviewTaskMarginPx",
                getOverviewProfile().getTaskMarginPx()));
        writer.println(prefix + pxToDpStr("overviewTaskIconSizePx",
                getOverviewProfile().getTaskIconSizePx()));
        writer.println(prefix + pxToDpStr("overviewTaskIconDrawableSizePx",
                getOverviewProfile().getTaskIconDrawableSizePx()));
        writer.println(prefix + pxToDpStr("overviewTaskIconDrawableSizeGridPx",
                getOverviewProfile().getTaskIconDrawableSizeGridPx()));
        writer.println(prefix + pxToDpStr("overviewActionsTopMarginPx",
                getOverviewProfile().getActionsTopMarginPx()));
        writer.println(prefix + pxToDpStr("overviewActionsHeight",
                getOverviewProfile().getActionsHeight()));
        writer.println(prefix + pxToDpStr("overviewActionsClaimedSpaceBelow",
                getOverviewActionsClaimedSpaceBelow()));
        writer.println(prefix + pxToDpStr("overviewPageSpacing",
                getOverviewProfile().getPageSpacing()));
        writer.println(prefix + pxToDpStr("overviewRowSpacing",
                getOverviewProfile().getRowSpacing()));
        writer.println(prefix + pxToDpStr("overviewGridSideMargin",
                getOverviewProfile().getGridSideMargin()));

        writer.println(prefix + pxToDpStr("dropTargetBarTopMarginPx",
                getDropTargetProfile().getBarTopMarginPx()));
        writer.println(prefix + pxToDpStr("dropTargetBarSizePx",
                getDropTargetProfile().getBarSizePx()));
        writer.println(
                prefix + pxToDpStr("dropTargetBarBottomMarginPx",
                        getDropTargetProfile().getBarBottomMarginPx()));

        writer.println(prefix + pxToDpStr("getCellLayoutSpringLoadShrunkTop()",
                getCellLayoutSpringLoadShrunkTop()));
        writer.println(prefix + pxToDpStr("getCellLayoutSpringLoadShrunkBottom()",
                getCellLayoutSpringLoadShrunkBottom(context)));
        writer.println(prefix + pxToDpStr("workspaceSpringLoadedMinNextPageVisiblePx",
                mWorkspaceProfile.getWorkspaceSpringLoadedMinNextPageVisiblePx()));
        writer.println(prefix + pxToDpStr("getWorkspaceSpringLoadScale()",
                getWorkspaceSpringLoadScale(context)));
        writer.println(prefix + pxToDpStr("getCellLayoutHeight()", getCellLayoutHeight()));
        writer.println(prefix + pxToDpStr("getCellLayoutWidth()", getCellLayoutWidth()));
        if (mIsResponsiveGrid) {
            writer.println(prefix + "\tmResponsiveWorkspaceHeightSpec:"
                    + mResponsiveWorkspaceHeightSpec.toString());
            writer.println(prefix + "\tmResponsiveWorkspaceWidthSpec:"
                    + mResponsiveWorkspaceWidthSpec.toString());
            writer.println(prefix + "\tmResponsiveAllAppsHeightSpec:"
                    + mResponsiveAllAppsHeightSpec.toString());
            writer.println(prefix + "\tmResponsiveAllAppsWidthSpec:"
                    + mResponsiveAllAppsWidthSpec.toString());
            writer.println(prefix + "\tmResponsiveFolderHeightSpec:" + mResponsiveFolderHeightSpec);
            writer.println(prefix + "\tmResponsiveFolderWidthSpec:" + mResponsiveFolderWidthSpec);
            writer.println(prefix + "\tmResponsiveHotseatSpec:" + mResponsiveHotseatSpec);
            writer.println(prefix + "\tmResponsiveWorkspaceCellSpec:"
                    + mResponsiveWorkspaceCellSpec);
            writer.println(prefix + "\tmResponsiveAllAppsCellSpec:" + mResponsiveAllAppsCellSpec);
        }
    }

    /** Returns a reduced representation of this DeviceProfile. */
    public String toSmallString() {
        return "isTablet:" + mDeviceProperties.isLargeScreen() + ", "
                + "mDeviceProperties.isMultiDisplay():"
                + mDeviceProperties.getDeviceConfiguration().isMultiDisplay() + ", "
                + "widthPx:" + mDeviceProperties.getWidthPx() + ", "
                + "heightPx:" + mDeviceProperties.getHeightPx() + ", "
                + "insets:" + mDeviceProperties.getInsets() + ", "
                + "rotationHint:" + mDeviceProperties.getRotationHint();
    }

    private static Context getContext(
            LauncherDisplayInfo info, int orientation, WindowBounds bounds) {
        Configuration config = new Configuration(info.context.getResources().getConfiguration());
        config.orientation = orientation;
        config.densityDpi = info.getDensityDpi();
        config.smallestScreenWidthDp = (int) info.smallestSizeDp(bounds);
        return info.context.createConfigurationContext(config);
    }

    /**
     * Returns whether Taskbar and Hotseat should adjust horizontally on bubble bar location update.
     */
    public boolean shouldAdjustHotseatOnNavBarLocationUpdate(Context context) {
        return enableBubbleBar()
                && !DisplayController.getNavigationMode(context).hasGestures;
    }

    /** Returns hotseat translation X for the bubble bar position. */
    public int getHotseatTranslationXForNavBar(Context context, boolean isBubblesOnLeft) {
        if (shouldAdjustHotseatOnNavBarLocationUpdate(context)) {
            boolean isRtl = Utilities.isRtl(context.getResources());
            if (isBubblesOnLeft) {
                return isRtl ? -getHotseatProfile().getNavButtonsLayoutWidthPx() : 0;
            } else {
                return isRtl ? 0 : getHotseatProfile().getNavButtonsLayoutWidthPx();
            }
        } else {
            return 0;
        }
    }

    public TaskbarProfile getTaskbarProfile() {
        return mTaskbarProfile;
    }

    public DropTargetProfile getDropTargetProfile() {
        return mDropTargetProfile;
    }

    public BottomSheetProfile getBottomSheetProfile() {
        return mBottomSheetProfile;
    }

    public AllAppsProfile getAllAppsProfile() {
        return mAllAppsProfile;
    }

    public void setAllAppsProfile(AllAppsProfile allAppsProfile) {
        mAllAppsProfile = allAppsProfile;
    }

    public FolderProfile getFolderProfile() {
        return mFolderProfile;
    }

    public SysuiProfile getSysuiProfile() {
        return mSysuiProfile;
    }

    public void setFolderProfile(FolderProfile folderProfile) {
        mFolderProfile = folderProfile;
    }

    public void setHotseatProfile(HotseatProfile hotseatProfile) {
        this.mHotseatProfile = hotseatProfile;
    }

    public void setSysuiProfile(SysuiProfile sysuiProfile) {
        mSysuiProfile = sysuiProfile;
    }

    public void setTaskbarProfile(TaskbarProfile taskbarProfile) {
        mTaskbarProfile = taskbarProfile;
    }

    /**
     * Callback when a component changes the DeviceProfile associated with it, as a result of
     * configuration change
     */
    public interface OnDeviceProfileChangeListener {

        /**
         * Called when the device profile is reassigned. Note that for layout and measurements, it
         * is sufficient to listen for inset changes. Use this callback when you need to perform
         * a one time operation.
         */
        void onDeviceProfileChanged(DeviceProfile dp);
    }

    /**
     * Handler that deals with ItemInfo of the views for the DeviceProfile
     */
    @FunctionalInterface
    public interface ViewScaleProvider {
        /**
         * Get the scales from the view
         *
         * @param itemInfo The tag of the widget view
         * @return PointF instance containing the scale information, or null if using the default
         * app widget scale of this device profile.
         */
        @NonNull
        PointF getScaleFromItemInfo(@Nullable ItemInfo itemInfo);
    }

    public static class Builder {
        private final InvariantDeviceProfile mInv;
        private final LauncherDisplayInfo mInfo;
        private final WindowManagerProxy mWMProxy;

        private WindowBounds mWindowBounds;
        private boolean mIsMultiDisplay;

        private boolean mIsExternalDisplay = false;
        private Boolean mTransposeLayoutWithOrientation;
        private Boolean mIsGestureMode;

        private Boolean mIsWorkspaceItemsLabelHidden = false;

        private ViewScaleProvider mViewScaleProvider = null;

        private Consumer<DeviceProfile> mOverrideProvider;

        private DisplayOptionSpec mDisplayOptionSpec;

        public Builder(
                InvariantDeviceProfile inv, LauncherDisplayInfo info, WindowManagerProxy wmProxy) {
            mInv = inv;
            mInfo = info;
            mWMProxy = wmProxy;
        }

        public Builder setExternalDisplay(boolean isExternalDisplay) {
            mIsExternalDisplay = isExternalDisplay;
            return this;
        }

        public Builder setIsMultiDisplay(boolean isMultiDisplay) {
            mIsMultiDisplay = isMultiDisplay;
            return this;
        }

        public Builder setWindowBounds(WindowBounds bounds) {
            mWindowBounds = bounds;
            return this;
        }

        public Builder setTransposeLayoutWithOrientation(boolean transposeLayoutWithOrientation) {
            mTransposeLayoutWithOrientation = transposeLayoutWithOrientation;
            return this;
        }

        /**
         * Sets whether the DeviceProfile hides workspace app icon labels.
         */
        public Builder setIsWorkspaceItemsLabelHidden(boolean isWorkspaceItemsLabelHidden) {
            mIsWorkspaceItemsLabelHidden = isWorkspaceItemsLabelHidden;
            return this;
        }

        public Builder setGestureMode(boolean isGestureMode) {
            mIsGestureMode = isGestureMode;
            return this;
        }

        public Builder withDimensionsOverride(Consumer<DeviceProfile> overrideProvider) {
            mOverrideProvider = overrideProvider;
            return this;
        }

        /**
         * Set the viewScaleProvider for the builder
         *
         * @param viewScaleProvider The viewScaleProvider to be set for the
         *                          DeviceProfile
         * @return This builder
         */
        @NonNull
        public Builder setViewScaleProvider(@Nullable ViewScaleProvider viewScaleProvider) {
            mViewScaleProvider = viewScaleProvider;
            return this;
        }

        /**
         * Set the displayOptionSpec for the builder for secondary displays
         *
         * @return This Builder
         */
        public Builder setSecondaryDisplayOptionSpec() {
            mDisplayOptionSpec = createDisplayOptionSpec(mInfo, mWindowBounds.isLandscape());
            return this;
        }

        private Builder setDisplayOptionSpec(DisplayOptionSpec displayOptionSpec) {
            mDisplayOptionSpec = displayOptionSpec;
            return this;
        }

        public DeviceProfile build() {
            if (mWindowBounds == null) {
                throw new IllegalArgumentException("Window bounds not set");
            }
            if (mTransposeLayoutWithOrientation == null) {
                mTransposeLayoutWithOrientation =
                        !(mInfo.isLargeScreen(mWindowBounds) || mInv.isFixedLandscape);
            }
            if (mIsGestureMode == null) {
                mIsGestureMode = mInfo.getNavigationMode().hasGestures;
            }
            if (mViewScaleProvider == null) {
                mViewScaleProvider = DEFAULT_PROVIDER;
            }
            if (mOverrideProvider == null) {
                mOverrideProvider = DEFAULT_DIMENSION_PROVIDER;
            }
            if (mDisplayOptionSpec == null) {
                mDisplayOptionSpec = createDefaultDisplayOptionSpec(mInfo, mWindowBounds,
                        mIsMultiDisplay, mInv);
            }
            return new DeviceProfile(
                    mInv,
                    mInfo,
                    DeviceProperties.Factory.createDeviceProperties(
                            mInfo,
                            mWindowBounds,
                            new DeviceConfiguration(
                                    mIsExternalDisplay,
                                    mTransposeLayoutWithOrientation,
                                    mIsMultiDisplay,
                                    mIsGestureMode,
                                    mIsWorkspaceItemsLabelHidden
                            ),
                            mWMProxy.isTaskbarDrawnInProcess()
                    ),
                    mViewScaleProvider,
                    mOverrideProvider,
                    mDisplayOptionSpec
            );
        }

        @VisibleForTesting
        static DisplayOptionSpec createDefaultDisplayOptionSpec(LauncherDisplayInfo info,
                WindowBounds windowBounds, boolean isMultiDisplay, InvariantDeviceProfile inv) {
            boolean isTwoPanels = info.isLargeScreen(windowBounds) && isMultiDisplay;
            boolean isLandscape = windowBounds.isLandscape();
            return new DisplayOptionSpec(inv, isTwoPanels, isLandscape);
        }
    }

    public static class Getter {
        private final Supplier<DeviceProfile> mDeviceProfileSupplier;

        @Inject
        public Getter(Supplier<DeviceProfile> deviceProfileSupplier) {
            mDeviceProfileSupplier = deviceProfileSupplier;
        }

        public DeviceProfile get() {
            return mDeviceProfileSupplier.get();
        }
    }
}
