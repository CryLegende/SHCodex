package org.Cry_Legende.sHCodex.command;

import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.config.RegionConfig;
import org.Cry_Legende.sHCodex.playerdata.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CodexAdminCommand {

    private final SHCodex plugin;

    public CodexAdminCommand(SHCodex plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shcodex.admin")) {
            sender.sendMessage(plugin.getConfigManager().getNoPermissionMessage());
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getAdminUsageMessage());
            return true;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.getConfigManager().getAdminPlayerNotFoundMessage().replace("{player}", playerName));
            return true;
        }

        plugin.getPlayerDataManager().loadPlayerData(target.getUniqueId()); // S'assure que les données (même offline) sont chargées
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());

        String action = args[2].toLowerCase();

        switch (action) {
            case "list":
                handleList(sender, target, playerData);
                break;

            case "unlock":
                handleUnlock(sender, target, playerData, args);
                break;

            case "lock":
                handleLock(sender, target, playerData, args);
                break;

            default:
                sender.sendMessage(plugin.getConfigManager().getAdminUsageMessage());
                break;
        }

        if (!target.isOnline()) { // Décharge les données si le joueur était offline
            plugin.getPlayerDataManager().unloadPlayerData(target.getUniqueId());
        }

        return true;
    }

    private void handleList(CommandSender sender, OfflinePlayer target, PlayerData playerData) {
        Map<String, RegionConfig> allRegions = plugin.getConfigManager().getRegionConfigs();
        int discoveredCount = playerData.getDiscoveredCount();
        int totalCount = allRegions.size();

        String header = plugin.getConfigManager().getAdminListHeader()
            .replace("{player}", target.getName())
            .replace("{discovered}", String.valueOf(discoveredCount))
            .replace("{total}", String.valueOf(totalCount));
        sender.sendMessage(header);

        allRegions.values().stream()
            .sorted((r1, r2) -> r1.getId().compareToIgnoreCase(r2.getId()))
            .forEach(region -> {
                String message;
                if (playerData.hasDiscovered(region.getId())) {
                    message = plugin.getConfigManager().getAdminListFormatDiscovered();
                } else {
                    message = plugin.getConfigManager().getAdminListFormatUndiscovered();
                }
                sender.sendMessage(message.replace("{region_name}", region.getDisplayName()));
            });
    }

    private void handleUnlock(CommandSender sender, OfflinePlayer target, PlayerData playerData, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getConfigManager().getAdminUsageMessage());
            return;
        }
        String regionId = args[3];
        RegionConfig regionConfig = plugin.getConfigManager().getRegionConfig(regionId);

        if (regionConfig == null) {
            sender.sendMessage(plugin.getConfigManager().getAdminRegionNotFoundMessage().replace("{region_id}", regionId));
            return;
        }

        if (playerData.hasDiscovered(regionId)) {
            sender.sendMessage(plugin.getConfigManager().getAdminAlreadyUnlockedMessage()
                .replace("{player}", target.getName())
                .replace("{region_name}", regionConfig.getDisplayName()));
            return;
        }

        playerData.discoverRegion(regionId);
        plugin.getStatsManager().incrementDiscoveryCount(regionId);
        plugin.getPlayerDataManager().savePlayerData(target.getUniqueId()); // Sauvegarde immédiate
        plugin.getStatsManager().saveStats(); // Sauvegarde immédiate

        sender.sendMessage(plugin.getConfigManager().getAdminUnlockSuccessMessage()
            .replace("{player}", target.getName())
            .replace("{region_name}", regionConfig.getDisplayName()));
    }

    private void handleLock(CommandSender sender, OfflinePlayer target, PlayerData playerData, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getConfigManager().getAdminUsageMessage());
            return;
        }
        String regionId = args[3];
        RegionConfig regionConfig = plugin.getConfigManager().getRegionConfig(regionId);

        if (regionConfig == null) {
            sender.sendMessage(plugin.getConfigManager().getAdminRegionNotFoundMessage().replace("{region_id}", regionId));
            return;
        }

        if (!playerData.hasDiscovered(regionId)) {
            sender.sendMessage(plugin.getConfigManager().getAdminNotYetUnlockedMessage()
                .replace("{player}", target.getName())
                .replace("{region_name}", regionConfig.getDisplayName()));
            return;
        }

        playerData.undiscoverRegion(regionId);
        plugin.getStatsManager().decrementDiscoveryCount(regionId);
        plugin.getPlayerDataManager().savePlayerData(target.getUniqueId()); // Sauvegarde immédiate
        plugin.getStatsManager().saveStats(); // Sauvegarde immédiate

        sender.sendMessage(plugin.getConfigManager().getAdminLockSuccessMessage()
            .replace("{player}", target.getName())
            .replace("{region_name}", regionConfig.getDisplayName()));
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        // args[0] is "admin", so we start at args[1] which is index 1 of the original array
        int subIndex = args.length - 2;

        if (subIndex == 0) { // Complétion du nom du joueur: /codex admin <player>
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }

        if (subIndex == 1) { // Complétion de l'action: /codex admin player <action>
            List<String> actions = Arrays.asList("list", "unlock", "lock");
            return StringUtil.copyPartialMatches(args[2], actions, new ArrayList<>());
        }

        if (subIndex == 2 && (args[2].equalsIgnoreCase("unlock") || args[2].equalsIgnoreCase("lock"))) { // Complétion de la région
            List<String> regionIds = new ArrayList<>(plugin.getConfigManager().getRegionConfigs().keySet());
            return StringUtil.copyPartialMatches(args[3], regionIds, new ArrayList<>());
        }

        return completions;
    }
}
