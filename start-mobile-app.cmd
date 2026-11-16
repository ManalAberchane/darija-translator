@echo off
setlocal
cd /d "%~dp0mobile-app"
if not exist node_modules (
    echo Installing dependencies ^(first run only^)...
    call npm install --no-audit --no-fund
    if errorlevel 1 (
        echo npm install failed.
        pause
        exit /b 1
    )
)
echo Scan the QR code below with the Expo Go app on your phone.
call npx expo start
pause
