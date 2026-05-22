package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateMobSpawnerEntityDefPayload(BlockPos blockPos, String mobId) implements CustomPacketPayload {
    public static final Type<UpdateMobSpawnerEntityDefPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_mob_spawner_v2_entity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateMobSpawnerEntityDefPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, UpdateMobSpawnerEntityDefPayload::blockPos,
        ByteBufCodecs.STRING_UTF8, UpdateMobSpawnerEntityDefPayload::mobId,
        UpdateMobSpawnerEntityDefPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
