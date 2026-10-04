# Baseline failure — original APK on Android 16 (API 36)

Device: redroid 16 `BP2A.250605.031.A3` in the test VM ([testing-environment.md](../testing-environment.md)).
APK: original 1.2.4 (SHA-256 `933557dc…7c753cc92`).

| Step | Result |
|---|---|
| `adb install` | **Success** (`dumpsys package`: targetSdk=30) |
| Launch | `am start` 18:31:12.4, FATAL 18:31:17.0 (4.6 s) |
| Status | **FAIL — crash on every launch, game unplayable** |

```text
10-04 18:31:16.967  2814  2880 E AndroidRuntime: FATAL EXCEPTION: pool-6-thread-1
10-04 18:31:16.967  2814  2880 E AndroidRuntime: Process: com.droidhen.fortconquer, PID: 2814
10-04 18:31:16.967  2814  2880 E AndroidRuntime: java.lang.NoClassDefFoundError: Failed resolution of: Lorg/apache/http/impl/client/DefaultHttpClient;
10-04 18:31:16.967  2814  2880 E AndroidRuntime:   at com.droidhen.fortconquer.kits.DiscountManager$NetworkService.run(DiscountManager.java:152)
10-04 18:31:16.967  2814  2880 E AndroidRuntime:   at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
10-04 18:31:16.967  2814  2880 E AndroidRuntime:   at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
10-04 18:31:16.967  2814  2880 E AndroidRuntime:   at java.lang.Thread.run(Thread.java:1119)
```

Root cause and fix: identical to [Android 14](android-14.md).

Android 16 behaviour changes that only apply to apps **targeting** API 36 (predictive back /
`KEYCODE_BACK` no longer dispatched, orientation requests ignored on large screens) do not affect
the original APK. They became work items when the target API was raised and are documented in
[compatibility/android-16.md](../compatibility/android-16.md).
