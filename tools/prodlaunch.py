"""Starts a *production* Minecraft from a launcher's version recipe (here: the Modrinth App's NeoForge 1.21.1 install),
the way a real launcher does, with Omniloader added as a library (= installed as a loader, not as a mod).
Read-only use of the launcher's files; the game runs in its own folder. Offline name "Dev".

usage: python3 tools/prodlaunch.py <launcher-meta-dir> <version-id> <java> <game-dir> <omniloader-jar|-> [extra -Dkey=value ...]"""
import json, os, platform, subprocess, sys, uuid

meta, version, java, game_dir, omni = sys.argv[1:6]
extra = sys.argv[6:]
vdir = os.path.join(meta, 'versions', version)
v = json.load(open(os.path.join(vdir, version + '.json')))
libdir = os.path.join(meta, 'libraries')
natives = os.path.join(meta, 'natives', version)
arch = 'arm64' if platform.machine() == 'arm64' else 'x86_64'

def allowed(rules):
    if not rules: return True
    ok = False
    for r in rules:
        osr = r.get('os', {})
        match = (not osr.get('name') or osr['name'] == 'osx') and (not osr.get('arch') or osr['arch'] == arch) and not r.get('features')
        if match: ok = r['action'] == 'allow'
    return ok

cp = []
seen_artifacts = set()
for lib in v['libraries']:
    if not allowed(lib.get('rules')): continue
    ga = ':'.join(lib['name'].split(':')[:2] + lib['name'].split(':')[3:4])  # group:artifact(:classifier)
    if ga in seen_artifacts: continue  # like launchers: first one wins (NeoForge's newer libraries come first)
    seen_artifacts.add(ga)
    art = lib.get('downloads', {}).get('artifact')
    if art and art.get('path'): p = os.path.join(libdir, art['path'])
    else:
        g, a, ver = lib['name'].split(':')[:3]
        p = os.path.join(libdir, g.replace('.', '/'), a, ver, f'{a}-{ver}.jar')
    if os.path.isfile(p) and p not in cp: cp.append(p)
# Jars put on Java's module path (-p) must not also be on the classpath.
modpath = set()
jl = v['arguments']['jvm']
for i, x in enumerate(jl):
    if x == '-p' and i + 1 < len(jl):
        for part in jl[i + 1].replace('${classpath_separator}', '|').split('|'):
            modpath.add(os.path.basename(part.replace('${library_directory}', libdir)))
cp = [c for c in cp if os.path.basename(c) not in modpath]
if omni != '-': cp.append(os.path.abspath(omni))      # Omniloader, installed as a loader library
# NeoForge's production start also expects Minecraft's resource jar ("client-extra") on the classpath, like the Modrinth App adds it.
for i, x in enumerate(v['arguments']['game']):
    if x == '--fml.neoFormVersion':
        nf = v['arguments']['game'][i + 1]; mc = [v['arguments']['game'][j + 1] for j, y in enumerate(v['arguments']['game']) if y == '--fml.mcVersion'][0]
        extra_jar = os.path.join(libdir, 'net/minecraft/client', f'{mc}-{nf}', f'client-{mc}-{nf}-extra.jar')
        if os.path.isfile(extra_jar): cp.append(extra_jar)
cp.append(os.path.join(vdir, version + '.jar'))
sep = os.pathsep
subst = {'natives_directory': natives, 'launcher_name': 'omniloader-test', 'launcher_version': '1', 'classpath': sep.join(cp),
         'library_directory': libdir, 'classpath_separator': sep, 'version_name': version,
         'auth_player_name': 'Dev', 'version_type': 'release', 'game_directory': game_dir, 'assets_root': os.path.join(meta, 'assets'),
         'assets_index_name': v.get('assetIndex', {}).get('id', v.get('assets', '')), 'auth_uuid': uuid.uuid4().hex, 'auth_access_token': '0',
         'clientid': '0', 'auth_xuid': '0', 'user_type': 'legacy', 'resolution_width': '854', 'resolution_height': '480'}
def expand(a):
    for k, val in subst.items(): a = a.replace('${' + k + '}', val)
    return a
def args(lst):
    out = []
    for x in lst:
        if isinstance(x, str): out.append(expand(x))
        elif allowed(x.get('rules')):
            val = x['value']; out += [expand(y) for y in (val if isinstance(val, list) else [val])]
    return out
jvm = args(v['arguments']['jvm']); game = args(v['arguments']['game'])
os.makedirs(game_dir, exist_ok=True)
cmd = [java, '-Xmx3G', '-XstartOnFirstThread'] + extra + jvm + [v['mainClass']] + game
sys.exit(subprocess.call(cmd, cwd=game_dir))
