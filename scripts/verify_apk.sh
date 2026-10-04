#!/usr/bin/env bash
# Verify a Fort Conquer APK (preservation build or original) and print a report.
#
# usage: scripts/verify_apk.sh [path/to.apk]   (default: dist/FortConquer-1.2.4-Modern-Android.apk)
#
# Uses the pinned apksigner (AOSP build-tools 33.0.2) and aapt2 (bundled in apktool 2.12.1),
# extracted by `./gradlew extractTools`. Requires Java 17+ and python3.
# Exit status is non-zero if any hard check fails.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
APK=${1:-$ROOT/dist/FortConquer-1.2.4-Modern-Android.apk}
TOOLS=$ROOT/build/tools
fail=0
ok()   { printf '  [ OK ] %s\n' "$*"; }
bad()  { printf '  [FAIL] %s\n' "$*"; fail=1; }
info() { printf '  [info] %s\n' "$*"; }

[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 2; }
if [ ! -x "$TOOLS/aapt2" ] || [ ! -f "$TOOLS/apksigner.jar" ]; then
  (cd "$ROOT" && ./gradlew -q extractTools)
fi

echo "== $APK"
ok "exists ($(stat -c %s "$APK" 2>/dev/null || stat -f %z "$APK") bytes)"
SHA=$( (sha256sum "$APK" 2>/dev/null || shasum -a 256 "$APK") | cut -d' ' -f1)
info "sha256 $SHA"

echo "== manifest (aapt2 dump badging)"
BADGING=$("$TOOLS/aapt2" dump badging "$APK" 2>/dev/null)
pkg=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$BADGING")
vcode=$(sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p" <<<"$BADGING")
vname=$(sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")
minsdk=$(sed -n "s/^minSdkVersion:'\([^']*\)'.*/\1/p" <<<"$BADGING")
tgtsdk=$(sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" <<<"$BADGING")
[ "$pkg" = "com.droidhen.fortconquer" ] && ok "package $pkg" || bad "package '$pkg'"
info "versionName $vname / versionCode $vcode"
info "minSdkVersion $minsdk / targetSdkVersion $tgtsdk"
grep "^uses-permission:" <<<"$BADGING" | sed "s/^uses-permission: name=/  [perm] /"
grep -q "^uses-library-not-required:'org.apache.http.legacy'" <<<"$BADGING" \
  && info "uses-library org.apache.http.legacy (required=false)" || true

echo "== debug / risky configuration"
XML=$("$TOOLS/aapt2" dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null)
grep -q 'android:debuggable([^)]*)=true' <<<"$XML" && bad "android:debuggable=true" || ok "not debuggable"
grep -q 'android:testOnly([^)]*)=true' <<<"$XML" && bad "android:testOnly=true" || ok "not testOnly"
grep -q 'android:usesCleartextTraffic([^)]*)=true' <<<"$XML" && bad "usesCleartextTraffic=true" || ok "no global cleartext opt-in"
grep -q 'networkSecurityConfig' <<<"$XML" && info "has networkSecurityConfig" || ok "no network-security-config overrides"
if [ "${tgtsdk:-0}" -ge 31 ] 2>/dev/null; then
  # every component with an intent-filter must declare android:exported
  python3 - "$XML" <<'PY' && ok "all components with intent filters declare android:exported" || bad "component with intent-filter lacks android:exported"
import re, sys
xml = sys.argv[1].splitlines()
comp = None; exported = False; missing = []
for line in xml:
    m = re.match(r'\s*E: (activity|activity-alias|service|receiver|provider) ', line)
    if m:
        if comp and comp[1] and not exported: missing.append(comp[0])
        comp = [line.strip(), False]; exported = False; continue
    if comp is not None:
        if 'android:exported(' in line: exported = True
        if 'E: intent-filter' in line: comp[1] = True
if comp and comp[1] and not exported: missing.append(comp[0])
sys.exit(1 if missing else 0)
PY
fi

echo "== contents"
python3 - "$APK" <<'PY'
import sys, zipfile, struct
apk = sys.argv[1]
z = zipfile.ZipFile(apk)
names = z.namelist()
dex = sorted(n for n in names if n.startswith('classes') and n.endswith('.dex'))
print('  [info] DEX files: %d (%s)' % (len(dex), ', '.join(dex)))
for d in dex:
    b = z.read(d)
    sizes = struct.unpack('<6I', b[56:80])[0::2]  # string,type,proto,field,method,class sizes
    meth = struct.unpack('<I', b[88:92])[0]
    print('  [info]   %s: dex %s, %d method refs, %d classes' % (d, b[4:7].decode(), meth, struct.unpack('<I', b[96:100])[0]))
libs = sorted({n.split('/')[1] for n in names if n.startswith('lib/') and n.count('/') >= 2})
print('  [info] native ABIs: %s' % (', '.join(libs) if libs else 'none (pure Java/Dalvik)'))
info = z.getinfo('resources.arsc')
print('  [%s] resources.arsc stored uncompressed' % (' OK ' if info.compress_type == 0 else 'FAIL'))
PY

echo "== alignment"
python3 - "$APK" <<'PY' && ok "stored entries 4-byte aligned (.so 16 KiB)" || bad "misaligned stored entries"
import sys, struct
f = open(sys.argv[1], 'rb'); d = f.read()
eocd = d.rfind(b'PK\x05\x06'); n = struct.unpack('<H', d[eocd+10:eocd+12])[0]
cd = struct.unpack('<I', d[eocd+16:eocd+20])[0]; p = cd; bad = []
for _ in range(n):
    method = struct.unpack('<H', d[p+10:p+12])[0]
    nl, el, cl = struct.unpack('<HHH', d[p+28:p+34]); lho = struct.unpack('<I', d[p+42:p+46])[0]
    name = d[p+46:p+46+nl].decode('utf-8', 'replace')
    lnl, lel = struct.unpack('<HH', d[lho+26:lho+30]); off = lho + 30 + lnl + lel
    if method == 0 and off % (16384 if name.endswith('.so') else 4): bad.append(name)
    p += 46 + nl + el + cl
for b in bad[:10]: print('    misaligned:', b)
sys.exit(1 if bad else 0)
PY

echo "== signatures (apksigner verify)"
if OUT=$(java -jar "$TOOLS/apksigner.jar" verify --verbose --print-certs "$APK" 2>&1); then
  ok "apksigner verify passed"
else
  bad "apksigner verify failed"
fi
grep -E '^Verified using|^Signer #1 certificate (DN|SHA-256|SHA-1)' <<<"$OUT" | sed 's/^/  /'
grep -q 'CN=hzstudio' <<<"$OUT" && info "signed with the ORIGINAL publisher certificate (reference APK)" \
  || info "signed with a preservation key (not the original DroidHen/hzstudio key)"

echo
[ $fail -eq 0 ] && echo "RESULT: PASS" || echo "RESULT: FAIL"
exit $fail
