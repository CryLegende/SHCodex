package org.Cry_Legende.sHCodex.listener;

import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.config.RegionConfig;
import org.Cry_Legende.sHCodex.menu.MenuManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // Pour potentiellement ajuster la priorité
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class MenuListener implements Listener {

    private final SHCodex plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey regionIdKey;

    public MenuListener(SHCodex plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "shcodex_action");
        this.regionIdKey = new NamespacedKey(plugin, "shcodex_region_id");
    }

    /** Le Codex est-il l'inventaire du haut ? (getHolder(false) : pas de copie d'état de bloc pour les autres inventaires) */
    private static boolean isCodexMenu(Inventory topInventory) {
        return topInventory.getHolder(false) instanceof MenuManager.CodexMenuHolder;
    }

    @EventHandler(priority = EventPriority.NORMAL) // Vous pouvez essayer HIGHEST si ça ne marche toujours pas
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();

        if (!isCodexMenu(topInventory)) {
            // Ce n'est pas notre menu, on ne fait rien.
            return;
        }

        // C'est notre menu : tout clic est annulé, y compris dans l'inventaire du joueur
        // (shift-clic qui déposait un objet dans le menu où il disparaissait, double-clic qui ramassait les objets du menu).
        event.setCancelled(true);

        if (clickedInventory == null || !topInventory.equals(clickedInventory)) {
            // Clic en dehors ou dans l'inventaire du joueur
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return; // Clic sur un slot vide
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) {
            return; // Item sans action (déjà annulé)
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String actionString = pdc.get(actionKey, PersistentDataType.STRING);

        if (actionString == null) return;

        // --- Début de la logique de traitement des actions ---
        if (actionString.startsWith("PAGE:")) {
            try {
                int page = Integer.parseInt(actionString.substring(5));
                plugin.getMenuManager().openCodexMenu(player, page);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid page number in menu action: " + actionString + " for player " + player.getName());
            }
        } else if (actionString.equalsIgnoreCase("CLOSE")) {
            player.closeInventory();
        } else if (actionString.startsWith("COMMAND:")) {
            String command = actionString.substring(8);
            command = command.replace("{player}", player.getName());

            if (pdc.has(regionIdKey, PersistentDataType.STRING)) {
                String regionId = pdc.get(regionIdKey, PersistentDataType.STRING);
                command = command.replace("{region_id}", regionId);
                RegionConfig regionConf = plugin.getConfigManager().getRegionConfig(regionId);
                if (regionConf != null) {
                    command = command.replace("{region_name}", regionConf.getDisplayName());
                }
            }
            Bukkit.dispatchCommand(player, command); // Exécuter la commande en tant que joueur
        } else if (actionString.startsWith("REGION_ACTION:")) {
            String regionId = pdc.get(regionIdKey, PersistentDataType.STRING);
            if (regionId != null) {
                RegionConfig regionConf = plugin.getConfigManager().getRegionConfig(regionId);
                if (regionConf != null && regionConf.getDiscoveredItem() != null && regionConf.getDiscoveredItem().getAction() != null) {
                    String clickAction = regionConf.getDiscoveredItem().getAction();
                    if (clickAction.startsWith("COMMAND:")) {
                        String command = clickAction.substring(8).replace("{player}", player.getName()).replace("{region_id}", regionId);
                        command = command.replace("{region_name}", regionConf.getDisplayName());
                        Bukkit.dispatchCommand(player, command);
                    }
                }
            }
        }
        // --- Fin de la logique de traitement des actions ---
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Un glisser-déposer qui touche le menu y déposerait des objets perdus à la fermeture
        if (isCodexMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }
}