@echo off
chcp 65001 >nul
title Transportation - Start All Services

echo.
echo ======================================================
echo   Transportation - Start All Microservices
echo ======================================================
echo.
echo [INFO] Checking Nacos on localhost:8848 ...
powershell -Command "try { $c=New-Object Net.Sockets.TcpClient; $c.Connect('localhost',8848); $c.Close(); exit 0 } catch { exit 1 }"
if %errorlevel% neq 0 (
    echo [WARN] Nacos is NOT running on localhost:8848 !
    echo [WARN] Please start Nacos first: bin\startup.cmd -m standalone
    echo.
    set /p CONT=Continue anyway? (y/N): 
    if /i not "%CONT%"=="y" (
        echo Cancelled.
        pause
        exit /b 0
    )
) else (
    echo [OK]   Nacos is running.
)

echo.
echo [INFO] Starting all services in parallel ...
echo.

start "user-service      :8081" cmd /k "cd /d %~dp0user-service      && mvn spring-boot:run"
start "auth-service      :8082" cmd /k "cd /d %~dp0auth-service      && mvn spring-boot:run"
start "gateway-service   :8083" cmd /k "cd /d %~dp0gateway-service   && mvn spring-boot:run"
start "customer-service  :8084" cmd /k "cd /d %~dp0customer-service  && mvn spring-boot:run"
start "shop-service      :8085" cmd /k "cd /d %~dp0shop-service      && mvn spring-boot:run"
start "order-service     :8086" cmd /k "cd /d %~dp0order-service     && mvn spring-boot:run"
start "logistics-service :8087" cmd /k "cd /d %~dp0logistics-service && mvn spring-boot:run"
start "driver-service    :8088" cmd /k "cd /d %~dp0driver-service    && mvn spring-boot:run"

echo.
echo ======================================================
echo   All 8 services launched in separate windows!
echo.
echo   user-service      -^> http://localhost:8081
echo   auth-service      -^> http://localhost:8082
echo   gateway-service   -^> http://localhost:8083  [gateway]
echo   customer-service  -^> http://localhost:8084
echo   shop-service      -^> http://localhost:8085
echo   order-service     -^> http://localhost:8086
echo   logistics-service -^> http://localhost:8087
echo   driver-service    -^> http://localhost:8088
echo   Nacos dashboard   -^> http://localhost:8848/nacos
echo ======================================================
echo.
pause















