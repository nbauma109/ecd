#!/usr/bin/env sh

# This script is used for building the whole plugin.
#
# Usage: ./build.sh
# Example: ./build.sh

M3_VERSION="3.9.9"

mvn wrapper:wrapper -Dmaven="${M3_VERSION}" --no-transfer-progress
./mvnw clean verify --no-transfer-progress
