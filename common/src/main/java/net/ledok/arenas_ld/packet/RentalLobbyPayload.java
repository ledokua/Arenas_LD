package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server → a member of a waiting dungeon/raid lobby: the rental build choice ({@code active=false}: none to show).
 *
 * @param controller the lobby's controller
 * @param presets    the presets (kit ids) the viewer can rent
 * @param ownLevel   the level of the kit the viewer's pick hands out (-1 without a pick)
 * @param own        the viewer's pick, its preset name ("" = own gear)
 * @param picks      the lobby's picks, member name → class
 * @param required   "rental kits only": everyone must pick a build (no own gear, full rewards)
 */
public record RentalLobbyPayload(boolean active, BlockPos controller, List<String> presets, int ownLevel, String own,
                                 Map<String, String> picks, boolean required) implements CustomPacketPayload {
    public static final RentalLobbyPayload NONE = new RentalLobbyPayload(false, BlockPos.ZERO, List.of(), -1, "", Map.of(), false);
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, String>> PICKS_CODEC =
        ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8, 32);
    public static final Type<RentalLobbyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "rental_lobby"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RentalLobbyPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBoolean(p.active());
            BlockPos.STREAM_CODEC.encode(buf, p.controller());
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(128)).encode(buf, p.presets());
            buf.writeVarInt(p.ownLevel());
            buf.writeUtf(p.own());
            PICKS_CODEC.encode(buf, p.picks());
            buf.writeBoolean(p.required());
        },
        buf -> new RentalLobbyPayload(buf.readBoolean(), BlockPos.STREAM_CODEC.decode(buf),
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(128)).decode(buf), buf.readVarInt(), buf.readUtf(),
            PICKS_CODEC.decode(buf), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
