@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
set "GRADLE_VERSION=9.5.1"
set "DISTRIBUTION_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_DIR=%PROJECT_DIR%.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin"
set "GRADLE_ZIP=%GRADLE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_EXE=%GRADLE_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat"

if exist "%GRADLE_EXE%" goto run_gradle

where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo PowerShell is required to download Gradle %GRADLE_VERSION%.
    exit /b 1
)

echo Gradle %GRADLE_VERSION% not found. Downloading...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$ProgressPreference = 'SilentlyContinue';" ^
  "New-Item -ItemType Directory -Force -Path $env:GRADLE_DIR | Out-Null;" ^
  "if (Test-Path -LiteralPath $env:GRADLE_ZIP) { Remove-Item -Force -LiteralPath $env:GRADLE_ZIP };" ^
  "Invoke-WebRequest -UseBasicParsing -Uri $env:DISTRIBUTION_URL -OutFile $env:GRADLE_ZIP;" ^
  "Expand-Archive -Force -LiteralPath $env:GRADLE_ZIP -DestinationPath $env:GRADLE_DIR;" ^
  "Remove-Item -Force -LiteralPath $env:GRADLE_ZIP"

if errorlevel 1 (
    echo Failed to download or unpack Gradle %GRADLE_VERSION%.
    exit /b 1
)

if not exist "%GRADLE_EXE%" (
    echo Gradle was unpacked, but "%GRADLE_EXE%" was not found.
    exit /b 1
)

:run_gradle
pushd "%PROJECT_DIR%"
call "%GRADLE_EXE%" %*
set "BUILD_EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %BUILD_EXIT_CODE%
