@echo off
REM Build + install + relaunch the DEV flavor (the daily-driver build with all
REM tools). Installs as "Glyph Maps Dev" (applicationId com.glyphnavtoy.dev),
REM so it coexists with the user build.
REM
REM Env quirks (see CLAUDE.md / README):
REM   1. JAVA_HOME → Android Studio's JDK 21
REM   2. TEMP → a path without spaces

setlocal
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "TEMP=C:\GradleTmp"
set "TMP=C:\GradleTmp"
set "GRADLE_OPTS=-Djava.io.tmpdir=C:\GradleTmp"
if not exist "C:\GradleTmp" mkdir "C:\GradleTmp"

REM Build BOTH flavors so the phone always has the latest of each. Shared
REM src/main means a new feature lands in both automatically.
call "%~dp0gradlew.bat" :app:assembleDevDebug :app:assembleUserDebug
if errorlevel 1 (
    echo.
    echo Build failed - skipping install.
    exit /b 1
)

echo.
echo Installing USER APK (Glyph Maps)...
adb install -r "%~dp0app\build\outputs\apk\user\debug\app-user-debug.apk"

echo.
echo Installing DEV APK (Glyph Maps Dev)...
adb install -r "%~dp0app\build\outputs\apk\dev\debug\app-dev-debug.apk"
if errorlevel 1 (
    echo.
    echo Install failed - is your phone connected? Try: adb devices
    exit /b 1
)

echo.
echo Relaunching Glyph Maps Dev (the active/listening build)...
adb shell am force-stop com.glyphnavtoy.dev
adb shell am start -n com.glyphnavtoy.dev/com.glyphnavtoy.MainActivity

echo.
echo Done. Both builds updated. Dev is the active listener.
endlocal
