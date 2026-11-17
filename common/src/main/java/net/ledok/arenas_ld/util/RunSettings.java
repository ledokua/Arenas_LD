package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * A dungeon or raid controller's own run settings (LeDok 2026-11-17), saved with the controller and set from its
 * admin screen's General tab, {@code /arenasld activity settings} or {@code ArenasApi}:
 * <ul>
 *   <li>{@code rentalMode}: {@link #OPTIONAL} (each member may rent a build, ×0.5 rewards) or {@link #REQUIRED}
 *       (everyone runs a rental build, full rewards).</li>
 *   <li>{@code baseLootRolls}: the completion's base loot rolls; below 0 = the global {@code completion_loot_rolls}.</li>
 * </ul>
 */
public record RunSettings(String rentalMode, double baseLootRolls) {
    public static final String OPTIONAL = "optional";
    public static final String REQUIRED = "required";
    /** The highest base rolls a controller may set. */
    public static final double MAX_ROLLS = 100;
    public static final RunSettings DEFAULT = new RunSettings(OPTIONAL, -1);

    public static final Codec<RunSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.optionalFieldOf("rentalMode", OPTIONAL).forGetter(RunSettings::rentalMode),
        Codec.DOUBLE.optionalFieldOf("baseLootRolls", -1.0).forGetter(RunSettings::baseLootRolls)
    ).apply(i, RunSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunSettings> STREAM_CODEC = StreamCodec.of(
        (buf, s) -> {
            buf.writeUtf(s.rentalMode());
            buf.writeDouble(s.baseLootRolls());
        },
        buf -> new RunSettings(buf.readUtf(), buf.readDouble()));

    private static final String NBT_KEY = "RunSettings";

    public RunSettings {
        rentalMode = REQUIRED.equals(rentalMode) ? REQUIRED : OPTIONAL;
        baseLootRolls = Double.isNaN(baseLootRolls) || baseLootRolls < 0 ? -1 : Math.min(MAX_ROLLS, baseLootRolls);
    }

    public boolean rentalRequired() {
        return REQUIRED.equals(rentalMode);
    }

    public boolean hasOwnLootRolls() {
        return baseLootRolls >= 0;
    }

    /** The base loot rolls this controller's completions roll: its own, or the global one. */
    public double lootRolls() {
        return hasOwnLootRolls() ? baseLootRolls : ArenasLdConfig.getInstance().completion_loot_rolls;
    }

    public RunSettings withRentalMode(String mode) {
        return new RunSettings(mode, baseLootRolls);
    }

    public RunSettings withBaseLootRolls(double rolls) {
        return new RunSettings(rentalMode, rolls);
    }

    /** Writes these settings to a controller's NBT (nothing for the default). */
    public void save(CompoundTag nbt) {
        if (equals(DEFAULT)) {
            return;
        }
        CODEC.encodeStart(NbtOps.INSTANCE, this).ifSuccess(tag -> nbt.put(NBT_KEY, tag));
    }

    public static RunSettings load(CompoundTag nbt) {
        Tag tag = nbt.get(NBT_KEY);
        if (tag == null) {
            return DEFAULT;
        }
        return CODEC.parse(NbtOps.INSTANCE, tag)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.warn("[Arenas] Bad run settings: {}", err))
            .orElse(DEFAULT);
    }

    /** The host's settings; the default for anything that has none (arenas, null). */
    public static RunSettings of(@Nullable Object host) {
        return host instanceof Holder holder ? holder.getRunSettings() : DEFAULT;
    }

    /** A dungeon or raid controller. */
    public interface Holder {
        RunSettings getRunSettings();

        void setRunSettings(RunSettings settings);
    }
}
