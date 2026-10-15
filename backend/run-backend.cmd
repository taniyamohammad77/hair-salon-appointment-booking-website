@echo off
rem ==========================================================================
rem AMORE BACKEND - MAIN LAUNCHER (permanent setup)
rem
rem WHAT THIS DOES
rem   Starts the EXISTING prebuilt jar (backend/target/backend-0.0.1-SNAPSHOT.jar)
rem   with the exact JVM flags the project requires, from the backend/ working
rem   directory, detached from whatever terminal started it, logging stdout and
rem   stderr to backend-dev.log (append - old logs are never overwritten).
rem
rem WHAT THIS DOES NOT DO
rem   - does NOT rebuild the jar (no Maven, ever)
rem   - does NOT touch run-dev.cmd, source code, config, database or frontend
rem   - does NOT check port 8080: this script is only ever invoked BY the
rem     supervisor (backend-supervisor.cmd), which owns all port checking and
rem     identity verification before deciding to launch. Calling it directly
rem     while the backend is already up would fail with "port in use" and exit -
rem     harmless, but the supported entry point for humans is start-backend.cmd.
rem
rem NOTE ON DETACHMENT
rem   This script is STARTed (in a new minimized console) by the supervisor, so
rem   the java process belongs to its own console, NOT to the Codex session or
rem   any interactive terminal. Closing this window's parent context does not
rem   arise: the window lives until the JVM exits.
rem ==========================================================================
setlocal
cd /d "%~dp0"

set "JAR=target\backend-0.0.1-SNAPSHOT.jar"

if not exist "%JAR%" (
    echo [run-backend] FATAL: %JAR% not found. Nothing was built or changed.
    echo [run-backend] This launcher never builds. Restore the jar and retry.
    exit /b 1
)
if not exist "lib\mssql-jdbc_auth-13.4.0.x64.dll" (
    echo [run-backend] FATAL: Windows-auth DLL missing ^(lib\mssql-jdbc_auth-13.4.0.x64.dll^).
    exit /b 1
)
if not exist "dev-tls12-legacy.security" (
    echo [run-backend] FATAL: dev-tls12-legacy.security missing.
    exit /b 1
)

echo [run-backend] %date% %time% launching existing jar via permanent setup >> backend-dev.log

java -Djava.security.properties=dev-tls12-legacy.security -Djava.library.path=lib -jar "%JAR%" >> backend-dev.log 2>&1

rem Reaching here means the JVM exited (crash or external stop). Log it and
rem exit; the supervisor loop notices the dead port and relaunches.
echo [run-backend] %date% %time% JVM exited with code %errorlevel% >> backend-dev.log
endlocal
exit /b %errorlevel%
