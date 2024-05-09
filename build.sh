#!/usr/bin/env sh

# This script is used for building the whole plugin.
#
# Usage: ./build.sh
# Example: ./build.sh


mvn clean verify -Dsigning.disabled=true --no-transfer-progress
