@echo off
rem Double-click to build & launch RuneLite dev mode with the Bronzeman TCG plugin.
rem JAVA_HOME points at this machine's Temurin 11 install - update if Java moves.
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-11.0.31.11-hotspot"
cd /d "%~dp0"
call gradlew.bat run
if errorlevel 1 (
	echo.
	echo Launch failed - see output above.
	pause
)
