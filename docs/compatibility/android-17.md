# Android 17

**NOT TESTED** — no Android 17 system image was available to the test environment
([testing-environment.md](../testing-environment.md)). Nothing here is a test result.

Static review of the published Android 17 behaviour changes against this APK:

| Android 17 change | Relevance | Assessment (not verified on a device) |
|---|---|---|
| Restriction on changing `static final` fields through reflection | `GameActivity.onDestroy()` → `cleanStatic()`/`cleanChildren()` null out fields of the game's own classes via reflection | The code checks `Modifier.isFinal` and skips final fields, so it does not hit the restriction |
| Further back-navigation and large-screen changes | Same areas as Android 16 | The preservation build already targets API 36 with predictive back and `appCategory="game"`; Android 17 should be re-checked when an image is available |

Recommendation: run [acceptance-test.md](../acceptance-test.md) on an Android 17 device or
emulator before claiming support.
