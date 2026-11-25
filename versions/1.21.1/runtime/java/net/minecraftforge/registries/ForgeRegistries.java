package net.minecraftforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.StatType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

/** Forge's registry list (Omniloader stand-in over the game's and NeoForge's registries). */
public final class ForgeRegistries {
    private ForgeRegistries() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> IForgeRegistry<T> view(Supplier<Registry<?>> r) { return new ForgeRegistryView<>((Supplier) r); }

    public static final IForgeRegistry<Block> BLOCKS = view(() -> BuiltInRegistries.BLOCK);
    public static final IForgeRegistry<Fluid> FLUIDS = view(() -> BuiltInRegistries.FLUID);
    public static final IForgeRegistry<Item> ITEMS = view(() -> BuiltInRegistries.ITEM);
    public static final IForgeRegistry<MobEffect> MOB_EFFECTS = view(() -> BuiltInRegistries.MOB_EFFECT);
    public static final IForgeRegistry<SoundEvent> SOUND_EVENTS = view(() -> BuiltInRegistries.SOUND_EVENT);
    public static final IForgeRegistry<Potion> POTIONS = view(() -> BuiltInRegistries.POTION);
    public static final IForgeRegistry<EntityType<?>> ENTITY_TYPES = view(() -> BuiltInRegistries.ENTITY_TYPE);
    public static final IForgeRegistry<BlockEntityType<?>> BLOCK_ENTITY_TYPES = view(() -> BuiltInRegistries.BLOCK_ENTITY_TYPE);
    public static final IForgeRegistry<ParticleType<?>> PARTICLE_TYPES = view(() -> BuiltInRegistries.PARTICLE_TYPE);
    public static final IForgeRegistry<MenuType<?>> MENU_TYPES = view(() -> BuiltInRegistries.MENU);
    public static final IForgeRegistry<RecipeType<?>> RECIPE_TYPES = view(() -> BuiltInRegistries.RECIPE_TYPE);
    public static final IForgeRegistry<RecipeSerializer<?>> RECIPE_SERIALIZERS = view(() -> BuiltInRegistries.RECIPE_SERIALIZER);
    public static final IForgeRegistry<Attribute> ATTRIBUTES = view(() -> BuiltInRegistries.ATTRIBUTE);
    public static final IForgeRegistry<StatType<?>> STAT_TYPES = view(() -> BuiltInRegistries.STAT_TYPE);
    public static final IForgeRegistry<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES = view(() -> BuiltInRegistries.COMMAND_ARGUMENT_TYPE);
    public static final IForgeRegistry<VillagerProfession> VILLAGER_PROFESSIONS = view(() -> BuiltInRegistries.VILLAGER_PROFESSION);
    public static final IForgeRegistry<PoiType> POI_TYPES = view(() -> BuiltInRegistries.POINT_OF_INTEREST_TYPE);
    public static final IForgeRegistry<MemoryModuleType<?>> MEMORY_MODULE_TYPES = view(() -> BuiltInRegistries.MEMORY_MODULE_TYPE);
    public static final IForgeRegistry<SensorType<?>> SENSOR_TYPES = view(() -> BuiltInRegistries.SENSOR_TYPE);
    public static final IForgeRegistry<Schedule> SCHEDULES = view(() -> BuiltInRegistries.SCHEDULE);
    public static final IForgeRegistry<Activity> ACTIVITIES = view(() -> BuiltInRegistries.ACTIVITY);
    public static final IForgeRegistry<WorldCarver<?>> WORLD_CARVERS = view(() -> BuiltInRegistries.CARVER);
    public static final IForgeRegistry<Feature<?>> FEATURES = view(() -> BuiltInRegistries.FEATURE);
    public static final IForgeRegistry<ChunkStatus> CHUNK_STATUS = view(() -> BuiltInRegistries.CHUNK_STATUS);
    public static final IForgeRegistry<BlockStateProviderType<?>> BLOCK_STATE_PROVIDER_TYPES = view(() -> BuiltInRegistries.BLOCKSTATE_PROVIDER_TYPE);
    public static final IForgeRegistry<FoliagePlacerType<?>> FOLIAGE_PLACER_TYPES = view(() -> BuiltInRegistries.FOLIAGE_PLACER_TYPE);
    public static final IForgeRegistry<TreeDecoratorType<?>> TREE_DECORATOR_TYPES = view(() -> BuiltInRegistries.TREE_DECORATOR_TYPE);
    public static final IForgeRegistry<CreativeModeTab> CREATIVE_MODE_TABS = view(() -> BuiltInRegistries.CREATIVE_MODE_TAB);
    public static final IForgeRegistry<DataComponentType<?>> DATA_COMPONENT_TYPES = view(() -> BuiltInRegistries.DATA_COMPONENT_TYPE);
    public static final IForgeRegistry<DataComponentType<?>> ENCHANTMENT_EFFECT_COMPONENT_TYPES = view(() -> BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE);
    public static final IForgeRegistry<ArmorMaterial> ARMOR_MATERIALS = view(() -> BuiltInRegistries.ARMOR_MATERIAL);
    @SuppressWarnings({"unchecked", "rawtypes"}) public static final IForgeRegistry ENTITY_DATA_SERIALIZERS = view(() -> (Registry) NeoForgeRegistries.ENTITY_DATA_SERIALIZERS);
    @SuppressWarnings({"unchecked", "rawtypes"}) public static final IForgeRegistry GLOBAL_LOOT_MODIFIER_SERIALIZERS = view(() -> (Registry) NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS);
    @SuppressWarnings({"unchecked", "rawtypes"}) public static final IForgeRegistry BIOME_MODIFIER_SERIALIZERS = view(() -> (Registry) NeoForgeRegistries.BIOME_MODIFIER_SERIALIZERS);
    @SuppressWarnings({"unchecked", "rawtypes"}) public static final IForgeRegistry STRUCTURE_MODIFIER_SERIALIZERS = view(() -> (Registry) NeoForgeRegistries.STRUCTURE_MODIFIER_SERIALIZERS);
    @SuppressWarnings({"unchecked", "rawtypes"}) public static final IForgeRegistry FLUID_TYPES = view(() -> (Registry) NeoForgeRegistries.FLUID_TYPES);
    @SuppressWarnings({"unchecked", "rawtypes"}) public static final IForgeRegistry HOLDER_SET_TYPES = view(() -> (Registry) NeoForgeRegistries.HOLDER_SET_TYPES);
    @SuppressWarnings({"unchecked", "rawtypes"}) public static final IForgeRegistry CONDITION_SERIALIZERS = view(() -> (Registry) NeoForgeRegistries.CONDITION_SERIALIZERS);
    @SuppressWarnings({"unchecked", "rawtypes"}) public static final IForgeRegistry INGREDIENT_SERIALIZERS = view(() -> (Registry) NeoForgeRegistries.INGREDIENT_TYPES);

