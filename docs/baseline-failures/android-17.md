# Baseline — original APK on Android 17

**NOT TESTED.** No Android 17 system image was available to the test environment
(see [testing-environment.md](../testing-environment.md)); nothing on this page is an observation.

What can be said from static analysis only:

* The start-up crash documented for [Android 14](android-14.md), [15](android-15.md) and
  [16](android-16.md) is caused by the Apache HTTP client being absent from the class path of
  apps targeting API 28+. Nothing suggests Android 17 restores it, so the original APK is
  expected to crash the same way. This is a prediction, not a result.
* Android 17's restriction on modifying `static final` fields via reflection applies only to apps
  targeting API 37 and would not affect this game anyway: its only reflection
  (`GameActivity.cleanStatic()`/`cleanChildren()`) skips `final` fields — see
  [compatibility/android-17.md](../compatibility/android-17.md).
