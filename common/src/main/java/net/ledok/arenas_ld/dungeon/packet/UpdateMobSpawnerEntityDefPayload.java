package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record UpdateMobSpawnerEntityDefPayload(BlockPos blockPos, String mobId, int spawnCount, int wave, List<BlockPos> spawnOffsets) implements CustomPacketPayload {
    public static final Type<UpdateMobSpawnerEntityDefPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_mob_spawner_v2_entity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateMobSpawnerEntityDefPayload> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeBlockPos(data.blockPos);
            buf.writeUtf(data.mobId);
            buf.writeVarInt(data.spawnCount);
            buf.writeVarInt(data.wave);
            buf.writeVarInt(data.spawnOffsets.size());
            for (BlockPos pos : data.spawnOffsets) {
                buf.writeBlockPos(pos);
            }
        },
        buf -> {
            BlockPos blockPos = buf.readBlockPos();
            String mobId = buf.readUtf();
            int spawnCount = buf.readVarInt();
            int wave = buf.readVarInt();
            int offsetCount = buf.readVarInt();
            List<BlockPos> offsets = new ArrayList<>(offsetCount);
            for (int i = 0; i < offsetCount; i++) {
                offsets.add(buf.readBlockPos());
            }
            return new UpdateMobSpawnerEntityDefPayload(blockPos, mobId, spawnCount, wave, offsets);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
