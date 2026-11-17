package net.ledok.arenas_ld.rewards;

import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One run modifier as a lobby screen offers it (the client can't read the server's config): its id,
 * name, description, what it does to rewards ("+2 loot · +25% XP"), its icon item id and the ids it can't be
 * picked with (both sides' conflicts lists).
 */
public record RunModifierOption(String id, Component name, Component description, Component rewards, String icon,
                                List<String> conflicts) {

    public static final StreamCodec<RegistryFriendlyByteBuf, RunModifierOption> STREAM_CODEC = StreamCodec.of(
        (buf, o) -> {
            buf.writeUtf(o.id());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, o.name());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, o.description());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, o.rewards());
            buf.writeUtf(o.icon());
            buf.writeCollection(o.conflicts(), (b, id) -> b.writeUtf(id));
        },
        buf -> new RunModifierOption(buf.readUtf(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
            ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
            buf.readUtf(), buf.readList(b -> b.readUtf())));

    public static final StreamCodec<RegistryFriendlyByteBuf, List<RunModifierOption>> LIST_CODEC =
        STREAM_CODEC.apply(ByteBufCodecs.list());

    /** What a lobby of {@code type} ("dungeon", "raid", "arena") offers. */
    public static List<RunModifierOption> forType(String type) {
        List<RunModifierOption> out = new ArrayList<>();
        for (Map.Entry<String, ArenasLdConfig.RunModifierDef> entry : RunModifierCatalog.selectable(type).entrySet()) {
            ArenasLdConfig.RunModifierDef def = entry.getValue();
            String name = def.name == null || def.name.isBlank() ? entry.getKey() : def.name;
            out.add(new RunModifierOption(entry.getKey(), RewardMath.sourceName(name),
                def.description == null || def.description.isBlank() ? Component.empty() : RewardMath.sourceName(def.description),
                rewards(RewardModifiers.definition(entry.getKey())), def.icon == null ? "" : def.icon,
                List.copyOf(RunModifierCatalog.conflicts(entry.getKey()))));
        }
        return out;
    }

    /** "+2 loot · +25% XP · loot → XP". */
    public static Component rewards(List<RewardModifier> modifiers) {
        MutableComponent line = Component.empty();
        boolean first = true;
        for (RewardModifier modifier : modifiers) {
            if (modifier.isNeutral()) {
                continue;
            }
            if (!first) {
                line.append(" · ");
            }
            first = false;
            Component stat = Component.translatable("message.arenas_ld.reward.stat_short." + modifier.stat().name().toLowerCase(Locale.ROOT));
            if (modifier.kind() == RewardModifier.Kind.CONVERT) {
                line.append(stat).append(" → ").append(Component.translatable("message.arenas_ld.reward.stat_short.xp"));
            } else {
                line.append(RewardMath.amount(modifier)).append(" ").append(stat);
            }
        }
        return line;
    }
}
