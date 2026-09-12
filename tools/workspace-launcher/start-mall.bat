@echo off
setlocal EnableExtensions

rem Mall one-click launcher
rem App web: 5173 | Admin web: 5174 | Portal API: 8085 | Admin API: 8080

set "ROOT=%~dp0"
set "LAUNCHER=%ROOT%start-mall.ps1"

if not exist "%LAUNCHER%" (
  echo [ERROR] Launcher not found: "%LAUNCHER%"
  pause
  exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%LAUNCHER%" %*
if errorlevel 1 (
  echo.
  echo [ERROR] Launcher failed.
  pause
  exit /b 1
)

endlocal
