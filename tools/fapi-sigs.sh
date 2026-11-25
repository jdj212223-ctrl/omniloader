#!/bin/sh
# Prints Fabric API public signatures with Minecraft names translated to Mojang's.
# usage: tools/fapi-sigs.sh <module-jar-name-part> <class-path-prefix>...
cd "$(dirname "$0")/../reference/fapi-1.21.1" || exit 1
T=$(ls ../../versions/1.21.1/devrun/run-server/.omniloader/mappings/intermediary-to-mojang-1.21.1.txt)
J=$(ls META-INF/jars/*$1*.jar | head -1); shift
mkdir -p x && cd x && unzip -oq "../$J" && cd ..
for p in "$@"; do
  find x -path "x/$p*" -name "*.class" | sed 's|^x/||; s|\.class$||' | sort | while read c; do
    javap -cp x -public "$c" 2>/dev/null
  done
done | python3 -I -c '
import sys,re
C={}; M={}; F={}
for l in open(sys.argv[1]):
    p=l.rstrip("\n").split("\t")
    if p[0]=="c": C[p[2].replace("/",".")]=p[3].replace("/",".")
    elif p[0]=="m": M[p[1]]=p[2]
    elif p[0]=="f": F[p[1]]=p[2]
def cls(m):
    n=m.group()
    if n in C: return C[n]
    if "$" in n:
        o,i=n.rsplit("$",1)
        return cls(type("m",(),{"group":lambda s,o=o:o})())+"$"+i
    return n
for line in sys.stdin:
    line=re.sub(r"net\.minecraft\.class_\d+(?:\$class_\d+)*", cls, line)
    line=re.sub(r"\b(method|field|comp)_\d+\b", lambda m: M.get(m.group(), F.get(m.group(), m.group())), line)
    if line.startswith("Compiled from"): continue
    sys.stdout.write(line.replace("net.minecraft.","").replace("java.lang.","").replace("java.util.",""))
' "$T"
rm -rf x
