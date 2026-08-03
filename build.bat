@echo off
REM This script is used for building the whole plugin.
REM
REM Usage: build.bat
REM Example: build.bat

:: Pin Maven to 3.9.9 for compatibility with Tycho 5.0.3 (Maven 4.x is not supported).
set "M3_VERSION=3.9.9"

call mvn wrapper:wrapper -Dmaven=%M3_VERSION% --no-transfer-progress
if errorlevel 1 exit /b %errorlevel%

call mvnw.cmd clean verify --no-transfer-progress
exit /b %errorlevel%
