package net.ledok.arenas_ld.dungeon.run;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class DungeonConnectionListener {
    private DungeonConnectionListener() {
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();
            DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(uuid);
            if (run == null) {
                return;
            }
            if (player.level() instanceof ServerLevel level) {
                run.markDisconnected(uuid, level.getGameTime());
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();
            DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(uuid);
            if (run == null) {
                return;
            }
            run.clearDisconnected(uuid);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.reconnected"));
        });
    }
}

