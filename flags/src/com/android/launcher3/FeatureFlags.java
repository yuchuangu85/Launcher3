package com.android.launcher3;
/** @hide */
public interface FeatureFlags {


    boolean enableAddAppWidgetViaConfigActivityV2();

    boolean enableAdditionalHomeAnimations();

    boolean enableCategorizedWidgetSuggestions();

    boolean enableCursorHoverStates();

    boolean enableExpandingPauseWorkButton();

    boolean enableFallbackOverviewInWindow();

    boolean enableFirstScreenBroadcastArchivingExtras();

    boolean enableFocusOutline();

    boolean enableGeneratedPreviews();

    boolean enableGridMigrationFix();

    boolean enableGridOnlyOverview();

    boolean enableHandleDelayedGestureCallbacks();

    boolean enableHomeTransitionListener();

    boolean enableLauncherBrMetricsFixed();

    boolean enableNarrowGridRestore();

    boolean enableOverviewIconMenu();

    boolean enablePredictiveBackGesture();

    boolean enablePrivateSpace();

    boolean enablePrivateSpaceInstallShortcut();

    boolean enableRebootUnlockAnimation();

    boolean enableRecentsInTaskbar();

    boolean enableRefactorTaskThumbnail();

    boolean enableResponsiveWorkspace();

    boolean enableScalingRevealHomeAnimation();

    boolean enableShortcutDontSuggestApp();

    boolean enableSmartspaceAsAWidget();

    boolean enableSmartspaceRemovalToggle();

    boolean enableSupportForArchiving();

    boolean enableTabletTwoPanePickerV2();

    boolean enableTaskbarCustomization();

    boolean enableTaskbarNoRecreate();

    boolean enableTaskbarPinning();

    boolean enableTwoPaneLauncherSettings();

    boolean enableTwolineAllapps();

    boolean enableTwolineToggle();

    boolean enableUnfoldStateAnimation();

    boolean enableUnfoldedTwoPanePicker();

    boolean enableWidgetTapToAdd();

    boolean enableWorkspaceInflation();

    boolean enabledFoldersInAllApps();

    boolean floatingSearchBar();

    boolean forceMonochromeAppIcons();

    boolean privateSpaceAddFloatingMaskView();

    boolean privateSpaceAnimation();

    boolean privateSpaceAppInstallerButton();

    boolean privateSpaceRestrictAccessibilityDrag();

    boolean privateSpaceRestrictItemDrag();

    boolean privateSpaceSysAppsSeparation();

    boolean useActivityOverlay();

    boolean accessibilityScrollOnAllapps();

    boolean allAppsBlur();

    boolean allAppsSheetForHandheld();

    boolean coordinateWorkspaceScale();

    boolean enableActiveGestureProtoLog();

    boolean enableAllAppsButtonInHotseat();

    boolean enableAltTabKqsFlatenning();

    boolean enableAltTabKqsOnConnectedDisplays();

    boolean enableContainerReturnAnimations();

    boolean enableContrastTiles();

    boolean enableDesktopExplodedView();

    boolean enableDesktopTaskAlphaAnimation();

    boolean enableDesktopWindowingCarouselDetach();

    boolean enableDismissPredictionUndo();

    boolean enableExpressiveDismissTaskMotion();

    boolean enableGestureNavHorizontalTouchSlop();

    boolean enableGestureNavOnConnectedDisplays();

    boolean enableGrowthNudge();

    boolean enableHoverOfChildElementsInTaskview();

    boolean enableLargeDesktopWindowingTile();

    boolean enableLauncherIconShapes();

    boolean enableLauncherOverviewInWindow();

    boolean enableLauncherVisualRefresh();

    boolean enableMouseInteractionChanges();

    boolean enableMultiInstanceMenuTaskbar();

    boolean enableOverviewBackgroundWallpaperBlur();

    boolean enableOverviewCommandHelperTimeout();

    boolean enableOverviewDesktopTileWallpaperBackground();

    boolean enableOverviewOnConnectedDisplays();

    boolean enablePinningAppWithContextMenu();

    boolean enableRecentsWindowProtoLog();

    boolean enableScalabilityForDesktopExperience();

    boolean enableSeparateExternalDisplayTasks();

    boolean enableShowEnabledShortcutsInAccessibilityMenu();

    boolean enableStateManagerProtoLog();

    boolean enableStrictMode();

    boolean enableTaskbarBehindShade();

    boolean enableTaskbarForDirectBoot();

    boolean enableTieredWidgetsByDefaultInPicker();

    boolean enableUseTopVisibleActivityForExcludeFromRecentTask();

    boolean expressiveThemeInTaskbarAndNavigation();

    boolean extendibleThemeManager();

    boolean gridMigrationRefactor();

    boolean gsfRes();

    boolean ignoreThreeFingerTrackpadForNavHandleLongPress();

    boolean letterFastScroller();

    boolean msdlFeedback();

    boolean multilineSearchBar();

    boolean navigateToChildPreference();

    boolean oneGridMountedMode();

    boolean oneGridRotationHandling();

    boolean oneGridSpecs();

    boolean predictiveBackToHomeBlur();

    boolean predictiveBackToHomePolish();

    boolean removeAppsRefreshOnRightClick();

    boolean removeExcludeFromScreenMagnificationFlagUsage();

    boolean restoreArchivedAppIconsFromDb();

    boolean restoreArchivedShortcuts();

    boolean showTaskbarPinningPopupFromAnywhere();

    boolean syncAppLaunchWithTaskbarStash();

    boolean taskbarOverflow();

    boolean taskbarQuietModeChangeSupport();

    boolean useNewIconForArchivedApps();

    boolean useSystemRadiusForAppWidgets();

    boolean workSchedulerInWorkProfile();
}
