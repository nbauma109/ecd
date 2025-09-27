#!/usr/bin/env bash
# Usage: ./update-eclipse-p2.sh [pom-path]
# Detect latest SimRel (YYYY-MM) with clear logging:
#   A) eclipseide.org banner
#   B) downloads page
#   validate by probing the p2 repo (jar metadata)
# If candidate not available, fall back ONCE to previous quarter only.
# Then update p2 URL and FIRST <version>...</version> to YYYY.MM.0

set -euo pipefail

POM="${1:-pom.xml}"
[[ -f "$POM" ]] || { echo "ERROR: Cannot find $POM" >&2; exit 1; }

BASE="https://download.eclipse.org/releases"
UA="Mozilla/5.0 (EclipseP2Updater)"
changed=0

log() { printf '%s\n' "$*" >&2; }

# ---- helpers --------------------------------------------------------------
sedi() { if sed --version >/dev/null 2>&1; then sed -i -E "$@"; else sed -i '' -E "$@"; fi; }

first_version_in_pom() {
  grep -Eo '<version>[^<]+</version>' "$POM" 2>/dev/null | head -n1 | sed -E 's#</?version>##g' || true
}

replace_first_version_with() {
  local newv="$1"
  awk -v newv="$newv" '
    BEGIN { done=0 }
    {
      if (!done) {
        m = match($0, /<version>[^<]+<\/version>/)
        if (m) {
          pre = substr($0, 1, RSTART-1)
          post = substr($0, RSTART+RLENGTH)
          print pre "<version>" newv "</version>" post
          done = 1
          next
        }
      }
      print
    }
  ' "$POM" > "$POM.__tmp__" && mv "$POM.__tmp__" "$POM"
}

exists_release() {
  # return 0 if any known metadata jar exists
  local rel="$1" url code
  for path in "compositeContent.jar" "compositeArtifacts.jar" "content.jar"; do
    url="${BASE}/${rel}/${path}"
    code="$(curl -fsSLI -A "$UA" -o /dev/null -w '%{http_code}' "$url" || true)"
    log "validate: HEAD $url -> HTTP $code"
    [[ "$code" == "200" ]] && return 0
  done
  return 1
}

prev_quarter() {
  # input YYYY-MM, output previous quarter as YYYY-MM (one step only)
  local y="${1%-*}" m="${1#*-}"
  case "$m" in
    03) printf '%s-%s\n' "$((10#$y-1))" "12" ;;
    06) printf '%s-%s\n' "$y" "03" ;;
    09) printf '%s-%s\n' "$y" "06" ;;
    12) printf '%s-%s\n' "$y" "09" ;;
    *)  # if odd month, map down to last official quarter then step back once
        if   ((10#$m <= 03)); then printf '%s-%s\n' "$((10#$y-1))" "12"
        elif ((10#$m <= 06)); then printf '%s-%s\n' "$y" "03"
        elif ((10#$m <= 09)); then printf '%s-%s\n' "$y" "06"
        else                     printf '%s-%s\n' "$y" "09"
        fi
        ;;
  esac
}

detect_latest_from_pages() {
  local cand html

  # A) eclipseide.org banner
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

  # B) downloads page
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

pick_release() {
  # Decide final release respecting "at most one quarter fallback"
  # 1) Try page-derived candidate; if unavailable -> fallback once
  local cand="$1"
  if [[ -n "$cand" ]]; then
    log "candidate from pages: $cand"
    if exists_release "$cand"; then
      echo "$cand"; return 0
    fi
    local prev; prev="$(prev_quarter "$cand")"
    log "fallback (one quarter): $prev"
    if exists_release "$prev"; then
      echo "$prev"; return 0
    fi
  fi

  # 2) If pages gave nothing, compute current quarter and try once previous
  local now_y now_m q
  now_y="$(date +%Y)"
  now_m="$(date +%m)"
  if   ((10#$now_m <= 03)); then q="03"
  elif ((10#$now_m <= 06)); then q="06"
  elif ((10#$now_m <= 09)); then q="09"
  else                           q="12"
  fi
  local cur="${now_y}-${q}"
  log "no page candidate; using calendar current: $cur"
  if exists_release "$cur"; then
    echo "$cur"; return 0
  fi
  local prev; prev="$(prev_quarter "$cur")"
  log "calendar fallback (one quarter): $prev"
  if exists_release "$prev"; then
    echo "$prev"; return 0
  fi

  return 1
}

# ---- detect latest (with logs) -------------------------------------------
page_cand="$(detect_latest_from_pages || true)"
latest="$(pick_release "$page_cand" || true)"

if [[ -z "${latest:-}" ]]; then
  log "RESULT: latest=<none>"
  echo "WARN: Could not determine latest Eclipse release; skipping updates."
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then echo "changed=false" >> "$GITHUB_OUTPUT"; fi
  exit 0
fi

latest_url="${BASE}/${latest}/"
y="${latest%-*}"
m="${latest#*-}"
new_version="${y}.${m}.0"

log "RESULT: latest=${latest}"
echo "Latest release : $latest"
echo "New p2 URL     : $latest_url"
echo "New version    : $new_version"

# ---- update p2 URL(s) -----------------------------------------------------
current_url="$(grep -Eo 'https://download\.eclipse\.org/releases/[0-9]{4}-[0-9]{2}/' "$POM" | head -n1 || true)"
log "p2 in POM      : ${current_url:-<none>}"
if [[ "${current_url:-}" != "$latest_url" ]]; then
  esc_url="$(printf '%s' "$latest_url" | sed 's/[\/&]/\\&/g')"
  sedi "s#https://download\.eclipse\.org/releases/[0-9]{4}-[0-9]{2}/#${esc_url}#g" "$POM"
  echo "✅ p2 URL updated -> $latest_url"
  changed=1
else
  echo "ℹ️ p2 URL already up to date."
fi

# ---- update FIRST <version>…</version> to YYYY.MM.0 ----------------------
current_ver="$(first_version_in_pom)"
log "version in POM : ${current_ver:-<none>}"
if [[ -z "${current_ver:-}" ]]; then
  echo "ℹ️ No <version> tag found; skipping version bump."
elif [[ "$current_ver" != "$new_version" ]]; then
  replace_first_version_with "$new_version"
  echo "✅ version updated -> $new_version (from ${current_ver})"
  changed=1
else
  echo "ℹ️ Version already up to date."
fi

# ---- outputs for GitHub Actions ------------------------------------------
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  if [[ $changed -eq 1 ]]; then
    {
      echo "changed=true"
      echo "branch=update-eclipse-${latest}"
      echo "latest=${latest}"
      echo "new_url=${latest_url}"
      echo "new_version=${new_version}"
    } >> "$GITHUB_OUTPUT"
  else
    echo "changed=false" >> "$GITHUB_OUTPUT"
  fi
fi

exit 0

