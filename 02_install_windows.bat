@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
set "MODSDIR=D:\ProjectKingdom\game\mods"
set "BACKUP=D:\ProjectKingdom\backups\kingdomnpc_before_install"
if not exist "%MODSDIR%" (echo Missing game mods directory: %MODSDIR% & pause & exit /b 1)
set "JAR=build\libs\kingdomnpc-0.1.0-stage0-voice3.3.jar"
if not exist "%JAR%" (echo JAR missing. Run 01_build_windows.bat first. & pause & exit /b 1)
if not exist "%BACKUP%" mkdir "%BACKUP%"
for %%F in ("%MODSDIR%\kingdomnpc*.jar") do (
 if exist "%%~fF" (
  echo Backing up %%~nxF
  copy /Y "%%~fF" "%BACKUP%\" >nul
  del "%%~fF"
 )
)
copy /Y "%JAR%" "%MODSDIR%\" >nul
if errorlevel 1 (echo Copy failed. Restore files from %BACKUP%. & pause & exit /b 1)
echo Installed %JAR% into %MODSDIR%
echo Please keep the game closed during installation.
pause
