"""Starts Minecraft 1.21.1 on Omniloader's OWN engine: vanilla game files + Omniloader, no NeoForge, no Fabric Loader.
Reads a launcher's vanilla/Fabric 1.21.1 recipe only for the plain libraries (LWJGL, Netty, Mixin...); Fabric Loader and
intermediary are left out. Read-only use of the launcher's files; the game runs in its own folder. Offline name "Dev".

usage: [OMNI_SERVER=1] python3 tools/enginelaunch.py <launcher-meta-dir> <version-id> <java> <game-dir> <engine-jar> [extra jvm args ...]"""
import json, os, platform, subprocess, sys, uuid

meta, version, java, game_dir, engine = sys.argv[1:6]
extra = sys.argv[6:]
vdir = os.path.join(meta, 'versions', version)
v = json.load(open(os.path.join(vdir, version + '.json')))
if 'assetIndex' not in v:   # NeoForge recipes from some launchers leave this out; 1.21.1 uses asset index 17
    v['assetIndex'] = {'id': '17'}
libdir = os.path.join(meta, 'libraries')
natives = os.path.join(meta, 'natives', version)
arch = 'arm64' if platform.machine() == 'arm64' else 'x86_64'
SKIP = ('net.fabricmc:fabric-loader', 'net.fabricmc:intermediary',
        # NeoForge's own loader (FML, ModLauncher...) and installer tools: Omniloader replaces these
        'net.neoforged.fancymodloader:', 'cpw.mods:', 'net.neoforged:coremods', 'net.neoforged.accesstransformers:', 'net.neoforged:accesstransformers',
        'net.neoforged:JarJar', 'org.openjdk.nashorn:', 'net.neoforged:neoforge:', 'net.neoforged:neoform', 'net.neoforged.installertools:',
        'net.neoforged:AutoRenamingTool', 'net.neoforged:srgutils', 'net.md-5:', 'de.siegmar:', 'com.opencsv:', 'com.modrinth.daedalus:')

def allowed(rules):
    if not rules: return True
    ok = False
    for r in rules:
        osr = r.get('os', {})
        if (not osr.get('name') or osr['name'] == 'osx') and (not osr.get('arch') or osr['arch'] == arch) and not r.get('features'):
            ok = r['action'] == 'allow'
    return ok

cp, seen = [], set()
for lib in v['libraries']:
    if not allowed(lib.get('rules')) or lib['name'].startswith(SKIP): continue
    ga = ':'.join(lib['name'].split(':')[:2] + lib['name'].split(':')[3:4])
    if ga in seen: continue
    seen.add(ga)
    art = lib.get('downloads', {}).get('artifact')
    if art and art.get('path'): p = os.path.join(libdir, art['path'])
    else:
        g, a, ver = lib['name'].split(':')[:3]
        p = os.path.join(libdir, g.replace('.', '/'), a, ver, f'{a}-{ver}.jar')
    if os.path.isfile(p) and p not in cp: cp.append(p)
cp.insert(0, os.path.abspath(engine))
if os.environ.get('OMNI_LIKE_PRISM') == '1':   # like Prism / the Minecraft Launcher: plain game jar on the class path, no library hint
    cp.append(os.path.join(meta, 'versions', '1.21.1-0.16.4', '1.21.1-0.16.4.jar'))
game_jar = os.path.join(vdir, version + '.jar')   # vanilla client jar; NOT on the class path: Omniloader loads it itself
os.makedirs(game_dir, exist_ok=True)
game = ['--username', 'Dev', '--version', 'omniloader-engine', '--gameDir', game_dir, '--assetsDir', os.path.join(meta, 'assets'),
        '--assetIndex', v.get('assetIndex', {}).get('id', v.get('assets', '')), '--uuid', uuid.uuid4().hex, '--accessToken', '0',
        '--userType', 'legacy', '--versionType', 'release']
server = os.environ.get('OMNI_SERVER') == '1'
if server: game = ['--nogui']
import shlex
game += shlex.split(os.environ.get('OMNI_GAME_ARGS', ''))   # e.g. --quickPlaySingleplayer "My World"
cmd = [java, '-Xmx3G'] + ([] if server else ['-XstartOnFirstThread']) + [ '-Djava.library.path=' + natives, '-Dorg.lwjgl.system.SharedLibraryExtractPath=' + natives,
       ] + ([] if os.environ.get('OMNI_LIKE_PRISM') == '1' else ['-Domniloader.gameJar=' + game_jar, '-DlibraryDirectory=' + libdir]) + extra + ['-cp', os.pathsep.join(cp), 'dev.omniloader.engine.ServerMain' if server else 'dev.omniloader.engine.ClientMain'] + game
sys.exit(subprocess.call(cmd, cwd=game_dir))
