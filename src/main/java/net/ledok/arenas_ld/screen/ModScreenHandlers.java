package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerData;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerScreenHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;


public class ModScreenHandlers {

    public static final MenuType<BossSpawnerScreenHandler> BOSS_SPAWNER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":boss_spawner"),
                    new ExtendedScreenHandlerType<>(BossSpawnerScreenHandler::new, BossSpawnerData.CODEC));

    public static final MenuType<DungeonBossSpawnerScreenHandler> DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_boss_spawner"),
                    new ExtendedScreenHandlerType<>(DungeonBossSpawnerScreenHandler::new, BossSpawnerData.CODEC));

    public static final MenuType<MobSpawnerScreenHandler> MOB_SPAWNER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_spawner"),
                    new ExtendedScreenHandlerType<>(MobSpawnerScreenHandler::new, MobSpawnerData.CODEC));

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

    public static final MenuType<DungeonControllerScreenHandler> DUNGEON_CONTROLLER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_controller"),
                    new ExtendedScreenHandlerType<>(DungeonControllerScreenHandler::new, DungeonControllerData.STREAM_CODEC));

    public static final MenuType<RaidControllerScreenHandler> RAID_CONTROLLER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":raid_controller"),
                    new ExtendedScreenHandlerType<>(RaidControllerScreenHandler::new, RaidControllerData.STREAM_CODEC));

    public static final MenuType<RoomControllerScreenHandler> ROOM_CONTROLLER_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":room_controller"),
                    new ExtendedScreenHandlerType<>(RoomControllerScreenHandler::new, RoomControllerData.STREAM_CODEC));

    public static final MenuType<net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreenHandler> MOB_SPAWNER_V2_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":mob_spawner_v2"),
                    new ExtendedScreenHandlerType<>(net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreenHandler::new, net.ledok.arenas_ld.dungeon.screen.MobSpawnerData.STREAM_CODEC));

    public static final MenuType<net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreenHandler> DUNGEON_BOSS_SPAWNER_V2_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_boss_spawner_v2"),
                    new ExtendedScreenHandlerType<>(net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreenHandler::new, net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerData.STREAM_CODEC));

    public static final MenuType<net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreenHandler> DUNGEON_CONTROLLER_V2_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, ResourceLocation.parse(ArenasLdMod.MOD_ID + ":dungeon_controller_v2"),
                    new ExtendedScreenHandlerType<>(net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreenHandler::new, net.ledok.arenas_ld.dungeon.screen.DungeonControllerData.STREAM_CODEC));

    public static void initialize() {
    }
}
