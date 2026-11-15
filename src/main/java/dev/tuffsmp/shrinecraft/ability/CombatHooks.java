package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.item.FrostLongbowItem;
import dev.tuffsmp.shrinecraft.item.SculkCannonItem;
import dev.tuffsmp.shrinecraft.item.SculkMaceItem;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.registry.ModKeys;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Everything that reacts to damage and kills:
 *  - Frost Longbow: Deep Freeze arrow (armed by the ability) freezes the target solid
 *  - Sculk Cannon: Blindness (mode 1) or armor-ignoring bonus damage (mode 2)
 *  - Sculk Mace: Warden kills advance the tier
 */
public final class CombatHooks {
    private CombatHooks() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatHooks::afterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(CombatHooks::afterDeath);
    }

    private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
        if (blocked || !(target.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        // Our own bonus-damage sources must never trigger more bonus damage (that would loop forever).
        if (source.isOf(ModKeys.SCULK_WAIL) || source.isOf(ModKeys.SCULK_WAVE)) {
            return;
        }
        Entity direct = source.getSource();
        if (!(direct instanceof PersistentProjectileEntity arrow)) {
            return;
        }
        if (TrustManager.trusts(source.getAttacker(), target)) {
            return; // trusted players get none of the arrow ability extras (deep freeze, blind, wail)
        }
        if (arrow.getCommandTags().contains(FrostLongbowItem.DEEP_FREEZE_ARROW_TAG)) {
            DeepFreezeManager.freeze(target, FrostLongbowItem.FREEZE_SECONDS);
        }
        if (arrow.getCommandTags().contains(SculkCannonItem.BLIND_ARROW_TAG)) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, SculkCannonItem.BLIND_SECONDS * 20, 0), source.getAttacker());
        }
        if (arrow.getCommandTags().contains(SculkCannonItem.WAIL_ARROW_TAG) && target.isAlive()) {
            DamageSource wail = new DamageSource(
                world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(ModKeys.SCULK_WAIL), source.getAttacker(), source.getAttacker());
            target.damage(world, wail, SculkCannonItem.WAIL_BONUS_DAMAGE);
        }
    }

    private static void afterDeath(LivingEntity dead, DamageSource source) {
        if (!(dead instanceof WardenEntity) || !(dead.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            ItemStack weapon = player.getMainHandStack();
            if (weapon.isOf(ModItems.SCULK_MACE)) {
                SculkMaceItem.tryAdvance(player, weapon, world);
            }
        }
    }
}
