#!/bin/bash

# Script to update plugin version across all files
# Usage: ./scripts/update-version.sh <new-version>

set -e

if [ $# -eq 0 ]; then
    echo "Usage: $0 <new-version>"
    echo "Example: $0 1.2.0"
    exit 1
fi

NEW_VERSION="$1"

# Validate version format (basic semver check)
if ! [[ $NEW_VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.-]+)?$ ]]; then
    echo "Error: Version must be in semver format (e.g., 1.2.0 or 1.2.0-beta.1)"
    exit 1
fi

echo "Updating plugin version to $NEW_VERSION..."

# Update gradle.properties
echo "Updating gradle.properties..."
sed -i.bak "s/^pluginVersion = .*/pluginVersion = $NEW_VERSION/" gradle.properties

# Update CHANGELOG.md if it exists
if [ -f "CHANGELOG.md" ]; then
    echo "Updating CHANGELOG.md..."
    # Add new version entry at the top (after the header)
    TODAY=$(date +%Y-%m-%d)
    sed -i.bak "/^# Changelog/a\\
\\
## [$NEW_VERSION] - $TODAY\\
\\
### Added\\
- \\
\\
### Changed\\
- \\
\\
### Fixed\\
- \\
" CHANGELOG.md
fi

# Update README.md version references if any
if [ -f "README.md" ]; then
    echo "Checking README.md for version references..."
    # This is optional - only update if there are explicit version references
    if grep -q "version.*[0-9]\+\.[0-9]\+\.[0-9]\+" README.md; then
        echo "Found version references in README.md - please update manually if needed"
    fi
fi

# Update DEVELOPMENT.md version references if any
if [ -f "DEVELOPMENT.md" ]; then
    echo "Updating DEVELOPMENT.md..."
    sed -i.bak "s/openrouter-intellij-plugin-[0-9]\+\.[0-9]\+\.[0-9]\+\.zip/openrouter-intellij-plugin-$NEW_VERSION.zip/g" DEVELOPMENT.md
fi

# Clean up backup files
rm -f gradle.properties.bak
rm -f CHANGELOG.md.bak 2>/dev/null || true
rm -f README.md.bak 2>/dev/null || true
rm -f DEVELOPMENT.md.bak 2>/dev/null || true

echo "Version updated successfully to $NEW_VERSION"
echo ""
echo "Files updated:"
echo "- gradle.properties"
echo "- CHANGELOG.md (if exists)"
echo "- DEVELOPMENT.md (if exists)"
echo ""
echo "Next steps:"
echo "1. Review the changes: git diff"
echo "2. Update CHANGELOG.md with actual changes"
echo "3. Test the build: ./gradlew build"
echo "4. Commit the changes: git add . && git commit -m 'Bump version to $NEW_VERSION'"
echo "5. Create a tag: git tag v$NEW_VERSION"
