@echo off
REM This script is used for building the whole plugin.
REM
REM Usage: build.bat
REM Example: build.bat

:: Use PowerShell to get the latest Maven version by fetching the redirect URL and extracting the version number.
for /f "delims=" %%i in ('powershell -Command "(Invoke-WebRequest -Uri https://github.com/apache/maven/releases/latest -UseBasicParsing -MaximumRedirection 0 -ErrorAction SilentlyContinue).Headers.Location -replace '^.*/maven-', ''"') do set M3_VERSION=%%i

call mvn wrapper:wrapper -Dmaven=%M3_VERSION% --no-transfer-progress
REM -Dmaven.resolver.validation=MILD: Maven 3.10 rejects the nested-jar classifiers injected by Tycho 5.0.3 (eclipse-tycho/tycho#6399). Drop it once Tycho includes the fix.
call mvnw.cmd clean verify -Dmaven.resolver.validation=MILD --no-transfer-progress
