#!/usr/bin/env bash
# One-shot release build: clean build, sign, verify, hash.
#
# usage: scripts/build_release.sh
#
# Equivalent to `./gradlew clean assembleRelease` followed by scripts/verify_apk.sh and a
# checksum check. Signing key resolution (see docs/signing.md):
#   1. FC_KEYSTORE, FC_KEYSTORE_PASSWORD, FC_KEY_ALIAS, FC_KEY_PASSWORD environment variables
#   2. keystore.properties in the repository root (git-ignored)
#   3. otherwise a local preservation key is generated once in .signing/ (git-ignored)
# Requires JDK 17+ (the build is tested with JDK 21) and python3; everything else is downloaded
# by Gradle with pinned versions and verified SHA-256 checksums.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

if ! command -v java >/dev/null; then echo "java not found (JDK 17+ required)" >&2; exit 2; fi
JV=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')
if [ "${JV%%.*}" -lt 17 ] 2>/dev/null; then echo "JDK 17+ required (found $JV)" >&2; exit 2; fi

./gradlew --no-daemon clean assembleRelease
echo
scripts/verify_apk.sh dist/FortConquer-1.2.4-Modern-Android.apk
echo
scripts/hash_artifact.sh --check dist/FortConquer-1.2.4-Modern-Android.sha256
echo
cat dist/FortConquer-1.2.4-Modern-Android.metadata.txt
