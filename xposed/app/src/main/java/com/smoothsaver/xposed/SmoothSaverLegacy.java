package com.smoothsaver.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

/**
 * SmoothSaver — Legacy Xposed API entry point.
 *
 * Hooks into system_server to prevent Battery Saver from forcing 60Hz.
 *
 * Hook Strategy:
 * 1. Primary: Hook BatterySaverObserver.onChange() to no-op it
 * 2. Secondary: Hook the vote-setting mechanism to skip low-power votes
 * 3. Fallback: Hook Settings.Global.getInt() for LOW_POWER_MODE key in display context
 */
public class SmoothSaverLegacy implements IXposedHookLoadPackage {

    private static final String TAG = "SmoothSaver";

    // DisplayModeDirector class paths (varies between Android versions)
    private static final String[] DISPLAY_MODE_DIRECTOR_PATHS = {
        // Android 14+ (moved to 'mode' subpackage)
        "com.android.server.display.mode.DisplayModeDirector",
        // Android 13 and earlier
        "com.android.server.display.DisplayModeDirector",
    };

    // Inner class names for the battery saver observer
    private static final String[] BATTERY_SAVER_OBSERVER_SUFFIXES = {
        "$BatterySaverObserver",
        "$BatterySaverController",
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // We only care about the system framework (system_server)
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        log("SmoothSaver loaded in system framework");
        log("Android SDK: " + android.os.Build.VERSION.SDK_INT);
        log("Device: " + android.os.Build.DEVICE);

        boolean hooked = false;

        // Attempt primary hook: BatterySaverObserver.onChange()
        hooked = attemptBatterySaverObserverHook(lpparam.classLoader);

        // Attempt secondary hook: Vote-based approach
        if (!hooked) {
            hooked = attemptVoteHook(lpparam.classLoader);
        }

        // Fallback: Hook Settings.Global.getInt for LOW_POWER_MODE
        if (!hooked) {
            hooked = attemptSettingsHook(lpparam.classLoader);
        }

        if (hooked) {
            log("Successfully hooked battery saver refresh rate restriction!");
        } else {
            log("WARNING: No hooks were successful. Battery saver may still force 60Hz.");
            log("Please report this issue with your Android version: " + android.os.Build.VERSION.RELEASE);
        }
    }

