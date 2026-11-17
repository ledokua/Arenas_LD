package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * Server → the run details screen's Timeline tab (4.37.0): one page of a player's run event log (from the run the
 * viewer has open), each event as its fields in text ({@code [tick, type, …]}, see {@code RunEvents}).
 *
 * @param from    the index of the first event in this page
 * @param total   how many events the player has now
 * @param dropped how many were left out over the cap
 */
public record RunTimelinePayload(UUID player, int from, int total, int dropped, List<List<String>> events)
    implements CustomPacketPayload {
    public static final Type<RunTimelinePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_timeline"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RunTimelinePayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, RunTimelinePayload::player,
        ByteBufCodecs.VAR_INT, RunTimelinePayload::from,
        ByteBufCodecs.VAR_INT, RunTimelinePayload::total,
        ByteBufCodecs.VAR_INT, RunTimelinePayload::dropped,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()), RunTimelinePayload::events,
        RunTimelinePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client → server: the next page of {@code player}'s events, from index {@code from}. */
    public record Request(UUID player, int from) implements CustomPacketPayload {
        public static final Type<Request> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_timeline_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Request::player, ByteBufCodecs.VAR_INT, Request::from, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
