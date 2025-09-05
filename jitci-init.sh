#!/usr/bin/env bash
export JAVA_HOME=/home/jitpack/tools/jdk21
export M3_VERSION=$(curl -Ls -o /dev/null -w %{url_effective} https://github.com/apache/maven/releases/latest | sed 's,https://github.com/apache/maven/releases/tag/maven-,,g')
mvn wrapper:wrapper -Dmaven=${M3_VERSION} --no-transfer-progress
./mvnw -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies --no-transfer-progress
