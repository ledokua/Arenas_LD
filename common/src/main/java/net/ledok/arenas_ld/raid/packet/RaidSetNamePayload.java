package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetNamePayload(BlockPos controllerPos, String name) implements CustomPacketPayload {
    public static final Type<RaidSetNamePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_set_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetNamePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidSetNamePayload::controllerPos,
            ByteBufCodecs.STRING_UTF8, RaidSetNamePayload::name,
            RaidSetNamePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
