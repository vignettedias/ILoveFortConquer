#!/bin/bash
# Boot a redroid Android userspace inside a QEMU (TCG) VM.
# usage: run_android.sh <disk.img> <name> [width] [height] [dpi] [adbport] [gpu: virgl|guest]
#   gpu=virgl : virtio-gpu-gl device, GL rendered natively on the host by Mesa llvmpipe via
#               virglrenderer (QEMU SDL display on an Xvfb server). Much faster than guest mode.
#   gpu=guest : SwiftShader inside the guest (no host GL needed, ~0.2 fps under TCG).
DISK=$1; NAME=$2; W=${3:-720}; H=${4:-1600}; DPI=${5:-320}; PORT=${6:-6555}; GPU=${7:-virgl}
RT=${FC_HARNESS:-$HOME/fc-harness}   # holds vmlinuz, initrd.img, sockets and logs
rm -f "$RT/$NAME.sock" "$RT/$NAME.mon"
if [ "$GPU" = "virgl" ]; then
  export DISPLAY=:99
  if ! xdpyinfo -display :99 >/dev/null 2>&1; then
    Xvfb :99 -screen 0 1280x1024x24 +extension GLX -nolisten tcp >"$RT/xvfb.log" 2>&1 &
    sleep 2
  fi
  GPUARGS="-vga none -device virtio-gpu-gl-pci -display sdl,gl=on"
  MODE=host
else
  GPUARGS="-display none"
  MODE=guest
fi
exec qemu-system-x86_64 -accel tcg,thread=multi,tb-size=1024 -cpu max -smp 4 -m 6144 \
  -kernel "$RT/vmlinuz" -initrd "$RT/initrd.img" \
  -no-reboot -append "panic=1 console=ttyS0 loglevel=6 printk.devkmsg=on androidboot.console=ttyS0 androidboot.hardware=redroid androidboot.redroid_gpu_mode=$MODE androidboot.redroid_width=$W androidboot.redroid_height=$H androidboot.redroid_dpi=$DPI androidboot.use_memfd=1 androidboot.redroid_fps=30" \
  -drive file="$DISK",format=raw,if=virtio,cache=unsafe \
  -netdev user,id=n0,hostfwd=tcp:127.0.0.1:$PORT-:5555 -device virtio-net-pci,netdev=n0 \
  -chardev socket,id=s0,path="$RT/$NAME.sock",server=on,wait=off,logfile="$RT/$NAME.serial.log" \
  -serial chardev:s0 $GPUARGS -monitor unix:"$RT/$NAME.mon",server,nowait
