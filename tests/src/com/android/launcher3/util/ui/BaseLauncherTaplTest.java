/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.util.ui;

import static android.os.Process.myUserHandle;
import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.ui.ActivityStartUtils.getAppPackageName;
import static com.android.launcher3.util.ui.ActivityStartUtils.resolveSystemApp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Debug;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.flag.junit.SetFlagsRule;
import android.platform.test.rule.ExtendedLongPressTimeoutRule;
import android.platform.test.rule.LimitDevicesRule;
import android.util.Log;

import androidx.lifecycle.LifecycleRegistry;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.launcher3.tapl.Background;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.tapl.HomeAppIcon;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.util.BaseContext;
import com.android.launcher3.util.LifecycleRegistryWrapper;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.launcher3.util.rule.SamplerRule;
import com.android.launcher3.util.rule.ScreenRecordRule;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.util.rule.SkipAfterTimeOutRule;
import com.android.launcher3.util.rule.TestIsolationRule;
import com.android.launcher3.util.rule.TestStabilityRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import shark.AndroidMetadataExtractor;
import shark.AndroidObjectInspectors;
import shark.AndroidReferenceMatchers;
import shark.ApplicationLeak;
import shark.FilteringLeakingObjectFinder;
import shark.HeapAnalysis;
import shark.HeapAnalysisSuccess;
import shark.HeapAnalyzer;
import shark.HeapField;
import shark.HeapObject.HeapInstance;
import shark.LeakTrace;
import shark.LeakTraceReference;
import shark.OnAnalysisProgressListener;

/**
 * Base class for all TAPL tests in Launcher providing various utility methods.
 */
public abstract class BaseLauncherTaplTest {

    public static final long DEFAULT_ACTIVITY_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    public static final long DEFAULT_BROADCAST_TIMEOUT_SECS = 10;

    public static final long DEFAULT_UI_TIMEOUT = TestUtil.DEFAULT_UI_TIMEOUT;
    private static final String TAG = "BaseLauncherTaplTest";

    private static final long BYTES_PER_MEGABYTE = 1 << 20;

    private static boolean sDumpWasGenerated = false;
    private static boolean sUiSurfaceLeakReported = false;
    private static boolean sSeenKeyguard = false;
    private static boolean sFirstTimeWaitingForWizard = true;

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String BASE_CONTEXT_CLASS = BaseContext.class.getName();
    private static final String LIFECYCLE_WRAPPER_CLASS =
            LifecycleRegistryWrapper.class.getName();
    private static final String LIFECYCLE_REGISTRY_CLASS = LifecycleRegistry.class.getName();
    private static final String ENUM_CLASS = Enum.class.getName();

    protected final UiDevice mDevice = getUiDevice();
    protected final LauncherInstrumentation mDefaultDisplayLauncher =
            createLauncherInstrumentation();

    /**
     * this is used by default for TAPL actions in tests and may be overridden if test display
     * changes. see {@link com.android.quickstep.AbstractQuickStepTest#onTestDisplayChanged(int)}.
     * To guarantee a TAPL action is always performed on default display launcher use
     * {@link #mDefaultDisplayLauncher} instead.
     */
    protected LauncherInstrumentation mLauncher = mDefaultDisplayLauncher;

    @NonNull
    public static LauncherInstrumentation createLauncherInstrumentation() {
        waitForSetupWizardDismissal(); // precondition for creating LauncherInstrumentation
        return new LauncherInstrumentation(true);
    }

    protected Context mTargetContext;
    protected String mTargetPackage;
    private int mLauncherPid;

    private final ActivityManager.MemoryInfo mMemoryInfo = new ActivityManager.MemoryInfo();
    private final ActivityManager mActivityManager;
    private long mMemoryBefore;

    protected int mDisplayId = DEFAULT_DISPLAY;

    /** Detects UI surface leaks and throws an exception if a leak is found. */
    public static void checkDetectedLeaks(LauncherInstrumentation launcher) {
        if (TestStabilityRule.isPresubmit()) return; // b/313501215

        if (sUiSurfaceLeakReported) return;

        // Check whether activity leak detector has found leaked activities.
        launcher.waitForCondition(() -> getUiSurfaceLeakErrorMessage(launcher),
                DEFAULT_UI_TIMEOUT, () -> {
                    launcher.forceGc();
                    return MAIN_EXECUTOR.submit(
                            () -> launcher.noLeakedUiSurfaces()).get();
                });
    }

