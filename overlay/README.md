# RRO Overlay — SmoothSaver Refresh Rate Override

This directory contains the source files for a Runtime Resource Overlay (RRO) APK
that overrides Android's framework refresh rate configuration.

## What It Does

Overrides the following `frameworks/base` resource values:
- `config_defaultPeakRefreshRate` → 90 (instead of 60 during power saving)
- `config_defaultRefreshRate` → 90

## Building

You need `aapt2` (from Android SDK Build Tools) to compile this overlay:

```bash
# Compile resources
aapt2 compile res/values/integers.xml -o compiled/

# Link into APK
aapt2 link compiled/values_integers.xml.flat \
    --manifest AndroidManifest.xml \
    -o SmoothSaverOverlay.apk \
    -I $ANDROID_HOME/platforms/android-35/android.jar

# Sign (use any debug keystore)
apksigner sign --ks debug.keystore SmoothSaverOverlay.apk
```

## Installation (Manual)

Place the compiled APK at one of these paths on the device:
- `/system/product/overlay/SmoothSaverOverlay.apk`
- `/vendor/overlay/SmoothSaverOverlay.apk`

Or use the KSU module which handles placement automatically.

## Note

This overlay alone may not be sufficient — the `DisplayModeDirector` may read
the refresh rate config at boot and cache it, or it may use a different code path
entirely. The LSPosed Xposed module provides a more robust solution by hooking
the voting logic directly.
