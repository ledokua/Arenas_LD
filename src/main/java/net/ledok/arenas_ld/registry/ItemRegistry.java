package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.item.LinkerItem;
import net.ledok.arenas_ld.item.LootBundleItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public class ItemRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArenasLdMod.MOD_ID);

    public static final DeferredItem<Item> LINKER = ITEMS.register("linker", () -> new LinkerItem(new Item.Properties()));
    public static final DeferredItem<Item> LOOT_BUNDLE = ITEMS.register("loot_bundle", () -> new LootBundleItem(new Item.Properties()));

    public static void initialize(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
