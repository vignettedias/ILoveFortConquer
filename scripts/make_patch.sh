#!/usr/bin/env bash
# Create a new compatibility patch from an edited copy of the patched tree.
#
# Workflow:
#   ./gradlew preparePatchedTree                       # build/fc/patched = decode + patches/series
#   cp -a build/fc/patched /tmp/fc-edit                # edit files in /tmp/fc-edit
#   scripts/make_patch.sh /tmp/fc-edit patches/00NN-short-name.patch
#   echo 00NN-short-name.patch >> patches/series
#
# The patch is a plain unified diff with a/ b/ prefixes, applied strictly (no fuzz) by the build.
set -euo pipefail
EDIT=$(cd "${1:?edited tree}" && pwd)
OUT=${2:?output patch file}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BASE="$ROOT/build/fc/patched"
[ -d "$BASE" ] || { echo "run ./gradlew preparePatchedTree first" >&2; exit 1; }
TMP=$(mktemp -d)
trap 'rm -f "$TMP/a" "$TMP/b"; rmdir "$TMP"' EXIT
ln -s "$BASE" "$TMP/a"
ln -s "$EDIT" "$TMP/b"
# -N: treat absent files as empty (new files); exclude apktool build intermediates.
( cd "$TMP" && diff -ruN --exclude=build --exclude=dist a/ b/ ) > "$ROOT/$OUT" || [ $? -eq 1 ]
# strip timestamps from ---/+++ headers so patches are stable across machines
sed -i -E 's/^(---|\+\+\+) ([^\t]+)\t.*/\1 \2/' "$ROOT/$OUT"
echo "wrote $OUT ($(grep -c '^+++ ' "$ROOT/$OUT") file(s))"
