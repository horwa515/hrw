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
if not exist build\classes mkdir build\classes
if not exist build\test-classes mkdir build\test-classes
if not exist build\package mkdir build\package
if not exist dist mkdir dist
(for /r "src\main\java" %%f in (*.java) do @echo "%%f") > build\main-sources.txt
"%JAVAC%" -J-Dfile.encoding=UTF-8 --release 17 -encoding UTF-8 -d build\classes @build\main-sources.txt
if errorlevel 1 goto fail
xcopy /e /i /y src\main\resources\* build\classes\ >nul
if errorlevel 1 goto fail
"%JAR%" --create --file dist\ppt-format-converter.jar --main-class com.pptconverter.App -C build\classes .
if errorlevel 1 goto fail
copy /y dist\ppt-format-converter.jar build\package\ >nul
(for /r "src\test\java" %%f in (*.java) do @echo "%%f") > build\test-sources.txt
"%JAVAC%" -J-Dfile.encoding=UTF-8 --release 17 -encoding UTF-8 -cp build\classes -d build\test-classes @build\test-sources.txt
if errorlevel 1 goto fail
"%JAVA%" -Dfile.encoding=UTF-8 -ea -cp "build\classes;build\test-classes" com.pptconverter.ConverterTest
if errorlevel 1 goto fail
echo Build and tests passed: dist\ppt-format-converter.jar
exit /b 0
:fail
echo Build failed. Install JDK 17+ and check JAVA_HOME.
exit /b 1
