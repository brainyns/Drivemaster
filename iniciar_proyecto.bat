@echo off
chcp 65001 > nul
title DriveMaster - Sistema Completo

echo ======================================================================
echo           INICIANDO SISTEMA DRIVEMASTER CON MODELO PREDICTIVO
echo   Integrantes: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello
echo ======================================================================
echo.

echo [1/3] Iniciando Servicio de Machine Learning (Python - Puerto 8000)...
start "DriveMaster - ML API (Python)" cmd /k "cd ml && python app.py 8000"

timeout /t 2 /nobreak > nul

echo [2/3] Iniciando Backend (Spring Boot - Puerto 8080)...
start "DriveMaster - Backend (Spring Boot)" cmd /k "cd drivemaster && mvnw.cmd spring-boot:run"

timeout /t 3 /nobreak > nul

echo [3/3] Iniciando Frontend (Vite / React - Puerto 5173)...
start "DriveMaster - Frontend (React)" cmd /k "cd frontend && npm run dev"

echo.
echo ======================================================================
echo  Todos los servicios han sido lanzados en terminales independientes:
echo    - ML API (Python):    http://localhost:8000  (y Swagger / docs)
echo    - Backend (Java):     http://localhost:8080
echo    - Frontend (React):   http://localhost:5173
echo ======================================================================
pause

