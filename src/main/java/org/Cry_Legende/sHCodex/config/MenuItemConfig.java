package org.Cry_Legende.sHCodex.config;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public class MenuItemConfig {
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final String action; // e.g., "COMMAND:cmd", "NEXT_PAGE", "OPEN_MENU:other_menu_id"
    private final Map<String, String> nbtData; // Pourrait être utilisé pour stocker des données NBT si nécessaire

    public MenuItemConfig(Material material, String name, List<String> lore, String action, Map<String, String> nbtData) {
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.action = action;
        this.nbtData = nbtData;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public String getAction() {
        return action;
    }

    public Map<String, String> getNbtData() {
        return nbtData;
    }
}
