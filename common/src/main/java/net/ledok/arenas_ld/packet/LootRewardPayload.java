package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: everything one player just earned from a run event, for the loot-reveal HUD popup.
 * Items carry where they actually landed so the popup can tag overflow ("&rarr; inbox");
 * effects are shown as cards of their own with level and duration.
 * Sent only to online players; offline rewards go through the economy inbox silently.
 */
public record LootRewardPayload(Source source, List<Entry> items, List<MobEffectInstance> effects,
                                long currency, int skillXp)
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

    /**
     * One card per distinct item: stacks with the same item, the same components (NBT) and the
     * same destination are summed, so repeated rolls read "Purpur Block x8" rather than four x2
     * cards. The summed count may exceed the item's max stack size; it only drives the label.
     */
    public static List<Entry> mergeStacks(List<Entry> entries) {
        List<Entry> merged = new ArrayList<>(entries.size());
        outer:
        for (Entry entry : entries) {
            if (entry.stack().isEmpty()) {
                continue;
            }
            for (int i = 0; i < merged.size(); i++) {
                Entry existing = merged.get(i);
                if (existing.destination() == entry.destination()
                    && ItemStack.isSameItemSameComponents(existing.stack(), entry.stack())) {
                    merged.set(i, new Entry(existing.stack().copyWithCount(
                        existing.stack().getCount() + entry.stack().getCount()), existing.destination()));
                    continue outer;
                }
            }
            merged.add(new Entry(entry.stack().copy(), entry.destination()));
        }
        return merged;
    }

    /** Cards beyond this are dropped from the popup (delivery itself is unaffected). */
    public static final int MAX_ENTRIES = 32;

    /** Effect cards beyond this are dropped from the popup (the effects are still applied). */
    public static final int MAX_EFFECTS = 16;

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
        List<MobEffectInstance> effects = payload.effects.size() > MAX_EFFECTS
            ? payload.effects.subList(0, MAX_EFFECTS)
            : payload.effects;
        buf.writeVarInt(effects.size());
        for (MobEffectInstance effect : effects) {
            MobEffectInstance.STREAM_CODEC.encode(buf, effect);
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
        int effectCount = Math.min(buf.readVarInt(), MAX_EFFECTS);
        List<MobEffectInstance> effects = new ArrayList<>(Math.max(0, effectCount));
        for (int i = 0; i < effectCount; i++) {
            effects.add(MobEffectInstance.STREAM_CODEC.decode(buf));
        }
        long currency = buf.readVarLong();
        int skillXp = buf.readVarInt();
        return new LootRewardPayload(source, items, effects, currency, skillXp);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
