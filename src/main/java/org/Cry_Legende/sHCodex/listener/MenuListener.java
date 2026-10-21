package org.Cry_Legende.sHCodex.listener;

import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.config.RegionConfig;
import org.Cry_Legende.sHCodex.util.ChatUtil; // Assurez-vous d'avoir cet import
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // Pour potentiellement ajuster la priorité
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class MenuListener implements Listener {

    private final SHCodex plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey regionIdKey;
    private final String menuTitleBase; // Stocker la base du titre pour comparaison

    public MenuListener(SHCodex plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "shcodex_action");
        this.regionIdKey = new NamespacedKey(plugin, "shcodex_region_id");
        // Extraire la partie fixe du titre du menu pour une vérification de secours
        String rawTitle = plugin.getConfigManager().getMenuTitle(); // Ex: "&8&lMenu &8| Régions ({page}/{max_pages})"
        if (rawTitle.contains("{page}")) {
            this.menuTitleBase = ChatUtil.colorize(rawTitle.substring(0, rawTitle.indexOf("{page}")));
        } else {
            this.menuTitleBase = ChatUtil.colorize(rawTitle); // Au cas où il n'y aurait pas de placeholder de page
        }
    }

    @EventHandler(priority = EventPriority.NORMAL) // Vous pouvez essayer HIGHEST si ça ne marche toujours pas
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();

        if (clickedInventory == null || !topInventory.equals(clickedInventory)) {
            // Clic en dehors ou dans l'inventaire du joueur
            return;
        }

        // Vérification si c'est notre menu
        boolean isOurMenu = false;
        String menuIdentifier = plugin.getMenuManager().getPlayerOpenMenuIdentifier(player);

        if (menuIdentifier != null && menuIdentifier.startsWith("CODEX_MAIN_")) {
            isOurMenu = true;
        } else {
            // Plan B: Vérifier si le titre de l'inventaire correspond à notre pattern de base
            // Cela peut aider si menuIdentifier est devenu null à cause d'un timing
            // lors de la réouverture rapide d'inventaire.
            // On vérifie que le titre actuel de l'inventaire commence par la partie fixe configurée.
            if (event.getView().getTitle().startsWith(this.menuTitleBase)) {
                // Potentiel problème ici: si PAPI est dans le titre, PlaceholderAPI.setPlaceholders peut changer le début.
                // Pour un test, on peut être plus simple:
                // plugin.getLogger().info("[DEBUG] MenuID was null or wrong. TopInv Title: '" + event.getView().getTitle() + "', Base Pattern: '" + this.menuTitleBase + "'");
                isOurMenu = true; // On considère que c'est notre menu si le titre matche
            }
        }

        if (!isOurMenu) {
            // Ce n'est pas notre menu, on ne fait rien.
            return;
        }

        // C'est notre menu, on annule le clic.
        event.setCancelled(true);

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

    @EventHandler(priority = EventPriority.MONITOR) // MONITOR pour s'assurer qu'il s'exécute après les autres
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        String menuIdentifier = plugin.getMenuManager().getPlayerOpenMenuIdentifier(player);
        // On ne clear que si l'identifiant correspond ET que le titre de l'inventaire fermé
        // correspond aussi à ce que l'on attend de nos menus.
        // Cela évite de clear si un autre plugin ouvre un inventaire juste après une action de notre menu
        // qui aurait dû fermer le nôtre, mais que l'InventoryCloseEvent de notre menu arrive en retard.
        if (menuIdentifier != null && menuIdentifier.startsWith("CODEX_MAIN_")) {
            // Vérification supplémentaire basée sur le titre pour plus de robustesse.
            // Si le titre de l'inventaire fermé commence par notre base de titre,
            // alors il est très probable que ce soit notre menu qui se ferme.
            if (event.getView().getTitle().startsWith(this.menuTitleBase)) {
                // plugin.getLogger().info("[DEBUG] Clearing open menu for " + player.getName() + " due to close event. Title: " + event.getView().getTitle());
                plugin.getMenuManager().clearPlayerOpenMenu(player);
            }
        }
    }
}