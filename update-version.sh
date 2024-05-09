#!/usr/bin/env sh

# This script is used for updating the version of the whole plugin.
#
# Usage: ./update-version.sh VERSION
# Example: ./update-version.sh 3.1.0-SNAPSHOT

# Note that automatically updating the version only works when the versions
# in the files have not been manually edited and actually match the current
# version.

VERSION="$*"

if [ -z "$VERSION" ]; then
	echo "update-version.sh VERSION"
	exit 1
fi

M3_VERSION=$(curl -Ls -o /dev/null -w %{url_effective} https://github.com/apache/maven/releases/latest | sed 's,https://github.com/apache/maven/releases/tag/maven-,,g')

# Extract the Tycho version from pom.xml
TYCHO_VERSION=$(grep "<tycho.version>" pom.xml | sed 's/.*<tycho.version>\(.*\)<\/tycho.version>.*/\1/')

# Use the extracted Tycho version and maven wrapper
mvn wrapper:wrapper -Dmaven=${M3_VERSION} --no-transfer-progress
./mvnw \
	org.eclipse.tycho:tycho-versions-plugin:${TYCHO_VERSION}:set-version \
	-DnewVersion="$VERSION" -Dtycho.mode=maven --no-transfer-progress

./mvnw \
	org.eclipse.tycho:tycho-versions-plugin:${TYCHO_VERSION}:update-eclipse-metadata \
	-DnewVersion="$VERSION" -Dtycho.mode=maven --no-transfer-progress
