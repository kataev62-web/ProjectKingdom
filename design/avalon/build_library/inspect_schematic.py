#!/usr/bin/env python3
"""Inspect Minecraft WorldEdit schematics (read-only; Python standard library).

Supports legacy .schematic NBT and Sponge .schem metadata.
WARNING: Passing this inspector does NOT validate appearance, mod requirements,
rotation, or runtime compatibility. Never paste untested files into Avalon.
"""
import argparse
import collections
import gzip
import hashlib
import io
import json
import struct
import sys
import zlib

BASIC={1:">b", 2:">h", 3:">i", 4:">q", 5:">f", 6:">d"}

class Reader:
    def __init__(self, raw):
        self.f=io.BytesIO(raw)
    def get(self,n):
        b=self.f.read(n)
        if len(b)!=n: raise ValueError("Truncated NBT")
        return b
    def i32(self):
        return struct.unpack(">i", self.get(4))[0]
    def str(self):
        n=struct.unpack(">H",self.get(2))[0]
        if n>32767: raise ValueError("Oversized NBT string")
        return self.get(n).decode("utf-8","replace")
    def payload(self,t,depth=0):
        if depth>48: raise ValueError("NBT nesting too deep")
        if t in BASIC:
            fmt=BASIC[t]
            return struct.unpack(fmt,self.get(struct.calcsize(fmt)))[0]
        if t==7:
            n=self.i32()
            if n<0 or n>50_000_000: raise ValueError("Huge byte array")
            return self.get(n)
        if t==8: return self.str()
        if t==9:
            inner=self.get(1)[0]
            n=self.i32()
            if n<0 or n>1_000_000: raise ValueError("Huge list")
            return [self.payload(inner,depth+1) for _ in range(n)]
        if t==10:
            d={}
            while True:
                x=self.get(1)[0]
                if not x: return d
                key=self.str()
                d[key]=self.payload(x,depth+1)
        if t in (11,12):
            n=self.i32()
            if n<0 or n>50_000_000: raise ValueError("Huge integer array")
            size=4 if t==11 else 8
            self.get(n*size)
            return {"count":n,"byte_length":n*size}
        raise ValueError("Unknown NBT type: "+str(t))
    def root(self):
        if self.get(1)!=b"\x0a": raise ValueError("Root is not a compound")
        return self.str(),self.payload(10)

def inspect(blob):
    if blob[:2]==b"\x1f\x8b": raw=gzip.decompress(blob)
    elif len(blob)>2 and blob[0]==0x78: raw=zlib.decompress(blob)
    else: raw=blob
    if len(raw)>64_000_000: raise ValueError("Unpacked NBT over 64 MB")
    name,root=Reader(raw).root()
    legacy=("Blocks" in root and "Data" in root)
    node=root.get("Schematic",root)
    dims={key:int(node.get(key,0)) for key in ("Width","Height","Length")}
    report={"format":"legacy" if legacy else "sponge","root":name,
            "dimensions_xyz":[dims["Width"],dims["Height"],dims["Length"]],
            "volume":dims["Width"]*dims["Height"]*dims["Length"]}
    if legacy:
        counts=collections.Counter(node.get("Blocks",b""))
        report.update({
            "non_air":sum(v for k,v in counts.items() if k!=0),
            "legacy_numeric_ids_top20":{str(k):v for k,v in counts.most_common(20)},
            "tile_entities":len(node.get("TileEntities",[])),
            "entities":len(node.get("Entities",[])),
            "status":"REQUIRES_LEGACY_BLOCKSTATE_CONVERSION",
            "forge_1_20_1_ready":False})
    else:
        palette=node.get("Palette",{})
        report.update({
            "version":node.get("Version"),"data_version":node.get("DataVersion"),
            "palette_size":len(palette),
            "block_namespaces":sorted(set(s.split(":")[0] for s in palette)),
            "status":"REQUIRES_IN_GAME_VALIDATION",
            "forge_1_20_1_ready":None})
    return report

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("files",nargs="+")
    args=ap.parse_args()
    results=[]
    for path in args.files:
        try:
            with open(path,"rb") as f: raw=f.read()
            if len(raw)>32_000_000: raise ValueError("File > 32 MB")
            result={"path":path,"file_size_bytes":len(raw),
                    "sha256":hashlib.sha256(raw).hexdigest(),**inspect(raw)}
        except Exception as e:result={"path":path,"error":str(e)}
        results.append(result)
    print(json.dumps(results,indent=2,ensure_ascii=False))
    return 1 if any("error" in r for r in results) else 0

if __name__=="__main__":sys.exit(main())
