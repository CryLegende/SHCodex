package org.Cry_Legende.sHCodex.config;

import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ConfigManager {

    private final SHCodex plugin;
    private FileConfiguration config;
    private FileConfiguration regionsConfig;
    private FileConfiguration menuConfig;

    private File configFile;
    private File regionsFile;
    private File menuFile;

    // Cached values
    private List<String> blacklistedWorlds;
    private Map<String, RegionConfig> regionConfigs = new HashMap<>();
    private String menuTitle;
    private int menuSize;
    private Map<Integer, MenuItemConfig> menuStaticItems = new HashMap<>();
    private MenuItemConfig menuFillerItem;
    private List<Integer> regionItemSlots = new ArrayList<>();

    // Cached messages
    private String noPermissionMessage;
    private String reloadMessage;
    private String playerOnlyMessage;
    private String worldBlacklistedMessage;

    // Cached admin messages
    private String adminUsageMessage, adminPlayerNotFoundMessage, adminRegionNotFoundMessage;
    private String adminAlreadyUnlockedMessage, adminUnlockSuccessMessage;
    private String adminNotYetUnlockedMessage, adminLockSuccessMessage;
    private String adminListHeader, adminListFormatDiscovered, adminListFormatUndiscovered;

    public ConfigManager(SHCodex plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        menuFile = new File(plugin.getDataFolder(), "menu.yml");

        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        if (!regionsFile.exists()) {
            plugin.saveResource("regions.yml", false);
        }
        if (!menuFile.exists()) {
            plugin.saveResource("menu.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);
        menuConfig = YamlConfiguration.loadConfiguration(menuFile);

        // Charger les valeurs de config.yml
        String prefix = ChatUtil.colorize(config.getString("messages.prefix", ""));
        blacklistedWorlds = config.getStringList("blacklist-worlds");
        noPermissionMessage = prefix + ChatUtil.colorize(config.getString("messages.no-permission", "&cYou don't have permission."));
        reloadMessage = prefix + ChatUtil.colorize(config.getString("messages.reload", "&aSHCodex reloaded successfully!"));
        playerOnlyMessage = prefix + ChatUtil.colorize(config.getString("messages.player-only", "&cThis command can only be run by a player."));
        worldBlacklistedMessage = prefix + ChatUtil.colorize(config.getString("messages.world-blacklisted", "&cYou cannot open the Codex in this world."));

        // Charger les messages admin
        adminUsageMessage = prefix + ChatUtil.colorize(config.getString("messages.admin.usage", "&cUsage: /codex admin <player> <list|unlock|lock> [region_id]"));
        adminPlayerNotFoundMessage = prefix + ChatUtil.colorize(config.getString("messages.admin.player-not-found", "&cPlayer '{player}' not found."));
        adminRegionNotFoundMessage = prefix + ChatUtil.colorize(config.getString("messages.admin.region-not-found", "&cRegion '{region_id}' not found."));
        adminAlreadyUnlockedMessage = prefix + ChatUtil.colorize(config.getString("messages.admin.already-unlocked", "&e{player} has already unlocked {region_name}."));
        adminUnlockSuccessMessage = prefix + ChatUtil.colorize(config.getString("messages.admin.unlock-success", "&aUnlocked {region_name} for {player}."));
        adminNotYetUnlockedMessage = prefix + ChatUtil.colorize(config.getString("messages.admin.not-yet-unlocked", "&e{player} has not unlocked {region_name} yet."));
        adminLockSuccessMessage = prefix + ChatUtil.colorize(config.getString("messages.admin.lock-success", "&cLocked {region_name} for {player}."));
        adminListHeader = ChatUtil.colorize(config.getString("messages.admin.list-header", "&6--- {player}'s Codex ({discovered}/{total}) ---"));
        adminListFormatDiscovered = ChatUtil.colorize(config.getString("messages.admin.list-format-discovered", "&e✔ {region_name}"));
        adminListFormatUndiscovered = ChatUtil.colorize(config.getString("messages.admin.list-format-undiscovered", "&c✖ {region_name}"));

        // Charger les régions
        loadRegionConfigs();

        // Charger la configuration du menu
        loadMenuConfig();
    }

    private void loadRegionConfigs() {
        regionConfigs.clear();
        ConfigurationSection regionsSection = regionsConfig.getConfigurationSection("regions");
        if (regionsSection != null) {
            for (String key : regionsSection.getKeys(false)) {
                ConfigurationSection rSec = regionsSection.getConfigurationSection(key);
                if (rSec == null) continue;

                String wgRegionId = rSec.getString("worldguard-region-id");
                String worldName = rSec.getString("world");
                String displayName = ChatUtil.colorize(rSec.getString("display-name", "&7Unknown Region"));

                Material undiscoveredMat = Material.matchMaterial(rSec.getString("item-undiscovered.material", "BOOK"));
                String undiscoveredName = ChatUtil.colorize(rSec.getString("item-undiscovered.name", "&c???"));
                List<String> undiscoveredLore = rSec.getStringList("item-undiscovered.lore").stream().map(ChatUtil::colorize).collect(Collectors.toList());
                MenuItemConfig undiscoveredItem = new MenuItemConfig(undiscoveredMat != null ? undiscoveredMat : Material.BARRIER, undiscoveredName, undiscoveredLore, null, null);


                Material discoveredMat = Material.matchMaterial(rSec.getString("item-discovered.material", "WRITABLE_BOOK"));
                String discoveredName = ChatUtil.colorize(rSec.getString("item-discovered.name", "&a{region_name}"));
                List<String> discoveredLore = rSec.getStringList("item-discovered.lore").stream().map(ChatUtil::colorize).collect(Collectors.toList());
                String discoveredClickAction = rSec.getString("item-discovered.click-action");
                MenuItemConfig discoveredItem = new MenuItemConfig(discoveredMat != null ? discoveredMat : Material.EMERALD_BLOCK, discoveredName, discoveredLore, discoveredClickAction, null);


                List<RewardAction> rewards = new ArrayList<>();
                ConfigurationSection rewardsSec = rSec.getConfigurationSection("rewards");
                if (rewardsSec != null) {
                    for (String rewardKey : rewardsSec.getKeys(false)) {
                        ConfigurationSection rewardActionSec = rewardsSec.getConfigurationSection(rewardKey);
                        if (rewardActionSec == null) continue;
                        String typeStr = rewardActionSec.getString("type");
                        String value = rewardActionSec.getString("value");
                        float volume = (float) rewardActionSec.getDouble("volume", 1.0);
                        float pitch = (float) rewardActionSec.getDouble("pitch", 1.0);
                        int fadeIn = rewardActionSec.getInt("fadein", 10);
                        int stay = rewardActionSec.getInt("stay", 70);
                        int fadeOut = rewardActionSec.getInt("fadeout", 20);

                        try {
                            RewardAction.ActionType type = RewardAction.ActionType.valueOf(typeStr.toUpperCase());
                            rewards.add(new RewardAction(type, value, volume, pitch, fadeIn, stay, fadeOut));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid reward type '" + typeStr + "' for region '" + key + "'. Skipping.");
                        }
                    }
                }
                regionConfigs.put(key, new RegionConfig(key, wgRegionId, worldName, displayName, undiscoveredItem, discoveredItem, rewards));
            }
        }
        plugin.getLogger().info("Loaded " + regionConfigs.size() + " region configurations.");
    }

    private void loadMenuConfig() {
        menuStaticItems.clear();
        menuTitle = ChatUtil.colorize(menuConfig.getString("title", "&8Codex Menu - Page {page}/{max_pages}"));
        menuSize = menuConfig.getInt("size", 54);
        if (menuSize % 9 != 0 || menuSize < 9 || menuSize > 54) {
            plugin.getLogger().warning("Invalid menu size in menu.yml. Defaulting to 54.");
            menuSize = 54;
        }

        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("static-items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                if (itemSec == null) continue;
                int slot = itemSec.getInt("slot", -1);
                if (slot < 0 || slot >= menuSize) {
                    plugin.getLogger().warning("Invalid slot for static item '" + key + "'. Skipping.");
                    continue;
                }
                Material mat = Material.matchMaterial(itemSec.getString("material", "STONE"));
                String name = ChatUtil.colorize(itemSec.getString("name", " "));
                List<String> lore = itemSec.getStringList("lore").stream().map(ChatUtil::colorize).collect(Collectors.toList());
                String action = itemSec.getString("action");
                menuStaticItems.put(slot, new MenuItemConfig(mat != null ? mat : Material.BARRIER, name, lore, action, null));
            }
        }

        ConfigurationSection fillerSec = menuConfig.getConfigurationSection("filler-item");
        if (fillerSec != null && fillerSec.getBoolean("enabled", false)) {
            Material mat = Material.matchMaterial(fillerSec.getString("material", "BLACK_STAINED_GLASS_PANE"));
            String name = ChatUtil.colorize(fillerSec.getString("name", " "));
            List<String> lore = fillerSec.getStringList("lore").stream().map(ChatUtil::colorize).collect(Collectors.toList());
            String action = fillerSec.getString("action");
            menuFillerItem = new MenuItemConfig(mat != null ? mat : Material.BLACK_STAINED_GLASS_PANE, name, lore, action, null);
        } else {
            menuFillerItem = null;
        }

        regionItemSlots = menuConfig.getIntegerList("region-item-slots");
        if (regionItemSlots.isEmpty()) {
            for (int i = 0; i < menuSize; i++) {
                if (!menuStaticItems.containsKey(i)) {
                    regionItemSlots.add(i);
                }
            }
            plugin.getLogger().info("No 'region-item-slots' defined in menu.yml. Dynamically filling available slots.");
        }
        plugin.getLogger().info("Loaded menu configuration. Title: " + menuTitle);
    }

    public void reloadConfigs() {
        loadConfigs();
    }

    // Getters
    public List<String> getBlacklistedWorlds() {
        return blacklistedWorlds;
    }

    public Map<String, RegionConfig> getRegionConfigs() {
        return regionConfigs;
    }

    public RegionConfig getRegionConfig(String id) {
        return regionConfigs.get(id);
    }

    public String getMenuTitle() {
        return menuTitle;
    }

    public int getMenuSize() {
        return menuSize;
    }

    public Map<Integer, MenuItemConfig> getMenuStaticItems() {
        return menuStaticItems;
    }

    public MenuItemConfig getMenuFillerItem() {
        return menuFillerItem;
    }

    public List<Integer> getRegionItemSlots() {
        return regionItemSlots;
    }

    public String getNoPermissionMessage() {
        return noPermissionMessage;
    }

    public String getReloadMessage() {
        return reloadMessage;
    }

    public String getPlayerOnlyMessage() {
        return playerOnlyMessage;
    }

    public String getWorldBlacklistedMessage() {
        return worldBlacklistedMessage;
    }

    // Getters for admin messages
    public String getAdminUsageMessage() {
        return adminUsageMessage;
    }

    public String getAdminPlayerNotFoundMessage() {
        return adminPlayerNotFoundMessage;
    }

    public String getAdminRegionNotFoundMessage() {
        return adminRegionNotFoundMessage;
    }

    public String getAdminAlreadyUnlockedMessage() {
        return adminAlreadyUnlockedMessage;
    }

    public String getAdminUnlockSuccessMessage() {
        return adminUnlockSuccessMessage;
    }

    public String getAdminNotYetUnlockedMessage() {
        return adminNotYetUnlockedMessage;
    }

    public String getAdminLockSuccessMessage() {
        return adminLockSuccessMessage;
    }

    public String getAdminListHeader() {
        return adminListHeader;
    }

    public String getAdminListFormatDiscovered() {
        return adminListFormatDiscovered;
    }

    public String getAdminListFormatUndiscovered() {
        return adminListFormatUndiscovered;
    }
}
