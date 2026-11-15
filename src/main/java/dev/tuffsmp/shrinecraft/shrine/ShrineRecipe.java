package dev.tuffsmp.shrinecraft.shrine;

import java.util.List;
import java.util.function.Function;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * One Shrine recipe: what you must hand over, and what you get.
 *
 * @param id           stable id used in saved Shrines and in the config file (never rename an id that is already in use in a world)
 * @param displayName  name shown in the admin GUI and in chat
 * @param ingredients  the materials that get consumed. An EMPTY list means "recipe not set up yet" - the Shrine will refuse to craft it.
 * @param output       builds the reward (already enchanted etc.)
 * @param placeholder  true = this recipe is a stand-in the owner still has to fill in; it is clearly labelled everywhere
 */
public record ShrineRecipe(String id, String displayName, List<Ingredient> ingredients, Function<RegistryWrapper.WrapperLookup, ItemStack> output, boolean placeholder) {

    /**
     * "count x item". {@code potion} is optional: when set (e.g. minecraft:strong_swiftness) only potions of exactly that type count, so
     * "9 Potions of Swiftness II" can be asked for (the item is then minecraft:potion).
     */
    public record Ingredient(Item item, int count, Identifier potion) {
        public Ingredient(Item item, int count) {
            this(item, count, null);
        }

        public boolean matches(ItemStack stack) {
            if (!stack.isOf(item)) {
                return false;
            }
            if (potion == null) {
                return true;
            }
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            return contents != null && contents.potion().isPresent() && contents.potion().get().matchesId(potion);
        }

        /** A stack of this ingredient (right potion included), used for display and by the self-test. */
        public ItemStack stackOf(int amount) {
            ItemStack stack = new ItemStack(item, amount);
            if (potion != null) {
                Registries.POTION.getEntry(potion).ifPresent(entry -> stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(entry)));
            }
            return stack;
        }

        public Text displayName() {
            return stackOf(1).getName();
        }
    }

    public ItemStack createOutput(RegistryWrapper.WrapperLookup registries) {
        return output.apply(registries);
    }

    /** A recipe with no ingredients is an unfinished placeholder and cannot be crafted. */
    public boolean isCraftable() {
        return !ingredients.isEmpty();
    }

    public ShrineRecipe with(String newName, List<Ingredient> newIngredients, boolean newPlaceholder) {
        return new ShrineRecipe(id, newName, List.copyOf(newIngredients), output, newPlaceholder);
    }
}
