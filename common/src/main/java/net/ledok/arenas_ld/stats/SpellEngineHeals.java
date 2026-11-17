package net.ledok.arenas_ld.stats;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Credits Spell Engine heals to their caster: Spell Engine fires {@code SpellEvents.HEAL} right after a spell heals
 * someone, naming the caster. Read by reflection, so Arenas_LD runs without Spell Engine.
 */
final class SpellEngineHeals {
    private SpellEngineHeals() {
    }

    static void register() {
        try {
            Class<?> events = Class.forName("net.spell_engine.api.spell.event.SpellEvents");
            Class<?> listenerType = Class.forName("net.spell_engine.api.spell.event.SpellEvents$HealEvent");
            Class<?> argsType = Class.forName("net.spell_engine.api.spell.event.SpellEvents$HealEvent$Args");
            Method caster = argsType.getMethod("caster");
            Method target = argsType.getMethod("target");
            Method spell = argsType.getMethod("spell");
            Object event = events.getField("HEAL").get(null);
            Object listener = Proxy.newProxyInstance(SpellEngineHeals.class.getClassLoader(), new Class<?>[]{listenerType},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> "ArenasRunStatsHealListener";
                        };
                    }
                    if (args != null && args.length == 1 && RunStats.lastHealed != null
                        && caster.invoke(args[0]) instanceof LivingEntity from
                        && target.invoke(args[0]) instanceof LivingEntity to) {
                        String label = null;
                        if (spell.invoke(args[0]) instanceof net.minecraft.core.Holder<?> holder) {
                            label = holder.unwrapKey().map(k -> "spell:" + k.location()).orElse(null);
                        }
                        RunStats.onSpellHeal(from, to, label);
                    }
                    return null;
                });
            event.getClass().getMethod("register", Object.class).invoke(event, listener);
            ArenasLdMod.LOGGER.info("[Arenas] Run stats: Spell Engine heals are credited to their caster");
        } catch (ClassNotFoundException e) {
            // No Spell Engine: heals count for the healed player.
        } catch (ReflectiveOperationException | RuntimeException e) {
            ArenasLdMod.LOGGER.warn("[Arenas] Run stats: couldn't hook Spell Engine heals (they count for the healed player)", e);
        }
    }
}
