"""Generates Fabric API event classes (Omniloader stand-ins) from a compact list.
Run: python3 tools/gen-fabric-events.py   (writes into runtime/src/main/java)
Combine rules for listener results:
  void    -> call every listener
  allow   -> boolean, false wins (stop at first false)
  any     -> boolean, true wins
  result  -> InteractionResult, first non-PASS wins (else PASS)
  holder  -> InteractionResultHolder<ItemStack>, first non-PASS wins (else pass with the held item)
  first   -> object, first non-null wins (else null)
  chain:N -> listeners transform argument N (0-based) in turn; returns the final value
"""
import os, re
ROOT = os.path.join(os.path.dirname(__file__), '..', 'runtime', 'src', 'main', 'java')

# (package, outer class or None for callback-interface, kind, javadoc, [ (FIELD, Iface, method, return, params, combine) ], imports)
SPECS = []
def ev(pkg, outer, doc, events, imports, per_key=None):
    SPECS.append((pkg, outer, doc, events, imports, per_key))

I_COMMON = ['net.minecraft.core.BlockPos', 'net.minecraft.core.Direction', 'net.minecraft.world.InteractionHand', 'net.minecraft.world.InteractionResult',
            'net.minecraft.world.entity.Entity', 'net.minecraft.world.entity.player.Player', 'net.minecraft.world.level.Level']

# ---- event/player (callback interfaces: the interface itself holds EVENT)
for name, method, ret, params, comb, extra in [
    ('AttackBlockCallback', 'interact', 'InteractionResult', 'Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction', 'result', []),
    ('AttackEntityCallback', 'interact', 'InteractionResult', 'Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hitResult', 'result', ['net.minecraft.world.phys.EntityHitResult']),
    ('UseBlockCallback', 'interact', 'InteractionResult', 'Player player, Level world, InteractionHand hand, BlockHitResult hitResult', 'result', ['net.minecraft.world.phys.BlockHitResult']),
    ('UseEntityCallback', 'interact', 'InteractionResult', 'Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hitResult', 'result', ['net.minecraft.world.phys.EntityHitResult']),
    ('UseItemCallback', 'interact', 'InteractionResultHolder<ItemStack>', 'Player player, Level world, InteractionHand hand', 'holder', ['net.minecraft.world.InteractionResultHolder', 'net.minecraft.world.item.ItemStack']),
]:
    ev('net.fabricmc.fabric.api.event.player', None, 'Fabric API callback', [('EVENT', name, method, ret, params, comb)], I_COMMON + extra)

BS = 'Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity'
ev('net.fabricmc.fabric.api.event.player', 'PlayerBlockBreakEvents', 'Fabric API: a player breaking a block', [
    ('BEFORE', 'Before', 'beforeBlockBreak', 'boolean', BS, 'allow'),
    ('AFTER', 'After', 'afterBlockBreak', 'void', BS, 'void'),
    ('CANCELED', 'Canceled', 'onBlockBreakCanceled', 'void', BS, 'void'),
], ['net.minecraft.core.BlockPos', 'net.minecraft.world.entity.player.Player', 'net.minecraft.world.level.Level',
    'net.minecraft.world.level.block.entity.BlockEntity', 'net.minecraft.world.level.block.state.BlockState'])

I_ENT = ['net.minecraft.core.BlockPos', 'net.minecraft.core.Direction', 'net.minecraft.world.InteractionResult', 'net.minecraft.world.damagesource.DamageSource',
         'net.minecraft.world.entity.Entity', 'net.minecraft.world.entity.LivingEntity', 'net.minecraft.world.entity.Mob', 'net.minecraft.world.entity.player.Player',
         'net.minecraft.server.level.ServerLevel', 'net.minecraft.server.level.ServerPlayer', 'net.minecraft.world.level.block.state.BlockState', 'net.minecraft.world.phys.Vec3']
