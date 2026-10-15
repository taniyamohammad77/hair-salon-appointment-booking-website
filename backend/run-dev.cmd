@echo off
rem Start the Salon backend locally (Windows).
rem -Djava.security.properties = legacy TLS for local SQL Server 2012 (see
rem dev-tls12-legacy.security). -Djava.library.path = Windows-auth DLL.
cd /d "%~dp0"

set DLL=lib\mssql-jdbc_auth-13.4.0.x64.dll
if not exist "%DLL%" (
  echo Windows-auth DLL missing - downloading from Maven Central...
  if not exist lib mkdir lib
  curl -sf -o "%DLL%" "https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc_auth/13.4.0.x64/mssql-jdbc_auth-13.4.0.x64.dll"
  if errorlevel 1 (
    echo Download failed. See README.md ^(Windows-auth DLL^).
    exit /b 1
  )
)

if not exist "target\backend-0.0.1-SNAPSHOT.jar" (
  echo Jar not found - building first...
  call mvnw.cmd -q -DskipTests package || exit /b 1
)
java -Djava.security.properties="%~dp0dev-tls12-legacy.security" -Djava.library.path="%~dp0lib" -jar "target\backend-0.0.1-SNAPSHOT.jar"
