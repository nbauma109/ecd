@echo off
REM This script is used for building the whole plugin.
REM
REM Usage: build.bat
REM Example: build.bat

call mvn wrapper:wrapper -Dmaven=3.9.6 --no-transfer-progress
call mvnw.cmd clean verify -Dsigning.disabled=true --no-transfer-progress
