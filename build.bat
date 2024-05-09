@echo off
REM This script is used for building the whole plugin.
REM
REM Usage: build.bat
REM Example: build.bat

:: Use PowerShell to get the latest Maven version by fetching the redirect URL and extracting the version number.
for /f "delims=" %%i in ('powershell -Command "(Invoke-WebRequest -Uri https://github.com/apache/maven/releases/latest -MaximumRedirection 0 -ErrorAction SilentlyContinue).Headers.Location -replace '^.*/maven-', ''"') do set M3_VERSION=%%i

call mvn wrapper:wrapper -Dmaven=%M3_VERSION% --no-transfer-progress
call mvnw.cmd clean verify -Dsigning.disabled=true --no-transfer-progress
