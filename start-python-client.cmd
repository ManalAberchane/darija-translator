@echo off
setlocal
cd /d "%~dp0python-client"
if not exist .env (
    echo python-client\.env is missing. Copy python-client\.env.example to python-client\.env and set the API credentials.
    pause
    exit /b 1
)
if /i "%~1"=="cli" (
    python cli.py
) else (
    start "" pythonw app.py
)
