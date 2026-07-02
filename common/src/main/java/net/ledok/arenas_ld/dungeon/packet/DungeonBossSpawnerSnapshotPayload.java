package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonBossSpawnerSnapshotPayload(DungeonBossSpawnerData data) implements CustomPacketPayload {
    public static final Type<DungeonBossSpawnerSnapshotPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_boss_spawner_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonBossSpawnerSnapshotPayload> STREAM_CODEC =
        StreamCodec.composite(
            DungeonBossSpawnerData.STREAM_CODEC,
            DungeonBossSpawnerSnapshotPayload::data,
            DungeonBossSpawnerSnapshotPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
