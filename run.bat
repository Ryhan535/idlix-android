@echo off
set "ADB=D:\Development\AndroidSDK\platform-tools\adb.exe"
set "DEVICE=192.168.1.67:32879"

call build.bat

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Connecting ADB to %DEVICE%...
    "%ADB%" connect %DEVICE%

    echo Installing APK to device...
    "%ADB%" -s %DEVICE% install -r app\build\outputs\apk\debug\app-debug.apk

    echo Launching IDLIX App on device...
    "%ADB%" -s %DEVICE% shell am start -n com.idlix.android/.MainActivity
    echo Done!
)
