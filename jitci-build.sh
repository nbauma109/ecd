#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="$HOME/.sdkman/candidates/java/current"

mvn -B install -DskipTests -Dsigning.disabled=true --no-transfer-progress
