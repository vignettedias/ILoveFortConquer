#!/usr/bin/env bash
# Forensic inventory of an Android APK (used for docs/apk-forensics.md).
#
# usage: scripts/analyze_apk.sh [path/to.apk] [workdir]
#   default APK: the reference APK at the repository root
#   default workdir: build/analysis/<first 12 hex of sha256>  (decoded tree is kept there)
#
# Reads the binary only: hashes, signing certificates and schemes (apksigner), manifest
# (aapt2 + apktool decode), DEX statistics, native libraries, asset inventory, and a scan of the
# smali code for platform APIs that matter on modern Android. Nothing is modified.
# Uses the pinned tools extracted by `./gradlew extractTools` (apktool 2.12.1, aapt2, apksigner).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
APK=${1:-$ROOT/com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk}
TOOLS=$ROOT/build/tools
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 2; }
if [ ! -f "$TOOLS/apktool.jar" ] || [ ! -x "$TOOLS/aapt2" ] || [ ! -f "$TOOLS/apksigner.jar" ]; then
  (cd "$ROOT" && ./gradlew -q extractTools)
fi
SHA=$( (sha256sum "$APK" 2>/dev/null || shasum -a 256 "$APK") | cut -d' ' -f1)
WORK=${2:-$ROOT/build/analysis/${SHA:0:12}}
mkdir -p "$WORK"
section() { printf '\n## %s\n\n' "$*"; }

echo "# APK analysis: $(basename "$APK")"
section "Identity"
echo "sha256: $SHA"
echo "sha1:   $( (sha1sum "$APK" 2>/dev/null || shasum -a 1 "$APK") | cut -d' ' -f1)"
echo "size:   $(stat -c %s "$APK" 2>/dev/null || stat -f %z "$APK") bytes"
"$TOOLS/aapt2" dump badging "$APK" 2>/dev/null \
  | grep -E "^(package|sdkVersion|minSdkVersion|targetSdkVersion|uses-permission|uses-library|application-label:|launchable-activity|native-code|supports-screens|densities)" || true

section "Signing (apksigner verify --print-certs)"
java -jar "$TOOLS/apksigner.jar" verify --verbose --print-certs "$APK" 2>&1 \
  | grep -E "^(Verified using|Signer #1 certificate|Signer #1 key|Number of signers|DOES NOT VERIFY|ERROR)" || true
unzip -l "$APK" 'META-INF/*' 2>/dev/null | awk 'NR>3 && $4 ~ /^META-INF/ {print "  " $4}'

section "Decode (apktool, framework kept in workdir)"
if [ ! -f "$WORK/decoded/AndroidManifest.xml" ]; then
  java -jar "$TOOLS/apktool.jar" d -f -p "$WORK/framework" -o "$WORK/decoded" "$APK" >"$WORK/apktool.log" 2>&1
fi
echo "decoded tree: $WORK/decoded"
sed -n '/^sdkInfo:/,/^[a-z]/p;/^versionInfo:/,/^[a-z]/p' "$WORK/decoded/apktool.yml" | grep -v '^[a-z].*:$' || true

section "Manifest components"
python3 - "$WORK/decoded/AndroidManifest.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
A = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(sys.argv[1]).getroot()
app = root.find('application')
print('application:', {k.replace(A, ''): v for k, v in app.attrib.items()})
for tag in ('activity', 'activity-alias', 'service', 'receiver', 'provider'):
    for c in app.findall(tag):
        filters = len(c.findall('intent-filter'))
        print('%-9s %s exported=%s enabled=%s intent-filters=%d' % (
            tag, c.get(A + 'name'), c.get(A + 'exported', '(unset)'), c.get(A + 'enabled', '(default)'), filters))
for m in app.findall('meta-data'):
    print('meta-data', m.get(A + 'name'), '=', m.get(A + 'value') or m.get(A + 'resource'))
for u in app.findall('uses-library'):
    print('uses-library', u.get(A + 'name'), 'required=%s' % u.get(A + 'required', 'true'))
PY

