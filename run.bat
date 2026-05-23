@echo off
setlocal

REM Flood CRM Service - local Windows launcher.
REM Values are loaded by Spring Boot from .env via spring-dotenv.
REM Do not hardcode secrets here.

cd /d "%~dp0"

if not exist ".env" (
  echo.
  echo Missing .env file.
  echo Create D:\floodstuff\flood-service-crm\.env before starting this service.
  echo.
  exit /b 1
)

echo.
echo Flood CRM Service
echo Starting on http://localhost:4002
echo Press Ctrl+C to stop
echo.

call "%~dp0mvnw.cmd" spring-boot:run "-Dmaven.test.skip=true"
