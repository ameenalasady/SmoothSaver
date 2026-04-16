#!/system/bin/sh
# SmoothSaver — Post-FS-Data Script
# Runs early in boot before system services start
# Sets system properties to prepare for 90Hz enforcement

MODDIR=${0%/*}
LOG="$MODDIR/smoothsaver.log"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') [post-fs-data] $1" >> "$LOG"
}

log "SmoothSaver post-fs-data started"
log "Device: $(getprop ro.product.device)"
log "Android: $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"

# Truncate log if it gets too large (> 100KB)
if [ -f "$LOG" ] && [ $(wc -c < "$LOG") -gt 102400 ]; then
    tail -n 100 "$LOG" > "$LOG.tmp"
    mv "$LOG.tmp" "$LOG"
    log "Log truncated"
fi

log "post-fs-data complete"
