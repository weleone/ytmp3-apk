#!/bin/sh
# Verify 16KB alignment of an APK:
#  1. zipalign -c -P 16 -v 4  (ZIP alignment, per developer.android.com/16kb-page-size)
#  2. ELF LOAD p_align >= 16384 for every .so inside the APK (python3, stdlib only)
# Usage: scripts/verify-16kb.sh <apk> [zipalign-path]
set -e
APK="$1"
ZIPALIGN="${2:-zipalign}"
if [ -z "$APK" ]; then echo "usage: $0 <apk> [zipalign]"; exit 2; fi
echo "== ZIP alignment =="
"$ZIPALIGN" -c -P 16 -v 4 "$APK"
echo "== ELF alignment =="
python3 - "$APK" << 'PY'
import sys, zipfile, struct, os, tempfile
apk = sys.argv[1]
def elf_align(path):
    with open(path,'rb') as f:
        d=f.read(64)
        if d[:4]!=b'\x7fELF': return None
        is64=d[4]==2
        if is64:
            phoff=struct.unpack('<Q', d[32:40])[0]
            phentsize, phnum=struct.unpack('<HH', d[54:58])
        else:
            phoff=struct.unpack('<I', d[28:32])[0]
            phentsize, phnum=struct.unpack('<HH', d[42:46])
        out=[]
        with open(path,'rb') as f2:
            f2.seek(phoff)
            for _ in range(phnum):
                h=f2.read(phentsize)
                if is64:
                    t,_,_,_,_,_,_,al=struct.unpack('<IIQQQQQQ', h[:56])
                else:
                    t,_,_,_,_,_,_,al=struct.unpack('<IIIIIIII', h[:32])
                if t==1: out.append(al)
        return out
bad=0; total=0
with zipfile.ZipFile(apk) as z, tempfile.TemporaryDirectory() as td:
    for n in sorted(x for x in z.namelist() if x.endswith('.so')):
        total+=1
        p=z.extract(n, td)
        a=elf_align(p)
        ok=a and all(x>=16384 for x in a)
        if not ok: bad+=1
        print(f"{'OK  ' if ok else 'BAD '} {n}: LOAD p_align={a}")
print(f"{total-bad}/{total} .so files 16KB-aligned")
sys.exit(1 if bad else 0)
PY
