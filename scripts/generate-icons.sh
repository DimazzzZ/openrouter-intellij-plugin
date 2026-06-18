#!/bin/bash

# OpenRouter IntelliJ Plugin - Icon Generation Script
#
# All status icons are now pure SVG and require no generation:
#   openrouter-statusbar.svg / openrouter-statusbar_dark.svg  — status-bar base (16×16)
#   openrouter-badge-ok.svg                                   — green ✓ badge (8×8)
#   openrouter-badge-error.svg                                — red × badge (8×8)
#   openrouter-logo.svg / openrouter-logo_dark.svg            — main logo
#   openrouter-toolwindow*.svg                                — tool-window button family
#   META-INF/pluginIcon.svg                                   — Marketplace icon
#
# This script is kept for reference only. No PNG generation is needed.

GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}🎨 OpenRouter IntelliJ Plugin Icon Catalog${NC}"
echo ""
echo -e "${GREEN}All icons are vector SVGs — no generation step required.${NC}"
echo ""
echo -e "${BLUE}Icon catalog:${NC}"
echo -e "  openrouter-logo.svg              — main logo, light theme"
echo -e "  openrouter-logo_dark.svg         — main logo, dark theme"
echo -e "  openrouter-statusbar.svg         — status-bar base icon, light theme (16×16)"
echo -e "  openrouter-statusbar_dark.svg    — status-bar base icon, dark theme  (16×16)"
echo -e "  openrouter-badge-ok.svg          — green ✓ badge (8×8)"
echo -e "  openrouter-badge-error.svg       — red × badge (8×8)"
echo -e "  openrouter-toolwindow.svg        — tool-window button, light theme (16×16)"
echo -e "  openrouter-toolwindow_dark.svg   — tool-window button, dark theme  (16×16)"
echo -e "  openrouter-toolwindow@20x20.svg  — tool-window button, light HiDPI (20×20)"
echo -e "  openrouter-toolwindow@20x20_dark.svg — tool-window button, dark HiDPI (20×20)"
echo -e "  META-INF/pluginIcon.svg          — plugin Marketplace icon"
