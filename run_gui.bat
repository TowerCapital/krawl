@echo off
setlocal

set "JAVA_EXE=C:\Users\Yashodhan12\.jdks\openjdk-27\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=java"

set "CP=d:\mseproject_krawl\krawl;d:\mseproject_krawl\krawl\lib\mysql-connector-j-8.4.0.jar"

set "USER=%~1"
set "PORT=%~2"

if "%USER%"=="" set "USER=Alice"
if "%PORT%"=="" set "PORT=9001"

echo ============================================================
echo   Launching MeshLink GUI for user: %USER% on port: %PORT%
echo ============================================================
"%JAVA_EXE%" -cp "%CP%" MeshLinkGUI "%USER%" "%PORT%"
pause

