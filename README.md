# SmoothSaver — Pixel 7 90Hz in Battery Saver

**Prevent Android's Battery Saver from forcing your Pixel 7 display to 60Hz.**

When Battery Saver mode is enabled, Android forcibly caps your display refresh rate to 60Hz via `DisplayModeDirector.BatterySaverObserver`. This module neutralizes that restriction, letting you keep 90Hz smooth scrolling even while saving battery.

## How It Works

This project provides **two complementary approaches**:

### 1. KernelSU Module (Systemless)
- **Boot script** that continuously monitors and re-applies 90Hz peak refresh rate
- **Service script** that watches for battery saver state changes and overrides the 60Hz cap
- Works with KernelSU and Magisk
- No LSPosed required

### 2. LSPosed/Xposed Module (Recommended)
- Hooks into `system_server` to intercept the `DisplayModeDirector.BatterySaverObserver`
- Neutralizes the low-power refresh rate vote at the framework level
- Most reliable approach — prevents the restriction before it's ever applied
- Requires LSPosed (Zygisk)

## Requirements

- **Device**: Google Pixel 7 (panther) or Pixel 7 Pro (cheetah)
- **Android**: 14, 15, or 16
- **Root**: KernelSU or Magisk
- **Optional**: LSPosed framework (for the Xposed module)

## Installation

### KernelSU Module
1. Download the latest `SmoothSaver-KSU-vX.X.zip` from [Releases](../../releases)
2. Open KernelSU Manager → Modules → Install from storage
3. Select the zip file
4. Reboot

### LSPosed Module
1. Download `SmoothSaver-Xposed-vX.X.apk` from [Releases](../../releases)
2. Install the APK on your device
3. Open LSPosed Manager → Modules → Enable "SmoothSaver"
4. Ensure scope includes **System Framework** (`android`)
5. Reboot

## Verification

After installation, verify 90Hz is maintained in Battery Saver:

1. **Enable Battery Saver** (Settings → Battery → Battery Saver)
2. **Check refresh rate** via one of:
   - Developer Options → "Show refresh rate" overlay
   - `adb shell dumpsys display | grep -i "refresh"`
   - `adb shell settings get system peak_refresh_rate`

Expected: Display stays at 90Hz even with Battery Saver active.

## Building from Source

### KSU Module
```bash
# Just zip the module directory
cd module/
zip -r ../SmoothSaver-KSU-v1.0.zip .
```

### LSPosed/Xposed Module
```bash
cd xposed/
./gradlew assembleRelease
# APK output: app/build/outputs/apk/release/app-release.apk
```

Or use the GitHub Actions CI workflow (push to trigger a build).

## How the Hook Works

Android's `DisplayModeDirector` manages refresh rates through a voting system. The `BatterySaverObserver` inner class:

1. Watches `Settings.Global.LOW_POWER_MODE`
2. When enabled → casts `Vote.PRIORITY_LOW_POWER_MODE` to cap refresh rate at 60Hz
3. `DisplayModeDirector` aggregates votes → enforces the lowest common denominator

Our Xposed module hooks:
- `BatterySaverObserver.onChange()` → no-op (prevents the vote from being cast)
- `BatterySaverObserver.observe()` → prevents registration entirely  
- Fallback: hooks `Settings.Global.getInt()` for `low_power` key within display context → always returns 0

## ⚠️ Warnings

- Keeping 90Hz during Battery Saver will consume more battery (that's the trade-off)
- Test on your specific Android version before daily-driving
- Always have a way to disable modules via recovery/ADB if issues arise
- `adb shell cmd module disable <module_id>` can disable KSU modules without booting

## License

MIT License — see [LICENSE](LICENSE)
