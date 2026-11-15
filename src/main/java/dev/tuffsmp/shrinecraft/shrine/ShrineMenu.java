package dev.tuffsmp.shrinecraft.shrine;

import dev.tuffsmp.shrinecraft.shrine.ShrineRecipe.Ingredient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The admin's recipe picker: a chest-style window showing all 14 rewards (each as the real 3D item).
 * It only exists on the server - the client just sees a normal 2-row chest - so it needs no client code.
 * Clicking a reward assigns it to the Shrine; nothing can ever be taken out of the window.
 */
public class ShrineMenu extends GenericContainerScreenHandler {
    private final ShrineBlockEntity shrine;
    private final List<ShrineRecipe> recipes = ShrineRecipes.all();

    public ShrineMenu(int syncId, PlayerInventory playerInventory, ShrineBlockEntity shrine) {
        super(ScreenHandlerType.GENERIC_9X2, syncId, playerInventory, buildDisplay(playerInventory.player), 2);
        this.shrine = shrine;
    }

    private static SimpleInventory buildDisplay(PlayerEntity viewer) {
        SimpleInventory inv = new SimpleInventory(18);
        List<ShrineRecipe> all = ShrineRecipes.all();
        for (int slot = 0; slot < 18; slot++) {
            if (slot < all.size()) {
                inv.setStack(slot, displayStack(all.get(slot), slot + 1, viewer));
            } else {
                ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                filler.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
                inv.setStack(slot, filler);
            }
        }
        return inv;
    }

    private static ItemStack displayStack(ShrineRecipe recipe, int number, PlayerEntity viewer) {
        ItemStack stack = recipe.createOutput(viewer.getEntityWorld().getRegistryManager());
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(number + ". " + recipe.displayName() + (recipe.placeholder() ? "  [PLACEHOLDER]" : ""))
            .formatted(recipe.placeholder() ? Formatting.RED : Formatting.GOLD).styled(s -> s.withItalic(false)));
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("Click to assign this reward to the Shrine").formatted(Formatting.YELLOW).styled(s -> s.withItalic(false)));
        if (recipe.ingredients().isEmpty()) {
            lore.add(Text.literal("Recipe not set up yet - can't be crafted").formatted(Formatting.RED).styled(s -> s.withItalic(false)));
        } else {
            lore.add(Text.literal("Costs:").formatted(Formatting.GRAY).styled(s -> s.withItalic(false)));
            for (Ingredient in : recipe.ingredients()) {
                lore.add(Text.literal(" " + in.count() + " x ").formatted(Formatting.GRAY).append(in.displayName().copy().formatted(Formatting.WHITE)).styled(s -> s.withItalic(false)));
            }
        }
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return stack;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Never let anything be moved, dropped or dragged in this window.
        if (slotIndex >= 0 && slotIndex < recipes.size() && actionType == SlotActionType.PICKUP && player instanceof ServerPlayerEntity sp) {
            assign(sp, recipes.get(slotIndex));
            return;
        }
        // Make the client forget any item it thinks it picked up.
        this.sendContentUpdates();
        syncState();
    }

    private void assign(ServerPlayerEntity admin, ShrineRecipe recipe) {
        if (!ShrineInteraction.isAdmin(admin) || shrine.isRemoved() || shrine.getShrineState() != ShrineState.UNCONFIGURED) {
            admin.closeHandledScreen();
            return;
        }
        shrine.configure(recipe.id());
        admin.closeHandledScreen();
        admin.sendMessage(Text.literal("Shrine set to: " + recipe.displayName()).formatted(Formatting.GOLD), false);
        admin.getEntityWorld().playSound(null, shrine.getPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return !shrine.isRemoved() && player.squaredDistanceTo(shrine.getPos().getX() + 0.5, shrine.getPos().getY() + 0.5, shrine.getPos().getZ() + 0.5) <= 64.0;
    }
}
