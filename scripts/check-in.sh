#!/bin/bash
# Auto check-in script
# Usage: ./scripts/check-in.sh [commit message]

cd "$(dirname "$0")/.."

BRANCH=$(git rev-parse --abbrev-ref HEAD)
REMOTE=$(git config branch.${BRANCH}.remote 2>/dev/null || echo "origin")

if [ -z "$1" ]; then
    MSG="Auto check-in: $(date '+%Y-%m-%d %H:%M:%S')"
else
    MSG="$1"
fi

# Check for changes
if git diff --quiet && git diff --cached --quiet; then
    echo "No changes to commit"
    exit 0
fi

# Add all changes
git add -A

# Commit
git commit -m "$MSG"

# Get version from most recent tag or use V1.0001
VERSION=$(git describe --tags 2>/dev/null || echo "V1.0000")
NEXT_VERSION="V1.$(printf '%04d' $((${VERSION##V1.} + 1)))"
git tag -a "$NEXT_VERSION" -m "Version $NEXT_VERSION" 2>/dev/null || true

echo "Committed: $MSG"
echo "Current version: $NEXT_VERSION"

exit 0
