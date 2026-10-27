@echo off
REM This script is used for building the whole plugin.
REM
REM Usage: build.bat
REM Example: build.bat

:: Pinned to 3.9.16, the last Maven version with a passing CI. Builds with the then-latest 3.10.0 fail with "Invalid Collect Request".
set M3_VERSION=3.9.16

call mvn wrapper:wrapper -Dmaven=%M3_VERSION% --no-transfer-progress
call mvnw.cmd clean verify --no-transfer-progress
