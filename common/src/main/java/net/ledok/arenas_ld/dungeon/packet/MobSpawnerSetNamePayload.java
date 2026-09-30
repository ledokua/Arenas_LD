package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MobSpawnerSetNamePayload(BlockPos blockPos, String name) implements CustomPacketPayload {
    public static final Type<MobSpawnerSetNamePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "mob_spawner_set_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MobSpawnerSetNamePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, MobSpawnerSetNamePayload::blockPos,
            ByteBufCodecs.STRING_UTF8, MobSpawnerSetNamePayload::name,
            MobSpawnerSetNamePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
