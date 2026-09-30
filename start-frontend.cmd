@echo off
rem ==========================================================================
rem AMORE - one double-click launcher for local frontend development.
rem
rem WHAT IT DOES
rem   1. Starts the SAME dev server as "npm run dev" (serve-frontend.js on
rem      http://localhost:8081) - no duplicate server logic, no extra port.
rem   2. Opens http://localhost:8081 (the home page) in your default browser.
rem
rem WHY: pages opened by double-clicking HTML files run from file://, which the
rem backend's CORS rules correctly reject (Origin: null). Served over
rem http://localhost:8081 every /api call works. The backend itself
rem (http://localhost:8080, backend\run-dev.cmd) must be started separately.
rem
rem NOTE: a server that is already listening on the port is reused - the
rem second run only opens the browser tab. Keep the minimized "Amore frontend
rem server" window open while using the site; closing it stops the server.
rem Override the port with:  set PORT=3000 && start-frontend.cmd
rem ==========================================================================
setlocal
cd /d "%~dp0"

rem --- which port will the server use? (must match serve-frontend.js logic)
if not defined PORT set "PORT=8081"

rem --- is something already serving on that port? (reuse instead of a
rem     duplicate process; a second node would just exit with EADDRINUSE)
set "ALREADY_RUNNING=0"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%PORT% .*LISTENING" 2^>nul') do (
    set "ALREADY_RUNNING=1"
    set "EXISTING_PID=%%P"
)
if "%ALREADY_RUNNING%"=="1" goto reuse

echo Starting Amore frontend server on http://localhost:%PORT% ...
start "Amore frontend server" /min cmd /c "node serve-frontend.js"

rem --- wait (max ~15s) until the port is listening before opening the browser
set /a tries=0
:waitloop
rem ~1s delay (ping works even when stdin is redirected; timeout does not)
ping -n 2 127.0.0.1 >nul
set "ALREADY_RUNNING=0"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%PORT% .*LISTENING" 2^>nul') do set "ALREADY_RUNNING=1"
if "%ALREADY_RUNNING%"=="1" goto serverup
set /a tries+=1
if %tries% lss 15 goto waitloop
echo.
echo ERROR: server did not start on port %PORT%. Is Node.js installed?
echo Try running:  npm run dev
pause
exit /b 1

:serverup
echo Server is up.
goto open

:reuse
echo Server already running on port %PORT% ^(PID %EXISTING_PID%^) - reusing it.

:open
rem --- open the default browser at the local http address (never file://)
echo Opening http://localhost:%PORT% in your browser...
start "" "http://localhost:%PORT%"

echo.
echo Done. Leave the minimized "Amore frontend server" window open while
echo using the site. Backend API must be running too (backend\run-dev.cmd).
ping -n 4 127.0.0.1 >nul
endlocal
exit /b 0
