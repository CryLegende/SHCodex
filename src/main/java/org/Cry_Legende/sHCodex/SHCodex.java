package org.Cry_Legende.sHCodex;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.Cry_Legende.sHCodex.command.CodexCommand;
import org.Cry_Legende.sHCodex.config.ConfigManager;
import org.Cry_Legende.sHCodex.listener.MenuListener;
import org.Cry_Legende.sHCodex.listener.PlayerRegionListener;
import org.Cry_Legende.sHCodex.menu.MenuManager;
import org.Cry_Legende.sHCodex.network.CodexDatabase;
import org.Cry_Legende.sHCodex.network.NetworkCodex;
import org.Cry_Legende.sHCodex.network.NetworkLink;
import org.Cry_Legende.sHCodex.network.ShNetworkLink;
import org.Cry_Legende.sHCodex.placeholder.SHCodexExpansion;
import org.Cry_Legende.sHCodex.playerdata.PlayerDataManager;
import org.Cry_Legende.sHCodex.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.logging.Level;

public class SHCodex extends JavaPlugin {

    private static SHCodex instance;
    private ConfigManager configManager;
    private MenuManager menuManager;
    private PlayerDataManager playerDataManager;
    private StatsManager statsManager;
    private WorldGuard worldGuard;
    // Non nul seulement avec SHNetwork : les régions découvertes sont dans la base partagée
    private NetworkCodex network;

    public static StateFlag CODEX_REGION_FLAG;

    @Override
    public void onLoad() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        String flagName = "shcodex-discoverable";
        com.sk89q.worldguard.protection.flags.Flag<?> existingFlag = registry.get(flagName);

        if (existingFlag == null) {
            getLogger().info("WorldGuard flag '" + flagName + "' not found. Registering it now.");
            try {
                StateFlag flag = new StateFlag(flagName, true);
                registry.register(flag);
                CODEX_REGION_FLAG = flag;
                getLogger().info("Custom WorldGuard flag '" + flagName + "' registered successfully.");
            } catch (FlagConflictException e) {
                getLogger().log(Level.SEVERE, "A flag conflict occurred while trying to register '" + flagName + "'. This is unexpected.", e);
            }
        } else {
            getLogger().info("WorldGuard flag '" + flagName + "' already exists. Retrieving it.");
            if (existingFlag instanceof StateFlag) {
                CODEX_REGION_FLAG = (StateFlag) existingFlag;
            } else {
                getLogger().severe("A flag named '" + flagName + "' exists but is not of the correct type (StateFlag)! SHCodex may not function correctly.");
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;

        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().severe("WorldGuard not found! SHCodex will not work.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        worldGuard = WorldGuard.getInstance();
        getLogger().info("WorldGuard found and hooked.");

        // Initialisation des managers
        configManager = new ConfigManager(this);
        enableNetwork();
        playerDataManager = new PlayerDataManager(this, network);
        menuManager = new MenuManager(this);
        statsManager = new StatsManager(this, network);
        if (network != null) {
            network.start(playerDataManager::flushPlayer, statsManager::applyCounts);
        }

        // Lancement du calcul initial des stats si ce n'est pas déjà fait
        if (!statsManager.hasInitialCalculationBeenDone()) {
            statsManager.performInitialCalculation();
        }

        registerCommands();
        registerListeners();

        // Intégration avec PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SHCodexExpansion(this).register();
            getLogger().info("PlaceholderAPI found. Placeholders registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Some features might be limited.");
        }

        // Gestion du rechargement à chaud (PlugMan)
        getLogger().info("Loading data for all currently online players...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerDataManager.loadPlayerData(player.getUniqueId());
        }
        getLogger().info("Finished loading online player data.");

        getLogger().info("SHCodex has been enabled!");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayerData();
        }
        if (statsManager != null) {
            statsManager.saveStats();
        }
        if (network != null) {
            network.stop(); // termine les écritures en cours
        }
        getLogger().info("SHCodex has been disabled.");
    }

    /**
     * Passe en mode réseau si SHNetwork est installé et 'reseau.actif' est vrai. Les classes qui touchent à SHNetwork
     * ne sont chargées qu'ici : sans lui, le plugin se comporte exactement comme avant (playerdata/ et stats.yml).
     */
    private void enableNetwork() {
        org.bukkit.configuration.file.FileConfiguration config = configManager.getRawConfig();
        if (!config.getBoolean("reseau.actif", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) return;
        CodexDatabase db = null;
        try {
            NetworkLink link = ShNetworkLink.open();
            db = new CodexDatabase(
                config.getString("reseau.base-de-donnees.url", ""),
                config.getString("reseau.base-de-donnees.utilisateur", ""),
                config.getString("reseau.base-de-donnees.mot-de-passe", ""),
                Math.max(250, config.getInt("reseau.base-de-donnees.delai-ms", 3000)));
            db.createTable();
            Duration wait = Duration.ofSeconds(Math.max(1, config.getInt("reseau.attente-max-secondes", 10)));
            String channel = config.getString("reseau.canal", "shcodex").toLowerCase();
            String registry = config.getString("reseau.table-premieres-connexions", "");
            if (!registry.matches("[A-Za-z0-9_.]*")) {
                getLogger().warning("reseau.table-premieres-connexions ignorée (nom de table invalide) : " + registry);
                registry = "";
            }
            network = new NetworkCodex(this, db, link, wait, channel,
                config.getInt("reseau.cache-stats-secondes", 30), registry);
            getLogger().info("SHNetwork détecté : les régions découvertes et les stats sont dans la base partagée.");
        } catch (Exception | LinkageError e) {
            network = null;
            if (db != null) db.close();
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, codex local à ce serveur : " + e);
        }
    }

    private void registerCommands() {
        CodexCommand codexCommand = new CodexCommand(this);
        getCommand("codex").setExecutor(codexCommand);
        getCommand("codex").setTabCompleter(codexCommand);
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerRegionListener(this), this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(playerDataManager, this);
    }

    // Getters
    public static SHCodex getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    /** Le mode réseau, ou null sans SHNetwork. */
    public NetworkCodex getNetwork() {
        return network;
    }

    public WorldGuard getWorldGuard() {
        return worldGuard;
    }
}
