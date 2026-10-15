#!/usr/bin/env bash
# Start the Salon backend locally (Git Bash).
# -Djava.security.properties → legacy TLS for local SQL Server 2012 (see
# dev-tls12-legacy.security). -Djava.library.path → Windows-auth DLL.
cd "$(dirname "$0")" || exit 1

DLL="lib/mssql-jdbc_auth-13.4.0.x64.dll"
if [ ! -f "$DLL" ]; then
  echo "Windows-auth DLL missing — downloading from Maven Central..."
  curl -sf --create-dirs -o "$DLL" \
    "https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc_auth/13.4.0.x64/mssql-jdbc_auth-13.4.0.x64.dll" \
    || { echo "Download failed. See README.md (Windows-auth DLL)."; exit 1; }
fi

if [ ! -f target/backend-0.0.1-SNAPSHOT.jar ]; then
  echo "Jar not found — building first..."
  ./mvnw -q -DskipTests package || exit 1
fi
exec java \
  -Djava.security.properties=dev-tls12-legacy.security \
  -Djava.library.path=lib \
  -jar target/backend-0.0.1-SNAPSHOT.jar
