@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "ANDROID_HOME=D:\Development\AndoidSDK"
set "GRADLE_EXEC=C:\Users\Koko\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat"

echo =========================================
echo Building IDLIX Android APK...
echo =========================================

"%GRADLE_EXEC%" assembleDebug %*

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