section "Code (DEX)"
python3 - "$APK" "$WORK/decoded" <<'PY'
import collections, os, struct, sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
for d in sorted(n for n in z.namelist() if n.startswith('classes') and n.endswith('.dex')):
    b = z.read(d)
    u = lambda o: struct.unpack('<I', b[o:o + 4])[0]
    print('%s: dex %s, %d strings, %d types, %d field refs, %d method refs, %d classes' % (
        d, b[4:7].decode(), u(56), u(64), u(80), u(88), u(96)))
pk = collections.Counter()
for base, _, files in os.walk(sys.argv[2]):
    if '/smali' not in base: continue
    rel = base.split('/smali', 1)[1].split('/', 1)[-1] if '/' in base.split('/smali', 1)[1] else ''
    parts = rel.split('/')
    key = '.'.join(parts[:3]) if parts and parts[0] in ('com', 'org', 'io', 'net') else '.'.join(parts[:2])
    pk[key or '(default)'] += sum(f.endswith('.smali') for f in files)
print('\nclasses per package (top 25):')
for k, v in pk.most_common(25):
    print('  %5d  %s' % (v, k))
libs = sorted(n for n in z.namelist() if n.startswith('lib/'))
print('\nnative libraries:', ', '.join(libs) if libs else 'none')
PY

section "Assets"
python3 - "$APK" <<'PY'
import collections, os, sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
c = collections.defaultdict(lambda: [0, 0])
for i in z.infolist():
    if not i.filename.startswith(('assets/', 'res/')): continue
    top = '/'.join(i.filename.split('/')[:2])
    ext = os.path.splitext(i.filename)[1].lower() or '(none)'
    c[(top, ext)][0] += 1; c[(top, ext)][1] += i.file_size
for (top, ext), (n, size) in sorted(c.items(), key=lambda kv: -kv[1][1])[:30]:
    print('  %-28s %-7s %5d files %10d bytes' % (top, ext, n, size))
PY

section "Platform API scan (smali references)"
scan() { # label, regex
  local n; n=$( (grep -rlE -e "$2" "$WORK/decoded"/smali* 2>/dev/null || true) | wc -l | tr -d ' ')
  printf '  %-46s %4s files\n' "$1" "$n"
}
scan "Apache HTTP client (org.apache.http)"        'Lorg/apache/http/'
scan "HttpURLConnection"                           'Ljava/net/HttpURLConnection;'
scan "WebView"                                     'Landroid/webkit/WebView;'
scan "external storage (Environment.getExternal*)" 'Landroid/os/Environment;->getExternal'
scan "TelephonyManager.getDeviceId / IMEI"         'TelephonyManager;->get(DeviceId|Imei|SubscriberId)'
scan "Settings.Secure ANDROID_ID"                  'android_id'
scan "registerReceiver"                            '->registerReceiver\('
scan "PendingIntent"                               'Landroid/app/PendingIntent;->get'
scan "startService / foreground service"           '->start(Foreground)?Service\('
scan "AlarmManager"                                'Landroid/app/AlarmManager;'
scan "Notification"                                'Landroid/app/Notification'
scan "Activity.onBackPressed / KEYCODE_BACK"       'onBackPressed|onKeyDown\(ILandroid/view/KeyEvent;\)Z'
scan "setRequestedOrientation"                     '->setRequestedOrientation\('
scan "GLSurfaceView / EGL"                         'Landroid/opengl/GLSurfaceView|Ljavax/microedition/khronos/egl/'
scan "reflection (Class.forName / getDeclared*)"   'Ljava/lang/Class;->(forName|getDeclared)'
scan "Java serialization (ObjectInputStream)"      'Ljava/io/ObjectInputStream;'
scan "Google Play Billing (com.android.billingclient)" 'Lcom/android/billingclient/'
scan "legacy in-app billing (IInAppBillingService)" 'IInAppBillingService'
scan "AdMob / Google Mobile Ads"                   'Lcom/google/android/gms/ads/'
echo
echo "cleartext URLs in smali/resources (unique):"
(grep -rhoE 'http://[A-Za-z0-9._/%?=&-]+' "$WORK/decoded/smali"* "$WORK/decoded/res/values" 2>/dev/null || true) \
  | sort -u | sed 's/^/  /' | head -40
echo
echo "report complete (decoded tree kept in $WORK)"
