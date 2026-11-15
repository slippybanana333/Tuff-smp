package dev.tuffsmp.shrinecraft.mixin;

import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ForgingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the input/output inventories of the anvil/smithing screen handlers.
 * (@Shadow cannot reach them from a subclass-targeted mixin because they are declared in this parent class.)
 */
@Mixin(ForgingScreenHandler.class)
public interface ForgingScreenHandlerAccessor {
    @Accessor("input")
    Inventory shrinecraft$getInput();

    @Accessor("output")
    CraftingResultInventory shrinecraft$getOutput();
}
