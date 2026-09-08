package com.android.launcher3.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Compatibility boundary for security APIs introduced after this standalone Launcher's minSdk.
 *
 * <p>Platform Launcher3 is compiled against the current framework and directly calls
 * {@code android.security.Flags.appLockApis()}. That generated flags class is absent on older
 * Android versions, so direct linkage crashes an ordinary installed APK while loading workspace
 * items. App-lock actions are optional; treat an unavailable platform API as disabled.
 */
public final class SecurityFlagsCompat {

    private static final String SECURITY_FLAGS_CLASS = "android.security.Flags";
    private static final String APP_LOCK_APIS_METHOD = "appLockApis";

    private SecurityFlagsCompat() { }

    /** Returns whether the platform exposes and enables the optional App Lock APIs. */
    public static boolean areAppLockApisEnabled() {
        try {
            Class<?> flagsClass = Class.forName(SECURITY_FLAGS_CLASS);
            Method method = flagsClass.getMethod(APP_LOCK_APIS_METHOD);
            Object result = method.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InvocationTargetException | LinkageError e) {
            return false;
        }
    }
}
