@echo off
REM Pull all on-device capture files from the phone into captures/phone/
REM (the in-app CaptureWriter saves to /sdcard/Android/data/com.glyphnavtoy/files/captures/).
REM
REM Run this whenever you come back from a walk / drive — it grabs every
REM raw + parsed log file the app accumulated while you were out of ADB
REM range, regardless of how long ago. Persistent, not subject to logcat's
REM 256 KB ring buffer.
REM
REM Files land under captures/phone/ (gitignored — they have real GPS data).

setlocal
set "DEV=192.168.1.16:5555"
REM Full path required — `/sdcard/Android/data/...` is restricted to the
REM app's UID on Android 11+, the `/storage/emulated/0/...` form works.
set "REMOTE=/storage/emulated/0/Android/data/com.glyphnavtoy/files/captures"
set "LOCAL=%~dp0captures\phone"
if not exist "%LOCAL%" mkdir "%LOCAL%"

echo Pulling from %REMOTE% on %DEV% ...
REM -s targets the specific device — required when both USB and wireless
REM are simultaneously connected, otherwise adb errors with "more than one
REM device/emulator". Default is wireless; edit DEV above if USB-only.
adb -s %DEV% pull "%REMOTE%/." "%LOCAL%"
if errorlevel 1 (
    echo.
    echo Pull failed. Common causes:
    echo   - Phone disconnected: check 'adb devices'
    echo   - App never ran: nothing has been written yet
    echo   - Wrong device address: edit DEV= at the top of this file
    exit /b 1
)

echo.
echo Files now in %LOCAL%:
dir /b "%LOCAL%"
endlocal
