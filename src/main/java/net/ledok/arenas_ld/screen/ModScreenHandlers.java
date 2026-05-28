package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerData;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerScreenHandler;
import net.ledok.arenas_ld.raid.screen.RaidBossSpawnerData;
import net.ledok.arenas_ld.raid.screen.RaidBossSpawnerScreenHandler;
import net.ledok.arenas_ld.raid.screen.RaidControllerData;
import net.ledok.arenas_ld.raid.screen.RaidControllerScreenHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

public class ModScreenHandlers {
    public static final MenuType<RaidBossSpawnerScreenHandler> RAID_BOSS_SPAWNER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":raid_boss_spawner"),
                    new ExtendedScreenHandlerType<>(RaidBossSpawnerScreenHandler::new, RaidBossSpawnerData.CODEC));

    public static final MenuType<MobArenaSpawnerScreenHandler> MOB_ARENA_SPAWNER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_arena_spawner"),
                    new ExtendedScreenHandlerType<>(MobArenaSpawnerScreenHandler::new, MobArenaSpawnerData.STREAM_CODEC));

    public static final MenuType<MobAttributesScreenHandler> MOB_ATTRIBUTES_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_attributes"),
                    new ExtendedScreenHandlerType<>(MobAttributesScreenHandler::new, MobAttributesData.STREAM_CODEC));

    public static final MenuType<EquipmentScreenHandler> EQUIPMENT_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":equipment"),
                    new ExtendedScreenHandlerType<>(EquipmentScreenHandler::new, EquipmentScreenData.STREAM_CODEC));

    public static final MenuType<MobArenaControllerScreenHandler> MOB_ARENA_CONTROLLER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_arena_controller"),
                    new ExtendedScreenHandlerType<>(MobArenaControllerScreenHandler::new, MobArenaControllerData.STREAM_CODEC));

    public static final MenuType<RaidControllerScreenHandler> RAID_CONTROLLER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":raid_controller"),
                    new ExtendedScreenHandlerType<>(RaidControllerScreenHandler::new, RaidControllerData.STREAM_CODEC));

    public static final MenuType<RoomControllerScreenHandler> ROOM_CONTROLLER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":room_controller"),
                    new ExtendedScreenHandlerType<>(RoomControllerScreenHandler::new, RoomControllerData.STREAM_CODEC));

    public static final MenuType<net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreenHandler> MOB_SPAWNER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_spawner"),
                    new ExtendedScreenHandlerType<>(net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreenHandler::new, net.ledok.arenas_ld.dungeon.screen.MobSpawnerData.STREAM_CODEC));

    public static final MenuType<net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreenHandler> DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_boss_spawner"),
                    new ExtendedScreenHandlerType<>(net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreenHandler::new, net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerData.STREAM_CODEC));

    public static final MenuType<net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreenHandler> DUNGEON_CONTROLLER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_controller"),
                    new ExtendedScreenHandlerType<>(net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreenHandler::new, net.ledok.arenas_ld.dungeon.screen.DungeonControllerData.STREAM_CODEC));

    public static final MenuType<net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminScreenHandler> DUNGEON_CONTROLLER_ADMIN_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_controller_admin"),
                    new ExtendedScreenHandlerType<>(net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminScreenHandler::new, net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminData.STREAM_CODEC));

    public static void initialize() {
    }
}
