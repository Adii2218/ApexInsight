@echo off
setlocal
cd /d "%~dp0"

set "JDK_HOME="
for /d %%D in ("%ProgramFiles%\Java\jdk*") do (
    if exist "%%~fD\bin\javac.exe" set "JDK_HOME=%%~fD"
)

if not defined JDK_HOME (
    echo ApexInsight requires a JDK 17 or newer.
    echo Install a JDK and try again. The Maven Wrapper will download Maven and dependencies.
    pause
    exit /b 1
)

set "JAVA_HOME=%JDK_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%~dp0mvnw.cmd" -q exec:java
if errorlevel 1 (
    echo ApexInsight did not start. Review the error above, then try again.
    pause
)
