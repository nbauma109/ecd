#!/usr/bin/env bash
set -eo pipefail

export SDKMAN_DIR="$HOME/.sdkman"
export SDKMAN_CANDIDATES_API="${SDKMAN_CANDIDATES_API:-https://api.sdkman.io/2}"
export SDKMAN_OFFLINE_MODE="${SDKMAN_OFFLINE_MODE:-false}"
export LC_ALL=C

if [[ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
  curl -s https://get.sdkman.io | bash
fi

source "$SDKMAN_DIR/bin/sdkman-init.sh"
mkdir -p "$SDKMAN_DIR/etc"
{ echo "sdkman_auto_answer=true"; echo "sdkman_selfupdate_enable=false"; } >> "$SDKMAN_DIR/etc/config"

ALL_21=$(sdk list java | awk '/(^|[[:space:]])21([[:space:]\.].*-)[[:alnum:]-]+/ {print $NF}' | sed 's/[^[:alnum:].-]//g' | sort -Vu || true)
CAND_ORA=$(printf '%s\n' "$ALL_21" | awk '/-oracle$/ {print}' | sort -V | tail -n1 || true)

if [[ -z "${CAND_ORA:-}" ]]; then
  echo "Oracle 21 not available via SDKMAN on this runner." >&2
  echo "Available Java 21 candidates:" >&2
  printf '%s\n' "$ALL_21" >&2
  exit 1
fi

sdk install java "$CAND_ORA"
sdk use java "$CAND_ORA"
export JAVA_HOME="$SDKMAN_DIR/candidates/java/current"

M3_VERSION="$(curl -Ls -o /dev/null -w '%{url_effective}' https://github.com/apache/maven/releases/latest | sed 's#.*/maven-##')"
sdk install maven "$M3_VERSION"
sdk use maven "$M3_VERSION"

mvn -B -ntp de.qaware.maven:go-offline-maven-plugin:resolve-dependencies --no-transfer-progress
