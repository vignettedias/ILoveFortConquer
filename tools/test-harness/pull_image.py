#!/usr/bin/env python3
"""Pull a Docker Hub image (linux/amd64) into a directory of layer tarballs + manifest, verifying sha256 digests."""
import sys, json, urllib.request, hashlib, os
repo, tag, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(out, exist_ok=True)
def get(url, headers={}):
    req = urllib.request.Request(url, headers=headers)
    return urllib.request.urlopen(req, timeout=120)
tok = json.load(get(f"https://auth.docker.io/token?service=registry.docker.io&scope=repository:{repo}:pull"))["token"]
H = {"Authorization": f"Bearer {tok}", "Accept": ",".join([
    "application/vnd.oci.image.index.v1+json","application/vnd.docker.distribution.manifest.list.v2+json",
    "application/vnd.oci.image.manifest.v1+json","application/vnd.docker.distribution.manifest.v2+json"])}
r = get(f"https://registry-1.docker.io/v2/{repo}/manifests/{tag}", H)
m = json.load(r); digest = r.headers.get("Docker-Content-Digest")
if "manifests" in m:
    ent = [x for x in m["manifests"] if x.get("platform",{}).get("architecture")=="amd64"][0]
    digest = ent["digest"]
    m = json.load(get(f"https://registry-1.docker.io/v2/{repo}/manifests/{digest}", H))
json.dump({"repo":repo,"tag":tag,"manifest_digest":digest,"manifest":m}, open(f"{out}/manifest.json","w"), indent=1)
print("manifest", digest, "layers", len(m["layers"]), "total", sum(l["size"] for l in m["layers"]))
cfg = m["config"]["digest"]
open(f"{out}/config.json","wb").write(get(f"https://registry-1.docker.io/v2/{repo}/blobs/{cfg}", H).read())
for i,l in enumerate(m["layers"]):
    fn = f"{out}/layer{i:02d}.tar.gz"
    d = l["digest"].split(":")[1]
    if os.path.exists(fn) and hashlib.sha256(open(fn,"rb").read()).hexdigest()==d: continue
    h = hashlib.sha256()
    with get(f"https://registry-1.docker.io/v2/{repo}/blobs/{l['digest']}", H) as resp, open(fn,"wb") as f:
        while True:
            b = resp.read(1<<20)
            if not b: break
            h.update(b); f.write(b)
    assert h.hexdigest()==d, f"digest mismatch layer {i}"
    print("layer", i, l["size"], "ok")
