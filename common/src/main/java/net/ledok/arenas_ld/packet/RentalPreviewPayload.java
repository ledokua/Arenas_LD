package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server → a lobby member who asked: every preset they can rent (the kits of the highest level folder at or below their
 * level and the run's level) and what each hands out (copies); recommended presets first.
 */
public record RentalPreviewPayload(BlockPos controller, int level, List<Kit> kits) implements CustomPacketPayload {
    /**
     * One preset: its kit id, display name, level (its folder) and items; {@code recommended} for the player's
     * skill-tree class; its spell book's choice ({@code spells} the pool's allowed spells, up to {@code maxSpells},
     * {@code chosen} what the picker starts with; no spells: no choice).
     */
    public record Kit(String kitId, String name, int level, List<ItemStack> items, boolean recommended,
                      List<String> spells, int maxSpells, List<String> chosen, List<Integer> spellTiers) {
        public Kit(String kitId, String name, int level, List<ItemStack> items) {
            this(kitId, name, level, items, false, List.of(), 0, List.of(), List.of());
        }

        /** {@code spellTiers}: each spell's tier when the book takes one spell per tier (4.40.4); empty: free choice. */
        public boolean perTier() {
            return !spellTiers.isEmpty() && spellTiers.size() == spells.size();
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Kit> CODEC = StreamCodec.of(
            (buf, k) -> {
                buf.writeUtf(k.kitId());
                buf.writeUtf(k.name());
                buf.writeVarInt(k.level());
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, k.items());
                buf.writeBoolean(k.recommended());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)).encode(buf, k.spells());
                buf.writeVarInt(k.maxSpells());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)).encode(buf, k.chosen());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).encode(buf, k.spellTiers());
            },
            buf -> new Kit(buf.readUtf(), buf.readUtf(), buf.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf), buf.readBoolean(),
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)).decode(buf), buf.readVarInt(),
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)).decode(buf),
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).decode(buf)));
    }

    public static final Type<RentalPreviewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "rental_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RentalPreviewPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RentalPreviewPayload::controller,
        ByteBufCodecs.VAR_INT, RentalPreviewPayload::level,
        Kit.CODEC.apply(ByteBufCodecs.list(64)), RentalPreviewPayload::kits,
        RentalPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