    private static String getUiSurfaceLeakErrorMessage(LauncherInstrumentation launcher) {
        sUiSurfaceLeakReported = true;
        return "Leak detector has found leaked UI surfaces; "
                + dumpHprofData(launcher) + ".";
    }

    private static String dumpHprofData(LauncherInstrumentation launcher) {
        String result;
        if (sDumpWasGenerated) {
            result = "dump has already been generated by another test";
        } else {
            try {
                final String fileName =
                        getInstrumentation().getTargetContext().getFilesDir().getPath()
                                + "/UiSurfaceLeakHeapDump.hprof";
                final UiDevice device = getUiDevice();
                if (TestHelpers.isInLauncherProcess()) {
                    Debug.dumpHprofData(fileName);
                } else {
                    device.executeShellCommand(
                            "am dumpheap " + device.getLauncherPackageName() + " " + fileName);
                }
                Log.d(TAG, "Saved leak dump, the leak is still present: "
                        + !launcher.noLeakedUiSurfaces());
                sDumpWasGenerated = true;

                File hprofFile = new File(fileName);
                // Make the hprof file readable for the heap analyzer.
                device.executeShellCommand("chmod 644 " + fileName);

                String referenceChain = null;
                try {
                    referenceChain = createLeakReportFromHeap(hprofFile);
                } catch (Throwable e) {
                    Log.e(TAG, "Heap analysis failed", e);
                }

                if (referenceChain != null) {
                    // Omit the full list of UI surfaces when a specific leak path is found to keep
                    // the assertion message focused and concise.
                    return "Saved memory dump and leak analysis as artifacts. "
                            + "Path from GC root to the leaking object: " + referenceChain;
                }

                result = "saved memory dump as an artifact";
            } catch (Throwable e) {
                Log.e(TAG, "dumpHprofData failed", e);
                result = "failed to save memory dump";
            }
        }
        return result + ". Full list of UI surfaces: " + launcher.getRootedUiSurfacesList();
    }

