package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.util.EconomyCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Before a run starts (Start pressed) and when players are moved into or out of a run (LeDok 4.36.1): every screen
 * of theirs closes (lobby, Eden's hub, inventory, a crafting table, an anvil…), so the countdown title shows and
 * nothing is left mid-move. What was on the cursor or in a crafting grid or other input slot goes back into the
 * inventory, as vanilla does when a screen closes; what doesn't fit goes to the economy inbox instead of the ground.
 */
public final class RunPlayerSafety {
    /** Players being secured now → what didn't fit (server thread only; {@code InventoryDropMixin} fills it). */
    private static final Map<UUID, List<ItemStack>> CATCHING = new HashMap<>();

    private RunPlayerSafety() {
    }

    public static void secure(MinecraftServer server, Collection<UUID> players) {
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                secure(player);
            }
        }
    }

    public static void secure(ServerPlayer player) {
        if (player.hasDisconnected() || !player.isAlive()) {
            return;
        }
        List<ItemStack> overflow = new ArrayList<>();
        CATCHING.put(player.getUUID(), overflow);
        try {
            // Sends the close to the client (it closes whatever screen is open) and runs the menu's removed():
            // the cursor stack and the input slots go back into the inventory.
            player.closeContainer();
        } catch (RuntimeException e) {
            net.ledok.arenas_ld.ArenasLdMod.LOGGER.warn("[Arenas] Couldn't close {}'s screen before the run",
                player.getScoreboardName(), e);
        } finally {
            CATCHING.remove(player.getUUID());
        }
        if (overflow.isEmpty()) {
            return;
        }
        int count = 0;
        for (ItemStack stack : overflow) {
            if (EconomyCompat.isAvailable()) {
                EconomyCompat.deliverItem(player.getUUID(), stack.copyWithCount(1), stack.getCount(), "RUN_INVENTORY_FULL");
                count += stack.getCount();
            } else {
                player.drop(stack, false); // nowhere else to put it (no Economy_LD)
            }
        }
        if (count > 0) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.run.items_to_inbox", count)
                .withStyle(ChatFormatting.YELLOW));
        }
    }

    /**
     * {@code Inventory.placeItemBackInInventory} found no room: while the player is being secured, keep the stack for
     * the inbox instead of dropping it. False: drop as vanilla does.
     */
    public static boolean catchDrop(Player player, ItemStack stack) {
        List<ItemStack> overflow = CATCHING.get(player.getUUID());
        if (overflow == null || stack.isEmpty()) {
            return false;
        }
        overflow.add(stack.copy());
        stack.setCount(0);
        return true;
    }
}
