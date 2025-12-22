package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.item.LinkerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

@SuppressWarnings("unused")
public class ItemRegistry {

    public static final Item LINKER = ItemInit.register(new LinkerItem(new Item.Properties()), "linker");

    public class ItemInit {
        public static Item register(Item item, String id) {
            ResourceLocation itemID = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, id);

            // Register the item to the built-in registry for items.
            Item registeredItem = Registry.register(BuiltInRegistries.ITEM, itemID, item);

            // Return the registered item.
            return registeredItem;
        }
    }

    public static void initialize() {
    }
}
