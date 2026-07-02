package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.MobSpawnerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MobSpawnerSnapshotPayload(MobSpawnerData data) implements CustomPacketPayload {
    public static final Type<MobSpawnerSnapshotPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "mob_spawner_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MobSpawnerSnapshotPayload> STREAM_CODEC =
        StreamCodec.composite(
            MobSpawnerData.STREAM_CODEC,
            MobSpawnerSnapshotPayload::data,
            MobSpawnerSnapshotPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
