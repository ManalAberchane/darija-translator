@echo off
setlocal
cd /d "%~dp0php-client"
set "PHP=php"
if exist "%~dp0tools\php\php.exe" set "PHP=%~dp0tools\php\php.exe"
if not exist .env (
    echo php-client\.env is missing. Copy php-client\.env.example to php-client\.env and set the API credentials.
    pause
    exit /b 1
)
echo PHP client running at http://localhost:8000  (close this window to stop)
start "" http://localhost:8000
"%PHP%" -S localhost:8000 -t public
pause
