package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.*;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntitiesRegistry {
    // --- Block entities register ---
    public static final BlockEntityType<RaidBossSpawnerBlockEntity> RAID_BOSS_SPAWNER_BLOCK_ENTITY =
            RegistryBridge.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":raid_boss_spawner_be"),
                    BlockEntityType.Builder.of(RaidBossSpawnerBlockEntity::new, BlockRegistry.RAID_BOSS_SPAWNER_BLOCK).build(null));

    public static final BlockEntityType<net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity> ARENA_SPAWNER_BLOCK_ENTITY =
            RegistryBridge.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":arena_spawner_be"),
                    BlockEntityType.Builder.of(net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity::new, BlockRegistry.ARENA_SPAWNER_BLOCK).build(null));

    public static final BlockEntityType<net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity> ARENA_CONTROLLER_BLOCK_ENTITY =
            RegistryBridge.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":arena_controller_be"),
                    BlockEntityType.Builder.of(net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity::new, BlockRegistry.ARENA_CONTROLLER_BLOCK).build(null));

    public static final BlockEntityType<PhaseBlockEntity> PHASE_BLOCK_ENTITY =
            RegistryBridge.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":phase_block_be"),
                    BlockEntityType.Builder.of(PhaseBlockEntity::new, BlockRegistry.PHASE_BLOCK).build(null));

    public static final BlockEntityType<RaidControllerBlockEntity> RAID_CONTROLLER_BLOCK_ENTITY =
            RegistryBridge.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":raid_controller_be"),
                    BlockEntityType.Builder.of(RaidControllerBlockEntity::new, BlockRegistry.RAID_CONTROLLER_BLOCK).build(null));

    public static final BlockEntityType<RoomControllerBlockEntity> ROOM_CONTROLLER_BLOCK_ENTITY =
            RegistryBridge.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":room_controller_be"),
                    BlockEntityType.Builder.of(RoomControllerBlockEntity::new, BlockRegistry.ROOM_CONTROLLER_BLOCK).build(null));

    public static final BlockEntityType<net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity> MOB_SPAWNER_BLOCK_ENTITY =
            RegistryBridge.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_spawner_be"),
                    BlockEntityType.Builder.of(net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity::new, BlockRegistry.MOB_SPAWNER_BLOCK).build(null));

    public static final BlockEntityType<net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity> DUNGEON_BOSS_SPAWNER_BLOCK_ENTITY =
            RegistryBridge.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_boss_spawner_be"),
                    BlockEntityType.Builder.of(net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity::new, BlockRegistry.DUNGEON_BOSS_SPAWNER_BLOCK).build(null));

    public static final BlockEntityType<net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity> DUNGEON_CONTROLLER_BLOCK_ENTITY =
            RegistryBridge.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_controller_be"),
                    BlockEntityType.Builder.of(net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity::new, BlockRegistry.DUNGEON_CONTROLLER_BLOCK).build(null));

    public static void initialize() {
    }
}
