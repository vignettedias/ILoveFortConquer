# Signing

## The original

`CN=hzstudio, OU=hzstudio, O=hzstudio, L=hz, ST=zj, C=86` — RSA 1024, SHA1withRSA, valid
2009-11-30 → 2037-04-17, certificate SHA-256
`d35f30d80048c9969562982f3ef55205e4ebf078426d891202d8e93e38d78722`. The private key belongs to
the original publisher and is not available. **The preservation build cannot be signed with it.**

## Consequences

* Android refuses to install an APK over an installed app signed with a different key
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). **Uninstall the original Fort Conquer before installing
  the preservation build.** Uninstalling deletes the original's local save.
* Google Play Protect may warn about an app from an unknown developer.
* Two preservation builds signed with different keys also cannot update each other. Keep using
  one key if you want in-place updates.

## Key used for the published `dist/` APK

| | |
|---|---|
| Subject | `CN=Fort Conquer Local Preservation Build, O=Unofficial preservation build, C=XX` |
| Algorithm | RSA 4096, SHA256withRSA, validity 50 years |
| Certificate SHA-256 | `bbdd17d42f7ee3818081e91ccb815789fdc4fcbf38e47f6789a7afba6e976634` |
| Certificate SHA-1 | `f3b87fb4f42958467334e5a5462f1237e604216d` |
| Schemes | APK Signature Scheme v1 (JAR), v2, v3 |

This key was generated automatically by the build inside the ephemeral environment that
produced the release. It is **not** in the repository and was not kept: a future build will be
signed with a different key, so treat the published APK as a one-off and use your own key for
anything you maintain.

Verify any copy with:

```sh
scripts/verify_apk.sh path/to/FortConquer-1.2.4-Modern-Android.apk
# or: apksigner verify --verbose --print-certs path/to.apk
```

## Signing with your own key

Key resolution order in `build.gradle.kts`:

1. Environment variables: `FC_KEYSTORE` (path), `FC_KEYSTORE_PASSWORD`, `FC_KEY_ALIAS`,
   `FC_KEY_PASSWORD`.
2. `keystore.properties` in the repository root (git-ignored):
   ```properties
   storeFile=/absolute/path/to/release.p12
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
3. Otherwise a local key is generated once in `.signing/local-preservation.p12` with a random
   password in `.signing/local-preservation.password` (both git-ignored) and reused for later
   builds on that machine.

Passwords reach `apksigner` through environment variables (`--ks-pass env:…`), never on a command
line. Create a key with:

```sh
keytool -genkeypair -keystore release.p12 -storetype PKCS12 -alias fortconquer \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 18250 \
  -dname "CN=Your name, O=Unofficial Fort Conquer preservation build"
```

Never commit a keystore or its passwords. `.gitignore` excludes `.signing/`,
`keystore.properties`, `*.jks`, `*.keystore`, `*.p12`, `*.pk8` and `*.pem`.
