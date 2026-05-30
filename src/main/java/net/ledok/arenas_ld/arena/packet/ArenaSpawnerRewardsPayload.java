package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.util.MobArenaRewardData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Replaces the arena spawner's full per-wave reward list. */
public record ArenaSpawnerRewardsPayload(BlockPos spawnerPos, List<MobArenaRewardData> rewards) implements CustomPacketPayload {
    public static final Type<ArenaSpawnerRewardsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_spawner_rewards"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaSpawnerRewardsPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.spawnerPos());
            buf.writeVarInt(p.rewards().size());
            for (MobArenaRewardData r : p.rewards()) buf.writeNbt(r.toNbt());
        },
        buf -> {
            BlockPos pos = buf.readBlockPos();
            int n = buf.readVarInt();
            List<MobArenaRewardData> rewards = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                CompoundTag tag = buf.readNbt();
                if (tag != null) rewards.add(MobArenaRewardData.fromNbt(tag));
            }
            return new ArenaSpawnerRewardsPayload(pos, rewards);
        });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