P = 'net.fabricmc.fabric.api.entity.event.v1'
ev(P, 'ServerLivingEntityEvents', 'Fabric API: mobs taking damage and dying (server)', [
    ('ALLOW_DAMAGE', 'AllowDamage', 'allowDamage', 'boolean', 'LivingEntity entity, DamageSource source, float amount', 'allow'),
    ('AFTER_DAMAGE', 'AfterDamage', 'afterDamage', 'void', 'LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked', 'void'),
    ('ALLOW_DEATH', 'AllowDeath', 'allowDeath', 'boolean', 'LivingEntity entity, DamageSource damageSource, float damageAmount', 'allow'),
    ('AFTER_DEATH', 'AfterDeath', 'afterDeath', 'void', 'LivingEntity entity, DamageSource damageSource', 'void'),
    ('MOB_CONVERSION', 'MobConversion', 'onConversion', 'void', 'Mob previous, Mob converted, boolean keepEquipment', 'void'),
], I_ENT)
ev(P, 'ServerPlayerEvents', 'Fabric API: player join/leave/respawn (server)', [
    ('COPY_FROM', 'CopyFrom', 'copyFromPlayer', 'void', 'ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive', 'void'),
    ('AFTER_RESPAWN', 'AfterRespawn', 'afterRespawn', 'void', 'ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive', 'void'),
    ('JOIN', 'Join', 'onJoin', 'void', 'ServerPlayer player', 'void'),
    ('LEAVE', 'Leave', 'onLeave', 'void', 'ServerPlayer player', 'void'),
    ('ALLOW_DEATH', 'AllowDeath', 'allowDeath', 'boolean', 'ServerPlayer player, DamageSource damageSource, float damageAmount', 'allow'),
], I_ENT)
ev(P, 'ServerEntityCombatEvents', 'Fabric API: something killed something (server)', [
    ('AFTER_KILLED_OTHER_ENTITY', 'AfterKilledOtherEntity', 'afterKilledOtherEntity', 'void', 'ServerLevel world, Entity entity, LivingEntity killedEntity', 'void'),
], I_ENT)
ev(P, 'ServerEntityWorldChangeEvents', 'Fabric API: entities/players moving between dimensions (server)', [
    ('AFTER_ENTITY_CHANGE_WORLD', 'AfterEntityChange', 'afterChangeWorld', 'void', 'Entity originalEntity, Entity newEntity, ServerLevel origin, ServerLevel destination', 'void'),
    ('AFTER_PLAYER_CHANGE_WORLD', 'AfterPlayerChange', 'afterChangeWorld', 'void', 'ServerPlayer player, ServerLevel origin, ServerLevel destination', 'void'),
], I_ENT)
ev(P, 'EntityElytraEvents', 'Fabric API: elytra flight (provided; not fired yet)', [
    ('ALLOW', 'Allow', 'allowElytraFlight', 'boolean', 'LivingEntity entity', 'allow'),
    ('CUSTOM', 'Custom', 'useCustomElytra', 'boolean', 'LivingEntity entity, boolean tickElytra', 'any'),
], I_ENT)
ev(P, 'EntitySleepEvents', 'Fabric API: sleeping in beds (start/stop/allow are fired; the rest are provided but not fired yet)', [
    ('ALLOW_SLEEPING', 'AllowSleeping', 'allowSleep', 'Player.BedSleepingProblem', 'Player player, BlockPos sleepingPos', 'first'),
    ('START_SLEEPING', 'StartSleeping', 'onStartSleeping', 'void', 'LivingEntity entity, BlockPos sleepingPos', 'void'),
    ('STOP_SLEEPING', 'StopSleeping', 'onStopSleeping', 'void', 'LivingEntity entity, BlockPos sleepingPos', 'void'),
    ('ALLOW_BED', 'AllowBed', 'allowBed', 'InteractionResult', 'LivingEntity entity, BlockPos sleepingPos, BlockState state, boolean vanillaResult', 'result'),
    ('ALLOW_SLEEP_TIME', 'AllowSleepTime', 'allowSleepTime', 'InteractionResult', 'Player player, BlockPos sleepingPos, boolean vanillaResult', 'result'),
    ('ALLOW_NEARBY_MONSTERS', 'AllowNearbyMonsters', 'allowNearbyMonsters', 'InteractionResult', 'Player player, BlockPos sleepingPos, boolean vanillaResult', 'result'),
    ('ALLOW_RESETTING_TIME', 'AllowResettingTime', 'allowResettingTime', 'boolean', 'Player player', 'allow'),
    ('MODIFY_SLEEPING_DIRECTION', 'ModifySleepingDirection', 'modifySleepDirection', 'Direction', 'LivingEntity entity, BlockPos sleepingPos, Direction sleepingDirection', 'chain:2'),
    ('ALLOW_SETTING_SPAWN', 'AllowSettingSpawn', 'allowSettingSpawn', 'boolean', 'Player player, BlockPos sleepingPos', 'allow'),
    ('SET_BED_OCCUPATION_STATE', 'SetBedOccupationState', 'setBedOccupationState', 'boolean', 'LivingEntity entity, BlockPos sleepingPos, BlockState bedState, boolean occupied', 'any'),
    ('MODIFY_WAKE_UP_POSITION', 'ModifyWakeUpPosition', 'modifyWakeUpPosition', 'Vec3', 'LivingEntity entity, BlockPos sleepingPos, BlockState bedState, Vec3 wakeUpPos', 'chain:3'),
], I_ENT)

