package org.Cry_Legende.sHCodex.stats;

import org.Cry_Legende.sHCodex.SHCodex;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class StatsManager {

    private final SHCodex plugin;
    private final File statsFile;
    private FileConfiguration statsConfig;
    private final Map<String, Integer> discoveryCounts = new ConcurrentHashMap<>();

    public StatsManager(SHCodex plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        loadStats();
    }

    /**
     * Charge les statistiques depuis le fichier stats.yml.
     */
    public void loadStats() {
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create stats.yml", e);
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        discoveryCounts.clear();
        if (statsConfig.isConfigurationSection("discovery-counts")) {
            for (String regionId : statsConfig.getConfigurationSection("discovery-counts").getKeys(false)) {
                discoveryCounts.put(regionId, statsConfig.getInt("discovery-counts." + regionId));
            }
        }
        plugin.getLogger().info("Loaded " + discoveryCounts.size() + " region discovery stats.");
    }

    /**
     * Sauvegarde les statistiques actuelles dans le fichier stats.yml.
     */
    public void saveStats() {
        // Met à jour la config en mémoire avant de sauvegarder pour ne rien écraser
        statsConfig.set("discovery-counts", null); // Vide l'ancienne section
        for (Map.Entry<String, Integer> entry : discoveryCounts.entrySet()) {
            statsConfig.set("discovery-counts." + entry.getKey(), entry.getValue());
        }
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save stats.yml", e);
        }
    }

    /**
     * Vérifie si le calcul initial des stats a déjà été effectué en regardant une clé dans stats.yml.
     *
     * @return true si le calcul a déjà été fait, false sinon.
     */
    public boolean hasInitialCalculationBeenDone() {
        return statsConfig.getBoolean("stats-recalculated", false);
    }

    /**
     * Lance le recalcul complet des statistiques en parcourant tous les fichiers de données des joueurs.
     * Cette opération est lourde et doit être lancée de manière asynchrone pour ne pas lagger le serveur.
     */
    public void performInitialCalculation() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getLogger().info("Starting a full scan of player data to build initial stats. This may take a moment...");

            File playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
            if (!playerDataFolder.exists() || !playerDataFolder.isDirectory()) {
                plugin.getLogger().warning("PlayerData folder not found. Cannot calculate initial stats.");
                return;
            }

            File[] playerFiles = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (playerFiles == null || playerFiles.length == 0) {
                plugin.getLogger().info("No player data files found. Initial stats calculation skipped.");
                markCalculationAsDone(); // On le marque comme fait même s'il n'y a personne pour ne pas réessayer.
                return;
            }

            Map<String, Integer> newCounts = new ConcurrentHashMap<>();

            for (File playerFile : playerFiles) {
                FileConfiguration pConfig = YamlConfiguration.loadConfiguration(playerFile);
                ConfigurationSection discoveredSection = pConfig.getConfigurationSection("discovered-regions");
                if (discoveredSection != null) {
                    for (String regionId : discoveredSection.getKeys(false)) {
                        newCounts.put(regionId, newCounts.getOrDefault(regionId, 0) + 1);
                    }
                }
            }

            // On retourne sur le thread principal pour mettre à jour la map en mémoire et sauvegarder
            Bukkit.getScheduler().runTask(plugin, () -> {
                discoveryCounts.clear();
                discoveryCounts.putAll(newCounts);
                markCalculationAsDone();
                saveStats();
                plugin.getLogger().info("Successfully calculated and saved initial stats for " + discoveryCounts.size() + " regions from " + playerFiles.length + " player files.");
            });
        });
    }

    /**
     * Ajoute la clé 'stats-recalculated: true' au fichier de config.
     */
    private void markCalculationAsDone() {
        statsConfig.set("stats-recalculated", true);
    }

    public int getDiscoveryCount(String regionId) {
        return discoveryCounts.getOrDefault(regionId, 0);
    }

    public void incrementDiscoveryCount(String regionId) {
        discoveryCounts.put(regionId, getDiscoveryCount(regionId) + 1);
    }

    public void decrementDiscoveryCount(String regionId) {
        int currentCount = getDiscoveryCount(regionId);
        if (currentCount > 0) {
            discoveryCounts.put(regionId, currentCount - 1);
        }
    }
}
