package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class BlockRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArenasLdMod.MOD_ID);

    public static final DeferredBlock<Block> BOSS_SPAWNER_BLOCK = registerBlock("boss_spawner",
            () -> new BossSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final DeferredBlock<Block> DUNGEON_BOSS_SPAWNER_BLOCK = registerBlock("dungeon_boss_spawner",
            () -> new DungeonBossSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final DeferredBlock<Block> EXIT_PORTAL_BLOCK = registerBlock("exit_portal",
            () -> new ExitPortalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.GLASS).noCollission().strength(-1.0f, 3600000.0f)));

    public static final DeferredBlock<Block> ENTER_PORTAL_BLOCK = registerBlock("enter_portal",
            () -> new EnterPortalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.GLASS).noCollission().strength(-1.0f, 3600000.0f)));

    public static final DeferredBlock<Block> MOB_SPAWNER_BLOCK = registerBlock("mob_spawner",
            () -> new MobSpawnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER).strength(-1.0f, 3600000.0f)));

    public static final DeferredBlock<Block> PHASE_BLOCK = registerBlock("phase_block",
            () -> new PhaseBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noOcclusion().strength(-1.0f, 3600000.0f)));


    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ItemRegistry.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void initialize(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
