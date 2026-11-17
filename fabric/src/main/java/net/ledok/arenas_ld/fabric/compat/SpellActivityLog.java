package net.ledok.arenas_ld.fabric.compat;

import net.fabricmc.fabric.api.event.Event;
import net.ledok.arenas_ld.stats.RunEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Spell Engine Expansion's {@code SpellActivityEvents} (SEE 0.3.7+) into the run event log (4.37.0). Read by
 * reflection (SEE's own names: the event fields, the listener interfaces and the record accessors), so Arenas needs
 * no compile dependency on SEE; registered only when {@code spell_engine_expansion_ld} is loaded. RunEvents drops
 * everything for players outside runs.
 */
public final class SpellActivityLog {
    private static final String API = "net.ledok.spell_engine_expansion_ld.api.event.SpellActivityEvents";
    private static final Map<Class<?>, Map<String, Method>> ACCESSORS = new ConcurrentHashMap<>();

    private SpellActivityLog() {
    }

    /** False when this SEE has no SpellActivityEvents (before 0.3.7). */
    public static boolean register() {
        Class<?> api;
        try {
            api = Class.forName(API);
        } catch (ClassNotFoundException e) {
            return false;
        }
        on(api, "CAST_START", "CastStartListener", e -> RunEvents.spell(player(e), time(e), "cast_start",
            text(e, "spell"), text(e, "mode"), container(e), item(e), get(e, "plannedTicks")));
        on(api, "CAST_END", "CastEndListener", e -> RunEvents.spell(player(e), time(e), "cast_end",
            text(e, "spell"), text(e, "mode"), container(e), item(e), get(e, "ticks"), get(e, "ratio"),
            get(e, "cooldownTicks"), get(e, "chargesLeft")));
        on(api, "CAST_CANCEL", "CastCancelListener", e -> RunEvents.spell(player(e), time(e), "cast_cancel",
            text(e, "spell"), text(e, "mode"), get(e, "ticksSoFar"), text(e, "reason")));
        on(api, "CHANNEL_START", "ChannelStartListener", e -> RunEvents.spell(player(e), time(e), "channel_start",
            text(e, "spell"), container(e), item(e), get(e, "plannedTicks")));
        on(api, "CHANNEL_END", "ChannelEndListener", e -> RunEvents.spell(player(e), time(e), "channel_end",
            text(e, "spell"), get(e, "ticksChanneled"), get(e, "pulses"), text(e, "reason"), get(e, "cooldownTicks")));
        on(api, "PASSIVE", "PassiveListener", e -> RunEvents.spell(player(e), time(e), "passive",
            text(e, "spell"), text(e, "trigger"), text(e, "stage"), container(e), item(e), get(e, "delayTicks")));
        on(api, "DODGE", "DodgeListener", e -> {
            Object attacker = get(e, "attacker");
            RunEvents.spell(player(e), time(e), "dodge",
                attacker instanceof Entity entity ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() : "",
                text(e, "damageType"), get(e, "amount"));
        });
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void on(Class<?> api, String field, String listener, Consumer<Object> handler) {
        try {
            Event event = (Event) api.getField(field).get(null);
            Class<?> type = Class.forName(API + "$" + listener);
            Object proxy = Proxy.newProxyInstance(SpellActivityLog.class.getClassLoader(), new Class<?>[]{type},
                (self, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(self);
                            case "equals" -> self == args[0];
                            default -> "ArenasRunEventLog:" + listener;
                        };
                    }
                    if (args != null && args.length == 1) {
                        try {
                            handler.accept(args[0]);
                        } catch (RuntimeException e) {
                            net.ledok.arenas_ld.ArenasLdMod.LOGGER.debug("[Arenas] Run event log: {} failed", field, e);
                        }
                    }
                    return null;
                });
            event.register(proxy);
        } catch (ReflectiveOperationException | ClassCastException e) {
            net.ledok.arenas_ld.ArenasLdMod.LOGGER.warn("[Arenas] Run event log: no SEE event {}", field, e);
        }
    }

    private static Object get(Object record, String name) {
        Method method = ACCESSORS.computeIfAbsent(record.getClass(), c -> new ConcurrentHashMap<>())
            .computeIfAbsent(name, n -> {
                try {
                    return record.getClass().getMethod(n);
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(e);
                }
            });
        try {
            return method.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ServerPlayer player(Object e) {
        return (ServerPlayer) get(e, "player");
    }

    private static long time(Object e) {
        return ((Number) get(e, "gameTime")).longValue();
    }

    /** A ResourceLocation, an enum or a string, as text ("" for null). */
    private static String text(Object e, String name) {
        Object value = get(e, name);
        return value == null ? "" : value instanceof Enum<?> en ? en.name() : value.toString();
    }

    private static final java.util.regex.Pattern UUID = java.util.regex.Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** The container's name; a skill-tree container is named by a UUID, shown as "skill_tree" (the spell is the node). */
    private static String container(Object e) {
        Object source = get(e, "source");
        String name = source == null ? "" : String.valueOf(get(source, "container"));
        return UUID.matcher(name).matches() ? "skill_tree" : name;
    }

    private static String item(Object e) {
        Object source = get(e, "source");
        Object item = source == null ? null : get(source, "item");
        return item instanceof Item i && i != Items.AIR ? BuiltInRegistries.ITEM.getKey(i).toString() : "";
    }
}
