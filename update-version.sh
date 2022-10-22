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

mvn \
	org.eclipse.tycho:tycho-versions-plugin:2.7.4:set-version \
	-DnewVersion="$VERSION" -Dtycho.mode=maven

mvn \
	org.eclipse.tycho:tycho-versions-plugin:2.7.4:update-eclipse-metadata \
	-DnewVersion="$VERSION" -Dtycho.mode=maven
