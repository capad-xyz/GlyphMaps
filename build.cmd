@echo off
REM Wrapper around gradlew that sets the two env quirks we discovered:
REM   1. JAVA_HOME must point at Android Studio's JDK 21 (JDK 17 had a
REM      Unix-Domain-Socket loopback bug on this machine)
REM   2. TEMP must be a path without spaces or 8.3-truncated names
REM      (the path "C:\Users\AADARS~1\..." broke AF_UNIX socket creation)

setlocal
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "TEMP=C:\GradleTmp"
set "TMP=C:\GradleTmp"
set "GRADLE_OPTS=-Djava.io.tmpdir=C:\GradleTmp"

if not exist "C:\GradleTmp" mkdir "C:\GradleTmp"

if "%~1"=="" (
    call gradlew.bat :app:assembleDebug
) else (
    call gradlew.bat %*
)
endlocal
