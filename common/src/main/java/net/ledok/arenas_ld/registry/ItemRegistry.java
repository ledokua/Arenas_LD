package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.item.DungeonToolItem;
import net.ledok.arenas_ld.item.LootBundleItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

@SuppressWarnings("unused")
public class ItemRegistry {

    public static final Item DUNGEON_TOOL = ItemInit.register(new DungeonToolItem(new Item.Properties()), "dungeon_tool");
    public static final Item LOOT_BUNDLE = ItemInit.register(new LootBundleItem(new Item.Properties()), "loot_bundle");

    public class ItemInit {
        public static Item register(Item item, String id) {
            ResourceLocation itemID = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, id);

            // Register the item to the built-in registry for items.
            Item registeredItem = RegistryBridge.register(BuiltInRegistries.ITEM, itemID, item);

            // Return the registered item.
            return registeredItem;
        }
    }

    public static void initialize() {
    }
}
