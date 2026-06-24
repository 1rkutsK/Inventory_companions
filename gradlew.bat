@echo off
setlocal enabledelayedexpansion

set "GRADLE_VERSION=9.5.1"
set "PROJECT_DIR=%~dp0"
set "GRADLE_DIR=%PROJECT_DIR%.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin"
set "GRADLE_ZIP=%GRADLE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_EXE=%GRADLE_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat"

if exist "%GRADLE_EXE%" goto run_gradle

echo Gradle %GRADLE_VERSION% not found. Downloading...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Force -Path '%GRADLE_DIR%' | Out-Null; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'; Expand-Archive -Force -Path '%GRADLE_ZIP%' -DestinationPath '%GRADLE_DIR%'"
if errorlevel 1 (
  echo Failed to download or unpack Gradle. Check internet connection and PowerShell.
  exit /b 1
)

:run_gradle
call "%GRADLE_EXE%" %*
