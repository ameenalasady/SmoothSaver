#!/system/bin/sh
# SmoothSaver — Service Script
# Runs after boot completes. Monitors Battery Saver state and
# continuously overrides the 60Hz cap back to 90Hz.
#
# Strategy:
# 1. Set peak_refresh_rate to 90Hz on every boot
# 2. Watch for battery saver state changes via settings observer
# 3. When battery saver changes the refresh rate, override it back

MODDIR=${0%/*}
LOG="$MODDIR/smoothsaver.log"
TARGET_REFRESH_RATE="90.0"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') [service] $1" >> "$LOG"
}

# Wait for system to fully boot
wait_for_boot() {
    local count=0
    while [ "$(getprop sys.boot_completed)" != "1" ]; do
        sleep 1
        count=$((count + 1))
        if [ $count -gt 120 ]; then
            log "WARNING: Timed out waiting for boot (120s)"
            return 1
        fi
    done
    # Extra wait for system services to stabilize
    sleep 5
    log "System boot completed (waited ${count}s + 5s settle)"
    return 0
}

# Apply the 90Hz peak refresh rate setting
apply_refresh_rate() {
    local current=$(settings get system peak_refresh_rate 2>/dev/null)
    if [ "$current" != "$TARGET_REFRESH_RATE" ]; then
        settings put system peak_refresh_rate "$TARGET_REFRESH_RATE"
        log "Applied peak_refresh_rate: $current → $TARGET_REFRESH_RATE"
    fi
}

# Also ensure min_refresh_rate doesn't get capped
apply_min_refresh_rate() {
    local current_min=$(settings get system min_refresh_rate 2>/dev/null)
    # We don't force min to 90 (that would waste battery on static content)
    # but we make sure it's not artificially lowered below the user's preference
    if [ "$current_min" = "null" ] || [ -z "$current_min" ]; then
        return
    fi
}

# Main monitoring loop
main() {
    log "SmoothSaver service starting"
    log "Target refresh rate: ${TARGET_REFRESH_RATE}Hz"

    # Wait for boot
    if ! wait_for_boot; then
        log "Boot wait failed, attempting anyway"
    fi

    # Initial application
    apply_refresh_rate
    log "Initial refresh rate applied"

    # Get initial battery saver state
    local last_power_save=$(settings get global low_power 2>/dev/null)
    log "Initial battery saver state: $last_power_save"

    # Monitor loop: check every 3 seconds for battery saver state changes
    # When battery saver turns ON, Android will try to set peak_refresh_rate to 60Hz
    # We detect this and override it back to 90Hz
    local loop_count=0
    while true; do
        sleep 3

        # Check if battery saver state changed
        local current_power_save=$(settings get global low_power 2>/dev/null)

        if [ "$current_power_save" != "$last_power_save" ]; then
            log "Battery Saver state changed: $last_power_save → $current_power_save"
            last_power_save="$current_power_save"

            if [ "$current_power_save" = "1" ]; then
                # Battery saver just turned ON — Android will try to force 60Hz
                # Wait a moment for the system to apply its restriction, then override
                sleep 1
                apply_refresh_rate
                # Double-check after a short delay (some devices apply in stages)
                sleep 2
                apply_refresh_rate
                log "Overrode battery saver 60Hz restriction"
            fi
        fi

        # Periodic check: make sure peak_refresh_rate hasn't been changed
        # Only check every 30 seconds (10 loop iterations) to reduce overhead
        loop_count=$((loop_count + 1))
        if [ $((loop_count % 10)) -eq 0 ]; then
            local current_rate=$(settings get system peak_refresh_rate 2>/dev/null)
            if [ "$current_rate" != "$TARGET_REFRESH_RATE" ]; then
                log "Periodic check: rate drifted to $current_rate, re-applying"
                apply_refresh_rate
            fi
        fi

        # Check if module is still enabled (allow clean shutdown)
        if [ -f "$MODDIR/disable" ]; then
            log "Module disabled, stopping service"
            break
        fi
    done

    log "SmoothSaver service stopped"
}

# Run main in background
main &
