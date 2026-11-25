"""Builds Omniloader's Forge -> NeoForge class table by matching names against NeoForge's real class list.
usage: python3 tools/forge-map.py <neoforge-class-list> <forge-mod-jars...>   (prints the table + what's unmatched)"""
import sys, re, zipfile, collections
neo = set(open(sys.argv[1]).read().split())
PREFIX = [
    ('net/minecraftforge/fml/common/', 'net/neoforged/fml/common/'),
    ('net/minecraftforge/fml/', 'net/neoforged/fml/'),
    ('net/minecraftforge/eventbus/api/', 'net/neoforged/bus/api/'),
    ('net/minecraftforge/api/distmarker/', 'net/neoforged/api/distmarker/'),
    ('net/minecraftforge/forgespi/', 'net/neoforged/neoforgespi/'),
    ('net/minecraftforge/', 'net/neoforged/neoforge/'),
]
SPECIAL = {
    'net/minecraftforge/common/MinecraftForge': 'net/neoforged/neoforge/common/NeoForge',
    'net/minecraftforge/common/ForgeConfigSpec': 'net/neoforged/neoforge/common/ModConfigSpec',

    'net/minecraftforge/common/ForgeHooks': 'net/neoforged/neoforge/common/CommonHooks',
    'net/minecraftforge/client/ForgeHooksClient': 'net/neoforged/neoforge/client/ClientHooks',
    'net/minecraftforge/fml/common/Mod$EventBusSubscriber': 'net/neoforged/fml/common/EventBusSubscriber',
    'net/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus': 'net/neoforged/fml/common/EventBusSubscriber$Bus',

    'net/minecraftforge/eventbus/api/Cancelable': 'net/neoforged/bus/api/ICancellableEvent',
    'net/minecraftforge/common/capabilities/ForgeCapabilities': None,
}
# Kept as Forge-shaped stand-ins provided by Omniloader (their NeoForge twins work differently).
KEEP = ('net/minecraftforge/registries/DeferredRegister', 'net/minecraftforge/registries/RegistryObject', 'net/minecraftforge/registries/IForgeRegistry',
        'net/minecraftforge/registries/ForgeRegistries', 'net/minecraftforge/fml/ModLoadingContext', 'net/minecraftforge/fml/DistExecutor',
        'net/minecraftforge/fml/IExtensionPoint$DisplayTest', 'net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext',
        'net/minecraftforge/client/ConfigScreenHandler', 'net/minecraftforge/event/TickEvent', 'net/minecraftforge/common/util/NonNull')
def ext(n):  # IForgeItem -> IItemExtension
    m = re.match(r'(.*/)IForge([A-Z]\w*)$', n)
    return m.group(1) + 'I' + m.group(2) + 'Extension' if m else None
def target(c):
    if c.startswith(KEEP): return None
    if c in SPECIAL: return SPECIAL[c]
    o = c.split('$')[0]
    if o in SPECIAL and SPECIAL[o]: return SPECIAL[o] + c[len(o):]
    outer, _, inner = c.partition('$')
    for a, b in PREFIX:
        if c.startswith(a):
            t = b + c[len(a):]
            if t in neo: return t
            e = ext(b + outer[len(a):])
            if e and (e + ('$' + inner if inner else '')) in neo: return e + ('$' + inner if inner else '')
    return None
refs = collections.Counter()
for j in sys.argv[2:]:
    try: z = zipfile.ZipFile(j)
    except Exception: continue
    seen = set()
    for n in z.namelist():
        if n.endswith('.class'):
            for m in re.findall(rb'net/minecraftforge/[A-Za-z0-9_/$]+', z.read(n)): seen.add(m.decode())
    for s in seen: refs[s] += 1
mapped = {c: target(c) for c in refs}
ok = sum(n for c, n in refs.items() if mapped[c]); total = sum(refs.values())
print(f'# {len(refs)} Forge classes used; {sum(1 for c in refs if mapped[c])} have a NeoForge match ({100*ok//total}% of uses)')
for c, t in sorted(mapped.items()):
    if t: print('c', c, t)
print('# --- no NeoForge match (most used first)')
for c, n in refs.most_common():
    if not mapped[c]: print('#', n, c)