    private static String createLeakReportFromHeap(File hprofFile) {
        HeapAnalyzer heapAnalyzer = new HeapAnalyzer(
                OnAnalysisProgressListener.Companion.getNO_OP());
        List<FilteringLeakingObjectFinder.LeakingObjectFilter> filters = new ArrayList<>(
                AndroidObjectInspectors.Companion.getAppLeakingObjectFilters());

        // This additional filter is needed to detect leaks of BaseContext objects which is not
        // covered by standard Activity/Fragment leak detectors.
        // Check if the BaseContext is destroyed by traversing the object graph:
        // BaseContext -> lifecycleRegistryWrapper -> base (LifecycleRegistry) -> state -> name.
        filters.add(heapObject -> {
            if (heapObject instanceof HeapInstance) {
                HeapInstance instance = (HeapInstance) heapObject;
                if (instance.instanceOf(BASE_CONTEXT_CLASS)) {
                    HeapInstance wrapper = getRef(instance, BASE_CONTEXT_CLASS,
                            "lifecycleRegistryWrapper");
                    if (wrapper == null) return false;
                    HeapInstance registry = getRef(wrapper, LIFECYCLE_WRAPPER_CLASS, "base");
                    if (registry == null) return false;
                    HeapInstance state = getRef(registry, LIFECYCLE_REGISTRY_CLASS, "state");
                    if (state != null) {
                        HeapInstance name = getRef(state, ENUM_CLASS, "name");
                        if (name != null && "DESTROYED".equals(name.readAsJavaString())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });

        HeapAnalysis analysis = heapAnalyzer.analyze(
                hprofFile,
                new FilteringLeakingObjectFinder(filters),
                AndroidReferenceMatchers.Companion.getAppDefaults(),
                true,
                AndroidObjectInspectors.Companion.getAppDefaults(),
                AndroidMetadataExtractor.INSTANCE,
                null
        );

        String analysisResult = analysis.toString();

        try {
            File analysisFile = new File(hprofFile.getParent(), "UiSurfaceLeakAnalysis.txt");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(analysisFile)) {
                fos.write(analysisResult.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Saved heap analysis to file: " + analysisFile);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write analysis to file", e);
        }

        return getConciseLeakPath(analysis);
    }

    private static String getConciseLeakPath(HeapAnalysis analysis) {

        if (!(analysis instanceof HeapAnalysisSuccess)) {
            return null;
        }

        HeapAnalysisSuccess success = (HeapAnalysisSuccess) analysis;
        if (success.getApplicationLeaks().isEmpty()) {
            return null;
        }

        // We only extract the first leak to keep the assertion error message readable
        ApplicationLeak firstLeak = success.getApplicationLeaks().get(0);
        if (firstLeak.getLeakTraces().isEmpty()) {
            return null;
        }

        LeakTrace trace = firstLeak.getLeakTraces().get(0);
        List<String> pathElements = new ArrayList<>();

        for (LeakTraceReference ref : trace.getReferencePath()) {
            String className = ref.getOriginObject().getClassName();
            if (className.startsWith("java.lang.") || className.startsWith("java.util.")) {
                continue;
            }
            pathElements.add(ref.getOwningClassSimpleName()
                    + "." + ref.getReferenceDisplayName());
        }

        pathElements.add(trace.getLeakingObject().getClassSimpleName());

        return String.join(" -> ", pathElements);
    }

    private static HeapInstance getRef(HeapInstance instance, String className, String fieldName) {
        HeapField field = instance.get(className, fieldName);
        if (field != null && field.getValue().isNonNullReference()) {
            return field.getValue().getAsObject().getAsInstance();
        }
        return null;
    }

    protected BaseLauncherTaplTest() {
        mActivityManager = InstrumentationRegistry.getContext()
                .getSystemService(ActivityManager.class);
        mLauncher.enableCheckEventsForSuccessfulGestures();
        mLauncher.setAnomalyChecker(BaseLauncherTaplTest::verifyKeyguardInvisible);
        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        mLauncher.enableDebugTracing();
        // Avoid double-reporting of Launcher crashes.
        mLauncher.setOnLauncherCrashed(() -> mLauncherPid = 0);
    }

    @Rule
    public ShellCommandRule mDisableHeadsUpNotification =
            ShellCommandRule.disableHeadsUpNotification();

    @Rule
    public ScreenRecordRule mScreenRecordRule = new ScreenRecordRule();

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);

    @Rule
    public ExtendedLongPressTimeoutRule mLongPressTimeoutRule = new ExtendedLongPressTimeoutRule();

    @Rule
    public LimitDevicesRule mlimitDevicesRule = new LimitDevicesRule();

    @Rule(order = -1000) // This should be the outermost rule
    public SkipAfterTimeOutRule mSkipAfterTimeOutRule = new SkipAfterTimeOutRule();

    // TODO(b/377678992): revert ag/37092345 once NexusLauncherTests-OverviewInWindowEnabled is
    //  successfully blocking presubmit.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface AllowInRecentsWindowTests {}

    protected void performInitialization() {
        reinitializeLauncherData();
        // Replace mDevice.pressHome() with TAPL's more robust version
        mLauncher.goHome();

        // Wait for all apps view to be gone.
        mDevice.wait(Until.gone(By.res(mTargetPackage, "apps_view")), DEFAULT_UI_TIMEOUT);

        // Check that we switched to home.
        mLauncher.getWorkspace();
        checkDetectedLeaks(mLauncher);
    }

    protected void clearPackageData(String pkg) throws IOException, InterruptedException {
        assertTrue("pm clear command failed",
                mDevice.executeShellCommand(
                        String.format("pm clear --user %d %s", myUserHandle().getIdentifier(), pkg))
                        .contains("Success"));
        assertTrue("pm wait-for-handler command failed",
                mDevice.executeShellCommand("pm wait-for-handler")
                        .contains("Success"));
    }

    protected TestRule getRulesInsideActivityMonitor() {
        final RuleChain inner = RuleChain
                .outerRule(new FailureWatcher(mLauncher))
                .around(new TestIsolationRule(mLauncher, true));
        return TestHelpers.isInLauncherProcess()
                ? RuleChain.outerRule(ShellCommandRule.setDefaultLauncher()).around(inner)
                : inner;
    }

    @Rule
    public TestRule mOrderSensitiveRules = RuleChain
            .outerRule(new SamplerRule())
            .around(new TestStabilityRule())
            .around(getRulesInsideActivityMonitor());

    public UiDevice getDevice() {
        return mDevice;
    }

    @Before
    public void setUp() throws Exception {
        mLauncher.onTestStart();

        final String launcherPackageName = mDevice.getLauncherPackageName();
        try {
            final Context context = InstrumentationRegistry.getContext();
            final PackageManager pm = context.getPackageManager();
            final PackageInfo launcherPackage = pm.getPackageInfo(launcherPackageName, 0);

            if (!launcherPackage.versionName.equals("BuildFromAndroidStudio")) {
                Assert.assertEquals("Launcher version doesn't match tests version",
                        pm.getPackageInfo(context.getPackageName(), 0).getLongVersionCode(),
                        launcherPackage.getLongVersionCode());
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        mLauncherPid = 0;

        mTargetContext = InstrumentationRegistry.getTargetContext();
        mTargetPackage = mTargetContext.getPackageName();
        mLauncherPid = mLauncher.getPid();

        UserManager userManager = mTargetContext.getSystemService(UserManager.class);
        if (userManager != null) {
            for (UserHandle userHandle : userManager.getUserProfiles()) {
                if (!userHandle.isSystem()) {
                    mDevice.executeShellCommand(
                            "pm remove-user --wait " + userHandle.getIdentifier());
                }
            }
        }

        onTestStart();
        performInitialization();
    }

    private long getAvailableMemory() {
        mActivityManager.getMemoryInfo(mMemoryInfo);

        return Math.divideExact(mMemoryInfo.availMem,  BYTES_PER_MEGABYTE);
    }

    @Before
    public void saveMemoryBefore() {
        mMemoryBefore = getAvailableMemory();
    }

    @After
    public void logMemoryAfter() {
        long memoryAfter = getAvailableMemory();

        Log.d(TAG, "Available memory: before=" + mMemoryBefore
                + "MB, after=" + memoryAfter
                + "MB, delta=" + (memoryAfter - mMemoryBefore) + "MB");
    }

    @Before
    public void checkTestInAllowlist() {
        Annotation annotation = getClass().getDeclaredAnnotation(AllowInRecentsWindowTests.class);

        assumeTrue("Skipping unannotated test because a recents window flag is enabled",
                !mLauncher.isRecentsWindowEnabled() || annotation != null);
    }

    /** Method that should be called when a test starts. */
    public static void onTestStart() {
        waitForSetupWizardDismissal();

        if (TestStabilityRule.isPresubmit()) {
            aggressivelyUnlockSysUi();
        } else {
            verifyKeyguardInvisible();
        }
    }

    private static boolean hasSystemUiObject(String resId) {
        return getUiDevice().hasObject(
                By.res(SYSTEMUI_PACKAGE, resId));
    }

    @NonNull
    private static UiDevice getUiDevice() {
        return UiDevice.getInstance(getInstrumentation());
    }

    /**
     * Get the base container for the display associated with this test after going to Home if
     * possible. Prefer this method over mLauncher.goHome to support tests on external displays
     *
     * @return a Workspace object for default display or a LaunchedAppState for non-default
     */
    protected Background getBaseContainer() {
        if (mDisplayId == DEFAULT_DISPLAY) {
            return mLauncher.goHome();
        } else {
            return mLauncher.getLaunchedAppState();
        }
    }

    private static void aggressivelyUnlockSysUi() {
        final UiDevice device = getUiDevice();
        for (int i = 0; i < 10 && hasSystemUiObject("keyguard_status_view"); ++i) {
            Log.d(TAG, "Before attempting to unlock the phone");
            try {
                device.executeShellCommand("input keyevent 82");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            device.waitForIdle();
        }
        Assert.assertTrue("Keyguard still visible",
                TestHelpers.wait(
                        Until.gone(By.res(SYSTEMUI_PACKAGE, "keyguard_status_view")), 60000));
        Log.d(TAG, "Keyguard is not visible");
    }

    /** Waits for setup wizard to go away. */
    private static void waitForSetupWizardDismissal() {
        if (sFirstTimeWaitingForWizard) {
            try {
                getUiDevice().executeShellCommand(
                        "am force-stop com.google.android.setupwizard");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        final boolean wizardDismissed = TestHelpers.wait(
                Until.gone(By.pkg("com.google.android.setupwizard").depth(0)),
                sFirstTimeWaitingForWizard ? 120000 : 0);
        sFirstTimeWaitingForWizard = false;
        Assert.assertTrue("Setup wizard is still visible", wizardDismissed);
    }

    /** Asserts that keyguard is not visible */
    public static void verifyKeyguardInvisible() {
        final boolean keyguardAlreadyVisible = sSeenKeyguard;

        sSeenKeyguard = sSeenKeyguard
                || !TestHelpers.wait(
                Until.gone(By.res(SYSTEMUI_PACKAGE, "keyguard_status_view")), 60000);

        Assert.assertFalse(
                "Keyguard is visible, which is likely caused by a crash in SysUI, seeing keyguard"
                        + " for the first time = "
                        + !keyguardAlreadyVisible,
                sSeenKeyguard);
    }

    @After
    public void resetFreezeRecentTaskList() {
        try {
            mDevice.executeShellCommand("wm reset-freeze-recent-tasks");
        } catch (IOException e) {
            Log.e(TAG, "Failed to reset fozen recent tasks list", e);
        }
    }

    @After
    public void verifyLauncherState() {
        try {
            // Limits UI tests affecting tests running after them.
            mDevice.pressHome();
            mLauncher.waitForLauncherInitialized();
            if (mLauncherPid != 0) {
                assertEquals("Launcher crashed, pid mismatch:",
                        mLauncherPid, mLauncher.getPid().intValue());
            }
        } finally {
            mLauncher.onTestFinish();
        }
    }

    protected void reinitializeLauncherData() {
        reinitializeLauncherData(false);
    }

    protected void reinitializeLauncherData(boolean clearWorkspace) {
        if (clearWorkspace) {
            mLauncher.clearLauncherData();
        } else {
            mLauncher.reinitializeLauncherData();
        }
        mLauncher.waitForLauncherInitialized();
    }

    protected HomeAppIcon createShortcutInCenterIfNotExist(String name) {
        Point dimension = mLauncher.getWorkspace().getIconGridDimensions();
        return createShortcutIfNotExist(name, dimension.x / 2, dimension.y / 2);
    }

    protected HomeAppIcon createShortcutIfNotExist(String name, Point cellPosition) {
        return createShortcutIfNotExist(name, cellPosition.x, cellPosition.y);
    }

    protected HomeAppIcon createShortcutIfNotExist(String name, int cellX, int cellY) {
        HomeAppIcon homeAppIcon = mLauncher.getWorkspace().tryGetWorkspaceAppIcon(name);
        if (homeAppIcon == null) {
            HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
            allApps.freeze();
            try {
                allApps.getAppIcon(name).dragToWorkspace(cellX, cellY);
            } finally {
                allApps.unfreeze();
            }
            homeAppIcon = mLauncher.getWorkspace().getWorkspaceAppIcon(name);
        }
        return homeAppIcon;
    }

    /** Clears all recent tasks */
    public void clearAllRecentTasks() {
        if (mDisplayId == DEFAULT_DISPLAY) {
            mLauncher.goHome();
        }
        try {
            getUiDevice().executeShellCommand(
                    "dumpsys activity service SystemUIService WMShell desktopmode removeAllDesks");
            getUiDevice().executeShellCommand(
                    "dumpsys activity service SystemUIService WMShell recents clearAll");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void startAppFast(String pkg) {
        ActivityStartUtils.startAppFast(pkg, mDisplayId);
    }

    protected void startTestActivity(int activityNumber) {
        ActivityStartUtils.startTestActivity(activityNumber, mDisplayId);
    }

    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    protected void startTestApps() {
        startAppFast(getAppPackageName());
        startAppFast(CALCULATOR_APP_PACKAGE);
        startTestActivity(2);
    }
}
