# Android 17 (API 37)

**NOT TESTED** — no Android 17 system image was available to the test environment
(redroid publishes none; Google's image servers were not reachable), see
[testing-environment.md](../testing-environment.md). Nothing here is a test result.

Android 17 is already shipping on phones — for example Samsung's One UI 9 for the Galaxy S25
series ([device notes](../devices/samsung-galaxy-s25.md)).

## Static review against Google's published changes

The preservation build targets API 36, so only the
[changes for all apps](https://developer.android.com/about/versions/17/behavior-changes-all)
apply:

| Android 17 change (all apps) | Relevance to Fort Conquer |
|---|---|
| App memory limits based on device RAM (`MemoryLimiter:AnonSwap` kills) | Measured 45–75 MB TOTAL PSS on Android 14–16; low risk |
| Background audio hardening: audio playback/focus/volume calls outside valid lifecycle states fail silently | At most a call made while backgrounded is ignored; no exception, so no crash path |
| Default IME visibility not restored after unhandled configuration changes | Name dialog only; orientation changes are handled in-process |
| `usesCleartextTraffic` deprecation plan | Not used |
| WebOTP/SMS OTP delay, cross-profile loopback block, keystore key limits, touchpad pointer capture, Bluetooth re-pairing | Not used by the game |

[Changes for apps targeting Android 17](https://developer.android.com/about/versions/17/behavior-changes-17)
(unmodifiable `static final` fields, no opt-out from large-screen orientation/resizability
overrides, certificate transparency and ECH by default, local-network permission, BAL hardening,
background-audio foreground-service requirement, …) do **not** apply while the app targets API 36.
If the target is ever raised to 37: the game's reflection already skips `final` fields;
`appCategory="game"` would no longer keep landscape on ≥ 600 dp displays; certificate
transparency would apply to the HTTPS endpoint.

Recommendation: run [acceptance-test.md](../acceptance-test.md) on an Android 17 device before
claiming support.
