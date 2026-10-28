@echo off
setlocal
echo ========================================================
echo   Kjorer alle enhets- og samtidighetstester for Barber
echo ========================================================
cd /d "%~dp0"
call mvnw.cmd test
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo   ALLE TESTER FULLFORT OG BESTATT! (23/23)
    echo ========================================================
) else (
    echo.
    echo [FEIL] En eller flere tester feilet.
)
pause

