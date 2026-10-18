#!/bin/bash

set -e

./gradlew publish --no-configuration-cache --info -Psign=true -PreleaseVersion="$RELEASE_VERSION" -PsigningKey="$SIGNING_KEY" -PsigningPassword="$SIGNING_PASSWORD"
