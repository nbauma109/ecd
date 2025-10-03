#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# update-eclipse-p2.sh
#
# Updates all Maven POM files in the repository to point to the latest
# Eclipse SimRel update site and delegates the project version bump to
# ./update-version.sh, but only if the computed version is strictly newer.
#
# Workflow:
#   1) Detect latest SimRel release (YYYY-MM) via eclipseide.org and the
#      Eclipse downloads page; validate the p2 repository exists.
#   2) If latest is not yet live, fall back once to the previous quarter.
#   3) Compute new version as YYYY.m.0 (month without leading zero).
#   4) Read current project version from the baseline POM.
#   5) If new version <= current version, exit without changes.
#   6) Otherwise:
#       - Update p2 repository URL and id (<id>eclipse-YYYY-MM</id>)
#         in all pom.xml files.
#       - Run ./update-version.sh YYYY.m.0 to update versions repo-wide.
#
# Usage:
#   ./update-eclipse-p2.sh                 # update all pom.xml files
#   ./update-eclipse-p2.sh path/to/pom.xml # update a single POM only (for URL/id);
#                                          # version update still runs repo-wide
# -----------------------------------------------------------------------------

set -euo pipefail

BASE="https://download.eclipse.org/releases"
UA="Mozilla/5.0 (EclipseP2Updater)"
changed_urls_or_ids=0

log() { printf '%s\n' "$*" >&2; }

# ---- sed helper (GNU/BSD portable) ----------------------------------------
sedi() { if sed --version >/dev/null 2>&1; then sed -i -E "$@"; else sed -i '' -E "$@"; fi; }

