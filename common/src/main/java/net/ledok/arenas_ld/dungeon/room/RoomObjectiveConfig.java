package net.ledok.arenas_ld.dungeon.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.util.AttributeData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Optional;

/**
 * What a party must do to clear a dungeon room. {@link Type#KILL_ALL} is the default and matches
 * the pre-objective behavior exactly, so untouched rooms are unaffected.
 *
 * <ul>
 *   <li>{@code KILL_ALL} — every wave's mobs must die.</li>
 *   <li>{@code SURVIVE} — a {@code surviveSeconds} timer runs from activation; the next wave
 *       (looping) force-spawns every {@code surviveWaveIntervalSeconds} regardless of what's still
 *       alive, so the party can't AFK behind a leftover mob. When the timer expires, remaining
 *       mobs are discarded and the room clears.</li>
 *   <li>{@code KILL_BOSS} — the room clears the instant its boss dies; remaining and unspawned
 *       mobs are discarded. Falls back to KILL_ALL behavior when no boss spawner is linked.</li>
 *   <li>{@code PROTECT} — a {@code protectMobId} mob spawns at {@code protectOffset} (relative to
 *       the controller, set with the Configurator) on activation; all waves must die while it
 *       lives. Its death loses the whole run. Falls back to KILL_ALL behavior when the position
 *       or mob id is missing/invalid.</li>
 *   <li>{@code REACH_ZONE} (4.44.0, LeDok 2026-11-20) — the room clears once {@link #clampedEntryPercent} of
 *       the party's active players (online, not downed) stand in the zone at once: the box between
 *       {@code zoneCornerA} and {@code zoneCornerB} (offsets from the controller, set with the Dungeon Tool's
 *       goal-zone mode). Waves loop on the SURVIVE schedule (every {@code surviveWaveIntervalSeconds}) with no
 *       time limit; remaining mobs are discarded on clear. Falls back to KILL_ALL when the zone isn't set.</li>
 * </ul>
 *
 * <p>{@code maxAliveMobs} (SURVIVE and REACH_ZONE only; 0 = no cap): a timed wave spawns only as many mobs as fit
 * under it, so a party can't end up buried under hundreds of looped mobs.
 */
public record RoomObjectiveConfig(Type type, int surviveSeconds, int surviveWaveIntervalSeconds,
                                  String protectMobId, boolean protectStationary, Optional<BlockPos> protectOffset,
                                  List<AttributeData> protectAttributes, int entryPercent,
                                  Optional<BlockPos> zoneCornerA, Optional<BlockPos> zoneCornerB, int maxAliveMobs) {

    /** The pre-4.44.0 shape: no zone, no mob cap. */
    public RoomObjectiveConfig(Type type, int surviveSeconds, int surviveWaveIntervalSeconds, String protectMobId,
                               boolean protectStationary, Optional<BlockPos> protectOffset,
                               List<AttributeData> protectAttributes, int entryPercent) {
        this(type, surviveSeconds, surviveWaveIntervalSeconds, protectMobId, protectStationary, protectOffset,
            protectAttributes, entryPercent, Optional.empty(), Optional.empty(), 0);
    }

    /** Highest mob cap the screen accepts. */
    public static final int MAX_MOB_CAP = 500;

    /** Default share of the online party that must register on the room's doors to lock it in. */
    public static final int DEFAULT_ENTRY_PERCENT = 25;

    public enum Type implements StringRepresentable {
        KILL_ALL("kill_all"),
        SURVIVE("survive"),
        KILL_BOSS("kill_boss"),
        PROTECT("protect"),
        REACH_ZONE("reach_zone");

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String name;

        Type(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final RoomObjectiveConfig DEFAULT =
        new RoomObjectiveConfig(Type.KILL_ALL, 60, 15, "minecraft:villager", true, Optional.empty(), List.of(),
            DEFAULT_ENTRY_PERCENT);

    public static final Codec<RoomObjectiveConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Type.CODEC.optionalFieldOf("type", Type.KILL_ALL).forGetter(RoomObjectiveConfig::type),
        Codec.INT.optionalFieldOf("surviveSeconds", 60).forGetter(RoomObjectiveConfig::surviveSeconds),
        Codec.INT.optionalFieldOf("surviveWaveIntervalSeconds", 15).forGetter(RoomObjectiveConfig::surviveWaveIntervalSeconds),
        Codec.STRING.optionalFieldOf("protectMobId", "minecraft:villager").forGetter(RoomObjectiveConfig::protectMobId),
        Codec.BOOL.optionalFieldOf("protectStationary", true).forGetter(RoomObjectiveConfig::protectStationary),
        BlockPos.CODEC.optionalFieldOf("protectOffset").forGetter(RoomObjectiveConfig::protectOffset),
        AttributeData.CODEC.listOf().optionalFieldOf("protectAttributes", List.of()).forGetter(RoomObjectiveConfig::protectAttributes),
        Codec.INT.optionalFieldOf("entryPercent", DEFAULT_ENTRY_PERCENT).forGetter(RoomObjectiveConfig::entryPercent),
        BlockPos.CODEC.optionalFieldOf("zoneCornerA").forGetter(RoomObjectiveConfig::zoneCornerA),
        BlockPos.CODEC.optionalFieldOf("zoneCornerB").forGetter(RoomObjectiveConfig::zoneCornerB),
        Codec.INT.optionalFieldOf("maxAliveMobs", 0).forGetter(RoomObjectiveConfig::maxAliveMobs)
    ).apply(i, RoomObjectiveConfig::new));

    public RoomObjectiveConfig withProtectOffset(Optional<BlockPos> newOffset) {
        return new RoomObjectiveConfig(type, surviveSeconds, surviveWaveIntervalSeconds,
            protectMobId, protectStationary, newOffset, protectAttributes, entryPercent,
            zoneCornerA, zoneCornerB, maxAliveMobs);
    }

    /** The goal zone's corners (offsets from the controller); both empty clears it. */
    public RoomObjectiveConfig withZone(Optional<BlockPos> cornerA, Optional<BlockPos> cornerB) {
        return new RoomObjectiveConfig(type, surviveSeconds, surviveWaveIntervalSeconds,
            protectMobId, protectStationary, protectOffset, protectAttributes, entryPercent,
            cornerA, cornerB, maxAliveMobs);
    }

    /** Whether both zone corners are set. */
    public boolean hasZone() {
        return zoneCornerA.isPresent() && zoneCornerB.isPresent();
    }

    /** Whether waves loop on a timer (SURVIVE, REACH_ZONE), where {@link #maxAliveMobs} applies. */
    public boolean timedWaves() {
        return type == Type.SURVIVE || type == Type.REACH_ZONE;
    }

    /** The mob cap in force: {@link #maxAliveMobs} for timed-wave rooms, 0 (none) otherwise. */
    public int mobCap() {
        return timedWaves() ? Math.clamp(maxAliveMobs, 0, MAX_MOB_CAP) : 0;
    }

    /** {@link #entryPercent} guarded against out-of-range values from old saves or raw NBT. */
    public int clampedEntryPercent() {
        return Math.clamp(entryPercent, 1, 100);
    }

    // NBT-via-CODEC, same wire approach as RoomRewardConfig; composes into both
    // RoomSetObjectivePayload and RoomControllerData.
    public static final StreamCodec<RegistryFriendlyByteBuf, RoomObjectiveConfig> STREAM_CODEC = StreamCodec.of(
        (buf, cfg) -> buf.writeNbt((CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, cfg).getOrThrow()),
        buf -> {
            CompoundTag tag = buf.readNbt();
            return tag == null ? DEFAULT : CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(DEFAULT);
        }
    );
}
