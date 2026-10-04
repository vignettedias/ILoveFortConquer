#!/bin/bash
# Wait until the Android guest reports sys.boot_completed=1 (via the serial console).
# usage: wait_boot.sh <name> [max_seconds]
NAME=$1; MAX=${2:-1500}; RT=${FC_HARNESS:-$HOME/fc-harness}; t=0
while [ $t -lt $MAX ]; do
  if ! pgrep -x qemu-system-x86 >/dev/null; then
    echo "QEMU EXITED"; grep -a -E 'panic|segfault' $RT/$NAME.serial.log | head -3; exit 1
  fi
  out=$(timeout 25 python3 $RT/con.py $RT/$NAME.sock 'echo "BC=$(getprop sys.boot_completed) SS=$(getprop sys.system_server.start_count)"' 20 2>/dev/null | tr -d '\r' | grep -E '^BC=' | tail -1)
  bc=$(echo "$out" | sed -n 's/^BC=\([0-9]*\) .*/\1/p'); ss=$(echo "$out" | sed -n 's/.*SS=\([0-9]*\).*/\1/p')
  if [ "$bc" = "1" ]; then echo "BOOT_COMPLETED at ~${t}s (system_server starts: $ss)"; exit 0; fi
  if [ -n "$ss" ] && [ "$ss" -gt 3 ]; then echo "SYSTEM_SERVER RESTART LOOP (starts=$ss)"; exit 1; fi
  sleep 20; t=$((t+40))
done
echo "TIMEOUT ($out)"; exit 1
