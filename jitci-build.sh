#!/usr/bin/env bash
set -eo pipefail

export JAVA_HOME="/home/jitpack/tools/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"

java -version

export SDKMAN_DIR="$HOME/.sdkman"
export SDKMAN_OFFLINE_MODE="${SDKMAN_OFFLINE_MODE:-false}"

if [[ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
  curl -s https://get.sdkman.io | bash
fi
source "$SDKMAN_DIR/bin/sdkman-init.sh"
mkdir -p "$SDKMAN_DIR/etc"
{ echo "sdkman_auto_answer=true"; echo "sdkman_selfupdate_enable=false"; } >> "$SDKMAN_DIR/etc/config"

M3_VERSION="$(curl -Ls -o /dev/null -w '%{url_effective}' https://github.com/apache/maven/releases/latest | sed 's#.*/maven-##')"
sdk install maven "$M3_VERSION"
sdk use maven "$M3_VERSION"

mvn -version

mvn -B -ntp de.qaware.maven:go-offline-maven-plugin:resolve-dependencies --no-transfer-progress
