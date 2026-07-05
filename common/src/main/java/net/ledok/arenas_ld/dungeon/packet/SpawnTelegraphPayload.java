package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** S2C: highlight upcoming mob spawn positions with flickering boxes for {@code durationTicks}. */
public record SpawnTelegraphPayload(List<BlockPos> positions, int durationTicks) implements CustomPacketPayload {
    public static final Type<SpawnTelegraphPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "spawn_telegraph"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnTelegraphPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), SpawnTelegraphPayload::positions,
            ByteBufCodecs.VAR_INT, SpawnTelegraphPayload::durationTicks,
            SpawnTelegraphPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
