#!/usr/bin/env bash
set -euo pipefail

export SDKMAN_DIR="$HOME/.sdkman"
export SDKMAN_CANDIDATES_API="${SDKMAN_CANDIDATES_API:-https://api.sdkman.io/2}"

if [[ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
  curl -s https://get.sdkman.io | bash
fi

set +u
source "$SDKMAN_DIR/bin/sdkman-init.sh"
set -u

mkdir -p "$SDKMAN_DIR/etc"
{ echo "sdkman_auto_answer=true"; echo "sdkman_selfupdate_enable=false"; } >> "$SDKMAN_DIR/etc/config"

CAND_ORA=$(sdk list java | awk '/[[:space:]]21(\.[0-9]+)*-oracle[[:space:]]/ {print $NF}' | sed 's/[^[:alnum:].-]//g' | sort -V | tail -n1)
if [[ -z "${CAND_ORA:-}" ]]; then
  echo "Oracle 21 not available via SDKMAN" >&2
  exit 1
fi

sdk install java "$CAND_ORA"
sdk use java "$CAND_ORA"
export JAVA_HOME="$SDKMAN_DIR/candidates/java/current"

M3_VERSION="$(curl -Ls -o /dev/null -w '%{url_effective}' https://github.com/apache/maven/releases/latest | sed 's#.*/maven-##')"
sdk install maven "$M3_VERSION"
sdk use maven "$M3_VERSION"

mvn -B -ntp de.qaware.maven:go-offline-maven-plugin:resolve-dependencies --no-transfer-progress
