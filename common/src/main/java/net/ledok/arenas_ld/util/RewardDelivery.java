package net.ledok.arenas_ld.util;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.packet.LootRewardPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rolls run-reward loot tables and delivers the results with one policy everywhere:
 * items go straight into the player's inventory; whatever does not fit goes to the
 * Economy_LD inbox when that mod is present, and is dropped at the player's feet otherwise.
 * Offline players get their whole reward via the inbox (or nothing without economy —
 * logged so an admin can see it happened). Currency lands directly in the wallet for
 * online players and in the inbox for offline ones.
 *
 * <p>Each helper returns/accepts {@link LootRewardPayload.Entry} rows so callers can
 * follow up with {@link #notify} to drive the client's loot-reveal popup.
 */
public final class RewardDelivery {
    private RewardDelivery() {
    }

    /**
     * Rolls {@code lootTableId} once with GIFT parameters. An unparseable or unknown id
     * yields an empty list (unknown tables resolve to {@code LootTable.EMPTY}).
     */
    public static List<ItemStack> rollLoot(ServerLevel world, String lootTableId,
                                           @Nullable ServerPlayer player, Vec3 origin) {
        if (lootTableId == null || lootTableId.isEmpty()) {
            return List.of();
        }
        ResourceLocation id = ResourceLocation.tryParse(lootTableId);
        if (id == null) {
            ArenasLdMod.LOGGER.warn("Invalid reward loot table id: {}", lootTableId);
            return List.of();
        }
        LootTable table = world.getServer().reloadableRegistries()
            .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        LootParams.Builder builder = new LootParams.Builder(world)
            .withParameter(LootContextParams.ORIGIN, origin);
        if (player != null) {
            builder.withParameter(LootContextParams.THIS_ENTITY, player);
        }
        List<ItemStack> rolled = new ArrayList<>();
        for (ItemStack stack : table.getRandomItems(builder.create(LootContextParamSets.GIFT))) {
            if (!stack.isEmpty()) {
                rolled.add(stack);
            }
        }
        return rolled;
    }

    /**
     * Delivers stacks to an online player: inventory first, overflow to the economy inbox,
     * ground as the last resort. Returns one entry per input stack recording where it (or
     * its overflow) ended up, for the loot popup.
     */
    public static List<LootRewardPayload.Entry> giveStacks(ServerPlayer player, List<ItemStack> stacks, String reason) {
        List<LootRewardPayload.Entry> delivered = new ArrayList<>(stacks.size());
        boolean inboxAvailable = EconomyCompat.isAvailable();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack display = stack.copy();
            // Inventory.add consumes what fits and leaves the remainder in the stack.
            LootRewardPayload.Destination destination = LootRewardPayload.Destination.INVENTORY;
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                if (inboxAvailable) {
                    EconomyCompat.deliverItem(player.getUUID(), stack.copyWithCount(1), stack.getCount(), reason);
                    destination = LootRewardPayload.Destination.INBOX;
                } else {
                    player.drop(stack.copy(), false);
                    destination = LootRewardPayload.Destination.GROUND;
                }
            }
            delivered.add(new LootRewardPayload.Entry(display, destination));
        }
        return delivered;
    }

    /**
     * Delivers stacks to an offline player's economy inbox. Without Economy_LD there is
     * nowhere durable to put them, so the loss is logged and the stacks are discarded.
     */
    public static void giveStacksOffline(UUID playerUuid, List<ItemStack> stacks, String reason) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (EconomyCompat.isAvailable()) {
                EconomyCompat.deliverItem(playerUuid, stack.copyWithCount(1), stack.getCount(), reason);
            } else {
                ArenasLdMod.LOGGER.warn("Dropping reward {}x {} for offline player {} (no economy inbox installed)",
                    stack.getCount(), stack.getItem(), playerUuid);
            }
        }
    }

    /**
     * Pays currency: straight into the wallet when the player is online, into the claimable
     * inbox when offline. No-op without Economy_LD.
     */
    public static void giveCurrency(MinecraftServer server, UUID playerUuid, long amount, String reason) {
        if (amount <= 0L || !EconomyCompat.isAvailable()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            EconomyCompat.giveCurrency(playerUuid, player.getGameProfile().getName(), amount, reason);
        } else {
            EconomyCompat.deliverCurrency(playerUuid, amount, reason);
        }
    }

    /** True when the popup should show a currency line at all (economy present and amount &gt; 0). */
    public static long displayableCurrency(long amount) {
        return EconomyCompat.isAvailable() ? Math.max(0L, amount) : 0L;
    }

    /** Sends the loot-reveal popup for what this player just received. Skips fully empty rewards. */
    public static void notify(ServerPlayer player, LootRewardPayload.Source source,
                              List<LootRewardPayload.Entry> items, long currency, int skillXp) {
        if (items.isEmpty() && currency <= 0L && skillXp <= 0) {
            return;
        }
        ServerPlayNetworking.send(player, new LootRewardPayload(source, items, currency, skillXp));
    }
}
