package dev.tuffsmp.shrinecraft.mixin;

import dev.tuffsmp.shrinecraft.item.FrostLongbowItem;
import dev.tuffsmp.shrinecraft.registry.ModKeys;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.registry.Registry;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frost Longbow: an arrow that was fired from a FULL draw hurts with the "frost_arrow" damage type instead of "arrow".
 * That damage type (data/shrinecraft/damage_type/frost_arrow.json) is listed in the vanilla tag minecraft:bypasses_armor,
 * which is exactly what makes the hit ignore armor ("true damage"). Everything else about the arrow stays vanilla.
 */
@Mixin(DamageSources.class)
public abstract class DamageSourcesMixin {
    @Shadow
    @Final
    public Registry<DamageType> registry;

    @Inject(method = "arrow", at = @At("RETURN"), cancellable = true)
    private void shrinecraft$frostArrowIsTrueDamage(PersistentProjectileEntity arrow, @Nullable Entity attacker, CallbackInfoReturnable<DamageSource> cir) {
        if (FrostLongbowItem.TRUE_DAMAGE && arrow.getCommandTags().contains(FrostLongbowItem.FROST_ARROW_TAG)) {
            cir.setReturnValue(new DamageSource(registry.getOrThrow(ModKeys.FROST_ARROW), arrow, attacker));
        }
    }
}
