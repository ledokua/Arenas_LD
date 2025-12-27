package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class BlockRegistry {
    public static final Block BOSS_SPAWNER_BLOCK = registerBlock("boss_spawner",
            new BossSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final Block DUNGEON_BOSS_SPAWNER_BLOCK = registerBlock("dungeon_boss_spawner",
            new DungeonBossSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final Block EXIT_PORTAL_BLOCK = registerBlock("exit_portal",
            new ExitPortalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.GLASS).noCollission().strength(-1.0f, 3600000.0f)));

    public static final Block ENTER_PORTAL_BLOCK = registerBlock("enter_portal",
            new EnterPortalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.GLASS).noCollission().strength(-1.0f, 3600000.0f)));

    public static final Block MOB_SPAWNER_BLOCK = registerBlock("mob_spawner",
            new MobSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final Block PHASE_BLOCK = registerBlock("phase_block",
            new PhaseBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noOcclusion().strength(-1.0f, 3600000.0f)));


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
