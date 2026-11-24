#!/bin/bash
# Omniloader Installer for macOS: finds a Java 17+ (Minecraft's own if possible) and opens the installer window.
RES="$(cd "$(dirname "$0")/../Resources" && pwd)"
JAR="$RES/omniloader-installer.jar"
AS="$HOME/Library/Application Support"

java_ok() { # prints the path if it is Java 17 or newer
    [ -x "$1" ] || return 1
    v=$("$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
    [ "$v" -ge 17 ] 2>/dev/null && echo "$1"
}

find_java() {
    for j in \
        "$AS/minecraft/runtime/java-runtime-delta/mac-os-arm64/java-runtime-delta/jre.bundle/Contents/Home/bin/java" \
        "$AS/minecraft/runtime/java-runtime-delta/mac-os/java-runtime-delta/jre.bundle/Contents/Home/bin/java" \
        "$AS/minecraft/runtime/java-runtime-gamma/mac-os-arm64/java-runtime-gamma/jre.bundle/Contents/Home/bin/java" \
        "$AS"/PrismLauncher/java/*/bin/java "$AS"/PrismLauncher/java/*/Contents/Home/bin/java \
        "$AS"/ModrinthApp/meta/java_versions/*/bin/java "$AS"/ModrinthApp/meta/java_versions/*/Contents/Home/bin/java; do
        java_ok "$j" && return 0
    done
    home=$(/usr/libexec/java_home -v 17+ 2>/dev/null) && java_ok "$home/bin/java" && return 0
    java_ok "$(command -v java)" && return 0
    return 1
}

JAVA=$(find_java)
if [ -z "$JAVA" ]; then
    answer=$(osascript -e 'display dialog "Omniloader needs Java to run its installer.\n\nThe easiest fix: open the Minecraft Launcher and play any version once (it downloads Java for you), then open this installer again.\n\nOr install Java 21 from adoptium.net." buttons {"Close", "Get Java"} default button "Get Java" with title "Omniloader Installer" with icon caution' -e 'button returned of result' 2>/dev/null)
    [ "$answer" = "Get Java" ] && open "https://adoptium.net/temurin/releases/?version=21&os=mac"
    exit 1
fi
exec "$JAVA" -Xdock:name="Omniloader Installer" -Xdock:icon="$RES/AppIcon.icns" -jar "$JAR" "$@"
