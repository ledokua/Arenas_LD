package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.*;
import net.ledok.arenas_ld.dungeon.block.RoomControllerBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class BlockRegistry {
    public static final Block BOSS_SPAWNER_BLOCK = registerBlock("boss_spawner",
            new BossSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final Block MOB_ARENA_SPAWNER_BLOCK = registerBlock("mob_arena_spawner",
            new MobArenaSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final Block PHASE_BLOCK = registerBlock("phase_block",
            new PhaseBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noOcclusion().strength(-1.0f, 3600000.0f)));

    public static final Block MOB_ARENA_CONTROLLER_BLOCK = registerBlock("mob_arena_controller",
            new MobArenaControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(-1.0f, 3600000.0f)));

    public static final Block RAID_CONTROLLER_BLOCK = registerBlock("raid_controller",
            new RaidControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(-1.0f, 3600000.0f)));

    public static final Block RESPAWN_POINT_BLOCK = registerBlock("respawn_point",
            new RespawnPointBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK)
                    .noOcclusion()
                    .noCollission()
                    .strength(-1.0f, 3600000.0f)));

    public static final Block ROOM_CONTROLLER_BLOCK = registerBlock("room_controller",
            new RoomControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(-1.0f, 3600000.0f)));

    public static final Block MOB_SPAWNER_BLOCK = registerBlock("mob_spawner",
            new net.ledok.arenas_ld.dungeon.block.MobSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final Block DUNGEON_BOSS_SPAWNER_BLOCK = registerBlock("dungeon_boss_spawner",
            new net.ledok.arenas_ld.dungeon.block.DungeonBossSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final Block DUNGEON_CONTROLLER_BLOCK = registerBlock("dungeon_controller",
            new net.ledok.arenas_ld.dungeon.block.DungeonControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(-1.0f, 3600000.0f)));

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":" + name), block);
    }

    private static void registerBlockItem(String name, Block block) {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":" + name),
                new BlockItem(block, new Item.Properties()));
    }

    public static void initialize() { }
}
