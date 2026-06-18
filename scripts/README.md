# Icon Generation Scripts

## Overview

The `generate-icons.sh` script generates the two PNG status icons from high-resolution source images.
The main logo uses SVG files and requires no generation.

## Icon Catalog

| File | Purpose | How it's produced |
|------|---------|-------------------|
| `icons/openrouter-logo.svg` | Main logo — light theme | Maintained by hand |
| `icons/openrouter-logo_dark.svg` | Main logo — dark theme | Maintained by hand |
| `icons/openrouter-plugin-success-16.png` | Success status icon (16×16) | `generate-icons.sh` |
| `icons/openrouter-plugin-error-16.png` | Error status icon (16×16) | `generate-icons.sh` |
| `META-INF/pluginIcon.svg` | Plugin marketplace icon | Maintained by hand |

## Usage

```bash
./scripts/generate-icons.sh
```

Requires [ImageMagick](https://imagemagick.org/):

```bash
brew install imagemagick
```

## Source Images

Place high-resolution source images in `src/main/resources/icons/` before running the script:

- `openrouter-plugin-success.png` — source for the success icon
- `openrouter-plugin-error.png` — source for the error icon
