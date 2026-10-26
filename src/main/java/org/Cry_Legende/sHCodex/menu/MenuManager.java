package org.Cry_Legende.sHCodex.menu;

import me.clip.placeholderapi.PlaceholderAPI;
import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.config.MenuItemConfig;
import org.Cry_Legende.sHCodex.config.RegionConfig;
import org.Cry_Legende.sHCodex.playerdata.PlayerData;
import org.Cry_Legende.sHCodex.util.ChatUtil;
import org.Cry_Legende.sHCodex.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class MenuManager {

    private final SHCodex plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey regionIdKey;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    /**
     * Marque l'inventaire du Codex. Remplace la carte joueur -> menu ouvert et la comparaison de titre :
     * la carte était vidée par la fermeture de la page précédente juste après l'ouverture de la suivante,
     * et le titre change au /codex reload.
     */
    public static final class CodexMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public MenuManager(SHCodex plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "shcodex_action");
        this.regionIdKey = new NamespacedKey(plugin, "shcodex_region_id");
    }

    public void openCodexMenu(Player player, int page) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            player.sendMessage(ChatUtil.colorize("&cErreur: Données du joueur non trouvées."));
            return;
        }

        List<RegionConfig> allRegions = new ArrayList<>(plugin.getConfigManager().getRegionConfigs().values());
        allRegions.sort(Comparator.comparing(RegionConfig::getId));

        List<Integer> regionSlots = plugin.getConfigManager().getRegionItemSlots();
        int itemsPerPage = regionSlots.size();
        if (itemsPerPage == 0) {
            player.sendMessage(ChatUtil.colorize("&cErreur: Aucun slot défini pour les régions dans menu.yml."));
            return;
        }

        int totalPages = (int) Math.ceil((double) allRegions.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = plugin.getConfigManager().getMenuTitle()
            .replace("{page}", String.valueOf(page + 1))
            .replace("{max_pages}", String.valueOf(totalPages));
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            title = PlaceholderAPI.setPlaceholders(player, title);
        }

        CodexMenuHolder holder = new CodexMenuHolder();
        Inventory menu = Bukkit.createInventory(holder, plugin.getConfigManager().getMenuSize(), title);
        holder.inventory = menu;

        // Placer les items statiques
        for (Map.Entry<Integer, MenuItemConfig> entry : plugin.getConfigManager().getMenuStaticItems().entrySet()) {
            int slot = entry.getKey();
            MenuItemConfig itemConfig = entry.getValue();

            String itemName = itemConfig.getName();
            List<String> itemLore = new ArrayList<>(itemConfig.getLore());

            ItemBuilder builder = new ItemBuilder(itemConfig.getMaterial()).setName(itemName).setLore(itemLore);

            String action = itemConfig.getAction();
            if (action != null) {
                if (action.equalsIgnoreCase("NEXT_PAGE")) {
                    if (page < totalPages - 1) {
                        builder.addPersistentData(actionKey, PersistentDataType.STRING, "PAGE:" + (page + 1));
                    } else {
                        builder.setName(ChatUtil.colorize("&7Pas de page suivante"));
                    }
                } else if (action.equalsIgnoreCase("PREVIOUS_PAGE")) {
                    if (page > 0) {
                        builder.addPersistentData(actionKey, PersistentDataType.STRING, "PAGE:" + (page - 1));
                    } else {
                        builder.setName(ChatUtil.colorize("&7Pas de page précédente"));
                    }
                } else {
                    builder.addPersistentData(actionKey, PersistentDataType.STRING, action);
                }
            }
            menu.setItem(slot, builder.build());
        }

        // Placer les items des régions pour la page actuelle
        int startIndex = page * itemsPerPage;
        for (int i = 0; i < itemsPerPage; i++) {
            int regionIndex = startIndex + i;
            if (regionIndex < allRegions.size()) {
                RegionConfig regionConfig = allRegions.get(regionIndex);
                int menuSlot = regionSlots.get(i);

                boolean discovered = playerData.hasDiscovered(regionConfig.getId());
                MenuItemConfig displayItemConfig = discovered ? regionConfig.getDiscoveredItem() : regionConfig.getUndiscoveredItem();

                String itemName = displayItemConfig.getName().replace("{region_name}", regionConfig.getDisplayName());
                List<String> itemLore = new ArrayList<>();

                for (String line : displayItemConfig.getLore()) {
                    line = line.replace("{region_name}", regionConfig.getDisplayName());
                    if (discovered) {
                        if (line.contains("{discovery_date}")) {
                            long discoveryTimestamp = playerData.getDiscoveryTimestamp(regionConfig.getId());
                            line = line.replace("{discovery_date}", discoveryTimestamp > 0 ? dateFormat.format(new Date(discoveryTimestamp)) : "N/A");
                        }
                        // --- NOUVEAU ---
                        // Gérer le placeholder pour le nombre total de découvertes
                        if (line.contains("{total_discovered}")) {
                            int total = plugin.getStatsManager().getDiscoveryCount(regionConfig.getId());
                            line = line.replace("{total_discovered}", String.valueOf(total));
                        }
                    }
                    itemLore.add(line);
                }

                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    itemName = PlaceholderAPI.setPlaceholders(player, itemName);
                    itemLore = itemLore.stream().map(l -> PlaceholderAPI.setPlaceholders(player, l)).collect(Collectors.toList());
                }

                ItemBuilder itemBuilder = new ItemBuilder(displayItemConfig.getMaterial())
                    .setName(itemName)
                    .setLore(itemLore)
                    .addPersistentData(regionIdKey, PersistentDataType.STRING, regionConfig.getId());

                if (discovered && regionConfig.getDiscoveredItem().getAction() != null) {
                    itemBuilder.addPersistentData(actionKey, PersistentDataType.STRING, regionConfig.getDiscoveredItem().getAction());
                }

                menu.setItem(menuSlot, itemBuilder.build());

            } else if (plugin.getConfigManager().getMenuFillerItem() != null) {
                int menuSlot = regionSlots.get(i);
                menu.setItem(menuSlot, new ItemBuilder(plugin.getConfigManager().getMenuFillerItem().getMaterial())
                    .setName(plugin.getConfigManager().getMenuFillerItem().getName()).build());
            }
        }

        // Remplir les slots restants
        MenuItemConfig fillerConfig = plugin.getConfigManager().getMenuFillerItem();
        if (fillerConfig != null) {
            for (int i = 0; i < menu.getSize(); i++) {
                if (menu.getItem(i) == null || menu.getItem(i).getType() == Material.AIR) {
                    menu.setItem(i, new ItemBuilder(fillerConfig.getMaterial()).setName(fillerConfig.getName()).build());
                }
            }
        }

        player.openInventory(menu);
    }
}