# ---- version compare: returns 0 if a<b, 1 if a==b, 2 if a>b ----------------
vercmp3() {
  local A="${1:-0}" B="${2:-0}"
  IFS='.' read -r a1 a2 a3 <<<"$A"
  IFS='.' read -r b1 b2 b3 <<<"$B"
  a1=$((10#${a1:-0})); a2=$((10#${a2:-0})); a3=$((10#${a3:-0}))
  b1=$((10#${b1:-0})); b2=$((10#${b2:-0})); b3=$((10#${b3:-0}))
  if   ((a1<b1)); then echo 0
  elif ((a1>b1)); then echo 2
  elif ((a2<b2)); then echo 0
  elif ((a2>b2)); then echo 2
  elif ((a3<b3)); then echo 0
  elif ((a3>b3)); then echo 2
  else echo 1
  fi
}

# ---- p2 repo existence check ----------------------------------------------
exists_release() {
  local rel="$1" url code
  for path in "compositeContent.jar" "compositeArtifacts.jar" "content.jar"; do
    url="${BASE}/${rel}/${path}"
    code="$(curl -fsSLI -A "$UA" -o /dev/null -w '%{http_code}' "$url" || true)"
    log "validate: HEAD $url -> HTTP $code"
    [[ "$code" == "200" ]] && return 0
  done
  return 1
}

# ---- previous quarter (single step) ---------------------------------------
prev_quarter() {
  local y="${1%-*}" m="${1#*-}"
  case "$m" in
    03) printf '%s-%s\n' "$((10#$y-1))" "12" ;;
    06) printf '%s-%s\n' "$y" "03" ;;
    09) printf '%s-%s\n' "$y" "06" ;;
    12) printf '%s-%s\n' "$y" "09" ;;
    *)
      if   ((10#$m <= 03)); then printf '%s-%s\n' "$((10#$y-1))" "12"
      elif ((10#$m <= 06)); then printf '%s-%s\n' "$y" "03"
      elif ((10#$m <= 09)); then printf '%s-%s\n' "$y" "06"
      else                     printf '%s-%s\n' "$y" "09"
      fi
      ;;
  esac
}

# ---- page scraping for candidate YYYY-MM ----------------------------------
detect_from_pages() {
  local cand html
  log "probe A: eclipseide.org"
  html="$(curl -fsSL -A "$UA" https://eclipseide.org/ 2>/dev/null || true)"
  if [[ -n "$html" ]]; then
    cand="$(printf '%s' "$html" | grep -Eo 'Download[[:space:]]+[0-9]{4}-[0-9]{2}' \
                         | grep -Eo '[0-9]{4}-[0-9]{2}' | head -n1 || true)"
    log "probe A: parsed -> '${cand:-<none>}'"
    [[ -n "$cand" ]] && echo "$cand" && return 0
  fi
  log "probe B: eclipse.org/downloads/packages/release/"
  cand="$(
    curl -fsSL -A "$UA" https://www.eclipse.org/downloads/packages/release/ 2>/dev/null \
      | grep -Eo 'release/[0-9]{4}-[0-9]{2}' \
      | grep -Eo '[0-9]{4}-[0-9]{2}' \
      | sort -u | sort -r | head -n1
  )"
  log "probe B: parsed -> '${cand:-<none>}'"
  [[ -n "$cand" ]] && echo "$cand" && return 0
  return 1
}

# ---- select final YYYY-MM with one-quarter fallback -----------------------
pick_release() {
  local cand="$1"
  if [[ -n "$cand" ]]; then
    log "candidate from pages: $cand"
    if exists_release "$cand"; then echo "$cand"; return 0; fi
    local prev; prev="$(prev_quarter "$cand")"
    log "fallback (one quarter): $prev"
    if exists_release "$prev"; then echo "$prev"; return 0; fi
  fi
  local now_y now_m q cur prev
  now_y="$(date +%Y)"; now_m="$(date +%m)"
  if   ((10#$now_m <= 03)); then q="03"
  elif ((10#$now_m <= 06)); then q="06"
  elif ((10#$now_m <= 09)); then q="09"
  else                           q="12"
  fi
  cur="${now_y}-${q}"
  log "no page candidate; calendar current: $cur"
  if exists_release "$cur"; then echo "$cur"; return 0; fi
  prev="$(prev_quarter "$cur")"
  log "calendar fallback (one quarter): $prev"
  if exists_release "$prev"; then echo "$prev"; return 0; fi
  return 1
}

# ---- POM helpers ----------------------------------------------------------
first_version_in_pom() {
  grep -Eo '<version>[^<]+</version>' "$1" 2>/dev/null | head -n1 | sed -E 's#</?version>##g' || true
}

update_one_pom_url_and_id() {
  local file="$1" latest="$2"
  local latest_url="${BASE}/${latest}/"
  local file_changed=0

  log "---- updating: $file"
  # URL
  local current_url
  current_url="$(grep -Eo 'https://download\.eclipse\.org/releases/[0-9]{4}-[0-9]{2}/' "$file" | head -n1 || true)"
  log "p2 in POM      : ${current_url:-<none>}"
  if [[ "${current_url:-}" != "$latest_url" ]]; then
    local esc_url; esc_url="$(printf '%s' "$latest_url" | sed 's/[\/&]/\\&/g')"
    sedi "s#https://download\.eclipse\.org/releases/[0-9]{4}-[0-9]{2}/#${esc_url}#g" "$file"
    echo "[$file] p2 URL updated -> $latest_url"
    file_changed=1
  fi
  # repository id
  local current_id new_id
  current_id="$(grep -Eo '<id>eclipse-[0-9]{4}-[0-9]{2}</id>' "$file" | head -n1 || true)"
  new_id="<id>eclipse-${latest}</id>"
  if [[ "${current_id:-}" != "$new_id" ]]; then
    local esc_id; esc_id="$(printf '%s' "$new_id" | sed 's/[\/&]/\\&/g')"
    sedi "s#<id>eclipse-[0-9]{4}-[0-9]{2}</id>#${esc_id}#g" "$file"
    echo "[$file] repository id updated -> eclipse-${latest}"
    file_changed=1
  fi
  if [[ $file_changed -eq 1 ]]; then changed_urls_or_ids=1; fi
}

# ==================== Main ====================

page_cand="$(detect_from_pages || true)"
latest="$(pick_release "$page_cand" || true)"
if [[ -z "${latest:-}" ]]; then
  log "RESULT: latest=<none>"
  echo "WARN: Could not determine latest Eclipse release; skipping updates."
  [[ -n "${GITHUB_OUTPUT:-}" ]] && echo "changed=false" >> "$GITHUB_OUTPUT"
  exit 0
fi

year="${latest%-*}"
month="${latest#*-}"
month_unpadded="$((10#$month))"
new_version="${year}.${month_unpadded}.0"

log "RESULT: latest=${latest}"
echo "New p2 URL     : ${BASE}/${latest}/"
echo "New version    : ${new_version}"

# Determine current project version
baseline_pom=""
if [[ $# -ge 1 && -f "${1:-}" ]]; then
  baseline_pom="$1"
elif [[ -f "pom.xml" ]]; then
  baseline_pom="pom.xml"
fi

current_version=""
if [[ -n "$baseline_pom" ]]; then
  current_version="$(first_version_in_pom "$baseline_pom")"
  log "Current version: ${current_version:-<none>} (from $baseline_pom)"
fi

if [[ -n "${current_version:-}" ]]; then
  case "$(vercmp3 "$new_version" "$current_version")" in
    0|1)
      echo "No update: computed version (${new_version}) is not newer than current (${current_version})."
      [[ -n "${GITHUB_OUTPUT:-}" ]] && echo "changed=false" >> "$GITHUB_OUTPUT"
      exit 0
      ;;
    2)
      : # proceed
      ;;
  esac
fi

# Update all pom.xml files for URL/id
declare -a files
if [[ $# -ge 1 && -f "${1:-}" ]]; then
  files=("$1")
else
  if command -v git >/dev/null 2>&1; then
    mapfile -t files < <(git ls-files '**/pom.xml' 'pom.xml' | grep -v '/target/' || true)
  else
    mapfile -t files < <(find . -type f -name 'pom.xml' -not -path '*/target/*')
  fi
fi

if [[ ${#files[@]} -eq 0 ]]; then
  echo "No pom.xml files found to update."
else
  for f in "${files[@]}"; do
    update_one_pom_url_and_id "$f" "$latest"
  done
fi

if [[ ! -x ./update-version.sh ]]; then
  echo "ERROR: ./update-version.sh not found or not executable." >&2
  exit 1
fi
echo "Running ./update-version.sh ${new_version}"
./update-version.sh "${new_version}"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "changed=true"
    echo "branch=update-eclipse-${latest}"
    echo "latest=${latest}"
    echo "new_url=${BASE}/${latest}/"
    echo "new_version=${new_version}"
  } >> "$GITHUB_OUTPUT"
fi

exit 0
