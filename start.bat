@echo off
title Hikari Server Core
color 0B

echo.
echo   ██╗  ██╗██╗██╗  ██╗ █████╗ ██████╗ ██╗
echo   ██║  ██║██║██║ ██╔╝██╔══██╗██╔══██╗██║
echo   ███████║██║█████╔╝ ███████║██████╔╝██║
echo   ██╔══██║██║██╔═██╗ ██╔══██║██╔══██╗██║
echo   ██║  ██║██║██║  ██╗██║  ██║██║  ██║██║
echo   ╚═╝  ╚═╝╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝
echo.
echo   Hikari Server Core — Startup
echo.

set JAR_NAME=hikari-1.21.11-server.jar
set MIN_RAM=2G
set MAX_RAM=4G

if not exist %JAR_NAME% (
    echo [ERROR] %JAR_NAME% not found!
    pause
    exit /b 1
)

if not exist eula.txt (
    echo eula=true> eula.txt
    echo [INFO] EULA accepted
)

echo [Hikari] Starting server with %MAX_RAM% RAM...
echo.

:loop
java -Xms%MIN_RAM% -Xmx%MAX_RAM% ^
    -XX:+UseG1GC ^
    -XX:+ParallelRefProcEnabled ^
    -XX:MaxGCPauseMillis=200 ^
    -XX:+UnlockExperimentalVMOptions ^
    -XX:+DisableExplicitGC ^
    -XX:G1NewSizePercent=30 ^
    -XX:G1MaxNewSizePercent=40 ^
    -XX:G1HeapRegionSize=8M ^
    -XX:G1ReservePercent=20 ^
    -XX:G1MixedGCCountTarget=4 ^
    -XX:InitiatingHeapOccupancyPercent=15 ^
    -XX:G1MixedGCLiveThresholdPercent=90 ^
    -XX:G1RSetUpdatingPauseTimePercent=5 ^
    -XX:SurvivorRatio=32 ^
    -XX:+PerfDisableSharedMem ^
    -XX:MaxTenuringThreshold=1 ^
    -Dusing.aikars.flags=https://mcflags.emc.gs ^
    -Daikars.new.flags=true ^
    -server ^
    -Dfile.encoding=UTF-8 ^
    -jar %JAR_NAME% --nogui

echo.
echo [Hikari] Server stopped. Restarting in 5 seconds...
echo          Press CTRL+C to cancel.
echo.
timeout /t 5 /nobreak >nul
goto loop
