# OpenRouter IntelliJ Plugin - Scripts

This directory contains utility scripts for the OpenRouter IntelliJ Plugin development.

## 🎨 Icon Generation Script

### `generate-icons.sh`

Automatically generates all required icon sizes from high-resolution source images.

#### Prerequisites

- **ImageMagick** must be installed:
  ```bash
  # macOS
  brew install imagemagick
  
  # Ubuntu/Debian
  sudo apt-get install imagemagick
  
  # Windows
  # Download from https://imagemagick.org/script/download.php
  ```

#### Usage

```bash
# Make script executable (first time only)
chmod +x scripts/generate-icons.sh

# Run the script
./scripts/generate-icons.sh
```

#### What it does

The script automatically generates the following icon sizes:

##### 📱 Status Bar Icons
- `*-13.png` - Status bar icons (13x13px)
- `*-16.png` - Tool window and general UI icons (16x16px)

##### 🖥️ Medium Icons  
- `*-20.png` - Medium UI elements (20x20px)
- `*-24.png` - Large UI elements (24x24px)

##### 📦 Plugin Manager Icons
- `*-40.png` - Plugin manager display (40x40px)
- `*-80.png` - Plugin manager @2x display (80x80px)
- `META-INF/pluginIcon.png` - Standard plugin icon (40x40px)
- `META-INF/pluginIcon@2x.png` - Retina plugin icon (80x80px)

#### Source Images

The script processes these high-resolution source images:

- `openrouter-plugin-logo-solid.png` - Main OpenRouter logo
- `openrouter-plugin-success.png` - Success status icon
- `openrouter-plugin-error.png` - Error status icon

#### Generated Icon Sets

1. **OpenRouter Logo Icons**
   - `openrouter-13.png` through `openrouter-80.png`
   - Used for: Tool windows, general branding

2. **Success Status Icons**
   - `openrouter-plugin-success-13.png` through `openrouter-plugin-success-80.png`
   - Used for: Connection success states

3. **Error Status Icons**
   - `openrouter-plugin-error-13.png` through `openrouter-plugin-error-80.png`
   - Used for: Connection error states

4. **Plugin Manager Icons**
   - `META-INF/pluginIcon.png` and `META-INF/pluginIcon@2x.png`
   - Used for: IntelliJ plugins list display

#### Code Integration

After running the script, update your icon references in the code:

```kotlin
// OpenRouterIcons.kt
@JvmField
val SUCCESS: Icon = IconLoader.getIcon("/icons/openrouter-plugin-success-16.png", OpenRouterIcons::class.java)

@JvmField  
val ERROR: Icon = IconLoader.getIcon("/icons/openrouter-plugin-error-16.png", OpenRouterIcons::class.java)
```

#### File Size Optimization

The script automatically:
- Strips metadata from images
- Uses high-quality compression
- Generates optimal file sizes for each use case

#### Troubleshooting

**ImageMagick not found:**
```bash
# Check if ImageMagick is installed
convert --version
# or
magick --version
```

**Permission denied:**
```bash
chmod +x scripts/generate-icons.sh
```

**Source images not found:**
- Ensure high-resolution source images are in `src/main/resources/icons/`
- Check file names match the script expectations

#### Future Enhancements

To add new icon types:

1. Add source image to `src/main/resources/icons/`
2. Update the script to include new icon generation
3. Add corresponding entries in `OpenRouterIcons.kt`

#### Output Example

```
🎨 OpenRouter IntelliJ Plugin Icon Generator
=============================================
✅ ImageMagick found

🚀 Starting icon generation...

🔄 Generating OpenRouter logo icons...
📐 Generating Status bar icon (13x13)...
   ✅ Created: .../openrouter-13.png
📐 Generating Tool window icon (16x16)...
   ✅ Created: .../openrouter-16.png
...

🎉 Icon generation complete!
Generated icons:
  📁 Status bar icons: 13px, 16px
  📁 Medium icons: 20px, 24px
  📁 Plugin manager icons: 40px, 80px
  📁 Plugin META-INF icons: pluginIcon.png, pluginIcon@2x.png

✅ All icons generated successfully!
```

---

## 📝 Adding New Scripts

When adding new utility scripts:

1. Make them executable: `chmod +x scripts/new-script.sh`
2. Add documentation to this README
3. Follow the existing naming conventions
4. Include error handling and user feedback
