@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr"
set "ANDROID_HOME=D:\Development\AndroidSDK"

echo =========================================
echo Building IDLIX Android APK...
echo =========================================

call gradlew.bat assembleDebug %*

if %ERRORLEVEL% EQU 0 (
    echo.
    echo =========================================
    echo [SUCCESS] APK compiled successfully!
    echo File: app\build\outputs\apk\debug\app-debug.apk
    echo =========================================
) else (
    echo.
    echo [ERROR] Build failed. Please check error log above.
)