P = 'net.fabricmc.fabric.api.event.lifecycle.v1'
ev(P, 'CommonLifecycleEvents', 'Fabric API: tags (re)loaded', [
    ('TAGS_LOADED', 'TagsLoaded', 'onTagsLoaded', 'void', 'RegistryAccess registries, boolean client', 'void'),
], ['net.minecraft.core.RegistryAccess'])
ev(P, 'ServerEntityEvents', 'Fabric API: entities appearing/disappearing in server worlds', [
    ('ENTITY_LOAD', 'Load', 'onLoad', 'void', 'Entity entity, ServerLevel world', 'void'),
    ('ENTITY_UNLOAD', 'Unload', 'onUnload', 'void', 'Entity entity, ServerLevel world', 'void'),
    ('EQUIPMENT_CHANGE', 'EquipmentChange', 'onChange', 'void', 'LivingEntity livingEntity, EquipmentSlot equipmentSlot, ItemStack previousStack, ItemStack currentStack', 'void'),
], ['net.minecraft.world.entity.Entity', 'net.minecraft.world.entity.LivingEntity', 'net.minecraft.world.entity.EquipmentSlot', 'net.minecraft.world.item.ItemStack', 'net.minecraft.server.level.ServerLevel'])
ev(P, 'ServerWorldEvents', 'Fabric API: server worlds loading/unloading', [
    ('LOAD', 'Load', 'onWorldLoad', 'void', 'MinecraftServer server, ServerLevel world', 'void'),
    ('UNLOAD', 'Unload', 'onWorldUnload', 'void', 'MinecraftServer server, ServerLevel world', 'void'),
], ['net.minecraft.server.MinecraftServer', 'net.minecraft.server.level.ServerLevel'])
ev('net.fabricmc.fabric.api.client.event.lifecycle.v1', 'ClientLifecycleEvents', 'Fabric API: the client finished starting / is stopping', [
    ('CLIENT_STARTED', 'ClientStarted', 'onClientStarted', 'void', 'Minecraft client', 'void'),
    ('CLIENT_STOPPING', 'ClientStopping', 'onClientStopping', 'void', 'Minecraft client', 'void'),
], ['net.minecraft.client.Minecraft'])

