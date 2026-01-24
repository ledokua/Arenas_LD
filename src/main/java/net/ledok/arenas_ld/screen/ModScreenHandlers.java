package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;


public class ModScreenHandlers {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(BuiltInRegistries.MENU, ArenasLdMod.MOD_ID);

    public static final Supplier<MenuType<BossSpawnerScreenHandler>> BOSS_SPAWNER_SCREEN_HANDLER =
            MENU_TYPES.register("boss_spawner", () -> IMenuTypeExtension.create((windowId, inv, data) -> new BossSpawnerScreenHandler(windowId, inv, BossSpawnerData.CODEC.decode(data))));

    public static final Supplier<MenuType<DungeonBossSpawnerScreenHandler>> DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER =
            MENU_TYPES.register("dungeon_boss_spawner", () -> IMenuTypeExtension.create((windowId, inv, data) -> new DungeonBossSpawnerScreenHandler(windowId, inv, BossSpawnerData.CODEC.decode(data))));

    public static final Supplier<MenuType<MobSpawnerScreenHandler>> MOB_SPAWNER_SCREEN_HANDLER =
            MENU_TYPES.register("mob_spawner", () -> IMenuTypeExtension.create((windowId, inv, data) -> new MobSpawnerScreenHandler(windowId, inv, MobSpawnerData.CODEC.decode(data))));

    public static final Supplier<MenuType<MobAttributesScreenHandler>> MOB_ATTRIBUTES_SCREEN_HANDLER =
            MENU_TYPES.register("mob_attributes", () -> IMenuTypeExtension.create((windowId, inv, data) -> new MobAttributesScreenHandler(windowId, inv, MobAttributesData.STREAM_CODEC.decode(data))));

    public static final Supplier<MenuType<EquipmentScreenHandler>> EQUIPMENT_SCREEN_HANDLER =
            MENU_TYPES.register("equipment", () -> IMenuTypeExtension.create((windowId, inv, data) -> new EquipmentScreenHandler(windowId, inv, EquipmentScreenData.STREAM_CODEC.decode(data))));

    public static void initialize(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
