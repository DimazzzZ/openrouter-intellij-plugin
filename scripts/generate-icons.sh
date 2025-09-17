#!/bin/bash

# OpenRouter IntelliJ Plugin - Icon Generation Script
# This script automatically generates all required icon sizes from source images

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ICONS_DIR="$PROJECT_ROOT/src/main/resources/icons"
META_INF_DIR="$PROJECT_ROOT/src/main/resources/META-INF"

# Source images (high resolution)
LOGO_SOURCE="$ICONS_DIR/openrouter-plugin-logo-solid.png"
SUCCESS_SOURCE="$ICONS_DIR/openrouter-plugin-success.png"
ERROR_SOURCE="$ICONS_DIR/openrouter-plugin-error.png"

echo -e "${BLUE}🎨 OpenRouter IntelliJ Plugin Icon Generator${NC}"
echo -e "${BLUE}=============================================${NC}"

# Check if ImageMagick is installed
if ! command -v convert &> /dev/null; then
    echo -e "${RED}❌ Error: ImageMagick is not installed${NC}"
    echo -e "${YELLOW}Please install ImageMagick:${NC}"
    echo -e "  macOS: ${GREEN}brew install imagemagick${NC}"
    echo -e "  Ubuntu: ${GREEN}sudo apt-get install imagemagick${NC}"
    echo -e "  Windows: Download from https://imagemagick.org/script/download.php"
    exit 1
fi

echo -e "${GREEN}✅ ImageMagick found${NC}"

# Function to resize image with high quality
resize_image() {
    local source="$1"
    local target="$2"
    local size="$3"
    local description="$4"
    
    if [[ ! -f "$source" ]]; then
        echo -e "${RED}❌ Source image not found: $source${NC}"
        return 1
    fi
    
    echo -e "${YELLOW}📐 Generating $description (${size}x${size})...${NC}"
    
    # Create target directory if it doesn't exist
    mkdir -p "$(dirname "$target")"
    
    # Resize with high quality settings
    convert "$source" \
        -resize "${size}x${size}" \
        -quality 100 \
        -strip \
        "$target"
    
    if [[ -f "$target" ]]; then
        echo -e "${GREEN}   ✅ Created: $target${NC}"
    else
        echo -e "${RED}   ❌ Failed to create: $target${NC}"
        return 1
    fi
}

# Function to generate all sizes for a specific icon type
generate_icon_set() {
    local source="$1"
    local base_name="$2"
    local description="$3"
    
    echo -e "\n${BLUE}🔄 Generating $description icons...${NC}"
    
    # Status bar icons (small)
    resize_image "$source" "$ICONS_DIR/${base_name}-13.png" 13 "Status bar icon"
    resize_image "$source" "$ICONS_DIR/${base_name}-16.png" 16 "Tool window icon"
    
    # Medium icons
    resize_image "$source" "$ICONS_DIR/${base_name}-20.png" 20 "Medium icon"
    resize_image "$source" "$ICONS_DIR/${base_name}-24.png" 24 "Large icon"
    
    # Plugin icons (for plugin manager)
    resize_image "$source" "$ICONS_DIR/${base_name}-40.png" 40 "Plugin manager icon"
    resize_image "$source" "$ICONS_DIR/${base_name}-80.png" 80 "Plugin manager icon @2x"
}

# Main icon generation
echo -e "\n${BLUE}🚀 Starting icon generation...${NC}"

# 1. Generate main OpenRouter logo icons
if [[ -f "$LOGO_SOURCE" ]]; then
    generate_icon_set "$LOGO_SOURCE" "openrouter" "OpenRouter logo"
    
    # Special plugin icons for META-INF
    echo -e "\n${BLUE}🔄 Generating plugin manager icons...${NC}"
    resize_image "$LOGO_SOURCE" "$META_INF_DIR/pluginIcon.png" 40 "Plugin manager icon"
    resize_image "$LOGO_SOURCE" "$META_INF_DIR/pluginIcon@2x.png" 80 "Plugin manager icon @2x"
else
    echo -e "${RED}❌ Logo source not found: $LOGO_SOURCE${NC}"
fi

# 2. Generate success icons
if [[ -f "$SUCCESS_SOURCE" ]]; then
    generate_icon_set "$SUCCESS_SOURCE" "openrouter-plugin-success" "Success status"
else
    echo -e "${RED}❌ Success icon source not found: $SUCCESS_SOURCE${NC}"
fi

# 3. Generate error icons  
if [[ -f "$ERROR_SOURCE" ]]; then
    generate_icon_set "$ERROR_SOURCE" "openrouter-plugin-error" "Error status"
else
    echo -e "${RED}❌ Error icon source not found: $ERROR_SOURCE${NC}"
fi

# 4. Generate additional utility icons if needed
echo -e "\n${BLUE}🔄 Generating utility icons...${NC}"

# Create a simple refresh icon from the logo
if [[ -f "$LOGO_SOURCE" ]]; then
    resize_image "$LOGO_SOURCE" "$ICONS_DIR/openrouter-refresh-16.png" 16 "Refresh icon"
fi

# Summary
echo -e "\n${GREEN}🎉 Icon generation complete!${NC}"
echo -e "${BLUE}Generated icons:${NC}"
echo -e "  📁 Status bar icons: 13px, 16px"
echo -e "  📁 Medium icons: 20px, 24px" 
echo -e "  📁 Plugin manager icons: 40px, 80px"
echo -e "  📁 Plugin META-INF icons: pluginIcon.png, pluginIcon@2x.png"

echo -e "\n${YELLOW}💡 Usage in code:${NC}"
echo -e "  Status bar: ${GREEN}openrouter-plugin-success-16.png${NC}"
echo -e "  Tool window: ${GREEN}openrouter-16.png${NC}"
echo -e "  Plugin manager: ${GREEN}META-INF/pluginIcon.png${NC}"

echo -e "\n${GREEN}✅ All icons generated successfully!${NC}"
