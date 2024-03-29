#!/usr/bin/env bash
curl -vkL https://github.com/ibmruntimes/semeru17-binaries/releases/download/jdk-17.0.9%2B9_openj9-0.41.0/ibm-semeru-open-jdk_x64_linux_17.0.9_9_openj9-0.41.0.tar.gz -o $HOME/ibm-semeru-open-jdk_x64_linux_17.0.8.1_1_openj9-0.40.0.tar.gz
tar xzvf $HOME/ibm-semeru-open-jdk_x64_linux_17.0.8.1_1_openj9-0.40.0.tar.gz -C $HOME
export JAVA_HOME=$HOME/jdk-17.0.9+9
export M3_VERSION=$(curl -Ls -o /dev/null -w %{url_effective} https://github.com/apache/maven/releases/latest | sed 's,https://github.com/apache/maven/releases/tag/maven-,,g')
curl -vkL https://archive.apache.org/dist/maven/maven-3/${M3_VERSION}/binaries/apache-maven-${M3_VERSION}-bin.zip -o $HOME/apache-maven-${M3_VERSION}-bin.zip
unzip -o $HOME/apache-maven-${M3_VERSION}-bin.zip -d $HOME
$HOME/apache-maven-${M3_VERSION}/bin/mvn -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies