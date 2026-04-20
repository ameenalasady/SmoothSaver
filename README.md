# Pixel 7 SmoothSaver (90Hz Battery Saver Enforcer)

Android 14+ on the Pixel 7 aggressively forces the display refresh rate down to 60Hz the moment Battery Saver is turned on. Standard system overrides (like forcing peak refresh rate in Developer Options) check for the Battery Saver state and obey the lock. 

This repository fixes that by providing an LSPosed module that neutralizes the restriction at the framework level, allowing you to use Battery Saver while retaining butter-smooth 90Hz scrolling!

## How It Works
The standard Android `DisplayModeDirector` heavily prioritizes a `BatterySaverObserver` which casts a vote to lock the screen at 60Hz. 
* **LSPosed Module:** Hooks into `DisplayModeDirector` and completely ignores the BatterySaverObserver, seamlessly untethering the refresh rate from the low-power active mode!

## Installation

### Requirements
* Pixel 7 (Android 14-16+)
* Rooted (KernelSU or Magisk)
* LSPosed (Zygisk-LSPosed or modern forks)

### LSPosed Module
1. Download `SmoothSaver-Xposed-vX.X.apk` from the latest [Release](../../releases).
2. Install the APK to your device.
3. Open your LSPosed Manager app.
4. Enable the **"SmoothSaver"** module.
5. Ensure the scope targets the **System Framework** (`android`).
6. Reboot your device.

## Building from Source

This project uses Gradle. If you wish to build it yourself:

```bash
cd xposed
./gradlew assembleRelease
```
The resulting module will be output at `xposed/app/build/outputs/apk/release/app-release.apk`
