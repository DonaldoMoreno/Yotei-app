@echo off
REM Rebuild APK with fixed display ID logic and install to TV device

echo.
echo ════════════════════════════════════════════════════════════
echo  REBUILDING ANDROID TV APP WITH DISPLAY ID FIX
echo ════════════════════════════════════════════════════════════
echo.

REM Change to app directory
cd /d "%~dp0"

echo [1/3] Cleaning old build...
call gradlew clean
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Clean failed
    exit /b 1
)

echo.
echo [2/3] Building new debug APK...
call gradlew assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Build failed
    exit /b 1
)

echo.
echo [3/3] Installing on TV device...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Install failed
    exit /b 1
)

echo.
echo ════════════════════════════════════════════════════════════
echo ✓ BUILD AND INSTALL COMPLETE
echo ════════════════════════════════════════════════════════════
echo.
echo Next steps:
echo 1. Clear logcat: adb logcat -c
echo 2. Trigger pairing on TV
echo 3. Monitor logs: adb logcat | grep -E "PairingScreen|Navigation|display"
echo.
pause
