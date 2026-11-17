package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ledok.arenas_ld.stats.PlayerCounters;
import net.ledok.arenas_ld.stats.RunStats;
import net.ledok.arenas_ld.stats.RunStatsHolder;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Run stats (RunStats design): the run's mark on its entities ({@link RunStatsHolder}) and the hooks that count.
 * Each hook starts with the one null check on that mark, so entities outside runs cost nothing more. A hit is
 * followed through vanilla's own steps in fields of the entity (no objects per hit): what reached armor, what armor
 * left, what Resistance and protection enchantments left, what absorption soaked, and the HP really lost.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRunStatsMixin implements RunStatsHolder {
    @Unique private @Nullable Object arenasLd$runStats;
    @Unique private @Nullable PlayerCounters arenasLd$applier;
    @Unique private @Nullable RunStats.Hits arenasLd$hits;
    @Unique private boolean arenasLd$boss;
    @Unique private float arenasLd$hpBefore;
    @Unique private float arenasLd$absorptionBefore;
    @Unique private float arenasLd$raw;
    @Unique private float arenasLd$armorIn;
    @Unique private float arenasLd$armorOut;
    @Unique private float arenasLd$magicIn;
    @Unique private float arenasLd$magicOut;
    @Unique private float arenasLd$enchantBlocked;
    @Unique private float arenasLd$hpBeforeHeal;

    @Shadow protected boolean dead;

    @Shadow public abstract float getHealth();

    @Shadow public abstract float getAbsorptionAmount();

    @Override
    public @Nullable Object arenasLd$runStats() {
        return arenasLd$runStats;
    }

    @Override
    public void arenasLd$setRunStats(@Nullable Object stats) {
        arenasLd$runStats = stats;
    }

    @Override
    public @Nullable PlayerCounters arenasLd$applier() {
        return arenasLd$applier;
    }

    @Override
    public void arenasLd$setApplier(@Nullable PlayerCounters applier) {
        arenasLd$applier = applier;
    }

    @Override
    public boolean arenasLd$boss() {
        return arenasLd$boss;
    }

    @Override
    public void arenasLd$setBoss(boolean boss) {
        arenasLd$boss = boss;
    }

    @Override
    public @Nullable RunStats.Hits arenasLd$hits() {
        return arenasLd$hits;
    }

    @Override
    public void arenasLd$setHits(@Nullable RunStats.Hits hits) {
        arenasLd$hits = hits;
    }

    @Override
    public void arenasLd$hurtStart(float amount) {
        if (arenasLd$runStats != null) {
            arenasLd$hpBefore = getHealth();
            arenasLd$absorptionBefore = getAbsorptionAmount();
            arenasLd$raw = amount;
            arenasLd$armorIn = arenasLd$armorOut = amount;
            arenasLd$magicIn = arenasLd$magicOut = amount;
            arenasLd$enchantBlocked = 0;
        }
    }

    @Override
    public void arenasLd$hurtEnd(DamageSource source) {
        Object mark = arenasLd$runStats;
        if (mark == null) {
            return;
        }
        float lost = Math.max(0, arenasLd$hpBefore - getHealth());
        float absorbed = Math.max(0, arenasLd$absorptionBefore - getAbsorptionAmount());
        float armor = Math.max(0, arenasLd$armorIn - arenasLd$armorOut);
        float magic = Math.max(0, arenasLd$magicIn - arenasLd$magicOut);
        float enchant = Math.min(arenasLd$enchantBlocked, magic);
        float toHealth = Math.max(0, arenasLd$magicOut - absorbed);
        if (lost > 0 || absorbed > 0 || armor > 0 || magic > 0) {
            RunStats.onHurt((LivingEntity) (Object) this, mark, source, arenasLd$raw, lost, toHealth,
                armor, magic - enchant, enchant, absorbed);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void arenasLd$beforeHurt(DamageSource source, float amount, CallbackInfo ci) {
        arenasLd$hurtStart(amount);
    }

    // RETURN, not TAIL: a hit fully soaked by absorption returns early.
    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void arenasLd$afterHurt(DamageSource source, float amount, CallbackInfo ci) {
        arenasLd$hurtEnd(source);
    }

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"))
    private void arenasLd$armorIn(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (arenasLd$runStats != null) {
            arenasLd$armorIn = amount;
        }
    }

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("RETURN"))
    private void arenasLd$armorOut(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (arenasLd$runStats != null) {
            arenasLd$armorOut = cir.getReturnValueF();
        }
    }

    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("HEAD"))
    private void arenasLd$magicIn(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (arenasLd$runStats != null) {
            arenasLd$magicIn = amount;
            arenasLd$enchantBlocked = 0;
        }
    }

    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
    private void arenasLd$magicOut(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (arenasLd$runStats != null) {
            arenasLd$magicOut = cir.getReturnValueF();
        }
    }

    /** The protection-enchantment part of the magic step (the rest of it is Resistance). */
    @WrapOperation(method = "getDamageAfterMagicAbsorb", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterMagicAbsorb(FF)F"))
    private float arenasLd$enchantments(float damage, float protection, Operation<Float> original) {
        float after = original.call(damage, protection);
        if (arenasLd$runStats != null) {
            arenasLd$enchantBlocked = Math.max(0, damage - after);
        }
        return after;
    }

    /** A shield took the whole hit (vanilla; NeoForge may block part, counted the same way). */
    @ModifyExpressionValue(method = "hurt", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean arenasLd$shield(boolean blocked, @Local(argsOnly = true) float amount) {
        if (blocked && amount > 0 && arenasLd$runStats instanceof PlayerCounters counters && (Object) this instanceof Player) {
            RunStats.onShieldBlocked(counters, amount);
        }
        return blocked;
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void arenasLd$died(DamageSource source, CallbackInfo ci) {
        if (arenasLd$runStats instanceof RunStats stats && !dead) {
            RunStats.onMobDied((LivingEntity) (Object) this, stats, source);
        } else if (arenasLd$runStats instanceof PlayerCounters && !dead) {
            net.ledok.arenas_ld.stats.RunEvents.died((LivingEntity) (Object) this, source);
        }
    }

    /** Run event log: an effect ran out or was taken off a run player. */
    @Inject(method = "onEffectRemoved", at = @At("HEAD"))
    private void arenasLd$effectRemoved(MobEffectInstance effect, CallbackInfo ci) {
        if (arenasLd$runStats instanceof PlayerCounters) {
            net.ledok.arenas_ld.stats.RunEvents.effectRemoved((LivingEntity) (Object) this, effect);
        }
    }

    /** Run event log: a run player's hand or armor slot changed item (vanilla's own once-a-tick equipment check). */
    @WrapOperation(method = "collectEquipmentChanges", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;equipmentHasChanged(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean arenasLd$swap(LivingEntity self, net.minecraft.world.item.ItemStack before,
                                  net.minecraft.world.item.ItemStack after, Operation<Boolean> original,
                                  @Local net.minecraft.world.entity.EquipmentSlot slot) {
        boolean changed = original.call(self, before, after);
        if (changed && arenasLd$runStats instanceof PlayerCounters) {
            net.ledok.arenas_ld.stats.RunEvents.swap(self, slot.getName(), before, after);
        }
        return changed;
    }

    @Inject(method = "heal", at = @At("HEAD"))
    private void arenasLd$beforeHeal(float amount, CallbackInfo ci) {
        if (arenasLd$runStats != null) {
            arenasLd$hpBeforeHeal = getHealth();
        }
    }

    @Inject(method = "heal", at = @At("TAIL"))
    private void arenasLd$afterHeal(float amount, CallbackInfo ci) {
        Object mark = arenasLd$runStats;
        if (mark != null && amount > 0) {
            RunStats.onHealed((LivingEntity) (Object) this, mark, amount, Math.max(0, getHealth() - arenasLd$hpBeforeHeal));
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"))
    private void arenasLd$effectAdded(MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
        Object mark = arenasLd$runStats;
        if (mark != null) {
            RunStats.onEffectAdded((LivingEntity) (Object) this, mark, effect, source);
        }
    }
}
