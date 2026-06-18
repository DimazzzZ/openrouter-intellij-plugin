# Icon Scripts

## Overview

All plugin icons are pure SVG files — no PNG generation or ImageMagick is required.
The `generate-icons.sh` script now serves as a catalog reference only.

## Icon Catalog

| File | Purpose | How it's produced |
|------|---------|-------------------|
| `icons/openrouter-logo.svg` | Main logo — light theme | Maintained by hand |
| `icons/openrouter-logo_dark.svg` | Main logo — dark theme | Maintained by hand |
| `icons/openrouter-statusbar.svg` | Status-bar base icon — light theme (16×16) | Maintained by hand |
| `icons/openrouter-statusbar_dark.svg` | Status-bar base icon — dark theme (16×16) | Maintained by hand |
| `icons/openrouter-badge-ok.svg` | Green ✓ badge overlay (8×8) | Maintained by hand |
| `icons/openrouter-badge-error.svg` | Red × badge overlay (8×8) | Maintained by hand |
| `icons/openrouter-toolwindow.svg` | Tool-window button — light theme (16×16) | Maintained by hand |
| `icons/openrouter-toolwindow_dark.svg` | Tool-window button — dark theme (16×16) | Maintained by hand |
| `icons/openrouter-toolwindow@20x20.svg` | Tool-window button — light HiDPI (20×20) | Maintained by hand |
| `icons/openrouter-toolwindow@20x20_dark.svg` | Tool-window button — dark HiDPI (20×20) | Maintained by hand |
| `META-INF/pluginIcon.svg` | Plugin Marketplace icon | Maintained by hand |

## How the status-bar icon is composed at runtime

The status-bar widget icon is assembled by `OpenRouterIcons` using IntelliJ's `LayeredIcon`:

```
┌────────────────┐
│                │
│  openrouter-   │
│  statusbar.svg │  ← 16×16 base (theme-aware)
│          ┌───┐ │
│          │ ✓ │ │  ← openrouter-badge-ok.svg (8×8, offset 8,8)
│          └───┘ │
└────────────────┘
```

- `READY` state → green ✓ badge (`openrouter-badge-ok.svg`)
- `ERROR` / `NOT_CONFIGURED` / `OFFLINE` states → red × badge (`openrouter-badge-error.svg`)
- `CONNECTING` state → JetBrains built-in spinner

## Usage

```bash
./scripts/generate-icons.sh
```

Prints the icon catalog. No external tools required.