# ---- screens: global + per-screen events
P = 'net.fabricmc.fabric.api.client.screen.v1'
SCR = ['net.minecraft.client.Minecraft', 'net.minecraft.client.gui.GuiGraphics', 'net.minecraft.client.gui.screens.Screen']
ev(P, 'ScreenEvents', 'Fabric API: screens opening, drawing, ticking and closing', [
    ('BEFORE_INIT', 'BeforeInit', 'beforeInit', 'void', 'Minecraft client, Screen screen, int scaledWidth, int scaledHeight', 'void'),
    ('AFTER_INIT', 'AfterInit', 'afterInit', 'void', 'Minecraft client, Screen screen, int scaledWidth, int scaledHeight', 'void'),
    ('@remove', 'Remove', 'onRemove', 'void', 'Screen screen', 'void'),
    ('@beforeRender', 'BeforeRender', 'beforeRender', 'void', 'Screen screen, GuiGraphics drawContext, int mouseX, int mouseY, float tickDelta', 'void'),
    ('@afterRender', 'AfterRender', 'afterRender', 'void', 'Screen screen, GuiGraphics drawContext, int mouseX, int mouseY, float tickDelta', 'void'),
    ('@beforeTick', 'BeforeTick', 'beforeTick', 'void', 'Screen screen', 'void'),
    ('@afterTick', 'AfterTick', 'afterTick', 'void', 'Screen screen', 'void'),
], SCR)
K = 'Screen screen, int key, int scancode, int modifiers'
ev(P, 'ScreenKeyboardEvents', 'Fabric API: keys pressed/released in a screen', [
    ('@allowKeyPress', 'AllowKeyPress', 'allowKeyPress', 'boolean', K, 'allow'),
    ('@beforeKeyPress', 'BeforeKeyPress', 'beforeKeyPress', 'void', K, 'void'),
    ('@afterKeyPress', 'AfterKeyPress', 'afterKeyPress', 'void', K, 'void'),
    ('@allowKeyRelease', 'AllowKeyRelease', 'allowKeyRelease', 'boolean', K, 'allow'),
    ('@beforeKeyRelease', 'BeforeKeyRelease', 'beforeKeyRelease', 'void', K, 'void'),
    ('@afterKeyRelease', 'AfterKeyRelease', 'afterKeyRelease', 'void', K, 'void'),
], SCR)
MC_ = 'Screen screen, double mouseX, double mouseY, int button'
MS = 'Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount'
ev(P, 'ScreenMouseEvents', 'Fabric API: mouse clicks/scrolls in a screen', [
    ('@allowMouseClick', 'AllowMouseClick', 'allowMouseClick', 'boolean', MC_, 'allow'),
    ('@beforeMouseClick', 'BeforeMouseClick', 'beforeMouseClick', 'void', MC_, 'void'),
    ('@afterMouseClick', 'AfterMouseClick', 'afterMouseClick', 'void', MC_, 'void'),
    ('@allowMouseRelease', 'AllowMouseRelease', 'allowMouseRelease', 'boolean', MC_, 'allow'),
    ('@beforeMouseRelease', 'BeforeMouseRelease', 'beforeMouseRelease', 'void', MC_, 'void'),
    ('@afterMouseRelease', 'AfterMouseRelease', 'afterMouseRelease', 'void', MC_, 'void'),
    ('@allowMouseScroll', 'AllowMouseScroll', 'allowMouseScroll', 'boolean', MS, 'allow'),
    ('@beforeMouseScroll', 'BeforeMouseScroll', 'beforeMouseScroll', 'void', MS, 'void'),
    ('@afterMouseScroll', 'AfterMouseScroll', 'afterMouseScroll', 'void', MS, 'void'),
], SCR)

def names(params):
    return [p.strip().split()[-1] for p in params.split(',')]

def invoker(iface, method, ret, params, comb):
    a = ', '.join(names(params))
    lam = '(' + a + ')' if len(names(params)) != 1 else a
    if comb == 'void':
        body = f'for ({iface} l : ls) l.{method}({a});'
    elif comb == 'allow':
        body = f'for ({iface} l : ls) if (!l.{method}({a})) return false; return true;'
    elif comb == 'any':
        body = f'for ({iface} l : ls) if (l.{method}({a})) return true; return false;'
    elif comb == 'result':
        body = f'for ({iface} l : ls) {{ InteractionResult r = l.{method}({a}); if (r != InteractionResult.PASS) return r; }} return InteractionResult.PASS;'
    elif comb == 'holder':
        body = f'for ({iface} l : ls) {{ var r = l.{method}({a}); if (r.getResult() != InteractionResult.PASS) return r; }} return InteractionResultHolder.pass(player.getItemInHand(hand));'
    elif comb == 'first':
        body = f'for ({iface} l : ls) {{ var r = l.{method}({a}); if (r != null) return r; }} return null;'
    elif comb.startswith('chain:'):
        n = names(params); i = int(comb.split(':')[1]); v = n[i]
        args = ', '.join(n)
        body = f'{ret} cur = {v}; for ({iface} l : ls) {{ {v} = cur; cur = l.{method}({args}); }} return cur;'
        # parameters are effectively final in lambdas; rebuild with a local copy
        body = f'{ret} cur = {v}; for ({iface} l : ls) cur = l.{method}({", ".join("cur" if x == v else x for x in n)}); return cur;'
    return f'ls -> {lam} -> {{ {body} }}'