    /**
     * Primary approach: Find and hook BatterySaverObserver's onChange method
     * to prevent it from ever updating the refresh rate vote.
     */
    private boolean attemptBatterySaverObserverHook(ClassLoader classLoader) {
        for (String directorPath : DISPLAY_MODE_DIRECTOR_PATHS) {
            for (String suffix : BATTERY_SAVER_OBSERVER_SUFFIXES) {
                String className = directorPath + suffix;
                try {
                    Class<?> observerClass = XposedHelpers.findClass(className, classLoader);
                    log("Found battery saver class: " + className);

                    // Hook onChange(boolean, Uri) — this is the ContentObserver callback
                    // that fires when LOW_POWER_MODE setting changes
                    boolean onChangeHooked = false;
                    for (Method method : observerClass.getDeclaredMethods()) {
                        if ("onChange".equals(method.getName())) {
                            XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                                @Override
                                protected Object replaceHookedMethod(MethodHookParam param) {
                                    log("Blocked BatterySaverObserver.onChange() — keeping 90Hz");
                                    return null;
                                }
                            });
                            onChangeHooked = true;
                            log("Hooked: " + method.toGenericString());
                        }
                    }

                    if (onChangeHooked) {
                        // Also try to hook the observe/register method to prevent
                        // the observer from being registered in the first place
                        hookObserveMethod(observerClass);
                        return true;
                    }
                } catch (XposedHelpers.ClassNotFoundError e) {
                    // Expected — try next class path
                } catch (Throwable t) {
                    log("Error hooking " + className + ": " + t.getMessage());
                }
            }
        }
        return false;
    }

    /**
     * Hook the observe() method if it exists — this prevents the observer
     * from being registered to watch LOW_POWER_MODE changes at all.
     */
    private void hookObserveMethod(Class<?> observerClass) {
        try {
            for (Method method : observerClass.getDeclaredMethods()) {
                String name = method.getName();
                if ("observe".equals(name) || "register".equals(name) ||
                    "startObserving".equals(name)) {
                    XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            log("Blocked BatterySaverObserver registration — observer will never fire");
                            return null;
                        }
                    });
                    log("Hooked registration method: " + method.getName());
                }
            }
        } catch (Throwable t) {
            log("Could not hook observe method (non-critical): " + t.getMessage());
        }
    }

    /**
     * Secondary approach: Hook the DisplayModeDirector's vote update mechanism.
     * Look for methods that set votes with LOW_POWER priority and skip them.
     */
    private boolean attemptVoteHook(ClassLoader classLoader) {
        for (String directorPath : DISPLAY_MODE_DIRECTOR_PATHS) {
            try {
                Class<?> directorClass = XposedHelpers.findClass(directorPath, classLoader);
                log("Found DisplayModeDirector: " + directorPath);

                // Look for updateVoteLocked, updateVote, or setVote methods
                boolean voteHooked = false;
                for (Method method : directorClass.getDeclaredMethods()) {
                    String name = method.getName().toLowerCase();
                    if (name.contains("vote") && (name.contains("update") || name.contains("set"))) {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                // Check if this vote is from the battery saver
                                // by inspecting the priority parameter
                                if (isLowPowerVote(param)) {
                                    log("Blocked low-power vote in " + method.getName());
                                    param.setResult(null);
                                }
                            }
                        });
                        voteHooked = true;
                        log("Hooked vote method: " + method.toGenericString());
                    }
                }

                if (voteHooked) return true;
            } catch (XposedHelpers.ClassNotFoundError e) {
                // Try next path
            } catch (Throwable t) {
                log("Error in vote hook: " + t.getMessage());
            }
        }
        return false;
    }

    /**
     * Check if a vote method call is for the low-power mode priority.
     * The priority is typically an int enum — LOW_POWER_MODE is usually
     * one of the lower priority values (around 3-5 in the priority enum).
     */
    private boolean isLowPowerVote(XC_MethodHook.MethodHookParam param) {
        try {
            Object[] args = param.args;
            if (args == null) return false;

            // Look for a priority parameter that matches LOW_POWER_MODE
            // The exact value varies by Android version, but we can check
            // by looking at the calling context (stack trace)
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement frame : stack) {
                String className = frame.getClassName();
                if (className != null && (
                    className.contains("BatterySaver") ||
                    className.contains("LowPower") ||
                    className.contains("PowerSave"))) {
                    return true;
                }
            }
        } catch (Throwable t) {
            // If we can't determine, don't block
        }
        return false;
    }

    /**
     * Fallback approach: Hook Settings.Global.getInt() to return 0 for
     * LOW_POWER_MODE when called from the display management context.
     * This makes the display service think battery saver is always OFF
     * for refresh rate purposes.
     */
    private boolean attemptSettingsHook(ClassLoader classLoader) {
        try {
            Class<?> settingsGlobal = XposedHelpers.findClass(
                "android.provider.Settings$Global", classLoader);

            // Hook getInt(ContentResolver, String, int)
            XposedHelpers.findAndHookMethod(settingsGlobal, "getInt",
                android.content.ContentResolver.class, String.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[1];
                        if ("low_power".equals(key) || "low_power_mode".equals(key)) {
                            // Check if the caller is in the display management context
                            if (isCalledFromDisplayContext()) {
                                log("Intercepted Settings.Global.getInt('" + key + "') → returning 0");
                                param.setResult(0);
                            }
                        }
                    }
                });

            // Also hook getInt(ContentResolver, String) — this variant throws on missing
            XposedHelpers.findAndHookMethod(settingsGlobal, "getInt",
                android.content.ContentResolver.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[1];
                        if ("low_power".equals(key) || "low_power_mode".equals(key)) {
                            if (isCalledFromDisplayContext()) {
                                log("Intercepted Settings.Global.getInt('" + key + "') → returning 0");
                                param.setResult(0);
                            }
                        }
                    }
                });

            log("Hooked Settings.Global.getInt() for LOW_POWER_MODE (fallback)");
            return true;
        } catch (Throwable t) {
            log("Failed to hook Settings.Global: " + t.getMessage());
            return false;
        }
    }

    /**
     * Check the call stack to determine if the caller is in the display
     * management context (DisplayModeDirector, BatterySaverObserver, etc.)
     */
    private boolean isCalledFromDisplayContext() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className != null && (
                className.contains("DisplayModeDirector") ||
                className.contains("BatterySaverObserver") ||
                className.contains("DisplayManagerService"))) {
                return true;
            }
        }
        return false;
    }

    private static void log(String message) {
        XposedBridge.log("[" + TAG + "] " + message);
    }
}
