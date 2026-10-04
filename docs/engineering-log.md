# Engineering log

Chronological record of every issue investigated during the compatibility restoration.
Each entry records the evidence that was actually observed. Test devices are described in
[testing-environment.md](testing-environment.md) (redroid AOSP images running in a QEMU VM;
no Google Play services).

---

## 1. Test environment: Android userspace crash-loops (netd) — environment issue, not the game

```text
Issue:            Android 14 test image never finished booting; system_server restarted every ~15 s.
Affected version: test harness only (redroid 14 inside QEMU)
Observed:         sys.system_server.start_count kept increasing; init logged
                  "Service 'netd' exited with status 1" followed by system_server being killed.
Root cause:       netd could not create its NFLOG / NETLINK_NETFILTER / XFRM sockets and
                  iptables-restore could not initialise the 'mangle' table. Android's init.rc
                  empties /proc/sys/kernel/modprobe, so the generic Ubuntu kernel used for the VM
                  could not autoload netfilter modules (Docker hosts load them from the host side).
Fix:              VM initramfs preloads the kernel's netfilter / xfrm / sched modules before
                  switching to Android's /init. (Test harness only; nothing in the APK.)
Validation:       boot completes in ~90 s with sys.system_server.start_count = 1.
```

## 2. Baseline: original APK crashes ~1 s after launch on Android 14

```text
Issue:            Original 1.2.4 APK installs, then the process dies immediately after onCreate.
Affected version: Android 14 (API 34) observed; applies to every Android 9+ device because the
                  app targets API 30.
Observed:         FATAL EXCEPTION: pool-4-thread-1 (and pool-5-thread-1)
                  java.lang.NoClassDefFoundError: Failed resolution of:
                      Lorg/apache/http/impl/client/DefaultHttpClient;
                    at com.droidhen.fortconquer.kits.DiscountManager$NetworkService.run(DiscountManager.java:152)
                  Caused by: java.lang.ClassNotFoundException: org.apache.http.impl.client.DefaultHttpClient
Root cause:       Android platform change (Android 9 / API 28): the Apache HTTP client is no longer
                  on the boot class path for apps targeting API >= 28 unless they declare
                  <uses-library android:name="org.apache.http.legacy"/>. 1.2.4 was rebuilt with
                  targetSdkVersion 30 but kept the Apache-based DiscountManager / ArenaAgent code and
                  did not declare the library. GameActivity.onCreate() -> DiscountManager
                  .updateDiscountRate() starts the request on an executor thread; the Error is not
                  caught (only IOException/JSONException are), so the process is killed.
Relevant code:    com.droidhen.fortconquer.kits.DiscountManager$NetworkService.run()
                  com.droidhen.fortconquer.kits.ArenaAgent$NetworkService.run()
Control test:     an unmodified apktool round-trip of the APK (no patches, re-signed) crashes with
                  the identical stack trace -> the rebuild pipeline itself does not change behaviour.
Fix:              patches/0001-apache-http-legacy-library.patch declares the platform library
                  (android:required="false").
Validation:       Android 14: no crash in 90 s; DroidHen logo scene and start menu render.
                  The request now fails gracefully inside the game's own catch block:
                  java.io.IOException: Cleartext traffic not permitted: http://fortconquer.droidhen.com
```

## 3. Test environment: software rendering too slow for gameplay testing

```text
Issue:            With redroid "guest" GPU mode (ANGLE -> SwiftShader Vulkan inside the emulated
                  CPU) the game rendered correctly but at 0.16 fps (50 frames in 307 s).
Root cause:       No KVM in the build container: every guest instruction, including SwiftShader's
                  rasteriser, runs under QEMU TCG.
Fix (harness):    virtio-gpu-gl device + virglrenderer: guest Mesa (libGLESv1_CM_mesa /
                  virtio_gpu_dri) forwards GL to the host, where Mesa llvmpipe renders natively
                  on an Xvfb display. Measured 30.0 fps (127 frames / 4.2 s, compositor capped at 30).
Note:             Two different GLES implementations were therefore exercised: ANGLE/SwiftShader
                  (as on Pixel-class devices that ship ANGLE) and Mesa/virgl.
```

## 4. Test environment: Android 16 image needs an ashmem driver

```text
Issue:            Android 16 guest never finished booting; system_server crashed in a loop with
                  java.lang.RuntimeException: Failed to create ashmem: No such file or directory
                    at com.android.internal.os.ApplicationSharedMemory.nativeCreate
Root cause:       Android 15+/16 libcutils only uses memfd when the kernel implements the Android
                  Common Kernel "ashmem-memfd compat" ioctls; the generic Ubuntu 6.8 kernel used for
                  the VM does not, and /dev/ashmem was removed from mainline in 5.18.
Fix (harness):    built the open-source ashmem driver from redroid-modules, ported to Linux 6.8
                  (shrinker_alloc()/vm_flags_clear()/kprobe-based symbol lookup), loaded from the
                  VM initramfs. Not part of the APK. Real Android 15/16 devices ship this in their
                  kernels.
```
