package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: state for the custom run-timer HUD bar (replaces the vanilla boss-bar timers).
 * Sent about once a second while a run is active; the client counts the timer down locally
 * between packets and hides the bar on {@link Kind#HIDDEN} or when updates stop arriving.
 */
public record RunHudPayload(Kind kind, Component label, int remainingTicks, int totalTicks,
                            boolean hardcore, int bossHpPercent)
    implements CustomPacketPayload {

    public enum Kind {
        /** No bar; clears whatever is showing. */
        HIDDEN,
        /** Main run timer (dungeon/raid timer, arena wave clock). */
        RUN,
        /** Post-run "returning home" countdown. */
        CLOSE;

        static final Kind[] VALUES = values();
    }

    /** {@code bossHpPercent} value meaning "no HP row". */
    public static final int NO_BOSS_HP = -1;

    public static final RunHudPayload HIDDEN =
        new RunHudPayload(Kind.HIDDEN, Component.empty(), 0, 0, false, NO_BOSS_HP);

    public static final Type<RunHudPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunHudPayload> STREAM_CODEC =
        StreamCodec.of(RunHudPayload::write, RunHudPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, RunHudPayload payload) {
        buf.writeByte(payload.kind.ordinal());
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.label);
        buf.writeVarInt(payload.remainingTicks);
        buf.writeVarInt(payload.totalTicks);
        buf.writeBoolean(payload.hardcore);
        buf.writeByte(payload.bossHpPercent);
    }

    private static RunHudPayload read(RegistryFriendlyByteBuf buf) {
        Kind kind = Kind.VALUES[Math.floorMod(buf.readByte(), Kind.VALUES.length)];
        Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
        int remainingTicks = buf.readVarInt();
        int totalTicks = buf.readVarInt();
        boolean hardcore = buf.readBoolean();
        int bossHpPercent = buf.readByte();
        return new RunHudPayload(kind, label, remainingTicks, totalTicks, hardcore, bossHpPercent);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
