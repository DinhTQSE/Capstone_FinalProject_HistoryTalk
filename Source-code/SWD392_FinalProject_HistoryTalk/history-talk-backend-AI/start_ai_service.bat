@echo off
title HistoryTalk - AI Backend Service (Port 8001)

echo =======================================================
echo    KHOI DONG HISTORYTALK BACKEND AI (FASTAPI + VLM)
echo =======================================================
echo.

cd /d "%~dp0"

echo [*] Thu muc lam viec hien tai: %CD%

:: 1. Kiem tra Python
where python >nul 2>nul
if %errorlevel% neq 0 (
    echo [LOI] Khong tim thay Python trong he thong! Vui long cai Python 3.10 tro len.
    pause
    exit /b 1
)

:: 2. Kiem tra hoac tao venv
if not exist "venv" (
    echo [*] Dang khoi tao moi truong ao venv...
    python -m venv venv
    if %errorlevel% neq 0 (
        echo [LOI] Khong the tao venv.
        pause
        exit /b 1
    )
    echo [OK] Da tao venv thanh cong!
)

:: 3. Kich hoat venv
echo [*] Dang kich hoat venv...
call venv\Scripts\activate.bat

:: 4. Kiem tra requirements
echo [*] Dang kiem tra thu vien requirements.txt...
pip install -r requirements.txt --quiet

:: 5. Kiem tra file .env
if not exist ".env" (
    echo [CANH BAO] Chua tim thay .env! Dang sao chep tu .env.example...
    copy .env.example .env
) else (
    echo [OK] Da tim thay file .env tai: %CD%\.env
)

:: 6. Khoi chay server
echo.
echo =======================================================
echo    DANG CHAY AI SERVICE TAI: http://localhost:8001
echo    Tai lieu Swagger API:      http://localhost:8001/docs
echo =======================================================
echo.

python main.py

if %errorlevel% neq 0 (
    echo.
    echo [LOI] Server bi dung dot ngot!
    pause
)
