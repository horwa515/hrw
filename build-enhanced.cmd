@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if defined JAVA_HOME (
  set "JAVAC=%JAVA_HOME%\bin\javac.exe"
  set "JAVA=%JAVA_HOME%\bin\java.exe"
  set "JAR=%JAVA_HOME%\bin\jar.exe"
) else (
  set "JAVAC=javac"
  set "JAVA=java"
  set "JAR=jar"
)
if not exist build\office-libs\dependencies.json (
  echo Run python scripts\fetch_office_dependencies.py first.
  exit /b 1
)
set "TARGET=--release 8 -Xlint:-options"
"%JAVAC%" -version 2>&1 | findstr /c:"javac 1.8." >nul
if not errorlevel 1 set "TARGET=-source 8 -target 8"
if not exist build\enhanced-classes mkdir build\enhanced-classes
if not exist build\enhanced-tests mkdir build\enhanced-tests
"%JAVAC%" %TARGET% -encoding UTF-8 -cp "build\classes;build\office-libs\*" -d build\enhanced-classes src\enhanced\java\com\pptconverter\*.java
if errorlevel 1 exit /b 1
"%JAR%" cf dist\ppt-office-encryption.jar -C build\enhanced-classes .
if errorlevel 1 exit /b 1
"%JAVAC%" %TARGET% -encoding UTF-8 -cp "build\classes;build\enhanced-classes;build\office-libs\*" -d build\enhanced-tests src\enhanced\test\com\pptconverter\*.java
if errorlevel 1 exit /b 1
if exist build\encrypted-validation.pptx del build\encrypted-validation.pptx
"%JAVA%" -Dfile.encoding=UTF-8 -ea -cp "build\classes;build\enhanced-classes;build\enhanced-tests;build\office-libs\*" com.pptconverter.OfficeEncryptionTest build\encrypted-validation.pptx
exit /b %errorlevel%
