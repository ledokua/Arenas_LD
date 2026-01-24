package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class BlockEntitiesRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ArenasLdMod.MOD_ID);

    // --- Block entities register ---
    public static final Supplier<BlockEntityType<BossSpawnerBlockEntity>> BOSS_SPAWNER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("boss_spawner_be", () ->
                    BlockEntityType.Builder.of(BossSpawnerBlockEntity::new, BlockRegistry.BOSS_SPAWNER_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<DungeonBossSpawnerBlockEntity>> DUNGEON_BOSS_SPAWNER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("dungeon_boss_spawner_be", () ->
                    BlockEntityType.Builder.of(DungeonBossSpawnerBlockEntity::new, BlockRegistry.DUNGEON_BOSS_SPAWNER_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<ExitPortalBlockEntity>> EXIT_PORTAL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("exit_portal_be", () ->
                    BlockEntityType.Builder.of(ExitPortalBlockEntity::new, BlockRegistry.EXIT_PORTAL_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<EnterPortalBlockEntity>> ENTER_PORTAL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("enter_portal_be", () ->
                    BlockEntityType.Builder.of(EnterPortalBlockEntity::new, BlockRegistry.ENTER_PORTAL_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<MobSpawnerBlockEntity>> MOB_SPAWNER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("mob_spawner_be", () ->
                    BlockEntityType.Builder.of(MobSpawnerBlockEntity::new, BlockRegistry.MOB_SPAWNER_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<PhaseBlockEntity>> PHASE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("phase_block_be", () ->
                    BlockEntityType.Builder.of(PhaseBlockEntity::new, BlockRegistry.PHASE_BLOCK.get()).build(null));


    public static void initialize(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
