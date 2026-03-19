#!/bin/sh
set -eu

git diff --name-only --diff-filter=M | while IFS= read -r file
do
    [ -n "$file" ] || continue
    [ -f "$file" ] || continue

    unix2dos "$file"
done
