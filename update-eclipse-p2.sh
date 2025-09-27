#!/usr/bin/env bash
# Usage:
#   ./update-eclipse-p2.sh                 # update URL/id in all pom.xml, then run ./update-version.sh YYYY.m.0
#   ./update-eclipse-p2.sh path/to/pom.xml # limit URL/id updates to that POM (version still updated repo-wide)
#
# Strategy
# - Detect latest SimRel (YYYY-MM):
#     A) eclipseide.org banner  ->  B) downloads page
#   Validate via p2 metadata JARs; if not live, fall back ONCE to previous quarter.
# - For each POM:
#     * replace p2 URL -> https://download.eclipse.org/releases/YYYY-MM/
#     * replace repository id -> <id>eclipse-YYYY-MM</id>
# - Then delegate version bump to ./update-version.sh with UNPADDED month: YYYY.m.0

set -euo pipefail

BASE="https://download.eclipse.org/releases"
UA="Mozilla/5.0 (EclipseP2Updater)"
changed_urls_or_ids=0

log() { printf '%s\n' "$*" >&2; }

# ----- portable sed -i (GNU/BSD) -----
sedi() { if sed --version >/dev/null 2>&1; then sed -i -E "$@"; else sed -i '' -E "$@"; fi; }

# ----- validate SimRel exists (any metadata JAR returning 200) -----
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

# ----- previous quarter (one step only) -----
prev_quarter() {
  local y="${1%-*}" m="${1#*-}"
  case "$m" in
    03) printf '%s-%s\n' "$((10#$y-1))" "12" ;;
    06) printf '%s-%s\n' "$y" "03" ;;
    09) printf '%s-%s\n' "$y" "06" ;;
    12) printf '%s-%s\n' "$y" "09" ;;
    *)  # map to nearest quarter then step back once
        if   ((10#$m <= 03)); then printf '%s-%s\n' "$((10#$y-1))" "12"
        elif ((10#$m <= 06)); then printf '%s-%s\n' "$y" "03"
        elif ((10#$m <= 09)); then printf '%s-%s\n' "$y" "06"
        else                     printf '%s-%s\n' "$y" "09"
        fi
        ;;
  esac
}

# ----- detect candidate from pages -----
detect_from_pages() {
  local cand html
  log "probe A: GET https://eclipseide.org/ (banner 'Download YYYY-MM')"
  html="$(curl -fsSL -A "$UA" https://eclipseide.org/ 2>/dev/null || true)"
  if [[ -n "$html" ]]; then
    cand="$(printf '%s' "$html" | grep -Eo 'Download[[:space:]]+[0-9]{4}-[0-9]{2}' \
                         | grep -Eo '[0-9]{4}-[0-9]{2}' | head -n1 || true)"
    log "probe A: parsed -> '${cand:-<none>}'"
    [[ -n "$cand" ]] && echo "$cand" && return 0
  else
    log "probe A: empty response"
  fi

  log "probe B: GET https://www.eclipse.org/downloads/packages/release/"
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

# ----- choose final release with single-quarter fallback -----
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
  log "no page candidate; using calendar current: $cur"
  if exists_release "$cur"; then echo "$cur"; return 0; fi
  prev="$(prev_quarter "$cur")"
  log "calendar fallback (one quarter): $prev"
  if exists_release "$prev"; then echo "$prev"; return 0; fi

  return 1
}

# ----- mutate a single POM: URL + repo id -----
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
    echo "✅ [$file] p2 URL -> $latest_url"
    file_changed=1
  fi

  # repository id
  local current_id new_id
  current_id="$(grep -Eo '<id>eclipse-[0-9]{4}-[0-9]{2}</id>' "$file" | head -n1 || true)"
  new_id="<id>eclipse-${latest}</id>"
  if [[ "${current_id:-}" != "$new_id" ]]; then
    local esc_id; esc_id="$(printf '%s' "$new_id" | sed 's/[\/&]/\\&/g')"
    sedi "s#<id>eclipse-[0-9]{4}-[0-9]{2}</id>#${esc_id}#g" "$file"
    echo "✅ [$file] repository id -> eclipse-${latest}"
    file_changed=1
  fi

  if [[ $file_changed -eq 1 ]]; then changed_urls_or_ids=1; fi
}

# ==================== main ====================

# Detect latest (with logs)
page_cand="$(detect_from_pages || true)"
latest="$(pick_release "$page_cand" || true)"
if [[ -z "${latest:-}" ]]; then
  log "RESULT: latest=<none>"
  echo "WARN: Could not determine latest Eclipse release; skipping updates."
  # Succeed (do not fail CI)
  [[ -n "${GITHUB_OUTPUT:-}" ]] && echo "changed=false" >> "$GITHUB_OUTPUT"
  exit 0
fi

year="${latest%-*}"
month="${latest#*-}"
# Unpadded month for OSGi/Maven sync: e.g., 2025.9.0
month_unpadded="$((10#$month))"
new_version="${year}.${month_unpadded}.0"

log "RESULT: latest=${latest}"
echo "New p2 URL     : ${BASE}/${latest}/"
echo "New version    : ${new_version}"

# Collect POM list
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
  echo "ℹ️ No pom.xml files found to update."
else
  for f in "${files[@]}"; do
    update_one_pom_url_and_id "$f" "$latest"
  done
fi

# Delegate version bump to your repo script
if [[ ! -x ./update-version.sh ]]; then
  echo "ERROR: ./update-version.sh not found or not executable. Please ensure it exists at repo root and is +x." >&2
  exit 1
fi
echo "➡️  Running ./update-version.sh ${new_version}"
./update-version.sh "${new_version}"

# GitHub Actions outputs
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
