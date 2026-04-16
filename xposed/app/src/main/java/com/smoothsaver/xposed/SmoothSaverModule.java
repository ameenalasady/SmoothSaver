package com.smoothsaver.xposed;

import android.content.ContentResolver;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

import java.lang.reflect.Method;

/**
 * SmoothSaver — Modern Xposed API (libxposed) entry point.
 *
 * This module hooks into the system_server process and prevents
 * the BatterySaverObserver from restricting the display refresh rate
 * when Battery Saver mode is enabled.
 *
 * Uses the modern libxposed API with Hooker-chain pattern.
 */
public class SmoothSaverModule extends XposedModule {

    private static final String TAG = "SmoothSaver";

    // DisplayModeDirector class paths
    private static final String[] DIRECTOR_PATHS = {
        "com.android.server.display.mode.DisplayModeDirector",
        "com.android.server.display.DisplayModeDirector",
    };

    private static final String[] OBSERVER_SUFFIXES = {
        "$BatterySaverObserver",
        "$BatterySaverController",
    };

    public SmoothSaverModule(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        log(TAG + ": Module instance created for process: " + param.getProcessName());
    }

    @Override
    public void onSystemServerLoaded(SystemServerLoadedParam param) {
        log(TAG + ": onSystemServerLoaded — hooking battery saver refresh rate restriction");
        log(TAG + ": Android SDK " + android.os.Build.VERSION.SDK_INT +
            ", Device: " + android.os.Build.DEVICE);

        ClassLoader classLoader = param.getClassLoader();
        boolean hooked = false;

        // Primary: Hook BatterySaverObserver.onChange()
        hooked = hookBatterySaverObserver(classLoader);

        // Fallback: Hook Settings.Global.getInt for display context
        if (!hooked) {
            hooked = hookSettingsGlobal(classLoader);
        }

        if (hooked) {
            log(TAG + ": Successfully neutralized battery saver 60Hz restriction!");
        } else {
            log(TAG + ": WARNING — Could not hook any targets. " +
                "Battery saver may still force 60Hz. " +
                "Android version: " + android.os.Build.VERSION.RELEASE);
        }
    }

    /**
     * Primary hook: Find and neutralize BatterySaverObserver.onChange()
     */
    private boolean hookBatterySaverObserver(ClassLoader classLoader) {
        for (String directorPath : DIRECTOR_PATHS) {
            for (String suffix : OBSERVER_SUFFIXES) {
                String className = directorPath + suffix;
                try {
                    Class<?> observerClass = classLoader.loadClass(className);
                    log(TAG + ": Found " + className);

                    boolean success = false;
                    for (Method method : observerClass.getDeclaredMethods()) {
                        if ("onChange".equals(method.getName())) {
                            hook(method, OnChangeHooker.class);
                            success = true;
                            log(TAG + ": Hooked " + method.toGenericString());
                        }

                        // Also hook observe/register to prevent observer setup
                        String name = method.getName();
                        if ("observe".equals(name) || "register".equals(name) ||
                            "startObserving".equals(name)) {
                            hook(method, ObserveBlocker.class);
                            log(TAG + ": Blocked registration: " + name);
                        }
                    }

                    if (success) return true;
                } catch (ClassNotFoundException e) {
                    // Expected — try next
                } catch (Throwable t) {
                    log(TAG + ": Error hooking " + className + ": " + t.getMessage());
                }
            }
        }
        return false;
    }

    /**
     * Fallback: Hook Settings.Global.getInt to return 0 for low_power
     * when called from DisplayModeDirector context.
     */
    private boolean hookSettingsGlobal(ClassLoader classLoader) {
        try {
            Class<?> settingsGlobal = classLoader.loadClass("android.provider.Settings$Global");

            for (Method method : settingsGlobal.getDeclaredMethods()) {
                if ("getInt".equals(method.getName())) {
                    Class<?>[] params = method.getParameterTypes();
                    // Match getInt(ContentResolver, String, int) and getInt(ContentResolver, String)
                    if (params.length >= 2 &&
                        ContentResolver.class.isAssignableFrom(params[0]) &&
                        String.class.isAssignableFrom(params[1])) {
                        hook(method, SettingsGetIntHooker.class);
                        log(TAG + ": Hooked Settings.Global.getInt (fallback)");
                    }
                }
            }
            return true;
        } catch (Throwable t) {
            log(TAG + ": Failed to hook Settings.Global: " + t.getMessage());
            return false;
        }
    }

    // ---- Hooker classes (modern API interceptor pattern) ----

    /**
     * Hooker that blocks BatterySaverObserver.onChange() calls entirely.
     */
    @XposedHooker
    public static class OnChangeHooker implements Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            XposedInterface.log(TAG + ": Blocked BatterySaverObserver.onChange() — keeping 90Hz");
            callback.returnAndSkip(null);
        }
    }

    /**
     * Hooker that blocks BatterySaverObserver.observe() registration.
     */
    @XposedHooker
    public static class ObserveBlocker implements Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            XposedInterface.log(TAG + ": Blocked BatterySaverObserver registration");
            callback.returnAndSkip(null);
        }
    }

    /**
     * Hooker for Settings.Global.getInt() — returns 0 for low_power
     * when called from the display management context.
     */
    @XposedHooker
    public static class SettingsGetIntHooker implements Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            Object[] args = callback.getArgs();
            if (args != null && args.length >= 2 && args[1] instanceof String) {
                String key = (String) args[1];
                if ("low_power".equals(key) || "low_power_mode".equals(key)) {
                    // Check call stack for display context
                    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                    for (StackTraceElement frame : stack) {
                        String className = frame.getClassName();
                        if (className != null && (
                            className.contains("DisplayModeDirector") ||
                            className.contains("BatterySaver"))) {
                            XposedInterface.log(TAG +
                                ": Intercepted Settings.Global.getInt('" + key + "') → 0");
                            callback.returnAndSkip(0);
                            return;
                        }
                    }
                }
            }
        }
    }
}
