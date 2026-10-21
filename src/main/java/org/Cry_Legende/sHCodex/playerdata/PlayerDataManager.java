package org.Cry_Legende.sHCodex.playerdata;

import org.Cry_Legende.sHCodex.SHCodex;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerDataManager implements Listener {

    private final SHCodex plugin;
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();
    private final File playerDataFolder;

    public PlayerDataManager(SHCodex plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
    }

    /**
     * Récupère les données d'un joueur actuellement chargé en mémoire.
     *
     * @param uuid L'UUID du joueur.
     * @return Les données du joueur, ou null s'il n'est pas chargé.
     */
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    /**
     * Charge les données d'un joueur depuis son fichier .yml.
     * Si le joueur est déjà chargé, ne fait rien.
     *
     * @param uuid L'UUID du joueur à charger.
     */
    public void loadPlayerData(UUID uuid) {
        if (playerDataMap.containsKey(uuid)) {
            return; // Déjà chargé
        }

        File playerFile = new File(playerDataFolder, uuid.toString() + ".yml");
        if (!playerFile.exists()) {
            playerDataMap.put(uuid, new PlayerData()); // Nouveau joueur, données vides
            return;
        }

        FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
        Map<String, Long> discoveredRegions = new HashMap<>();
        ConfigurationSection discoveredSection = playerConfig.getConfigurationSection("discovered-regions");
        if (discoveredSection != null) {
            for (String regionId : discoveredSection.getKeys(false)) {
                discoveredRegions.put(regionId, discoveredSection.getLong(regionId));
            }
        }
        playerDataMap.put(uuid, new PlayerData(discoveredRegions));
        plugin.getLogger().fine("Loaded player data for " + uuid.toString());
    }

    /**
     * Sauvegarde les données d'un joueur chargé en mémoire dans son fichier .yml.
     *
     * @param uuid L'UUID du joueur à sauvegarder.
     */
    public void savePlayerData(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data == null) return;

        File playerFile = new File(playerDataFolder, uuid.toString() + ".yml");
        FileConfiguration playerConfig = new YamlConfiguration();

        // Sauvegarder les régions découvertes avec leur timestamp
        for (Map.Entry<String, Long> entry : data.getDiscoveredRegionsWithTimestamps().entrySet()) {
            playerConfig.set("discovered-regions." + entry.getKey(), entry.getValue());
        }

        try {
            playerConfig.save(playerFile);
            plugin.getLogger().fine("Saved player data for " + uuid.toString());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player data for " + uuid, e);
        }
    }

    /**
     * Sauvegarde les données de tous les joueurs actuellement chargés.
     */
    public void saveAllPlayerData() {
        plugin.getLogger().info("Saving all loaded player data...");
        for (UUID uuid : playerDataMap.keySet()) {
            savePlayerData(uuid);
        }
        plugin.getLogger().info("All player data saved.");
    }

    /**
     * Décharge les données d'un joueur de la mémoire.
     * Utile pour les joueurs hors ligne après une commande admin.
     *
     * @param uuid L'UUID du joueur à décharger.
     */
    public void unloadPlayerData(UUID uuid) {
        playerDataMap.remove(uuid);
        plugin.getLogger().fine("Unloaded player data for " + uuid.toString());
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Charger les données de manière asynchrone pour éviter de lagger le thread principal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            loadPlayerData(player.getUniqueId());
            plugin.getLogger().info("Player data for " + player.getName() + " loaded.");
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        // Sauvegarder et décharger les données de manière asynchrone
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            savePlayerData(uuid);
            unloadPlayerData(uuid);
            plugin.getLogger().info("Player data for " + player.getName() + " saved and unloaded.");
        });
    }
}
