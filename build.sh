#!/usr/bin/env sh

# This script is used for building the whole plugin.
#
# Usage: ./build.sh
# Example: ./build.sh

M3_VERSION=$(curl -Ls -o /dev/null -w '%{url_effective}' https://github.com/apache/maven/releases/latest | sed 's,https://github.com/apache/maven/releases/tag/maven-,,g')

if [ -z "${M3_VERSION}" ] || [ "${M3_VERSION}" = "https://github.com/apache/maven/releases/latest" ]; then
  echo "Could not resolve the latest Maven version from GitHub." >&2
  exit 1
fi

mvn wrapper:wrapper -Dmaven="${M3_VERSION}" --no-transfer-progress
./mvnw clean verify --no-transfer-progress
