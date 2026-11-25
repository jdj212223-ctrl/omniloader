package dev.omniloader.runtime.fabricapi;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fabric API gameplay events (interaction, combat, players, entities, worlds, tags), fired from NeoForge's. */
public final class GameEventsBridge {
    private GameEventsBridge() {}

    /** Old player object kept between NeoForge's Clone and Respawn events, for Fabric's AFTER_RESPAWN(old, new). */
    private static final Map<UUID, ServerPlayer> OLD_PLAYERS = new ConcurrentHashMap<>();

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        // ---- interaction
        bus.addListener(PlayerInteractEvent.RightClickItem.class, e -> {
            var r = UseItemCallback.EVENT.invoker().interact(e.getEntity(), e.getLevel(), e.getHand());
            if (r.getResult() != InteractionResult.PASS) { e.setCanceled(true); e.setCancellationResult(r.getResult()); }
        });
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, e -> {
            var r = UseBlockCallback.EVENT.invoker().interact(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec());
            if (r != InteractionResult.PASS) { e.setCanceled(true); e.setCancellationResult(r); }
        });
        bus.addListener(PlayerInteractEvent.EntityInteract.class, e -> {
            var r = UseEntityCallback.EVENT.invoker().interact(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget(), new EntityHitResult(e.getTarget()));
            if (r != InteractionResult.PASS) { e.setCanceled(true); e.setCancellationResult(r); }
        });
        bus.addListener(PlayerInteractEvent.LeftClickBlock.class, e -> {
            if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
            var r = AttackBlockCallback.EVENT.invoker().interact(e.getEntity(), e.getLevel(), e.getHand(), e.getPos(), e.getFace());
            if (r != InteractionResult.PASS) e.setCanceled(true);
        });
        bus.addListener(AttackEntityEvent.class, e -> {
            var r = AttackEntityCallback.EVENT.invoker().interact(e.getEntity(), e.getEntity().level(), InteractionHand.MAIN_HAND, e.getTarget(), null);
            if (r != InteractionResult.PASS) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGH, BlockEvent.BreakEvent.class, e -> {
            if (!(e.getLevel() instanceof ServerLevel level)) return;
            var pos = e.getPos();
            BlockState state = e.getState();
            var be = level.getBlockEntity(pos);
            if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, e.getPlayer(), pos, state, be)) {
                e.setCanceled(true);
                PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, e.getPlayer(), pos, state, be);
            }
        });
        bus.addListener(EventPriority.LOWEST, BlockEvent.BreakEvent.class, e -> {
            if (e.isCanceled() || !(e.getLevel() instanceof ServerLevel level)) return;
            var pos = e.getPos(); var state = e.getState(); var be = level.getBlockEntity(pos); var player = e.getPlayer();
            // Fabric fires AFTER once the block is gone: run it right after this break finishes.
            level.getServer().execute(() -> PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, be));
        });

        // ---- damage and death (server)
        bus.addListener(LivingIncomingDamageEvent.class, e -> {
            if (e.getEntity().level().isClientSide()) return;
            if (!ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(e.getEntity(), e.getSource(), e.getAmount())) e.setCanceled(true);
        });
        bus.addListener(LivingDamageEvent.Post.class, e -> {
            if (e.getEntity().level().isClientSide()) return;
            ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(e.getEntity(), e.getSource(), e.getOriginalDamage(), e.getNewDamage(), e.getBlockedDamage() > 0);
        });
        bus.addListener(EventPriority.HIGH, LivingDeathEvent.class, e -> {
            if (e.getEntity().level().isClientSide()) return;
            boolean allow = ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(e.getEntity(), e.getSource(), 0f);
            if (allow && e.getEntity() instanceof ServerPlayer p) allow = ServerPlayerEvents.ALLOW_DEATH.invoker().allowDeath(p, e.getSource(), 0f);
            if (!allow) e.setCanceled(true);
        });
        bus.addListener(EventPriority.LOWEST, LivingDeathEvent.class, e -> {
            if (e.isCanceled() || !(e.getEntity().level() instanceof ServerLevel level)) return;
            ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(e.getEntity(), e.getSource());
            if (e.getSource().getEntity() != null)
                ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.invoker().afterKilledOtherEntity(level, e.getSource().getEntity(), e.getEntity());
        });
        bus.addListener(LivingConversionEvent.Post.class, e -> {
            if (e.getEntity() instanceof Mob from && e.getOutcome() instanceof Mob to && !from.level().isClientSide())
                ServerLivingEntityEvents.MOB_CONVERSION.invoker().onConversion(from, to, true);
        });

        // ---- players (server)
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> { if (e.getEntity() instanceof ServerPlayer p) ServerPlayerEvents.JOIN.invoker().onJoin(p); });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> { if (e.getEntity() instanceof ServerPlayer p) ServerPlayerEvents.LEAVE.invoker().onLeave(p); });
        bus.addListener(PlayerEvent.Clone.class, e -> {
            if (e.getOriginal() instanceof ServerPlayer old && e.getEntity() instanceof ServerPlayer now) {
                ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(old, now, !e.isWasDeath());
                OLD_PLAYERS.put(now.getUUID(), old);
            }
        });
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer now) {
                ServerPlayer old = OLD_PLAYERS.remove(now.getUUID());
                ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(old != null ? old : now, now, e.isEndConquered());
            }
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                ServerLevel from = p.server.getLevel(e.getFrom()), to = p.server.getLevel(e.getTo());
                if (from != null && to != null) ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.invoker().afterChangeWorld(p, from, to);
            }
        });
        bus.addListener(CanPlayerSleepEvent.class, e -> {
            var problem = EntitySleepEvents.ALLOW_SLEEPING.invoker().allowSleep(e.getEntity(), e.getPos());
            if (problem != null) e.setProblem(problem);
            else if (e.getProblem() == null) EntitySleepEvents.START_SLEEPING.invoker().onStartSleeping(e.getEntity(), e.getPos());
        });
        bus.addListener(PlayerWakeUpEvent.class, e -> e.getEntity().getSleepingPos().ifPresent(pos ->
                EntitySleepEvents.STOP_SLEEPING.invoker().onStopSleeping(e.getEntity(), pos)));

        // ---- entities and worlds (server)
        bus.addListener(EntityJoinLevelEvent.class, e -> { if (e.getLevel() instanceof ServerLevel l) ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(e.getEntity(), l); });
        bus.addListener(EntityLeaveLevelEvent.class, e -> { if (e.getLevel() instanceof ServerLevel l) ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(e.getEntity(), l); });
        bus.addListener(LivingEquipmentChangeEvent.class, e -> {
            LivingEntity en = e.getEntity();
            if (!en.level().isClientSide()) ServerEntityEvents.EQUIPMENT_CHANGE.invoker().onChange(en, e.getSlot(), e.getFrom(), e.getTo());
        });
        bus.addListener(LevelEvent.Load.class, e -> { if (e.getLevel() instanceof ServerLevel l) ServerWorldEvents.LOAD.invoker().onWorldLoad(l.getServer(), l); });
        bus.addListener(LevelEvent.Unload.class, e -> { if (e.getLevel() instanceof ServerLevel l) ServerWorldEvents.UNLOAD.invoker().onWorldUnload(l.getServer(), l); });
        bus.addListener(TagsUpdatedEvent.class, e -> CommonLifecycleEvents.TAGS_LOADED.invoker().onTagsLoaded(e.getRegistryAccess(),
                e.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED));
    }
}
