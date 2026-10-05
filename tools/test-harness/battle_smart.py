#!/usr/bin/env python3
"""Radar-guided Fort Conquer battle driver (test harness).

A background thread keeps reading the in-game radar (top HUD: one row per lane, red dots =
enemy units, green = ours) from screenshots; the main loop continuously drags the next troop
card into the lane whose enemy is closest to our fort.
usage: battle_smart.py <outdir> <seconds> [cards=3]
"""
import io, os, subprocess, sys, threading, time
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from touch import Touch

out, secs = sys.argv[1], float(sys.argv[2])
ncards = int(sys.argv[3]) if len(sys.argv) > 3 else 3
os.makedirs(out, exist_ok=True)
env = dict(os.environ, ADB_LOCAL_TRANSPORT_MAX_PORT='5553')
cards = [610, 750, 890, 1030, 1170, 1310][:ncards]
lane_y = [194, 280, 367, 454, 541]          # drop targets (screen y)
radar_rows = [(12, 25), (27, 40), (42, 55), (57, 70), (72, 86)]
state = {'threat': None, 'stop': False, 'shots': 0}
t0 = time.time()

def screenshot():
    png = subprocess.run('adb -s %s exec-out screencap -p' % os.environ.get('FC_SERIAL', '127.0.0.1:6555'), shell=True, env=env,
                         capture_output=True).stdout
    im = Image.open(io.BytesIO(png)).convert('RGB')
    return im if im.size == (1600, 720) else im.resize((1600, 720))  # radar sampled in 1600x720 space

def threat(im):
    px = im.load(); res = []
    for (y0, y1) in radar_rows:
        reds = [x for y in range(y0, y1, 2) for x in range(560, 1100, 3)
                if px[x, y][0] > 180 and px[x, y][1] < 70 and px[x, y][2] < 70]
        greens = [x for y in range(y0, y1, 2) for x in range(560, 1100, 3)
                  if px[x, y][1] > 160 and px[x, y][0] < 90 and px[x, y][2] < 90]
        res.append((min(reds) if reds else 9999, len(reds), len(greens)))
    return res

def radar_loop():
    next_save = 0
    while not state['stop']:
        im = screenshot(); el = time.time() - t0
        state['threat'] = threat(im)
        if el >= next_save:
            im.save('%s/smart_%03ds.png' % (out, int(el))); next_save += 15

th_thread = threading.Thread(target=radar_loop, daemon=True); th_thread.start()
t = Touch(); i = 0; log = open(out + '/log.txt', 'w')
while time.time() - t0 < secs:
    th = state['threat']
    if th is None:
        time.sleep(0.5); continue
    order = sorted(range(5), key=lambda k: (th[k][0], -th[k][1], th[k][2]))
    if th[order[0]][0] == 9999:
        order = sorted(range(5), key=lambda k: th[k][2])
    lane = order[i % 2]  # alternate between the two most threatened lanes
    cx = cards[i % len(cards)]
    t.drag(cx, 650, 760, lane_y[lane], steps=5, dt=0.03, hold=0.2)
    log.write('%.0fs lane=%d threat=%s\n' % (time.time() - t0, lane, th)); log.flush()
    i += 1
    time.sleep(0.3)
state['stop'] = True
print('drags=%d elapsed=%.0fs' % (i, time.time() - t0))
