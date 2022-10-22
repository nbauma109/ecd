#!/usr/bin/env bash
export M3_VERSION=3.8.6
export JAVA_HOME=$HOME/jdk-17.0.2+8
$HOME/apache-maven-${M3_VERSION}/bin/mvn -B deploy -DskipTests -Dfindbugs.skip=true -Dpmd.skip=true -Dcheckstyle.skip=true -DaltDeploymentRepository=jitci::default::file:///home/jitpack/deploy