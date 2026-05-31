package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: tells the client to close the raid controller screen for the given controller (sent to every
 * party member when their raid launches).
 */
public record RaidCloseScreenPayload(BlockPos controllerPos) implements CustomPacketPayload {
    public static final Type<RaidCloseScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_close_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidCloseScreenPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidCloseScreenPayload::controllerPos,
            RaidCloseScreenPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
