#!/bin/bash

# OpenRouter IntelliJ Plugin - Icon Generation Script
# Generates the two status/error PNG icons from source images.
# The main logo uses SVG (openrouter-logo.svg / openrouter-logo_dark.svg) and needs no generation.

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ICONS_DIR="$PROJECT_ROOT/src/main/resources/icons"

SUCCESS_SOURCE="$ICONS_DIR/openrouter-plugin-success.png"
ERROR_SOURCE="$ICONS_DIR/openrouter-plugin-error.png"

echo -e "${BLUE}🎨 OpenRouter IntelliJ Plugin Icon Generator${NC}"

if ! command -v convert &> /dev/null; then
    echo -e "${RED}❌ Error: ImageMagick is not installed${NC}"
    echo -e "${YELLOW}Install with: brew install imagemagick${NC}"
    exit 1
fi

resize_image() {
    local source="$1"
    local target="$2"
    local size="$3"
    local description="$4"

    if [[ ! -f "$source" ]]; then
        echo -e "${RED}❌ Source not found: $source${NC}"
        return 1
    fi

    echo -e "${YELLOW}📐 Generating $description (${size}x${size})...${NC}"
    convert "$source" -resize "${size}x${size}" -quality 100 -strip "$target"

    if [[ -f "$target" ]]; then
        echo -e "${GREEN}   ✅ $target${NC}"
    else
        echo -e "${RED}   ❌ Failed: $target${NC}"
        return 1
    fi
}

# Success icon (16px only — the only size used by the plugin)
if [[ -f "$SUCCESS_SOURCE" ]]; then
    resize_image "$SUCCESS_SOURCE" "$ICONS_DIR/openrouter-plugin-success-16.png" 16 "Success icon"
else
    echo -e "${RED}❌ Success source not found: $SUCCESS_SOURCE${NC}"
fi

# Error icon (16px only — the only size used by the plugin)
if [[ -f "$ERROR_SOURCE" ]]; then
    resize_image "$ERROR_SOURCE" "$ICONS_DIR/openrouter-plugin-error-16.png" 16 "Error icon"
else
    echo -e "${RED}❌ Error source not found: $ERROR_SOURCE${NC}"
fi

echo -e "\n${GREEN}✅ Done.${NC}"
echo -e "${BLUE}Icon catalog:${NC}"
echo -e "  openrouter-logo.svg        — main logo, light theme (SVG, no generation needed)"
echo -e "  openrouter-logo_dark.svg   — main logo, dark theme  (SVG, no generation needed)"
echo -e "  openrouter-plugin-success-16.png — success status icon"
echo -e "  openrouter-plugin-error-16.png   — error status icon"
echo -e "  META-INF/pluginIcon.svg    — plugin marketplace icon (SVG, no generation needed)"
