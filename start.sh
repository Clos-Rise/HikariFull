#!/bin/bash
# ============================================
#  Hikari Server Core — Startup Script
#  Minecraft 1.21.11 | Java 21
# ============================================

# --- Configuration ---
JAR_NAME="hikari-1.21.11-server.jar"
MIN_RAM="2G"
MAX_RAM="4G"
GUI=false

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# --- Banner ---
echo -e "${CYAN}"
echo "  ██╗  ██╗██╗██╗  ██╗ █████╗ ██████╗ ██╗"
echo "  ██║  ██║██║██║ ██╔╝██╔══██╗██╔══██╗██║"
echo "  ███████║██║█████╔╝ ███████║██████╔╝██║"
echo "  ██╔══██║██║██╔═██╗ ██╔══██║██╔══██╗██║"
echo "  ██║  ██║██║██║  ██╗██║  ██║██║  ██║██║"
echo "  ╚═╝  ╚═╝╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝"
echo -e "${NC}"
echo -e "${GREEN}  Hikari Server Core — Startup${NC}"
echo ""

# --- Check Java ---
if ! command -v java &> /dev/null; then
    echo -e "${RED}[ERROR] Java not found! Install Java 21+.${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "${RED}[ERROR] Java 21+ required, found Java $JAVA_VERSION${NC}"
    exit 1
fi
echo -e "${GREEN}[OK] Java $JAVA_VERSION detected${NC}"

# --- Check JAR ---
if [ ! -f "$JAR_NAME" ]; then
    echo -e "${RED}[ERROR] $JAR_NAME not found!${NC}"
    exit 1
fi
echo -e "${GREEN}[OK] Server jar found: $JAR_NAME${NC}"

# --- Accept EULA ---
if [ ! -f "eula.txt" ]; then
    echo "eula=true" > eula.txt
    echo -e "${YELLOW}[INFO] EULA accepted automatically${NC}"
fi

# --- JVM Flags (optimized for Hikari + Java 21) ---
JVM_FLAGS=(
    # Memory
    "-Xms${MIN_RAM}"
    "-Xmx${MAX_RAM}"

    # Garbage Collector — G1GC tuned for MC
    "-XX:+UseG1GC"
    "-XX:+ParallelRefProcEnabled"
    "-XX:MaxGCPauseMillis=200"
    "-XX:+UnlockExperimentalVMOptions"
    "-XX:+DisableExplicitGC"
    "-XX:G1NewSizePercent=30"
    "-XX:G1MaxNewSizePercent=40"
    "-XX:G1HeapRegionSize=8M"
    "-XX:G1ReservePercent=20"
    "-XX:G1MixedGCCountTarget=4"
    "-XX:InitiatingHeapOccupancyPercent=15"
    "-XX:G1MixedGCLiveThresholdPercent=90"
    "-XX:G1RSetUpdatingPauseTimePercent=5"
    "-XX:SurvivorRatio=32"
    "-XX:+PerfDisableSharedMem"
    "-XX:MaxTenuringThreshold=1"

    # Optimizations
    "-Dusing.aikars.flags=https://mcflags.emc.gs"
    "-Daikars.new.flags=true"
    "-server"

    # Paper/Purpur specific
    "-Dfile.encoding=UTF-8"
    "-Djava.net.preferIPv4Stack=true"

    # Hikari MDT — enable virtual threads for ForkJoinPool (Java 21)
    "--enable-preview"
)

# --- GUI flag ---
GUI_FLAG=""
if [ "$GUI" = false ]; then
    GUI_FLAG="--nogui"
fi

# --- Start ---
echo ""
echo -e "${CYAN}[Hikari] Starting server with ${MAX_RAM} RAM...${NC}"
echo -e "${CYAN}[Hikari] JVM flags: G1GC + Aikar optimizations${NC}"
echo ""

while true; do
    java "${JVM_FLAGS[@]}" -jar "$JAR_NAME" $GUI_FLAG

    echo ""
    echo -e "${YELLOW}[Hikari] Server stopped. Restarting in 5 seconds...${NC}"
    echo -e "${YELLOW}         Press CTRL+C to cancel.${NC}"
    echo ""
    sleep 5
done
