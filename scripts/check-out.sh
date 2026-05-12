#!/bin/bash
# Auto check-out script (pull latest changes)
# Usage: ./scripts/check-out.sh

cd "$(dirname "$0")/.."

BRANCH=$(git rev-parse --abbrev-ref HEAD)
REMOTE=$(git config branch.${BRANCH}.remote 2>/dev/null || echo "origin")

# Save current state if there are uncommitted changes
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Uncommitted changes detected. Stashing..."
    git stash push -m "Auto-stash before check-out: $(date)"
fi

# Fetch and pull
echo "Fetching from remote..."
git fetch --all 2>/dev/null

echo "Pulling latest changes..."
git pull --rebase || git pull

# Apply stashed changes if any
if git stash list | grep -q "Auto-stash"; then
    echo "Restoring stashed changes..."
    git stash pop
fi

echo "Check-out complete. Current version: $(git describe --tags 2>/dev/null || echo 'untagged')"

exit 0
