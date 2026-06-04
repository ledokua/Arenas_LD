package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Replaces the arena spawner's full mob list. */
public record ArenaSpawnerMobsPayload(BlockPos spawnerPos, List<MobArenaMobData> mobs) implements CustomPacketPayload {
    public static final Type<ArenaSpawnerMobsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_spawner_mobs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaSpawnerMobsPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.spawnerPos());
            buf.writeVarInt(p.mobs().size());
            for (MobArenaMobData m : p.mobs()) buf.writeNbt(m.toNbt(buf.registryAccess()));
        },
        buf -> {
            BlockPos pos = buf.readBlockPos();
            int n = buf.readVarInt();
            List<MobArenaMobData> mobs = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                CompoundTag tag = buf.readNbt();
                if (tag != null) mobs.add(MobArenaMobData.fromNbt(buf.registryAccess(), tag));
            }
            return new ArenaSpawnerMobsPayload(pos, mobs);
        });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
