@echo off
chcp 65001 >nul
title PPT Format Converter V1.1
setlocal
cd /d "%~dp0"
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
where java >nul 2>&1
if not errorlevel 1 set "JAVA=java"
if exist "runtime\bin\java.exe" set "JAVA=runtime\bin\java.exe"
set "APP=ppt-format-converter.jar"
if not exist "%APP%" set "APP=dist\ppt-format-converter.jar"
"%JAVA%" -Dfile.encoding=UTF-8 -jar "%APP%" %*
if errorlevel 1 (
  echo Application failed. This version requires Java 8 or newer.
  pause
  exit /b 1
)
