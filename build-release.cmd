@echo off
REM Build the signed USER release App Bundle (.aab) for Google Play.
REM Same env quirks as push.cmd (JDK 21 + clean TEMP). Signs with the upload
REM key from keystore.properties (gitignored).

setlocal
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "TEMP=C:\GradleTmp"
set "TMP=C:\GradleTmp"
set "GRADLE_OPTS=-Djava.io.tmpdir=C:\GradleTmp"
if not exist "C:\GradleTmp" mkdir "C:\GradleTmp"

call "%~dp0gradlew.bat" :app:bundleUserRelease
if errorlevel 1 (
    echo.
    echo Release bundle FAILED.
    exit /b 1
)

echo.
echo Done. Signed App Bundle:
echo   app\build\outputs\bundle\userRelease\app-user-release.aab
echo Upload this to the Play Console. See PUBLISHING.md for the rest.
endlocal
