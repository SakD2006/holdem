@echo off
rem Starts the Hold'em desktop app on Windows. Needs Java 21 or newer.
rem Double-click this file to run it.
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install Java 21 or newer from https://adoptium.net and try again.
  pause
  exit /b 1
)

java --module-path "javafx\win" --add-modules javafx.controls,javafx.media -cp "lib\*" com.saksham.poker.client.app.HoldemApp %*
if errorlevel 1 pause
