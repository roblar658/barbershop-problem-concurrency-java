@echo off
setlocal
echo ====================================================================
echo   Kjorer 1000-kunders stresstest med utsultingssjekk og CSV-eksport
echo ====================================================================
cd /d "%~dp0"
call mvnw.cmd compile -q
java -cp target\classes com.barbershop.stress.CsvStressRunner 1000 5 20
pause

