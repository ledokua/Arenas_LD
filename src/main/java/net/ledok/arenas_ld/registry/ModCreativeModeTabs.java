package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeModeTabs {
    public static final CreativeModeTab ARENAS_LD_TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("creativetab.arenas_ld_tab"))
            .icon(() -> new ItemStack(ItemRegistry.SPAWNER_CONFIGURATOR))
            .displayItems((displayParameters, output) -> {
                output.accept(ItemRegistry.LINKER);
                output.accept(ItemRegistry.SPAWNER_CONFIGURATOR);
                output.accept(ItemRegistry.LOOT_BUNDLE);
                output.accept(BlockRegistry.MOB_SPAWNER_BLOCK);
                output.accept(BlockRegistry.BOSS_SPAWNER_BLOCK);
                output.accept(BlockRegistry.DUNGEON_BOSS_SPAWNER_BLOCK);
                output.accept(BlockRegistry.PHASE_BLOCK);
                // output.accept(BlockRegistry.DUNGEON_CONTROLLER_BLOCK); // This block is technical and should not be in the creative tab
                output.accept(BlockRegistry.ENTER_PORTAL_BLOCK);
                output.accept(BlockRegistry.EXIT_PORTAL_BLOCK);
            }).build();

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arenas_ld_tab"), ARENAS_LD_TAB);
    }
}
