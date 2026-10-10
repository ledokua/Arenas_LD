package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Admin screen → server: a controller's name and activity id (any controller type). The id is
 * validated and kept unique server-side; a refused id is explained in chat and the admin screen's
 * next snapshot shows the stored one again.
 */
public record SetActivityInfoPayload(BlockPos controllerPos, String activityId, String name)
    implements CustomPacketPayload {
    public static final Type<SetActivityInfoPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "set_activity_info"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetActivityInfoPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.controllerPos());
            buf.writeUtf(p.activityId(), 256);
            buf.writeUtf(p.name(), 256);
        },
        buf -> new SetActivityInfoPayload(buf.readBlockPos(), buf.readUtf(256), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
