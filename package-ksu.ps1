# Package the KSU/Magisk module into a flashable zip
# Run from the project root directory

$ErrorActionPreference = "Stop"

$modulePath = "module"
$outputName = "SmoothSaver-KSU-v1.0.0.zip"

Write-Host "=== SmoothSaver KSU Module Packager ===" -ForegroundColor Cyan
Write-Host ""

# Check if module directory exists
if (-not (Test-Path $modulePath)) {
    Write-Host "ERROR: module/ directory not found" -ForegroundColor Red
    exit 1
}

# Remove old zip if exists
if (Test-Path $outputName) {
    Remove-Item $outputName
    Write-Host "Removed old $outputName" -ForegroundColor Yellow
}

# Create the zip
Write-Host "Packaging module..." -ForegroundColor Green
Compress-Archive -Path "$modulePath\*" -DestinationPath $outputName -Force

# Verify
if (Test-Path $outputName) {
    $size = (Get-Item $outputName).Length
    Write-Host ""
    Write-Host "SUCCESS: Created $outputName ($size bytes)" -ForegroundColor Green
    Write-Host ""
    Write-Host "To install:" -ForegroundColor Cyan
    Write-Host "  1. Transfer $outputName to your device"
    Write-Host "  2. Open KernelSU Manager -> Modules -> Install from storage"
    Write-Host "  3. Select the zip file"
    Write-Host "  4. Reboot"
} else {
    Write-Host "ERROR: Failed to create zip" -ForegroundColor Red
    exit 1
}
