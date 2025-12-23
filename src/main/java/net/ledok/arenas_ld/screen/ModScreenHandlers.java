package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;


public class ModScreenHandlers {

    public static final MenuType<BossSpawnerScreenHandler> BOSS_SPAWNER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":boss_spawner"),
                    new ExtendedScreenHandlerType<>(BossSpawnerScreenHandler::new, BossSpawnerData.CODEC));

    public static final MenuType<MobSpawnerScreenHandler> MOB_SPAWNER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_spawner"),
                    new ExtendedScreenHandlerType<>(MobSpawnerScreenHandler::new, MobSpawnerData.CODEC));

    public static final MenuType<MobAttributesScreenHandler> MOB_ATTRIBUTES_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_attributes"),
                    new ExtendedScreenHandlerType<>(MobAttributesScreenHandler::new, MobAttributesData.STREAM_CODEC));

    public static final MenuType<EquipmentScreenHandler> EQUIPMENT_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":equipment"),
                    new ExtendedScreenHandlerType<>(EquipmentScreenHandler::new, EquipmentScreenData.STREAM_CODEC));

    public static void initialize() {
    }
}
