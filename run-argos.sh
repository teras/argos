#!/bin/bash
# Argos runner script

# Find Java 11+ executable
JAVA_CMD=""
for java_path in "/usr/lib/jvm/java-21-openjdk/bin/java" "/usr/lib/jvm/java-24-openjdk/bin/java" "java"; do
    if command -v "$java_path" &> /dev/null; then
        JAVA_CMD="$java_path"
        break
    fi
done

if [ -z "$JAVA_CMD" ]; then
    echo "Error: Java 11+ not found. Please install Java 11 or later."
    exit 1
fi

# Get script directory
SCRIPT_DIR="$(dirname "$0")"
FAT_JAR="$SCRIPT_DIR/build/libs/argos-0.1.0-fat.jar"

# Build fat JAR if it doesn't exist or is older than source files
if [ ! -f "$FAT_JAR" ] || [ "$SCRIPT_DIR/src" -nt "$FAT_JAR" ]; then
    echo "Building fat JAR..."
    "$SCRIPT_DIR/gradlew" fatJar
    if [ $? -ne 0 ]; then
        echo "Error: Failed to build fat JAR"
        exit 1
    fi
fi

# Enable test mode if --test-mode is passed
if [[ "$*" == *"--test-mode"* ]]; then
    export _ARGOS_TEST_MODE_=true
    # Remove --test-mode from arguments
    set -- "${@/--test-mode/}"
fi

# Run the fat JAR
"$JAVA_CMD" -jar "$FAT_JAR" "$@"