package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.util.LivingEntityHooks;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * NeoForge driver for {@link LivingEntityHooks}; the Fabric side uses
 * LivingEntityMixin instead. Event semantics differ slightly from the mixin:
 * the damage multiplier applies at the start of the damage pipeline (before
 * shield/armor) rather than between shield and armor, and loot suppression
 * cancels all drops rather than just the loot table — equivalent in practice
 * because configured equipment has its drop chance forced to 0 at spawn.
 */
final class NeoForgeLivingEntityHandlers {
    private NeoForgeLivingEntityHandlers() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(NeoForgeLivingEntityHandlers::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(NeoForgeLivingEntityHandlers::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(NeoForgeLivingEntityHandlers::onLivingDrops);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            float amount = event.getAmount();
            float modified = LivingEntityHooks.modifyPlayerHurtAmount(player, event.getSource(), amount);
            if (modified != amount) {
                event.setAmount(modified);
            }
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && LivingEntityHooks.handlePlayerLethal(player)) {
            event.setCanceled(true);
        }
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        if (LivingEntityHooks.suppressNaturalLoot(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
