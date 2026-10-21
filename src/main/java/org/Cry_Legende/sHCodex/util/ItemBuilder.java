package org.Cry_Legende.sHCodex.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta itemMeta;

    public ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
        this.itemMeta = itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone(); // Travailler sur une copie
        this.itemMeta = this.itemStack.getItemMeta();
    }

    public ItemBuilder setName(String name) {
        if (name != null) {
            itemMeta.setDisplayName(ChatUtil.colorize(name));
        }
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ChatUtil.colorize(line));
            }
            itemMeta.setLore(coloredLore);
        }
        return this;
    }

    public ItemBuilder setLore(String... lore) {
        return setLore(Arrays.asList(lore));
    }

    public ItemBuilder setAmount(int amount) {
        itemStack.setAmount(amount);
        return this;
    }

    public ItemBuilder addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction) {
        itemMeta.addEnchant(enchantment, level, ignoreLevelRestriction);
        return this;
    }

    public ItemBuilder addItemFlags(ItemFlag... flags) {
        itemMeta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder setUnbreakable(boolean unbreakable) {
        itemMeta.setUnbreakable(unbreakable);
        return this;
    }

    public <T, Z> ItemBuilder addPersistentData(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        itemMeta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    public ItemBuilder setCustomModelData(int modelData) {
        itemMeta.setCustomModelData(modelData);
        return this;
    }

    public <T, Z> Z getPersistentData(NamespacedKey key, PersistentDataType<T, Z> type) {
        return itemMeta.getPersistentDataContainer().get(key, type);
    }

    public boolean hasPersistentData(NamespacedKey key, PersistentDataType<?, ?> type) {
        return itemMeta.getPersistentDataContainer().has(key, type);
    }


    public ItemStack build() {
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}