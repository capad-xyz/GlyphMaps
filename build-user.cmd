@echo off
REM Build the USER flavor — the clean product for distribution. No dev screen,
REM no on-disk capture logging. Installs as "GlyphMaps" (com.glyphnavtoy).
REM
REM This builds the debug-signed APK (fine for sideloading / community sharing).
REM For a proper signed release, switch to assembleUserRelease once a keystore
REM is set up.

setlocal
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "TEMP=C:\GradleTmp"
set "TMP=C:\GradleTmp"
set "GRADLE_OPTS=-Djava.io.tmpdir=C:\GradleTmp"
if not exist "C:\GradleTmp" mkdir "C:\GradleTmp"

call "%~dp0gradlew.bat" :app:assembleUserDebug
if errorlevel 1 (
    echo.
    echo Build failed.
    exit /b 1
)

echo.
echo USER APK built:
echo   %~dp0app\build\outputs\apk\user\debug\app-user-debug.apk
echo.
echo To install on the connected phone:
echo   adb install -r "%~dp0app\build\outputs\apk\user\debug\app-user-debug.apk"
endlocal
