#!/system/bin/sh
# SmoothSaver — KernelSU/Magisk Module Installation Script
# Prevents Battery Saver from forcing 60Hz on Pixel 7

SKIPUNZIP=1

# Print module info
ui_print "======================================="
ui_print "  SmoothSaver — 90Hz Battery Saver     "
ui_print "  Keep smooth display in power saving   "
ui_print "======================================="
ui_print ""

# Check device compatibility
DEVICE=$(getprop ro.product.device)
MODEL=$(getprop ro.product.model)
ANDROID_VER=$(getprop ro.build.version.sdk)

ui_print "- Device: $MODEL ($DEVICE)"
ui_print "- Android SDK: $ANDROID_VER"

case "$DEVICE" in
    panther|cheetah|lynx|tangorpro|felix)
        ui_print "- ✓ Compatible Pixel device detected"
        ;;
    *)
        ui_print "- ⚠ This module is designed for Pixel 7 series"
        ui_print "- Proceeding anyway (may work on other devices)"
        ;;
esac

# Check Android version (API 34+ = Android 14+)
if [ "$ANDROID_VER" -lt 34 ]; then
    ui_print "- ⚠ Warning: Designed for Android 14+, you have SDK $ANDROID_VER"
fi

# Extract module files
ui_print "- Extracting module files..."
unzip -o "$ZIPFILE" -x 'META-INF/*' -d "$MODPATH" >&2

# Set permissions
ui_print "- Setting permissions..."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755

# Check for LSPosed
if [ -d "/data/adb/lspd" ] || [ -d "/data/adb/modules/zygisk_lsposed" ] || [ -d "/data/adb/modules/lsposed" ]; then
    ui_print ""
    ui_print "- ✓ LSPosed detected!"
    ui_print "- For best results, also install the SmoothSaver Xposed module"
    ui_print "- The Xposed module provides deeper framework-level hooks"
else
    ui_print ""
    ui_print "- LSPosed not detected"
    ui_print "- This module will use boot scripts to maintain 90Hz"
    ui_print "- For more reliable results, consider installing LSPosed"
fi

ui_print ""
ui_print "- Installation complete!"
ui_print "- Reboot to activate"
ui_print ""
