#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="$HOME/.sdkman/candidates/java/current"

mvn -B deploy -DskipTests -Dfindbugs.skip=true -Dpmd.skip=true -Dcheckstyle.skip=true -Dsigning.disabled=true -DaltDeploymentRepository=jitci::default::file:///home/jitpack/deploy --no-transfer-progress
