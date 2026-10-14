@echo off
chcp 65001 >nul
title PPT Format Converter V1.0
setlocal
cd /d "%~dp0"
if exist "runtime\bin\java.exe" (
  set "JAVA=runtime\bin\java.exe"
) else if defined JAVA_HOME (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA=java"
)
set "APP=ppt-format-converter.jar"
if not exist "%APP%" set "APP=dist\ppt-format-converter.jar"
"%JAVA%" -Dfile.encoding=UTF-8 -jar "%APP%" %*
if errorlevel 1 (
  echo Application failed. Install Java 17+ or use the offline Windows portable package.
  pause
  exit /b 1
)
