package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The ally HP sidebar (UI rework step 4): the viewer's teammates in their run (or Eden party), with health. Sent a few
 * times a second, only when it changed; an empty list hides the sidebar.
 */
public record AllyHealthPayload(List<Ally> allies) implements CustomPacketPayload {
    public static final int MAX = 16;

    public enum State { ALIVE, DOWNED, OUT, OFFLINE }

    /**
     * @param seconds for DOWNED: seconds until they're back (-1 unknown)
     */
    public record Ally(UUID uuid, String name, float health, float maxHealth, float absorption, State state, int seconds) {
    }

    public static final Type<AllyHealthPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "ally_health"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AllyHealthPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.allies().size());
            for (Ally ally : payload.allies()) {
                UUIDUtil.STREAM_CODEC.encode(buf, ally.uuid());
                buf.writeUtf(ally.name(), 64);
                buf.writeFloat(ally.health());
                buf.writeFloat(ally.maxHealth());
                buf.writeFloat(ally.absorption());
                buf.writeByte(ally.state().ordinal());
                buf.writeVarInt(ally.seconds() + 1);
            }
        },
        buf -> {
            int count = ByteBufCodecs.readCount(buf, MAX);
            List<Ally> allies = new ArrayList<>(count);
            State[] states = State.values();
            for (int i = 0; i < count; i++) {
                UUID uuid = UUIDUtil.STREAM_CODEC.decode(buf);
                String name = buf.readUtf(64);
                float health = buf.readFloat();
                float max = buf.readFloat();
                float absorption = buf.readFloat();
                int state = buf.readByte();
                int seconds = buf.readVarInt() - 1;
                allies.add(new Ally(uuid, name, health, max, absorption, states[Math.floorMod(state, states.length)], seconds));
            }
            return new AllyHealthPayload(List.copyOf(allies));
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
