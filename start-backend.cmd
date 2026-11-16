@echo off
setlocal
cd /d "%~dp0backend"
if not exist .env (
    echo backend\.env is missing. Copy backend\.env.example to backend\.env and put your Gemini API key in it.
    pause
    exit /b 1
)
echo Building the backend with the Maven Wrapper...
call mvnw.cmd -q -B package -DskipTests
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)
java -jar target\darija-translator.jar
pause
