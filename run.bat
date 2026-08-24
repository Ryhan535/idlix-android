@echo off
setlocal enabledelayedexpansion

set "ADB=D:\Development\AndoidSDK\platform-tools\adb.exe"

call build.bat

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Searching for connected ADB devices...

    set "FOUND=0"
    for /f "tokens=1,2" %%A in ('"%ADB%" devices') do (
        if "%%B"=="device" (
            set "FOUND=1"
            echo.
            echo =========================================
            echo [Device: %%A] Installing APK...
            echo =========================================
            "%ADB%" -s %%A install -r app\build\outputs\apk\debug\app-debug.apk

            echo.
            echo [Device: %%A] Launching IDLIX App...
            "%ADB%" -s %%A shell am start -n com.idlix.android/.MainActivity
        )
    )

    if "!FOUND!"=="0" (
        echo.
        echo [ERROR] No connected devices found!
        echo Please make sure USB debugging is enabled and your device is attached.
    ) else (
        echo.
        echo =========================================
        echo All done!
        echo =========================================
    )
)

endlocal
