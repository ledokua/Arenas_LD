package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

public class ModCreativeModeTabs {
    public static final ResourceKey<CreativeModeTab> ARENAS_LD_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arenas_ld_tab"));

    public static void initialize() {
        RegistryBridge.register(BuiltInRegistries.CREATIVE_MODE_TAB, ARENAS_LD_TAB.location(),
                // Row/column are placeholders: both loaders re-place modded tabs (Fabric pages, NeoForge sorting).
                CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("creativetab.arenas_ld_tab"))
                .icon(() -> ItemRegistry.DUNGEON_TOOL.getDefaultInstance())
                .displayItems((displayContext, entries) -> {
                    entries.accept(BlockRegistry.RAID_BOSS_SPAWNER_BLOCK);
                    entries.accept(BlockRegistry.MOB_SPAWNER_BLOCK);
                    entries.accept(BlockRegistry.DUNGEON_BOSS_SPAWNER_BLOCK);
                    entries.accept(BlockRegistry.ARENA_SPAWNER_BLOCK);
                    entries.accept(BlockRegistry.ARENA_CONTROLLER_BLOCK);
                    entries.accept(BlockRegistry.RAID_CONTROLLER_BLOCK);
                    entries.accept(BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
                    entries.accept(BlockRegistry.ROOM_CONTROLLER_BLOCK);
                    entries.accept(BlockRegistry.PHASE_BLOCK);
                    entries.accept(ItemRegistry.DUNGEON_TOOL);
                })
                .build());
    }
}
