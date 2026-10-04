#!/bin/bash
# snap.sh <outdir> <name>  -> full PNG + 800px-wide JPEG preview of the guest screen
export ADB_LOCAL_TRANSPORT_MAX_PORT=5553
SERIAL=${FC_SERIAL:-127.0.0.1:6555}
mkdir -p "$1"
adb -s "$SERIAL" exec-out screencap -p > "$1/$2.png"
python3 -c "
from PIL import Image; im=Image.open('$1/$2.png').convert('RGB'); w,h=im.size; s=800/max(w,h)
im.resize((int(w*s),int(h*s))).save('$1/$2.jpg',quality=80)"
echo "$1/$2.jpg"
