#!/usr/bin/env bash
export M3_VERSION=$(curl -Ls -o /dev/null -w %{url_effective} https://github.com/apache/maven/releases/latest | sed 's,https://github.com/apache/maven/releases/tag/maven-,,g')
mvn wrapper:wrapper -Dmaven=${M3_VERSION} --no-transfer-progress
./mvnw -B deploy -DskipTests -Dfindbugs.skip=true -Dpmd.skip=true -Dcheckstyle.skip=true -Dsigning.disabled=true -DaltDeploymentRepository=jitci::default::file:///home/jitpack/deploy --no-transfer-progress