for pkg, outer, doc, events, imports, _ in SPECS:
    d = os.path.join(ROOT, *pkg.split('.'))
    os.makedirs(d, exist_ok=True)
    imps = sorted(set(imports + ['net.fabricmc.fabric.api.event.Event', 'net.fabricmc.fabric.api.event.EventFactory']))
    head = f'package {pkg};\n\n' + ''.join(f'import {i};\n' for i in imps if not i.startswith(pkg + '.')) + '\n'
    if outer is None:
        field, iface, method, ret, params, comb = events[0]
        src = head + f'/** {doc} (Omniloader stand-in; generated by tools/gen-fabric-events.py). */\n@FunctionalInterface\npublic interface {iface} {{\n'
        src += f'    Event<{iface}> EVENT = EventFactory.createArrayBacked({iface}.class, {invoker(iface, method, ret, params, comb)});\n\n'
        src += f'    {ret} {method}({params});\n}}\n'
        open(os.path.join(d, iface + '.java'), 'w').write(src)
        continue
    per = any(e[0].startswith('@') for e in events)
    src = head
    if per:
        src = src.replace('\n\n', '\nimport java.util.Map;\nimport java.util.WeakHashMap;\n\n', 1)
    src += f'/** {doc} (Omniloader stand-in; generated by tools/gen-fabric-events.py). */\npublic final class {outer} {{\n    private {outer}() {{}}\n\n'
    if per:
        src += '    /** Per-screen events, forgotten when the screen goes away. */\n    private static final Map<Screen, Map<String, Event<?>>> PER_SCREEN = new WeakHashMap<>();\n\n'
        src += '    @SuppressWarnings("unchecked")\n    private static synchronized <T> Event<T> of(Screen s, String k, java.util.function.Supplier<Event<T>> f) {\n'
        src += '        return (Event<T>) PER_SCREEN.computeIfAbsent(s, x -> new java.util.HashMap<>()).computeIfAbsent(k, x -> f.get());\n    }\n\n'
        src += '    /** Omniloader: drop a screen\'s per-screen listeners (Fabric does this each time a screen is set up again). */\n'
        src += '    public static synchronized void clear(Screen s) { PER_SCREEN.remove(s); }\n\n'
        src += '    /** Omniloader: the per-screen event if anyone registered on it. */\n'
        src += '    @SuppressWarnings("unchecked")\n    public static synchronized <T> Event<T> peek(Screen s, String k) { var m = PER_SCREEN.get(s); return m == null ? null : (Event<T>) m.get(k); }\n\n'
    for field, iface, method, ret, params, comb in events:
        inv = invoker(iface, method, ret, params, comb)
        if field.startswith('@'):
            k = field[1:]
            src += f'    public static Event<{iface}> {k}(Screen forScreen) {{ return of(forScreen, "{k}", () -> EventFactory.createArrayBacked({iface}.class, {inv})); }}\n'
        else:
            src += f'    public static final Event<{iface}> {field} = EventFactory.createArrayBacked({iface}.class, {inv});\n'
    src += '\n'
    for field, iface, method, ret, params, comb in events:
        src += f'    @FunctionalInterface public interface {iface} {{ {ret} {method}({params}); }}\n'
    src += '}\n'
    open(os.path.join(d, outer + '.java'), 'w').write(src)
print('generated', len(SPECS), 'files')
