#!/usr/bin/env bash
./mvnw -B deploy -DskipTests -Dfindbugs.skip=true -Dpmd.skip=true -Dcheckstyle.skip=true -Dsigning.disabled=true -DaltDeploymentRepository=jitci::default::file:///home/jitpack/deploy --no-transfer-progress
