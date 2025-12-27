package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntitiesRegistry {
    // --- Block entities register ---
    public static final BlockEntityType<BossSpawnerBlockEntity> BOSS_SPAWNER_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":boss_spawner_be"),
                    BlockEntityType.Builder.of(BossSpawnerBlockEntity::new, BlockRegistry.BOSS_SPAWNER_BLOCK).build(null));

    public static final BlockEntityType<DungeonBossSpawnerBlockEntity> DUNGEON_BOSS_SPAWNER_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_boss_spawner_be"),
                    BlockEntityType.Builder.of(DungeonBossSpawnerBlockEntity::new, BlockRegistry.DUNGEON_BOSS_SPAWNER_BLOCK).build(null));

    public static final BlockEntityType<ExitPortalBlockEntity> EXIT_PORTAL_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":exit_portal_be"),
                    BlockEntityType.Builder.of(ExitPortalBlockEntity::new, BlockRegistry.EXIT_PORTAL_BLOCK).build(null));

    public static final BlockEntityType<EnterPortalBlockEntity> ENTER_PORTAL_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":enter_portal_be"),
                    BlockEntityType.Builder.of(EnterPortalBlockEntity::new, BlockRegistry.ENTER_PORTAL_BLOCK).build(null));

    public static final BlockEntityType<MobSpawnerBlockEntity> MOB_SPAWNER_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_spawner_be"),
                    BlockEntityType.Builder.of(MobSpawnerBlockEntity::new, BlockRegistry.MOB_SPAWNER_BLOCK).build(null));

    public static final BlockEntityType<PhaseBlockEntity> PHASE_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(ArenasLdMod.MOD_ID + ":phase_block_be"),
                    BlockEntityType.Builder.of(PhaseBlockEntity::new, BlockRegistry.PHASE_BLOCK).build(null));


    public static void initialize() {
    }
}
