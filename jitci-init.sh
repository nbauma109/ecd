#!/usr/bin/env bash
set -eo pipefail

. ~/.sdkman/bin/sdkman-init.sh
sdk install java 21.0.10-oracle
sdk use java 21.0.10-oracle
export JAVA_HOME=~/.sdkman/candidates/java/current

echo "JAVA_HOME=$JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

M3_VERSION="$(curl -Ls -o /dev/null -w '%{url_effective}' https://github.com/apache/maven/releases/latest | sed 's#.*/maven-##')"
MVN_DIR="/home/jitpack/tools/apache-maven-${M3_VERSION}"
if [[ ! -x "$MVN_DIR/bin/mvn" ]]; then
  mkdir -p "$MVN_DIR"
  curl -sL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${M3_VERSION}/apache-maven-${M3_VERSION}-bin.tar.gz" | tar xz --strip-components=1 -C "$MVN_DIR"
fi
export PATH="$MVN_DIR/bin:$PATH"

mvn -V -B -ntp de.qaware.maven:go-offline-maven-plugin:resolve-dependencies --no-transfer-progress
