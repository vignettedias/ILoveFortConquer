#!/usr/bin/env bash
# Print or check SHA-256 checksums of release artifacts.
#
# usage: scripts/hash_artifact.sh [file ...]      print "<sha256>  <name>" (sha256sum format)
#        scripts/hash_artifact.sh --check [file.sha256 ...]
#                                                 verify checksum files (default: dist/*.sha256)
# With no file arguments the release APK in dist/ is hashed. Works with GNU coreutils
# (sha256sum) or macOS (shasum -a 256).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
sha256() { if command -v sha256sum >/dev/null; then sha256sum "$@"; else shasum -a 256 "$@"; fi; }

if [ "${1:-}" = "--check" ]; then
  shift
  [ $# -gt 0 ] || set -- "$ROOT"/dist/*.sha256
  rc=0
  for sums in "$@"; do
    # checksum files name the artifact relative to their own directory
    if (cd "$(dirname "$sums")" && sha256 -c "$(basename "$sums")"); then :; else rc=1; fi
  done
  exit $rc
fi

[ $# -gt 0 ] || set -- "$ROOT/dist/FortConquer-1.2.4-Modern-Android.apk"
for f in "$@"; do
  [ -f "$f" ] || { echo "not a file: $f" >&2; exit 2; }
  (cd "$(dirname "$f")" && sha256 "$(basename "$f")")
done
