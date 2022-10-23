#!/usr/bin/env bash
curl -vkL https://github.com/ibmruntimes/semeru17-binaries/releases/download/jdk-17.0.2%2B8_openj9-0.30.0/ibm-semeru-open-jdk_x64_linux_17.0.2_8_openj9-0.30.0.tar.gz -o $HOME/ibm-semeru-open-jdk_x64_linux_17.0.2_8_openj9-0.30.0.tar.gz
tar xzvf $HOME/ibm-semeru-open-jdk_x64_linux_17.0.2_8_openj9-0.30.0.tar.gz -C $HOME
export JAVA_HOME=$HOME/jdk-17.0.2+8
export M3_VERSION=3.8.6
curl -vkL https://archive.apache.org/dist/maven/maven-3/${M3_VERSION}/binaries/apache-maven-${M3_VERSION}-bin.zip -o $HOME/apache-maven-${M3_VERSION}-bin.zip
unzip -o $HOME/apache-maven-${M3_VERSION}-bin.zip -d $HOME
$HOME/apache-maven-${M3_VERSION}/bin/mvn -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies