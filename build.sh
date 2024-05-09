#!/usr/bin/env sh

# This script is used for building the whole plugin.
#
# Usage: ./build.sh
# Example: ./build.sh

M3_VERSION=$(curl -Ls -o /dev/null -w %{url_effective} https://github.com/apache/maven/releases/latest | sed 's,https://github.com/apache/maven/releases/tag/maven-,,g')

mvn wrapper:wrapper -Dmaven=${M3_VERSION} --no-transfer-progress
./mvnw clean verify -Dsigning.disabled=true -DskipTests --no-transfer-progress
