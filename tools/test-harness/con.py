#!/usr/bin/env python3
"""Run a shell command on the guest serial console (root shell) and print its output."""
import socket, sys, time, uuid

path, cmd = sys.argv[1], sys.argv[2]
timeout = float(sys.argv[3]) if len(sys.argv) > 3 else 60
m = "E" + uuid.uuid4().hex[:8]
s = socket.socket(socket.AF_UNIX)
s.connect(path)
s.settimeout(1)
# marker is split in the echoed command so only real output matches it
s.sendall(("\n" + cmd + "; echo __%s''__\n" % m).encode())
buf = b""
t0 = time.time()
end = ("__%s__" % m).encode()
while time.time() - t0 < timeout:
    try:
        d = s.recv(65536)
        if d:
            buf += d
    except socket.timeout:
        pass
    if end in buf:
        break
out = buf.decode(errors="replace")
start = out.find("''__")
if start >= 0:
    out = out[start + 4:]
print(out.replace("__%s__" % m, "").strip())
