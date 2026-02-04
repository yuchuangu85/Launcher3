package com.android.launcher3;
// TODO(b/303773055): Remove the annotation after access issue is resolved.
import androidx.core.os.BuildCompat;

import com.android.quickstep.util.DeviceConfigHelper;

import java.nio.file.Files;
import java.nio.file.Paths;
/** @hide */
public final class FeatureFlagsImpl implements FeatureFlags {
    private static final boolean isReadFromNew = Files.exists(Paths.get("/metadata/aconfig/boot/enable_only_new_storage"));
    private static volatile boolean isCached = false;
    private static volatile boolean launcher_is_cached = false;
    private static volatile boolean launcher_search_is_cached = false;
    private static boolean enableAddAppWidgetViaConfigActivityV2 = true;
    private static boolean enableAdditionalHomeAnimations = true;
    private static boolean enableCategorizedWidgetSuggestions = true;
    private static boolean enableCursorHoverStates = true;
    private static boolean enableExpandingPauseWorkButton = true;
    private static boolean enableFallbackOverviewInWindow = false;
    private static boolean enableFocusOutline = true;
    private static boolean enableGeneratedPreviews = true;
    private static boolean enableGridOnlyOverview = false;
    private static boolean enableHandleDelayedGestureCallbacks = true;
    private static boolean enableHomeTransitionListener = true;
    private static boolean enableOverviewIconMenu = false;
    private static boolean enablePredictiveBackGesture = true;
    private static boolean enablePrivateSpace = true;
    private static boolean enablePrivateSpaceInstallShortcut = true;
    private static boolean enableRebootUnlockAnimation = false;
    private static boolean enableRecentsInTaskbar = false;
    private static boolean enableRefactorTaskThumbnail = false;
    private static boolean enableResponsiveWorkspace = true;
    private static boolean enableScalingRevealHomeAnimation = true;
    private static boolean enableShortcutDontSuggestApp = true;
    private static boolean enableSmartspaceAsAWidget = false;
    private static boolean enableSmartspaceRemovalToggle = false;
    private static boolean enableSupportForArchiving = false;
    private static boolean enableTabletTwoPanePickerV2 = false;
    private static boolean enableTaskbarCustomization = false;
    private static boolean enableTaskbarNoRecreate = false;
    private static boolean enableTaskbarPinning = true;
    private static boolean enableTwoPaneLauncherSettings = false;
    private static boolean enableTwolineAllapps = false;
    private static boolean enableTwolineToggle = true;
    private static boolean enableUnfoldStateAnimation = false;
    private static boolean enableUnfoldedTwoPanePicker = true;
    private static boolean enableWidgetTapToAdd = true;
    private static boolean enableWorkspaceInflation = true;
    private static boolean enabledFoldersInAllApps = false;
    private static boolean floatingSearchBar = false;
    private static boolean forceMonochromeAppIcons = false;
    private static boolean privateSpaceAddFloatingMaskView = false;
    private static boolean privateSpaceAnimation = true;
    private static boolean privateSpaceAppInstallerButton = true;
    private static boolean privateSpaceRestrictAccessibilityDrag = true;
    private static boolean privateSpaceRestrictItemDrag = true;
    private static boolean privateSpaceSysAppsSeparation = true;
    private static boolean useActivityOverlay = true;


    private void init() {
        isCached = true;
    }

