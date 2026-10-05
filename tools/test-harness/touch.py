#!/usr/bin/env python3
"""Client for fctouch. Screen coords are landscape (1600x720); converted to panel coords."""
import socket, sys, time
ROT = int(__import__('os').environ.get('FC_TOUCH_ROT', '1'))  # display rotation of the landscape game
PW, PH = (int(v) for v in __import__('os').environ.get('FC_PANEL', '720x1600').split('x'))
# Callers use 1600x720 landscape coordinates; scale them to the real landscape size (PH x PW).
SX, SY = PH / 1600.0, PW / 720.0
class Touch:
    def __init__(self):
        self.s = socket.create_connection(('127.0.0.1', 7070), timeout=30)
        self.f = self.s.makefile('rw')
    def raw(self, line):
        self.f.write(line + '\n'); self.f.flush(); self.f.readline()
    def conv(self, x, y):
        x, y = int(x * SX), int(y * SY)
        if ROT == 1:   # display rotated 90: panel px = PW-1-y, py = x
            return PW - 1 - y, x
        if ROT == 3:   # rotated 270
            return y, PH - 1 - x
        return x, y
    def down(self, x, y): self.raw('d %d %d' % self.conv(x, y))
    def move(self, x, y): self.raw('m %d %d' % self.conv(x, y))
    def up(self): self.raw('u')
    def tap(self, x, y, hold=0.08):
        self.down(x, y); time.sleep(hold); self.up()
    def drag(self, x1, y1, x2, y2, steps=8, dt=0.03, hold=0.15):
        self.down(x1, y1); time.sleep(hold)
        for i in range(1, steps + 1):
            self.move(x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps); time.sleep(dt)
        time.sleep(hold); self.up()
if __name__ == '__main__':
    t = Touch(); cmd = sys.argv[1]; a = [float(v) for v in sys.argv[2:]]
    if cmd == 'tap': t.tap(*a)
    elif cmd == 'drag': t.drag(*a)
    elif cmd == 'rawdown': t.raw('d %d %d' % (a[0], a[1]))
    elif cmd == 'rawup': t.raw('u')
