#!/bin/bash

set -e

{
    echo "Changelog:"
    TAG=$(echo "refs/tags/$RELEASE_VERSION" | sed "s/.*tags\///g")
    # Find the line with ### version and get content until next ### or end
    awk "/^### $TAG/{flag=1; next} /^### / && flag{flag=0} flag" CHANGELOG.md | sed '/^$/d'
} > NOTE.md
