@echo off
REM Snapshot current phone logcat to captures/ as two timestamped files:
REM   <stamp>_parsed.log  — what MapsNotificationListener parsed (compact)
REM   <stamp>_raw.log     — full notification.extras dump (for format-drift checks)
REM
REM Both files are gitignored (they contain real GPS data + street names).
REM Run this any time you've just done a meaningful nav session, especially
REM if you saw an interesting maneuver (roundabout, ramp, fork, arrival, etc.)
REM so we can replay/regress against it later.

setlocal
set "DEV=192.168.1.16:5555"
set "DIR=%~dp0captures"
if not exist "%DIR%" mkdir "%DIR%"

REM PowerShell for a sortable timestamp (cmd's %date% is locale-dependent).
for /f "delims=" %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmmss"') do set "TS=%%t"

echo Saving parsed listener output ^(MapsNotifListener:I^)
adb -s %DEV% logcat -d -s MapsNotifListener:I > "%DIR%\%TS%_parsed.log"

echo Saving raw notification dump ^(MapsNotifDump:V^)
adb -s %DEV% logcat -d -s MapsNotifDump:V > "%DIR%\%TS%_raw.log"

echo.
echo Saved:
dir /b "%DIR%\%TS%_*.log"
echo.
echo (Folder: %DIR%)
endlocal
