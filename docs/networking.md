# Networking

## What the game talks to

| Caller | When | Request | Failure handling in the original code |
|---|---|---|---|
| `DiscountManager` | every `GameActivity.onCreate()` | `GET …/Main.php?function=discountSell` (discount for coin packs, optional notice image URL) | `catch (IOException, JSONException)`: keeps the cached/zero discount |
| `ArenaAgent` | Arena START (after stage 5) | `matchBattlePlayer` (opponent's team), result report | "Network error" dialog with CANCEL / RETRY |
| `DiscountManager` | if the server sent one | notice bitmap from a server-supplied URL | ignored on failure |

Host: `fortconquer.droidhen.com`, path `/FortConquer/game/Main.php`, Apache HttpClient
(`DefaultHttpClient`, `HttpGet`) on single-thread executors, 5–8 s timeouts.
`CrashDumpUploader` / `FileUploadRequest` exist but are never called.

## Changes

1. **Apache HttpClient availability** — `<uses-library android:name="org.apache.http.legacy"
   android:required="false"/>` ([0001](../patches/0001-apache-http-legacy-library.patch)). Without
   it the very first request crashes the game on Android 9+ ([baseline](baseline-failures/android-14.md)).
2. **HTTPS instead of cleartext** — the endpoint strings in `DiscountManager` and `ArenaAgent`
   now use `https://` ([0006](../patches/0006-legacy-endpoint-https.patch)).
   * The original targets API 30, so cleartext was **already blocked** on Android 9+
     (`IOException: Cleartext traffic not permitted`, observed after fixing the crash). Turning it
     back on would have needed `usesCleartextTraffic="true"` or a network-security config — both
     rejected: game traffic includes a device identifier and progress data, and a global cleartext
     opt-in would also apply to any URL the server sends.
   * Whether `fortconquer.droidhen.com` still answers over HTTPS (or at all) **could not be
     verified**: the build environment's outbound TLS goes through an intercepting proxy, so every
     request from the test devices failed the certificate check
     (`SSLHandshakeException: Trust anchor for certification path not found`). That failure is the
     platform's certificate validation working as intended; it was not weakened.
   * If the server only ever spoke HTTP, online features stay unavailable — the same outcome the
     original APK has had on Android 9+ since it was rebuilt for API 30.

No custom `TrustManager`, `HostnameVerifier` or network-security config was added, and none
exists in the original. `verify_apk.sh` checks for a cleartext opt-in.

## Offline behaviour (verified)

* Start-up: discount request fails quietly; the game is fully playable offline (all local
  stages, market, evolve, upgrade, coin store dialog).
* Arena: "Network error" → CANCEL returns to Status; RETRY re-sends (exercised on Android 16).
  The failed attempts did not consume Arena energy (40/40 after CANCEL on Android 14, 15, 16).

No server responses are emulated or faked anywhere in the preservation build.
