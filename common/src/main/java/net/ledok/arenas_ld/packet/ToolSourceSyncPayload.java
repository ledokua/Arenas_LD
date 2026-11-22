package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Server → a builder who just used the Dungeon Tool: the edited block's data (its client update tag), for when
 * its chunk is out of the builder's view distance and the normal block update doesn't reach them (4.44.0). The
 * client's selection overlay keeps drawing it from this.
 */
public record ToolSourceSyncPayload(ResourceKey<Level> dimension, BlockPos pos, int stateId, CompoundTag data)
    implements CustomPacketPayload {
    public static final Type<ToolSourceSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "tool_source_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToolSourceSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ResourceKey.streamCodec(Registries.DIMENSION), ToolSourceSyncPayload::dimension,
        BlockPos.STREAM_CODEC, ToolSourceSyncPayload::pos,
        ByteBufCodecs.VAR_INT, ToolSourceSyncPayload::stateId,
        ByteBufCodecs.COMPOUND_TAG, ToolSourceSyncPayload::data,
        ToolSourceSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Sends {@code pos}'s block entity in {@code level} to {@code player}, when its chunk isn't loaded for them. */
    public static void send(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        // Only when the player's client can't see it (normal block updates reach players tracking the chunk).
        if (player.level() == level && level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(pos), false)
            .contains(player)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        net.ledok.arenas_ld.platform.ArenasNetwork.sendToPlayer(player, new ToolSourceSyncPayload(level.dimension(), pos,
            Block.getId(be.getBlockState()), be.getUpdateTag(level.registryAccess())));
    }
}
