#!/bin/bash
#
# Install script for Abstratium Windsurf hooks
# This script copies the Python hook scripts to ~/.codeium/abstratium-hooks
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$HOME/.codeium/abstratium-hooks"

echo "Installing Abstratium Windsurf hooks..."

# Create the target directory if it doesn't exist
if [ ! -d "$TARGET_DIR" ]; then
    echo "Creating directory: $TARGET_DIR"
    mkdir -p "$TARGET_DIR"
fi

# Copy all Python scripts from the source directory to the target directory
for script in "$SCRIPT_DIR"/*.py; do
    if [ -f "$script" ]; then
        script_name=$(basename "$script")
        echo "Installing: $script_name"
        cp "$script" "$TARGET_DIR/"
    fi
done

echo ""
echo "✅ Hooks installed successfully to: $TARGET_DIR"
echo ""
echo "These scripts are already configured so that Windsurf uses them due to the hooks configured in the .windsurf/hooks.json folder"
echo ""
