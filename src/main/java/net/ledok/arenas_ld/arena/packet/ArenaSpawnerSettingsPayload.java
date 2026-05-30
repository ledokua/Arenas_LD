package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Scalar arena-spawner config: combat geometry, wave timing, archetype cadence, enabled objectives. */
public record ArenaSpawnerSettingsPayload(
    BlockPos spawnerPos, int battleRadius, int spawnDistance, double attributeScale, int entityHighlightTime,
    int waveTimer, int additionalTime, int timeBetweenWaves, int prepareTime, int bossWaveAdditionalTime,
    int bossEveryN, int eliteEveryN, int objectiveEveryN, List<String> objectives
) implements CustomPacketPayload {
    public static final Type<ArenaSpawnerSettingsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_spawner_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaSpawnerSettingsPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.spawnerPos());
            buf.writeVarInt(p.battleRadius()); buf.writeVarInt(p.spawnDistance());
            buf.writeDouble(p.attributeScale()); buf.writeVarInt(p.entityHighlightTime());
            buf.writeVarInt(p.waveTimer()); buf.writeVarInt(p.additionalTime()); buf.writeVarInt(p.timeBetweenWaves());
            buf.writeVarInt(p.prepareTime()); buf.writeVarInt(p.bossWaveAdditionalTime());
            buf.writeVarInt(p.bossEveryN()); buf.writeVarInt(p.eliteEveryN()); buf.writeVarInt(p.objectiveEveryN());
            buf.writeVarInt(p.objectives().size());
            for (String o : p.objectives()) buf.writeUtf(o);
        },
        buf -> {
            BlockPos pos = buf.readBlockPos();
            int br = buf.readVarInt(), sd = buf.readVarInt();
            double as = buf.readDouble(); int eh = buf.readVarInt();
            int wt = buf.readVarInt(), at = buf.readVarInt(), tbw = buf.readVarInt();
            int pt = buf.readVarInt(), bwt = buf.readVarInt();
            int bn = buf.readVarInt(), en = buf.readVarInt(), on = buf.readVarInt();
            int n = buf.readVarInt();
            List<String> objs = new ArrayList<>(n);
            for (int i = 0; i < n; i++) objs.add(buf.readUtf());
            return new ArenaSpawnerSettingsPayload(pos, br, sd, as, eh, wt, at, tbw, pt, bwt, bn, en, on, objs);
        });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
