#!/bin/bash

set -e

git config user.name github-actions
git config user.email github-actions@github.com
git remote set-url origin https://x-access-token:$GH_TOKEN@github.com/http4k/standards.git
LOCAL_VERSION=$(jq -r .http4k.version version.json)
git fetch --deepen=1 || true
if git rev-parse HEAD~1 >/dev/null 2>&1; then
    CHANGED_FILES=$(git diff --name-only HEAD~1 HEAD)
else
    CHANGED_FILES=$(git show --name-only --pretty=format: HEAD)
fi
if [[ "$CHANGED_FILES" != *version.json* ]]; then
    echo "Version did not change on this commit. Ignoring"
    echo "tag-created=false" >> $GITHUB_OUTPUT
    exit 0
fi
git tag -a "$LOCAL_VERSION" -m "Typeflows JVM version $LOCAL_VERSION"
git push origin "$LOCAL_VERSION"
# Output tag for repository dispatch
echo "tag=$LOCAL_VERSION" >> $GITHUB_OUTPUT
echo "tag-created=true" >> $GITHUB_OUTPUT
