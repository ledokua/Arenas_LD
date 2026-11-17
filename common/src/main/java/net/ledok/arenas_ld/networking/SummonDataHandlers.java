package net.ledok.arenas_ld.networking;

import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.EntityDefinition;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.packet.SummonDataPayloads;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.util.RunSplits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Server side of the summon "Extra data" editor ({@link SummonDataPayloads}). Ops only, like the other editors. */
public final class SummonDataHandlers {
    private SummonDataHandlers() {
    }

    public static void register() {
        ArenasNetwork.registerServerReceiver(SummonDataPayloads.Set.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!permitted(player)) {
                    return;
                }
                String problem = RunSplits.nbtProblem(payload.nbt());
                if (problem != null) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.summon_data.bad_nbt", problem)
                        .withStyle(ChatFormatting.RED));
                    return;
                }
                if (save(player.level().getBlockEntity(payload.pos()), payload.pos(), payload.nbt().trim(), payload.splitStrength())) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.summon_data.saved")
                        .withStyle(ChatFormatting.GREEN));
                }
            }));
    }

    private static boolean permitted(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
            return false;
        }
        return true;
    }

    /** The summon definition of a dungeon mob spawner, dungeon boss spawner or raid boss spawner; null for others. */
    public static @Nullable EntityDefinition definition(@Nullable BlockEntity be) {
        if (be instanceof MobSpawnerBlockEntity spawner) {
            return spawner.getEntityDefinition();
        }
        if (be instanceof DungeonBossSpawnerBlockEntity spawner) {
            return spawner.getEntityDefinition();
        }
        if (be instanceof RaidBossSpawnerBlockEntity spawner) {
            return spawner.getEntityDefinition();
        }
        return null;
    }

    private static boolean save(@Nullable BlockEntity be, BlockPos pos, String nbt, int splitStrength) {
        EntityDefinition def = definition(be);
        if (def == null) {
            return false;
        }
        EntityDefinition updated = def.withSummonData(nbt, splitStrength);
        if (be instanceof MobSpawnerBlockEntity spawner) {
            spawner.setEntityDefinition(updated);
        } else if (be instanceof DungeonBossSpawnerBlockEntity spawner) {
            spawner.setEntityDefinition(updated);
        } else if (be instanceof RaidBossSpawnerBlockEntity spawner) {
            spawner.setEntityDefinition(updated);
        }
        return true; // setEntityDefinition saves and syncs
    }
}