    public static final class Keys {
        private Keys() {}
        public static final ResourceKey<Registry<Block>> BLOCKS = Registries.BLOCK;
        public static final ResourceKey<Registry<Fluid>> FLUIDS = Registries.FLUID;
        public static final ResourceKey<Registry<Item>> ITEMS = Registries.ITEM;
        public static final ResourceKey<Registry<MobEffect>> MOB_EFFECTS = Registries.MOB_EFFECT;
        public static final ResourceKey<Registry<SoundEvent>> SOUND_EVENTS = Registries.SOUND_EVENT;
        public static final ResourceKey<Registry<Potion>> POTIONS = Registries.POTION;
        public static final ResourceKey<Registry<EntityType<?>>> ENTITY_TYPES = Registries.ENTITY_TYPE;
        public static final ResourceKey<Registry<BlockEntityType<?>>> BLOCK_ENTITY_TYPES = Registries.BLOCK_ENTITY_TYPE;
        public static final ResourceKey<Registry<ParticleType<?>>> PARTICLE_TYPES = Registries.PARTICLE_TYPE;
        public static final ResourceKey<Registry<MenuType<?>>> MENU_TYPES = Registries.MENU;
        public static final ResourceKey<Registry<RecipeType<?>>> RECIPE_TYPES = Registries.RECIPE_TYPE;
        public static final ResourceKey<Registry<RecipeSerializer<?>>> RECIPE_SERIALIZERS = Registries.RECIPE_SERIALIZER;
        public static final ResourceKey<Registry<Attribute>> ATTRIBUTES = Registries.ATTRIBUTE;
        public static final ResourceKey<Registry<StatType<?>>> STAT_TYPES = Registries.STAT_TYPE;
        public static final ResourceKey<Registry<ArgumentTypeInfo<?, ?>>> COMMAND_ARGUMENT_TYPES = Registries.COMMAND_ARGUMENT_TYPE;
        public static final ResourceKey<Registry<VillagerProfession>> VILLAGER_PROFESSIONS = Registries.VILLAGER_PROFESSION;
        public static final ResourceKey<Registry<PoiType>> POI_TYPES = Registries.POINT_OF_INTEREST_TYPE;
        public static final ResourceKey<Registry<MemoryModuleType<?>>> MEMORY_MODULE_TYPES = Registries.MEMORY_MODULE_TYPE;
        public static final ResourceKey<Registry<SensorType<?>>> SENSOR_TYPES = Registries.SENSOR_TYPE;
        public static final ResourceKey<Registry<Schedule>> SCHEDULES = Registries.SCHEDULE;
        public static final ResourceKey<Registry<Activity>> ACTIVITIES = Registries.ACTIVITY;
        public static final ResourceKey<Registry<WorldCarver<?>>> WORLD_CARVERS = Registries.CARVER;
        public static final ResourceKey<Registry<Feature<?>>> FEATURES = Registries.FEATURE;
        public static final ResourceKey<Registry<ChunkStatus>> CHUNK_STATUS = Registries.CHUNK_STATUS;
        public static final ResourceKey<Registry<BlockStateProviderType<?>>> BLOCK_STATE_PROVIDER_TYPES = Registries.BLOCK_STATE_PROVIDER_TYPE;
        public static final ResourceKey<Registry<FoliagePlacerType<?>>> FOLIAGE_PLACER_TYPES = Registries.FOLIAGE_PLACER_TYPE;
        public static final ResourceKey<Registry<TreeDecoratorType<?>>> TREE_DECORATOR_TYPES = Registries.TREE_DECORATOR_TYPE;
        public static final ResourceKey<Registry<CreativeModeTab>> CREATIVE_MODE_TABS = Registries.CREATIVE_MODE_TAB;
        public static final ResourceKey<Registry<DataComponentType<?>>> DATA_COMPONENT_TYPES = Registries.DATA_COMPONENT_TYPE;
        public static final ResourceKey<Registry<DataComponentType<?>>> ENCHANTMENT_EFFECT_COMPONENT_TYPES = Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE;
        public static final ResourceKey<Registry<ArmorMaterial>> ARMOR_MATERIALS = Registries.ARMOR_MATERIAL;
        @SuppressWarnings({"unchecked", "rawtypes"}) public static final ResourceKey ENTITY_DATA_SERIALIZERS = NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS;
        @SuppressWarnings({"unchecked", "rawtypes"}) public static final ResourceKey GLOBAL_LOOT_MODIFIER_SERIALIZERS = NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS;
        @SuppressWarnings({"unchecked", "rawtypes"}) public static final ResourceKey BIOME_MODIFIER_SERIALIZERS = NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS;
        @SuppressWarnings({"unchecked", "rawtypes"}) public static final ResourceKey STRUCTURE_MODIFIER_SERIALIZERS = NeoForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS;
        @SuppressWarnings({"unchecked", "rawtypes"}) public static final ResourceKey FLUID_TYPES = NeoForgeRegistries.Keys.FLUID_TYPES;
        @SuppressWarnings({"unchecked", "rawtypes"}) public static final ResourceKey HOLDER_SET_TYPES = NeoForgeRegistries.Keys.HOLDER_SET_TYPES;
        @SuppressWarnings({"unchecked", "rawtypes"}) public static final ResourceKey CONDITION_SERIALIZERS = NeoForgeRegistries.Keys.CONDITION_CODECS;
        @SuppressWarnings({"unchecked", "rawtypes"}) public static final ResourceKey INGREDIENT_SERIALIZERS = NeoForgeRegistries.Keys.INGREDIENT_TYPES;
    }
}
