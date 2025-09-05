#!/usr/bin/env bash
set -eo pipefail

export JAVA_HOME="/home/jitpack/tools/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"

java -version
mvn -version

mvn -B install -DskipTests -Dsigning.disabled=true --no-transfer-progress
