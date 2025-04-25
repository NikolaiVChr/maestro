#!/usr/bin/env sh

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Go one directory up from the script
cd "$SCRIPT_DIR" || exit 1
cd ..

# Launch Maestro
java --enable-native-access=ALL-UNNAMED --add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED -jar app/AbcTools.jar