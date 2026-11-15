package dev.tuffsmp.shrinecraft.util;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;

/** Small helper so item classes can say "Sharpness 5, Unbreaking 3" in one readable line. */
public final class Enchants {
    private Enchants() {}

    public static RegistryEntry<Enchantment> entry(RegistryWrapper.WrapperLookup registries, RegistryKey<Enchantment> key) {
        return registries.getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(key);
    }

    /** Builder: Enchants.on(stack, registries).add(Enchantments.SHARPNESS, 5).add(...).apply(); */
    public static Builder on(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        return new Builder(stack, registries);
    }

    public static final class Builder {
        private final ItemStack stack;
        private final RegistryWrapper.WrapperLookup registries;
        private final Map<RegistryKey<Enchantment>, Integer> levels = new LinkedHashMap<>();

        private Builder(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
            this.stack = stack;
            this.registries = registries;
        }

        public Builder add(RegistryKey<Enchantment> key, int level) {
            levels.put(key, level);
            return this;
        }

        /** Adds these levels on top of what is already on the stack. */
        public ItemStack apply() {
            EnchantmentHelper.apply(stack, builder -> {
                for (Map.Entry<RegistryKey<Enchantment>, Integer> e : levels.entrySet()) {
                    builder.set(entry(registries, e.getKey()), e.getValue());
                }
            });
            return stack;
        }

        /** Removes the given enchantments first (so a tier/mode change replaces rather than stacks), then adds ours. */
        public ItemStack replace(RegistryKey<Enchantment>... toRemove) {
            EnchantmentHelper.apply(stack, builder -> {
                for (RegistryKey<Enchantment> key : toRemove) {
                    RegistryEntry<Enchantment> e = entry(registries, key);
                    builder.remove(other -> other.matches(e));
                }
                for (Map.Entry<RegistryKey<Enchantment>, Integer> en : levels.entrySet()) {
                    builder.set(entry(registries, en.getKey()), en.getValue());
                }
            });
            return stack;
        }
    }
}
