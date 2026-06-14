package net.ledok.arenas_ld.util;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.run.ArenaRun;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/**
 * Blocks the use of blacklisted items ({@link RunItemBlacklist}) while a player is inside an active
 * raid, dungeon, or arena run. Covers all three use paths: air use (throwing/eating), using an item
 * on a block (e.g. an ender pearl or glow ink sac), and using an item on an entity.
 */
public final class RunItemRestrictions {
    private RunItemRestrictions() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer && blockBlacklisted(serverPlayer, hand)) {
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && blockBlacklisted(serverPlayer, hand)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && blockBlacklisted(serverPlayer, hand)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    /** True (and notifies the player) if a blacklisted item is being used while in a run. */
    private static boolean blockBlacklisted(ServerPlayer player, InteractionHand hand) {
        // Cheap blacklist test first; only then the (slightly heavier) "is in a run" lookup.
        if (!RunItemBlacklist.isBlacklisted(player.getItemInHand(hand)) || !isPlayerInRun(player)) {
            return false;
        }
        player.displayClientMessage(
            Component.translatable("message.arenas_ld.run.item_blocked").withStyle(ChatFormatting.RED), true);
        return true;
    }

    /** True while the player is a live participant in any raid, dungeon, or arena run. */
    public static boolean isPlayerInRun(ServerPlayer player) {
        if (ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(player.getUUID()) != null) {
            return true;
        }
        if (ArenasLdMod.RAID_BOSS_MANAGER.getSpawnerForPlayer(player) != null) {
            return true;
        }
        return isInArenaRun(player);
    }

    private static boolean isInArenaRun(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        for (ArenaControllerBlockEntity.ControllerKey key : ArenaControllerBlockEntity.getControllers()) {
            ServerLevel controllerLevel = server.getLevel(key.dimension());
            if (controllerLevel == null) {
                continue;
            }
            if (!(controllerLevel.getBlockEntity(key.pos()) instanceof ArenaControllerBlockEntity controller)) {
                continue;
            }
            for (ArenaRun run : controller.getActiveRuns().values()) {
                RunParticipant participant = run.participants().get(player.getUUID());
                if (participant != null && participant.status() != RunParticipant.ParticipantStatus.REMOVED) {
                    return true;
                }
            }
        }
        return false;
    }
}
