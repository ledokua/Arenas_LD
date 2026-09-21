package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: everything one player just earned from a run event, for the loot-reveal HUD popup.
 * Items carry where they actually landed so the popup can tag overflow ("&rarr; inbox").
 * Sent only to online players; offline rewards go through the economy inbox silently.
 */
public record LootRewardPayload(Source source, List<Entry> items, long currency, int skillXp)
    implements CustomPacketPayload {

    /** Which run event produced the reward; the popup uses it for its heading line. */
    public enum Source {
        DUNGEON_WIN, ROOM_CLEAR, RAID_WIN, ARENA_WAVE, ARENA_SUMMARY;

        static final Source[] VALUES = values();
    }

    /** Where a granted stack physically ended up. */
    public enum Destination {
        INVENTORY, INBOX, GROUND;

        static final Destination[] VALUES = values();
    }

    public record Entry(ItemStack stack, Destination destination) {
    }

    /** Cards beyond this are dropped from the popup (delivery itself is unaffected). */
    public static final int MAX_ENTRIES = 32;

    public static final Type<LootRewardPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "loot_reward"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LootRewardPayload> STREAM_CODEC =
        StreamCodec.of(LootRewardPayload::write, LootRewardPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, LootRewardPayload payload) {
        buf.writeByte(payload.source.ordinal());
        List<Entry> items = payload.items.size() > MAX_ENTRIES
            ? payload.items.subList(0, MAX_ENTRIES)
            : payload.items;
        buf.writeVarInt(items.size());
        for (Entry entry : items) {
            ItemStack.STREAM_CODEC.encode(buf, entry.stack());
            buf.writeByte(entry.destination().ordinal());
        }
        buf.writeVarLong(payload.currency);
        buf.writeVarInt(payload.skillXp);
    }

    private static LootRewardPayload read(RegistryFriendlyByteBuf buf) {
        Source source = Source.VALUES[Math.floorMod(buf.readByte(), Source.VALUES.length)];
        int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<Entry> items = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
            Destination destination = Destination.VALUES[Math.floorMod(buf.readByte(), Destination.VALUES.length)];
            items.add(new Entry(stack, destination));
        }
        long currency = buf.readVarLong();
        int skillXp = buf.readVarInt();
        return new LootRewardPayload(source, items, currency, skillXp);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
