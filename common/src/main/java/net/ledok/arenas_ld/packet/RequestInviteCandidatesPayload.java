package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Lobby screen → server: who can I invite (answered with {@link InviteCandidatesPayload}). 4.40.3. */
public record RequestInviteCandidatesPayload(BlockPos controller) implements CustomPacketPayload {
    public static final Type<RequestInviteCandidatesPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "request_invite_candidates"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestInviteCandidatesPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestInviteCandidatesPayload::controller, RequestInviteCandidatesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
