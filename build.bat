@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "ANDROID_HOME=D:\Development\AndoidSDK"

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
