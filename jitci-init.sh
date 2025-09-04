#!/usr/bin/env bash
set -eo pipefail

if [[ -x "/home/jitpack/tools/jdk21/bin/java" ]]; then
  export JAVA_HOME="/home/jitpack/tools/jdk21"
  export PATH="$JAVA_HOME/bin:$PATH"
  java -version
else
  export SDKMAN_DIR="$HOME/.sdkman"
  export SDKMAN_CANDIDATES_API="${SDKMAN_CANDIDATES_API:-https://api.sdkman.io/2}"
  export SDKMAN_OFFLINE_MODE="${SDKMAN_OFFLINE_MODE:-false}"
  if [[ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
    curl -s https://get.sdkman.io | bash
  fi
  source "$SDKMAN_DIR/bin/sdkman-init.sh"
  mkdir -p "$SDKMAN_DIR/etc"
  { echo "sdkman_auto_answer=true"; echo "sdkman_selfupdate_enable=false"; } >> "$SDKMAN_DIR/etc/config"
  CAND_ORA=$(sdk list java | awk '/[[:space:]]21(\.[0-9]+)*-oracle[[:space:]]/ {print $NF}' | sed 's/[^[:alnum:].-]//g' | sort -V | tail -n1)
  if [[ -z "${CAND_ORA:-}" ]]; then
    echo "Oracle 21 not available via SDKMAN on this runner."
    echo "Available Java 21 candidates:"
    sdk list java | awk '/(^|[[:space:]])21([[:space:]\.].*-)[[:alnum:]-]+/ {print $NF}' | sed 's/[^[:alnum:].-]//g' | sort -Vu
    exit 1
  fi
  sdk install java "$CAND_ORA"
  sdk use java "$CAND_ORA"
  export JAVA_HOME="$SDKMAN_DIR/candidates/java/current"
fi

if command -v mvn >/dev/null 2>&1; then
  :
else
  export SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
  if [[ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
    curl -s https://get.sdkman.io | bash
  fi
  source "$SDKMAN_DIR/bin/sdkman-init.sh"
  mkdir -p "$SDKMAN_DIR/etc"
  { echo "sdkman_auto_answer=true"; echo "sdkman_selfupdate_enable=false"; } >> "$SDKMAN_DIR/etc/config"
  M3_VERSION="$(curl -Ls -o /dev/null -w '%{url_effective}' https://github.com/apache/maven/releases/latest | sed 's#.*/maven-##')"
  sdk install maven "$M3_VERSION"
  sdk use maven "$M3_VERSION"
fi

mvn -B -ntp de.qaware.maven:go-offline-maven-plugin:resolve-dependencies --no-transfer-progress
