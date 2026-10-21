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
import org.Cry_Legende.sHCodex.placeholder.SHCodexExpansion;
import org.Cry_Legende.sHCodex.playerdata.PlayerDataManager;
import org.Cry_Legende.sHCodex.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class SHCodex extends JavaPlugin {

    private static SHCodex instance;
    private ConfigManager configManager;
    private MenuManager menuManager;
    private PlayerDataManager playerDataManager;
    private StatsManager statsManager;
    private WorldGuard worldGuard;

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
        playerDataManager = new PlayerDataManager(this);
        menuManager = new MenuManager(this);
        statsManager = new StatsManager(this);

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
        getLogger().info("SHCodex has been disabled.");
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

    public WorldGuard getWorldGuard() {
        return worldGuard;
    }
}