    private void load_overrides_launcher() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            enableAddAppWidgetViaConfigActivityV2 =
                    properties.getBoolean(Flags.FLAG_ENABLE_ADD_APP_WIDGET_VIA_CONFIG_ACTIVITY_V2, true);
            enableAdditionalHomeAnimations =
                    properties.getBoolean(Flags.FLAG_ENABLE_ADDITIONAL_HOME_ANIMATIONS, true);
            enableCategorizedWidgetSuggestions =
                    properties.getBoolean(Flags.FLAG_ENABLE_CATEGORIZED_WIDGET_SUGGESTIONS, true);
            enableCursorHoverStates =
                    properties.getBoolean(Flags.FLAG_ENABLE_CURSOR_HOVER_STATES, true);
            enableExpandingPauseWorkButton =
                    properties.getBoolean(Flags.FLAG_ENABLE_EXPANDING_PAUSE_WORK_BUTTON, true);
            enableFallbackOverviewInWindow =
                    properties.getBoolean(Flags.FLAG_ENABLE_FALLBACK_OVERVIEW_IN_WINDOW, false);
            enableFocusOutline =
                    properties.getBoolean(Flags.FLAG_ENABLE_FOCUS_OUTLINE, true);
            enableGeneratedPreviews =
                    properties.getBoolean(Flags.FLAG_ENABLE_GENERATED_PREVIEWS, true);
            enableGridOnlyOverview =
                    properties.getBoolean(Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW, false);
            enableHandleDelayedGestureCallbacks =
                    properties.getBoolean(Flags.FLAG_ENABLE_HANDLE_DELAYED_GESTURE_CALLBACKS, true);
            enableHomeTransitionListener =
                    properties.getBoolean(Flags.FLAG_ENABLE_HOME_TRANSITION_LISTENER, true);
            enableOverviewIconMenu =
                    properties.getBoolean(Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU, false);
            enablePredictiveBackGesture =
                    properties.getBoolean(Flags.FLAG_ENABLE_PREDICTIVE_BACK_GESTURE, true);
            enablePrivateSpaceInstallShortcut =
                    properties.getBoolean(Flags.FLAG_ENABLE_PRIVATE_SPACE_INSTALL_SHORTCUT, true);
            enableRebootUnlockAnimation =
                    properties.getBoolean(Flags.FLAG_ENABLE_REBOOT_UNLOCK_ANIMATION, false);
            enableRecentsInTaskbar =
                    properties.getBoolean(Flags.FLAG_ENABLE_RECENTS_IN_TASKBAR, false);
            enableRefactorTaskThumbnail =
                    properties.getBoolean(Flags.FLAG_ENABLE_REFACTOR_TASK_THUMBNAIL, false);
            enableResponsiveWorkspace =
                    properties.getBoolean(Flags.FLAG_ENABLE_RESPONSIVE_WORKSPACE, true);
            enableScalingRevealHomeAnimation =
                    properties.getBoolean(Flags.FLAG_ENABLE_SCALING_REVEAL_HOME_ANIMATION, true);
            enableShortcutDontSuggestApp =
                    properties.getBoolean(Flags.FLAG_ENABLE_SHORTCUT_DONT_SUGGEST_APP, true);
            enableSmartspaceAsAWidget =
                    properties.getBoolean(Flags.FLAG_ENABLE_SMARTSPACE_AS_A_WIDGET, false);
            enableSmartspaceRemovalToggle =
                    properties.getBoolean(Flags.FLAG_ENABLE_SMARTSPACE_REMOVAL_TOGGLE, true);
            enableSupportForArchiving =
                    properties.getBoolean(Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING, BuildCompat.isAtLeastU());
            enableTabletTwoPanePickerV2 =
                    properties.getBoolean(Flags.FLAG_ENABLE_TABLET_TWO_PANE_PICKER_V2, false);
            enableTaskbarCustomization =
                    properties.getBoolean(Flags.FLAG_ENABLE_TASKBAR_CUSTOMIZATION, false);
            enableTaskbarNoRecreate =
                    properties.getBoolean(Flags.FLAG_ENABLE_TASKBAR_NO_RECREATE, false);
            enableTaskbarPinning =
                    properties.getBoolean(Flags.FLAG_ENABLE_TASKBAR_PINNING, true);
            enableTwoPaneLauncherSettings =
                    properties.getBoolean(Flags.FLAG_ENABLE_TWO_PANE_LAUNCHER_SETTINGS, true);
            enableTwolineAllapps =
                    properties.getBoolean(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS, false);
            enableTwolineToggle =
                    properties.getBoolean(Flags.FLAG_ENABLE_TWOLINE_TOGGLE, true);
            enableUnfoldStateAnimation =
                    properties.getBoolean(Flags.FLAG_ENABLE_UNFOLD_STATE_ANIMATION, false);
            enableUnfoldedTwoPanePicker =
                    properties.getBoolean(Flags.FLAG_ENABLE_UNFOLDED_TWO_PANE_PICKER, true);
            enableWidgetTapToAdd =
                    properties.getBoolean(Flags.FLAG_ENABLE_WIDGET_TAP_TO_ADD, true);
            enableWorkspaceInflation =
                    properties.getBoolean(Flags.FLAG_ENABLE_WORKSPACE_INFLATION, true);
            enabledFoldersInAllApps =
                    properties.getBoolean(Flags.FLAG_ENABLED_FOLDERS_IN_ALL_APPS, false);
            floatingSearchBar =
                    properties.getBoolean(Flags.FLAG_FLOATING_SEARCH_BAR, false);
            forceMonochromeAppIcons =
                    properties.getBoolean(Flags.FLAG_FORCE_MONOCHROME_APP_ICONS, true);
            useActivityOverlay =
                    properties.getBoolean(Flags.FLAG_USE_ACTIVITY_OVERLAY, true);
        } catch (NullPointerException e) {
            // Ignored
        }
        launcher_is_cached = true;
    }

    private void load_overrides_launcher_search() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            enablePrivateSpace =
                    properties.getBoolean(Flags.FLAG_ENABLE_PRIVATE_SPACE, BuildCompat.isAtLeastU());
            privateSpaceAddFloatingMaskView =
                    properties.getBoolean(Flags.FLAG_PRIVATE_SPACE_ADD_FLOATING_MASK_VIEW, BuildCompat.isAtLeastU());
            privateSpaceAnimation =
                    properties.getBoolean(Flags.FLAG_PRIVATE_SPACE_ANIMATION, BuildCompat.isAtLeastU());
            privateSpaceAppInstallerButton =
                    properties.getBoolean(Flags.FLAG_PRIVATE_SPACE_APP_INSTALLER_BUTTON, BuildCompat.isAtLeastU());
            privateSpaceRestrictAccessibilityDrag =
                    properties.getBoolean(Flags.FLAG_PRIVATE_SPACE_RESTRICT_ACCESSIBILITY_DRAG, BuildCompat.isAtLeastU());
            privateSpaceRestrictItemDrag =
                    properties.getBoolean(Flags.FLAG_PRIVATE_SPACE_RESTRICT_ITEM_DRAG, BuildCompat.isAtLeastU());
            privateSpaceSysAppsSeparation =
                    properties.getBoolean(Flags.FLAG_PRIVATE_SPACE_SYS_APPS_SEPARATION, BuildCompat.isAtLeastU());
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace launcher_search "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        launcher_search_is_cached = true;
    }

    @Override
    public boolean accessibilityScrollOnAllapps() {
        return false;
    }

    @Override
    public boolean allAppsBlur() {
        return false;
    }

    @Override
    public boolean allAppsSheetForHandheld() {
        return false;
    }

    @Override
    public boolean coordinateWorkspaceScale() {
        return false;
    }

    @Override
    public boolean enableActiveGestureProtoLog() {
        return false;
    }

    @Override
    public boolean enableAddAppWidgetViaConfigActivityV2() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableAddAppWidgetViaConfigActivityV2;

    }

    public boolean enableAdditionalHomeAnimations() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableAdditionalHomeAnimations;

    }

    @Override
    public boolean enableAllAppsButtonInHotseat() {
        return false;
    }

    @Override
    public boolean enableAltTabKqsFlatenning() {
        return false;
    }

    @Override
    public boolean enableAltTabKqsOnConnectedDisplays() {
        return false;
    }

    @Override
    public boolean enableCategorizedWidgetSuggestions() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableCategorizedWidgetSuggestions;

    }

    @Override
    public boolean enableContainerReturnAnimations() {
        return false;
    }

    @Override
    public boolean enableContrastTiles() {
        return false;
    }

    @Override
    public boolean enableCursorHoverStates() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableCursorHoverStates;

    }

    @Override
    public boolean enableDesktopExplodedView() {
        return false;
    }

    @Override
    public boolean enableDesktopTaskAlphaAnimation() {
        return false;
    }

    @Override
    public boolean enableDesktopWindowingCarouselDetach() {
        return false;
    }

    @Override
    public boolean enableDismissPredictionUndo() {
        return false;
    }

    @Override
    public boolean enableExpandingPauseWorkButton() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableExpandingPauseWorkButton;

    }

    @Override
    public boolean enableExpressiveDismissTaskMotion() {
        return false;
    }

    @Override
    public boolean enableFallbackOverviewInWindow() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableFallbackOverviewInWindow;

    }

    @Override
    public boolean enableFirstScreenBroadcastArchivingExtras() {
        return false;

    }

    @Override
    public boolean enableFocusOutline() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableFocusOutline;

    }

    @Override
    public boolean enableGeneratedPreviews() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableGeneratedPreviews;

    }

    @Override
    public boolean enableGestureNavHorizontalTouchSlop() {
        return false;
    }

    @Override
    public boolean enableGestureNavOnConnectedDisplays() {
        return false;
    }

    @Override
    public boolean enableGridMigrationFix() {
        return false;

    }

    @Override
    public boolean enableGridOnlyOverview() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableGridOnlyOverview;

    }

    @Override
    public boolean enableGrowthNudge() {
        return false;
    }

    @Override
    public boolean enableHandleDelayedGestureCallbacks() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableHandleDelayedGestureCallbacks;

    }

    @Override
    public boolean enableHomeTransitionListener() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableHomeTransitionListener;

    }

    @Override
    public boolean enableHoverOfChildElementsInTaskview() {
        return false;
    }

    @Override
    public boolean enableLargeDesktopWindowingTile() {
        return false;
    }

    @Override
    public boolean enableLauncherBrMetricsFixed() {
        return true;

    }

    @Override
    public boolean enableLauncherIconShapes() {
        return false;
    }

    @Override
    public boolean enableLauncherOverviewInWindow() {
        return false;
    }

    @Override
    public boolean enableLauncherVisualRefresh() {
        return false;
    }

    @Override
    public boolean enableMouseInteractionChanges() {
        return false;
    }

    @Override
    public boolean enableMultiInstanceMenuTaskbar() {
        return false;
    }

    @Override
    public boolean enableNarrowGridRestore() {
        return false;

    }

    @Override
    public boolean enableOverviewBackgroundWallpaperBlur() {
        return false;
    }

    @Override
    public boolean enableOverviewCommandHelperTimeout() {
        return false;
    }

    @Override
    public boolean enableOverviewDesktopTileWallpaperBackground() {
        return false;
    }

    @Override
    public boolean enableOverviewIconMenu() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableOverviewIconMenu;

    }

    @Override
    public boolean enableOverviewOnConnectedDisplays() {
        return false;
    }

    @Override
    public boolean enablePinningAppWithContextMenu() {
        return false;
    }

    @Override
    public boolean enablePredictiveBackGesture() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enablePredictiveBackGesture;

    }

    @Override
    public boolean enablePrivateSpace() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_search_is_cached) {
                load_overrides_launcher_search();
            }
        }
        return enablePrivateSpace;

    }

    @Override
    public boolean enablePrivateSpaceInstallShortcut() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enablePrivateSpaceInstallShortcut;

    }

    @Override
    public boolean enableRebootUnlockAnimation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableRebootUnlockAnimation;

    }

    @Override
    public boolean enableRecentsInTaskbar() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableRecentsInTaskbar;

    }

    @Override
    public boolean enableRecentsWindowProtoLog() {
        return false;
    }

    @Override
    public boolean enableRefactorTaskThumbnail() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableRefactorTaskThumbnail;

    }

    @Override
    public boolean enableResponsiveWorkspace() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableResponsiveWorkspace;

    }

    @Override
    public boolean enableScalabilityForDesktopExperience() {
        return false;
    }

    @Override
    public boolean enableScalingRevealHomeAnimation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableScalingRevealHomeAnimation;

    }

    @Override
    public boolean enableSeparateExternalDisplayTasks() {
        return false;
    }

    @Override
    public boolean enableShortcutDontSuggestApp() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableShortcutDontSuggestApp;

    }

    @Override
    public boolean enableShowEnabledShortcutsInAccessibilityMenu() {
        return false;
    }

    @Override
    public boolean enableSmartspaceAsAWidget() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableSmartspaceAsAWidget;

    }

    @Override
    public boolean enableSmartspaceRemovalToggle() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableSmartspaceRemovalToggle;

    }

    @Override
    public boolean enableStateManagerProtoLog() {
        return false;
    }

    @Override
    public boolean enableStrictMode() {
        return false;
    }

    @Override
    public boolean enableSupportForArchiving() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableSupportForArchiving;

    }

    @Override
    public boolean enableTabletTwoPanePickerV2() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableTabletTwoPanePickerV2;

    }

    @Override
    public boolean enableTaskbarBehindShade() {
        return false;
    }

    @Override
    public boolean enableTaskbarCustomization() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableTaskbarCustomization;

    }

    @Override
    public boolean enableTaskbarForDirectBoot() {
        return false;
    }

    @Override
    public boolean enableTaskbarNoRecreate() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableTaskbarNoRecreate;

    }

    @Override
    public boolean enableTaskbarPinning() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableTaskbarPinning;

    }

    @Override
    public boolean enableTieredWidgetsByDefaultInPicker() {
        return false;
    }

    @Override
    public boolean enableTwoPaneLauncherSettings() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableTwoPaneLauncherSettings;

    }

    @Override
    public boolean enableTwolineAllapps() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableTwolineAllapps;

    }

    @Override
    public boolean enableTwolineToggle() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableTwolineToggle;

    }

    @Override
    public boolean enableUnfoldStateAnimation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableUnfoldStateAnimation;

    }

    @Override
    public boolean enableUnfoldedTwoPanePicker() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableUnfoldedTwoPanePicker;

    }

    @Override
    public boolean enableUseTopVisibleActivityForExcludeFromRecentTask() {
        return false;
    }

    @Override
    public boolean enableWidgetTapToAdd() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableWidgetTapToAdd;

    }

    @Override
    public boolean enableWorkspaceInflation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enableWorkspaceInflation;

    }

    @Override
    public boolean enabledFoldersInAllApps() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return enabledFoldersInAllApps;

    }

    @Override
    public boolean expressiveThemeInTaskbarAndNavigation() {
        return false;
    }

    @Override
    public boolean extendibleThemeManager() {
        return false;
    }

    @Override
    public boolean floatingSearchBar() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return floatingSearchBar;

    }

    @Override
    public boolean forceMonochromeAppIcons() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return forceMonochromeAppIcons;

    }

    @Override
    public boolean gridMigrationRefactor() {
        return false;
    }

    @Override
    public boolean gsfRes() {
        return false;
    }

    @Override
    public boolean ignoreThreeFingerTrackpadForNavHandleLongPress() {
        return false;
    }

    @Override
    public boolean letterFastScroller() {
        return false;
    }

    @Override
    public boolean msdlFeedback() {
        return false;
    }

    @Override
    public boolean multilineSearchBar() {
        return false;
    }

    @Override
    public boolean navigateToChildPreference() {
        return false;
    }

    @Override
    public boolean oneGridMountedMode() {
        return false;
    }

    @Override
    public boolean oneGridRotationHandling() {
        return false;
    }

    @Override
    public boolean oneGridSpecs() {
        return false;
    }

    @Override
    public boolean predictiveBackToHomeBlur() {
        return false;
    }

    @Override
    public boolean predictiveBackToHomePolish() {
        return false;
    }

    @Override
    public boolean privateSpaceAddFloatingMaskView() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_search_is_cached) {
                load_overrides_launcher_search();
            }
        }
        return privateSpaceAddFloatingMaskView;

    }

    @Override
    public boolean privateSpaceAnimation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_search_is_cached) {
                load_overrides_launcher_search();
            }
        }
        return privateSpaceAnimation;

    }

    @Override
    public boolean privateSpaceAppInstallerButton() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_search_is_cached) {
                load_overrides_launcher_search();
            }
        }
        return privateSpaceAppInstallerButton;

    }

    @Override
    public boolean privateSpaceRestrictAccessibilityDrag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_search_is_cached) {
                load_overrides_launcher_search();
            }
        }
        return privateSpaceRestrictAccessibilityDrag;

    }

    @Override
    public boolean privateSpaceRestrictItemDrag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_search_is_cached) {
                load_overrides_launcher_search();
            }
        }
        return privateSpaceRestrictItemDrag;

    }

    @Override
    public boolean privateSpaceSysAppsSeparation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_search_is_cached) {
                load_overrides_launcher_search();
            }
        }
        return privateSpaceSysAppsSeparation;

    }

    @Override
    public boolean removeAppsRefreshOnRightClick() {
        return false;
    }

    @Override
    public boolean removeExcludeFromScreenMagnificationFlagUsage() {
        return false;
    }

    @Override
    public boolean restoreArchivedAppIconsFromDb() {
        return false;
    }

    @Override
    public boolean restoreArchivedShortcuts() {
        return false;
    }

    @Override
    public boolean showTaskbarPinningPopupFromAnywhere() {
        return false;
    }

    @Override
    public boolean syncAppLaunchWithTaskbarStash() {
        return false;
    }

    @Override
    public boolean taskbarOverflow() {
        return false;
    }

    @Override
    public boolean taskbarQuietModeChangeSupport() {
        return false;
    }

    @Override
    public boolean useActivityOverlay() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!launcher_is_cached) {
                load_overrides_launcher();
            }
        }
        return useActivityOverlay;

    }

    @Override
    public boolean useNewIconForArchivedApps() {
        return false;
    }

    @Override
    public boolean useSystemRadiusForAppWidgets() {
        return false;
    }

    @Override
    public boolean workSchedulerInWorkProfile() {
        return false;
    }

}

