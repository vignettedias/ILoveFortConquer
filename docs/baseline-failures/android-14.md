# Baseline failure — original APK on Android 14 (API 34)

Device: redroid 14 `UD2A.240505.001.W1` in the test VM ([testing-environment.md](../testing-environment.md)).
APK: `com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk`, SHA-256 `933557dc…7c753cc92`, unmodified.

| Step | Result |
|---|---|
| `adb install` | **Success** (targetSdk 30 is above Android 14's minimum installable target of 23) |
| Launch | process started 17:31:01.9, killed 17:31:11.5 (this run used in-guest software rendering, so start-up was slow) |
| Status | **FAIL — crash on every launch, game unplayable** |

```text
E AndroidRuntime: FATAL EXCEPTION: pool-4-thread-1
E AndroidRuntime: Process: com.droidhen.fortconquer, PID: 2709
E AndroidRuntime: java.lang.NoClassDefFoundError: Failed resolution of: Lorg/apache/http/impl/client/DefaultHttpClient;
E AndroidRuntime:   at com.droidhen.fortconquer.kits.DiscountManager$NetworkService.run(DiscountManager.java:152)
E AndroidRuntime:   at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
E AndroidRuntime:   at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:644)
E AndroidRuntime:   at java.lang.Thread.run(Thread.java:1012)
E AndroidRuntime: Caused by: java.lang.ClassNotFoundException: org.apache.http.impl.client.DefaultHttpClient
```

## Root cause

`GameActivity.onCreate()` calls `DiscountManager.updateDiscountRate()`, which runs an Apache
HttpClient request on an executor thread. Since Android 9 (API 28) the Apache HTTP client is
removed from the boot class path of apps targeting API 28 or higher unless the manifest declares
`<uses-library android:name="org.apache.http.legacy" android:required="false"/>`. Version 1.2.4
targets API 30, does not declare the library and does not bundle the classes. The resulting
`NoClassDefFoundError` is an `Error`, not an `Exception`; the game's `catch (IOException |
JSONException)` does not catch it, so the uncaught-exception handler kills the process.

This is not specific to Android 14: by the platform rule above, every device running Android 9 or
later should hit the same crash (observed here on 14, [15](android-15.md) and [16](android-16.md)).

## Control experiment

An apktool decode + rebuild of the original APK **without any patch** (re-signed with the local
key, SHA-256 `c2888646…42a7e58e`) was installed on the same device and crashed with the identical
stack trace. This shows the decode/rebuild pipeline does not change behaviour, and that the fix
below is what changes the outcome.

## Fix

[`patches/0001-apache-http-legacy-library.patch`](../../patches/0001-apache-http-legacy-library.patch)
— see [networking.md](../networking.md) and [engineering-log.md #2](../engineering-log.md).
