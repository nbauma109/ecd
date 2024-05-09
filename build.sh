#!/usr/bin/env sh

# This script is used for building the whole plugin.
#
# Usage: ./build.sh
# Example: ./build.sh


mvn wrapper:wrapper -Dmaven=3.9.6 --no-transfer-progress
./mvnw clean verify -Dsigning.disabled=true --no-transfer-progress
