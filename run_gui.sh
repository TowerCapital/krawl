#!/bin/bash
JAVA_EXE="/c/Users/Yashodhan12/.jdks/openjdk-27/bin/java.exe"
if [ ! -f "$JAVA_EXE" ]; then
    JAVA_EXE="java"
fi

CP="d:/mseproject_krawl/krawl;d:/mseproject_krawl/krawl/lib/mysql-connector-j-8.4.0.jar"

USER="${1:-Alice}"
PORT="${2:-9001}"

echo "============================================================"
echo "  Launching MeshLink GUI for user: $USER on port: $PORT"
echo "============================================================"
"$JAVA_EXE" -cp "$CP" MeshLinkGUI "$USER" "$PORT"

