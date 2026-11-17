package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server → a viewer of the run details screen: a run as it is now, or as it ended. Sent when the screen opens,
 * then at most once a second while it is open, and only when something changed.
 *
 * @param phase        the run HUD's line (room, objective); empty for a finished run
 * @param bossHp       percent, or {@link RunHudPayload#NO_BOSS_HP}
 * @param result       "Win" / "Failed" …; empty while the run goes
 * @param highlights   "Top damage: Alice (1,234)" …
 * @param phases       when bosses crossed 75/50/25% HP and died
 * @param unattributed HP the run's mobs lost that nothing traced to a player; -1 for players (staff only)
 * @param over         the run has ended (the numbers stay as they were)
 * @param staff        rows carry the staff line (overkill, raw damage, blocked by source, loot …)
 * @param opening      opens the screen; an update only refreshes a screen still open
 * @param end          the end-of-run screen: opens once the loot popup is gone and no other screen is open
 */
public record RunDetailsPayload(Component activity, Component tier, boolean hardcore, List<Component> modifiers,
                                Component phase, int remainingTicks, int totalTicks, int elapsedSeconds, int bossHp,
                                Component rewards, Component result, List<Component> highlights, List<Component> phases,
                                List<Row> rows, float unattributed, boolean over, boolean staff, boolean opening,
                                boolean end)
    implements CustomPacketPayload {

    /** The same run, as the payload that opens the screen ({@code opening}) or one that only updates an open one. */
    public RunDetailsPayload opening(boolean opening) {
        return new RunDetailsPayload(activity, tier, hardcore, modifiers, phase, remainingTicks, totalTicks,
            elapsedSeconds, bossHp, rewards, result, highlights, phases, rows, unattributed, over, staff, opening, end);
    }

    /** A player's state in the run. */
    public enum State { ALIVE, DOWNED, OUT, OFFLINE, FORFEITED, WON, LOST }

    /**
     * One player: {@code hp} -1 when offline or the run is over, {@code respawnSeconds} -1 when not waiting to
     * respawn; {@code staffLine} empty for players.
     * <p>4.36.0: {@code uuid} (the head), {@code playerClass} (a spell book item id, "skills:&lt;branch&gt;" or ""),
     * {@code level} (-1 unknown), {@code healedOthers}, and {@code rewards}: what the player got on a win, the first
     * line the totals and the rest how they were counted (empty before the payout or without one).
     * <p>4.36.2: {@code classIcon}, the player's spell book with its components (empty without one).
     */
    public record Row(String name, State state, int deaths, int respawnSeconds, float hp, float maxHp,
                      float dealt, float taken, float blocked, float healed, int kills, Component staffLine,
                      java.util.UUID uuid, String playerClass, int level, float healedOthers, List<Component> rewards,
                      net.minecraft.world.item.ItemStack classIcon) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = StreamCodec.of(
            (buf, r) -> {
                buf.writeUtf(r.name());
                buf.writeEnum(r.state());
                buf.writeVarInt(r.deaths());
                buf.writeVarInt(r.respawnSeconds() + 1);
                buf.writeFloat(r.hp());
                buf.writeFloat(r.maxHp());
                buf.writeFloat(r.dealt());
                buf.writeFloat(r.taken());
                buf.writeFloat(r.blocked());
                buf.writeFloat(r.healed());
                buf.writeVarInt(r.kills());
                TEXT.encode(buf, r.staffLine());
                buf.writeUUID(r.uuid());
                buf.writeUtf(r.playerClass());
                buf.writeVarInt(r.level() + 1);
                buf.writeFloat(r.healedOthers());
                TEXTS.encode(buf, r.rewards());
                net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.classIcon());
            },
            buf -> new Row(buf.readUtf(), buf.readEnum(State.class), buf.readVarInt(), buf.readVarInt() - 1,
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), TEXT.decode(buf), buf.readUUID(), buf.readUtf(), buf.readVarInt() - 1, buf.readFloat(),
                TEXTS.decode(buf), net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
    }

    public static final Type<RunDetailsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_details"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_STREAM_CODEC;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> TEXTS = TEXT.apply(ByteBufCodecs.list());
    public static final StreamCodec<RegistryFriendlyByteBuf, RunDetailsPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            TEXT.encode(buf, p.activity());
            TEXT.encode(buf, p.tier());
            buf.writeBoolean(p.hardcore());
            TEXTS.encode(buf, p.modifiers());
            TEXT.encode(buf, p.phase());
            buf.writeVarInt(p.remainingTicks());
            buf.writeVarInt(p.totalTicks());
            buf.writeVarInt(p.elapsedSeconds());
            buf.writeVarInt(p.bossHp());
            TEXT.encode(buf, p.rewards());
            TEXT.encode(buf, p.result());
            TEXTS.encode(buf, p.highlights());
            TEXTS.encode(buf, p.phases());
            Row.CODEC.apply(ByteBufCodecs.list()).encode(buf, p.rows());
            buf.writeFloat(p.unattributed());
            buf.writeBoolean(p.over());
            buf.writeBoolean(p.staff());
            buf.writeBoolean(p.opening());
            buf.writeBoolean(p.end());
        },
        buf -> new RunDetailsPayload(TEXT.decode(buf), TEXT.decode(buf), buf.readBoolean(), TEXTS.decode(buf),
            TEXT.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), TEXT.decode(buf),
            TEXT.decode(buf), TEXTS.decode(buf), TEXTS.decode(buf), Row.CODEC.apply(ByteBufCodecs.list()).decode(buf),
            buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
