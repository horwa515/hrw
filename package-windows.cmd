@echo off
setlocal
cd /d "%~dp0"
call build.cmd
if errorlevel 1 exit /b 1
if defined JAVA_HOME (set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe") else (set "JPACKAGE=jpackage")
if exist dist\windows\PPTFormatConverter (
  echo Output exists. Move or remove dist\windows\PPTFormatConverter before rebuilding.
  exit /b 1
)
"%JPACKAGE%" --type app-image --name PPTFormatConverter --app-version 1.0.0 --vendor "PPT Format Converter" --input build\package --main-jar ppt-format-converter.jar --main-class com.pptconverter.App --dest dist\windows --win-console --java-options "-Dfile.encoding=UTF-8" --add-modules java.base,java.desktop,java.xml,jdk.httpserver
if errorlevel 1 exit /b 1
echo Offline app ready: dist\windows\PPTFormatConverter\PPTFormatConverter.exe
echo Copy the entire PPTFormatConverter directory, not only the EXE.
