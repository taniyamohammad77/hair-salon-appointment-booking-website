@echo off
rem Start the Amore frontend dev server locally (Windows).
rem Serves the frontend folder over http://localhost:8081 so the browser's
rem Origin is allowed by the backend CORS rules (file:// double-click is NOT).
rem Override the port with:  set PORT=3000 && run-frontend.cmd
cd /d "%~dp0"
node serve-frontend.js
