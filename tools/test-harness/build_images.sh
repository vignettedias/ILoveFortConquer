#!/bin/bash
# Build the Android 14/15/16 test VM disk images used for the preservation-build acceptance tests.
#
# usage: tools/test-harness/build_images.sh            (as root, on Ubuntu 24.04 x86_64)
#
# This is a consolidated record of the commands that were run step by step to create the test
# environment described in docs/testing-environment.md. The individual steps were all executed
# during the session; this script itself was assembled afterwards and has not been re-run end to
# end, so treat it as documentation first. Everything lands in $FC_HARNESS (default
# ~/fc-harness). Needs ~45 GB of free disk space and network access to Docker Hub, Ubuntu
# archives and raw.githubusercontent.com.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
RT=${FC_HARNESS:-$HOME/fc-harness}
KV=6.8.0-146-generic
mkdir -p "$RT" && cd "$RT"

# 1. Host packages: QEMU (TCG; no KVM was available), adb, ext4 tools, virgl/Xvfb for host GL.
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq qemu-system-x86 qemu-utils qemu-system-gui adb \
  e2fsprogs busybox-static cpio zstd kmod xvfb libgl1-mesa-dri mesa-utils libvirglrenderer1 xauth \
  gcc make "linux-headers-$KV" python3-pil

# 2. Guest kernel: stock Ubuntu 6.8 (has binder_linux as a module, virtio-gpu, uinput).
mkdir -p kernel && (cd kernel && apt-get download "linux-image-unsigned-$KV" "linux-modules-$KV" \
  "linux-modules-extra-$KV" && for f in *.deb; do dpkg-deb -x "$f" root; done)
cp "kernel/root/boot/vmlinuz-$KV" vmlinuz
S=kernel/root/lib/modules/$KV
zstd -d -q -f "$S/kernel/drivers/android/binder_linux.ko.zst" -o binder_linux.ko

# 3. ashmem: Android 15/16 need /dev/ashmem on a mainline kernel (redroid-modules, ported to 6.8).
mkdir -p ashmem-src/uapi
for f in Makefile ashmem.c ashmem.h deps.c deps.h uapi/ashmem.h dkms.conf; do
  curl -fsSL -o "ashmem-src/$f" "https://raw.githubusercontent.com/remote-android/redroid-modules/master/ashmem/$f"
done
(cd ashmem-src && patch -p2 < "$HERE/ashmem/ashmem-linux-6.8.patch" \
  && make -C "/usr/src/linux-headers-$KV" M="$PWD" modules)

# 4. Kernel modules for the Android userspace: Android empties kernel.modprobe, so the
#    netfilter/networking modules netd needs are preloaded from the initramfs.
D=modtree/lib/modules/$KV
rm -rf modtree && mkdir -p "$D" && cp "$S"/modules.builtin* "$S/modules.order" "$D/"
for sub in net lib crypto; do
  (cd "$S" && find "kernel/$sub" -name '*.ko.zst') | while read -r f; do
    mkdir -p "$D/$(dirname "$f")"; zstd -d -q -f "$S/$f" -o "$D/${f%.zst}"
  done
done
for f in kernel/drivers/net/dummy.ko.zst kernel/drivers/net/ifb.ko.zst kernel/drivers/net/veth.ko.zst \
         kernel/drivers/net/tun.ko.zst kernel/drivers/virtio/virtio_dma_buf.ko.zst \
         kernel/drivers/gpu/drm/virtio/virtio-gpu.ko.zst; do
  [ -f "$S/$f" ] && { mkdir -p "$D/$(dirname "$f")"; zstd -d -q -f "$S/$f" -o "$D/${f%.zst}"; }
done
sed -i 's/\.zst$//' "$D/modules.order"
depmod -b modtree "$KV"
(cd "$D" && find kernel/net/netfilter kernel/net/ipv4 kernel/net/ipv6 kernel/net/xfrm kernel/net/sched \
   kernel/net/key kernel/drivers/net -name '*.ko' | xargs -n1 basename | sed 's/\.ko$//' \
   | grep -v -E '^(ip_vs|nft_|nf_tables|xt_IDLETIMER|ipt_CLUSTERIP)' | sort) > "$D/preload.list"
printf 'virtio_dma_buf\nvirtio-gpu\n' >> "$D/preload.list"

# 5. initramfs: busybox, binder + ashmem, mounts the ext4 root, preloads modules, switch_root.
rm -rf initramfs && mkdir -p initramfs/{bin,proc,sys,dev,newroot}
cp /bin/busybox initramfs/bin/ && cp binder_linux.ko ashmem-src/ashmem_linux.ko initramfs/
cp "$HERE/initramfs/init" initramfs/init && chmod +x initramfs/init
(cd initramfs && find . | cpio -o -H newc --quiet | gzip -1 > ../initrd.img)

# 6. Android userspace: redroid images (AOSP, userdebug, no Google Play services).
#    Digests used for the published results:
#      14.0.0_64only-latest  sha256:68ae34dfbdb1000687d89691f12b9e17daa1b5c3ce528ada7e9d007090daab86
#      15.0.0_64only-latest  sha256:58e548d78c4854f032912757dc0782d7a86b9d602ffc10b2f09fb9e86731adf3
#      16.0.0_64only-latest  sha256:18eaf7058f1fbe17378106daa9c02936a7d293e71325db432462086854d9e9f0
for v in 14 15 16; do
  python3 "$HERE/pull_image.py" redroid/redroid "$v.0.0_64only-latest" "img$v"
  rm -rf "root$v" && mkdir -p "root$v"
  tar --numeric-owner --xattrs --xattrs-include='*' -xzf "img$v/layer00.tar.gz" -C "root$v"
  cp -a modtree/lib "root$v/"
  mkdir -p "root$v/sbin" && cp /bin/busybox "root$v/sbin/busybox" && ln -sf busybox "root$v/sbin/modprobe"
  truncate -s 0 "android$v.img" && truncate -s 10G "android$v.img"
  mkfs.ext4 -q -F -L android -d "root$v" "android$v.img"
done

# 7. Virtual touchscreen for fast, precise input (adb `input` is far too slow under TCG).
gcc -O2 -static -o fctouch "$HERE/fctouch.c"
echo "done: boot with $HERE/run_android.sh $RT/android16.img a16 720 1600 320 6555 virgl"
