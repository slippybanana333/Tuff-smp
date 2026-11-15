package dev.tuffsmp.shrinecraft.mixin;

import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The anvils must refuse to work on the mod's legendaries.
 *
 * WHY a mixin is needed at all: the anvil applies an enchanted book when the book's enchantment lists the item as
 * "acceptable" - and that list is vanilla's `minecraft:enchantable/*` tags, which (transitively, through the plain
 * #minecraft:swords / axes / pickaxes / spears / armor tags) contain several of our items. The vanilla check that
 * would normally stop all of this (`EnchantmentHelper.canHaveEnchantments` at the top of AnvilScreenHandler.updateResult)
 * only looks at whether the stack already HAS an enchantments component - and the Shrine hands the legendaries out
 * pre-enchanted (Sharpness 5 etc.), so for them the check always passes.
 *
 * With the anvil refusing these items they can also no longer be repaired or renamed there - on purpose: they are
 * unbreakable (repair is meaningless) and the owner asked for the anvil to not work on them at all. The grindstone
 * still strips built-in enchantments off (vanilla behaviour, worth knowing: DON'T grind a legendary).
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    @Shadow @Final private Property levelCost;

    /** true for every item the mod adds (all of the legendaries + the misc items). */
    private static boolean shrinecraft$isModItem(ItemStack stack) {
        return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getNamespace().equals("shrinecraft");
    }

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void shrinecraft$anvilRefusesModItems(CallbackInfo ci) {
        ForgingScreenHandlerAccessor slots = (ForgingScreenHandlerAccessor) this;
        if (shrinecraft$isModItem(slots.shrinecraft$getInput().getStack(0)) || shrinecraft$isModItem(slots.shrinecraft$getInput().getStack(1))) {
            slots.shrinecraft$getOutput().setStack(0, ItemStack.EMPTY);
            this.levelCost.set(0);
            ci.cancel();
        }
    }
}
