@echo off
setlocal
set "PROJECT_ROOT=%~dp0"
set "APK_SOURCE=%PROJECT_ROOT%app\build\outputs\apk\debug\app-debug.apk"
set "OUTPUT_DIR=%PROJECT_ROOT%dist"
set "APK_OUTPUT=%OUTPUT_DIR%\YouTooBee-v1.1.0-debug.apk"

rem Prefer the user's JDK; otherwise use Android Studio's bundled runtime.
if defined JAVA_HOME goto build
if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
  set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
  goto build
)
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install Android Studio or set JAVA_HOME to a JDK 25 installation.
  exit /b 1
)

:build
call "%PROJECT_ROOT%gradlew.bat" -p "%PROJECT_ROOT%." assembleDebug --console=plain
if errorlevel 1 exit /b 1

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
copy /Y "%APK_SOURCE%" "%APK_OUTPUT%" >nul
if errorlevel 1 exit /b 1

echo.
echo YouTooBee APK: %APK_OUTPUT%
endlocal
