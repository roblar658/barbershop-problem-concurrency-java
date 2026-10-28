@echo off
setlocal
echo ========================================================
echo   Starter Sleeping Barber konsoll-demonstrasjon
echo ========================================================
cd /d "%~dp0"
call mvnw.cmd compile -q
java -cp target\classes com.barbershop.Main
pause

