@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
 echo ERROR: Java not found. Install JDK 17 and add it to PATH.
 goto :fail
)
java -version 2>"%TEMP%\kingdom_java_version.txt"
findstr /C:"version \"17." "%TEMP%\kingdom_java_version.txt" >nul
if errorlevel 1 (
 echo WARNING: Minecraft Forge 1.20.1 requires JDK 17 for reliable builds.
 echo Current Java:
 type "%TEMP%\kingdom_java_version.txt"
 echo Set JAVA_HOME to JDK 17 and restart this script.
 goto :fail
)

if exist gradlew.bat (
 echo Building with project Gradle Wrapper...
 call gradlew.bat clean build --no-daemon
) else (
 where gradle >nul 2>nul
 if errorlevel 1 (
  echo ERROR: Gradle Wrapper is absent and Gradle is not installed.
  echo Install Gradle 8.8 or add the official Forge 1.20.1 MDK wrapper files.
  echo Do not copy wrappers from untrusted sources.
  goto :fail
 )
 echo Building with installed Gradle...
 call gradle clean build --no-daemon
)
if errorlevel 1 goto :fail
if not exist "build\libs\kingdomnpc-*.jar" (
 echo WARNING: Check build\libs for the generated mod jar.
)
echo BUILD SUCCESS. Check build\libs.
exit /b 0
:fail
echo BUILD NOT COMPLETED. No install attempted.
exit /b 1
