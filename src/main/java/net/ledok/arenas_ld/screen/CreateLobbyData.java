package net.ledok.arenas_ld.screen;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record CreateLobbyData(List<DungeonStatus> dungeons) {

    public record DungeonStatus(String name, Status status, int cooldown) {
        public static final StreamCodec<FriendlyByteBuf, DungeonStatus> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DungeonStatus::name,
                ByteBufCodecs.VAR_INT.map(Status::byId, Status::getId), DungeonStatus::status,
                ByteBufCodecs.INT, DungeonStatus::cooldown,
                DungeonStatus::new
        );
    }

    public enum Status {
        AVAILABLE, LOCKED, COOLDOWN;

        public static Status byId(int id) {
            return values()[id];
        }

        public int getId() {
            return this.ordinal();
        }
    }

    public static final StreamCodec<FriendlyByteBuf, CreateLobbyData> CODEC = StreamCodec.composite(
            DungeonStatus.CODEC.apply(ByteBufCodecs.list()), CreateLobbyData::dungeons,
            CreateLobbyData::new
    );
}
