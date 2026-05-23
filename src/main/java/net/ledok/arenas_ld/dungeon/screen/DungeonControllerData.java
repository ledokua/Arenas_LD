package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record DungeonControllerData(
    BlockPos blockPos,
    List<Lobby> visibleLobbies,
    Optional<Lobby> ownLobby,
    List<PendingInvite> myInvites,
    int maxPartySize,
    Map<DifficultyTier, TierConfig> tiers
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonControllerData> STREAM_CODEC = StreamCodec.of(
        DungeonControllerData::encode,
        DungeonControllerData::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, DungeonControllerData data) {
        buf.writeBlockPos(data.blockPos());

        buf.writeVarInt(data.visibleLobbies().size());
        for (Lobby lobby : data.visibleLobbies()) {
            writeTag(buf, (CompoundTag) Lobby.CODEC.encodeStart(NbtOps.INSTANCE, lobby).getOrThrow());
        }

        buf.writeBoolean(data.ownLobby().isPresent());
        data.ownLobby().ifPresent(lobby ->
            writeTag(buf, (CompoundTag) Lobby.CODEC.encodeStart(NbtOps.INSTANCE, lobby).getOrThrow()));

        buf.writeVarInt(data.myInvites().size());
        for (PendingInvite invite : data.myInvites()) {
            writeTag(buf, (CompoundTag) PendingInvite.CODEC.encodeStart(NbtOps.INSTANCE, invite).getOrThrow());
        }

        buf.writeVarInt(data.maxPartySize());
        buf.writeVarInt(data.tiers().size());
        for (Map.Entry<DifficultyTier, TierConfig> entry : data.tiers().entrySet()) {
            DifficultyTier.STREAM_CODEC.encode(buf, entry.getKey());
            writeTag(buf, (CompoundTag) TierConfig.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue()).getOrThrow());
        }
    }

    private static DungeonControllerData decode(RegistryFriendlyByteBuf buf) {
        BlockPos blockPos = buf.readBlockPos();

        int visibleCount = buf.readVarInt();
        List<Lobby> visible = new ArrayList<>(visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            visible.add(Lobby.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
        }

        Optional<Lobby> own = Optional.empty();
        if (buf.readBoolean()) {
            own = Optional.of(Lobby.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
        }

        int inviteCount = buf.readVarInt();
        List<PendingInvite> invites = new ArrayList<>(inviteCount);
        for (int i = 0; i < inviteCount; i++) {
            invites.add(PendingInvite.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
        }

        int maxPartySize = buf.readVarInt();
        int tierCount = buf.readVarInt();
        Map<DifficultyTier, TierConfig> tiers = new EnumMap<>(DifficultyTier.class);
        for (int i = 0; i < tierCount; i++) {
            DifficultyTier tier = DifficultyTier.STREAM_CODEC.decode(buf);
            tiers.put(tier, TierConfig.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
        }

        return new DungeonControllerData(blockPos, visible, own, invites, maxPartySize, tiers);
    }

    private static void writeTag(RegistryFriendlyByteBuf buf, CompoundTag tag) {
        buf.writeNbt(tag);
    }

    private static CompoundTag readTag(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            throw new IllegalStateException("Expected compound tag in DungeonControllerData packet");
        }
        return tag;
    }
}
