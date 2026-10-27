#!/usr/bin/env bash
set -eo pipefail

export JAVA_HOME=~/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# Pinned to 3.9.16, the last Maven version with a passing CI. Builds with the then-latest 3.10.0 fail with "Invalid Collect Request".
M3_VERSION="3.9.16"
MVN_DIR="/home/jitpack/tools/apache-maven-${M3_VERSION}"
if [[ ! -x "$MVN_DIR/bin/mvn" ]]; then
  mkdir -p "$MVN_DIR"
  curl -sL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${M3_VERSION}/apache-maven-${M3_VERSION}-bin.tar.gz" | tar xz --strip-components=1 -C "$MVN_DIR"
fi
export PATH="$MVN_DIR/bin:$PATH"
mvn -V -B install -DskipTests --no-transfer-progress
