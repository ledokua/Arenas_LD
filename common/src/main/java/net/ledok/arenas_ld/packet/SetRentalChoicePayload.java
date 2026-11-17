package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Lobby screen → server: the player's rental preset pick (kit id) for this lobby ("" = their own gear) and the spells
 * they picked for its spell book (empty: the kit's own).
 */
public record SetRentalChoicePayload(BlockPos controller, String kitId, List<String> spells) implements CustomPacketPayload {
    public SetRentalChoicePayload(BlockPos controller, String kitId) {
        this(controller, kitId, List.of());
    }

    public static final Type<SetRentalChoicePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "set_rental_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetRentalChoicePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetRentalChoicePayload::controller,
        ByteBufCodecs.stringUtf8(256), SetRentalChoicePayload::kitId,
        ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(16)), SetRentalChoicePayload::spells,
        SetRentalChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
