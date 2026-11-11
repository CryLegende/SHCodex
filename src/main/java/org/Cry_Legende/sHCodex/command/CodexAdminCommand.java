package org.Cry_Legende.sHCodex.command;

import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.config.RegionConfig;
import org.Cry_Legende.sHCodex.network.NetworkCodex;
import org.Cry_Legende.sHCodex.network.NetworkLink;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
        NetworkCodex network = plugin.getNetwork();
        OfflinePlayer target;
        if (network != null) {
            // Mode réseau : les données d'un joueur ne sont en mémoire que sur son serveur
            Player online = Bukkit.getPlayerExact(playerName);
            if (online == null) {
                handleNotHere(sender, args, network);
                return true;
            }
            target = online;
        } else {
            target = resolveTarget(playerName);

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(plugin.getConfigManager().getAdminPlayerNotFoundMessage().replace("{player}", playerName));
                return true;
            }
        }

        // Un joueur hors ligne résolu par son UUID n'a pas toujours son pseudo en cache : on garde celui qui a été tapé
        String targetName = target.getName() != null ? target.getName() : playerName;

        plugin.getPlayerDataManager().loadPlayerData(target.getUniqueId()); // S'assure que les données (même offline) sont chargées
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        if (playerData == null) { // mode réseau : joueur arrivé à l'instant, ses régions sont encore en cours de lecture
            sender.sendMessage(plugin.getConfigManager().adminMessage("loading",
                "&7&l« &e&lSH &8&l» &7Le codex de &e{player} &7est en cours de chargement, réessaie dans un instant.")
                .replace("{player}", targetName));
            return true;
        }

        String action = args[2].toLowerCase();

        switch (action) {
            case "list":
                handleList(sender, targetName, playerData);
                break;

            case "unlock":
                handleUnlock(sender, target, targetName, playerData, args);
                break;

            case "lock":
                handleLock(sender, target, targetName, playerData, args);
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

    /**
     * Résout le joueur visé sans requête Mojang. Bukkit.getOfflinePlayer(String) interroge Mojang sur le fil principal quand
     * le pseudo n'est pas en cache (gels de plusieurs secondes) et rend l'UUID premium des comptes originaux, alors que le
     * réseau range tous les joueurs sous leur UUID hors ligne (md5 de "OfflinePlayer:" + pseudo).
     */
    private OfflinePlayer resolveTarget(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online;
        }
        // Le cache de pseudos rétablit la casse exacte, qui change l'UUID hors ligne (jamais de requête réseau)
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(playerName);
        String exactName = cached != null && cached.getName() != null ? cached.getName() : playerName;
        return Bukkit.getOfflinePlayer(UUID.nameUUIDFromBytes(("OfflinePlayer:" + exactName).getBytes(StandardCharsets.UTF_8)));
    }

    private void handleList(CommandSender sender, String playerName, PlayerData playerData) {
        Map<String, RegionConfig> allRegions = plugin.getConfigManager().getRegionConfigs();
        int discoveredCount = playerData.getDiscoveredCount();
        int totalCount = allRegions.size();

        String header = plugin.getConfigManager().getAdminListHeader()
            .replace("{player}", playerName)
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

    private void handleUnlock(CommandSender sender, OfflinePlayer target, String targetName, PlayerData playerData, String[] args) {
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
                .replace("{player}", targetName)
                .replace("{region_name}", regionConfig.getDisplayName()));
            return;
        }

        playerData.discoverRegion(regionId);
        plugin.getStatsManager().incrementDiscoveryCount(regionId);
        plugin.getPlayerDataManager().regionUnlocked(target.getUniqueId(), regionId); // Sauvegarde immédiate
        plugin.getStatsManager().saveStats(); // Sauvegarde immédiate

        sender.sendMessage(plugin.getConfigManager().getAdminUnlockSuccessMessage()
            .replace("{player}", targetName)
            .replace("{region_name}", regionConfig.getDisplayName()));
    }

    private void handleLock(CommandSender sender, OfflinePlayer target, String targetName, PlayerData playerData, String[] args) {
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
                .replace("{player}", targetName)
                .replace("{region_name}", regionConfig.getDisplayName()));
            return;
        }

        playerData.undiscoverRegion(regionId);
        plugin.getStatsManager().decrementDiscoveryCount(regionId);
        plugin.getPlayerDataManager().regionLocked(target.getUniqueId(), regionId); // Sauvegarde immédiate
        plugin.getStatsManager().saveStats(); // Sauvegarde immédiate

        sender.sendMessage(plugin.getConfigManager().getAdminLockSuccessMessage()
            .replace("{player}", targetName)
            .replace("{region_name}", regionConfig.getDisplayName()));
    }

    // ------------------------------------------------------------------ mode réseau

    /**
     * Mode réseau : le joueur visé n'est pas sur ce serveur. S'il est connecté ailleurs, {@code unlock} et {@code lock} lui
     * sont transmis et exécutés là où se trouve sa mémoire ; {@code list} lit la base (chaque changement y est écrit
     * aussitôt). Sinon, il est hors ligne partout : les trois actions passent directement par la base.
     */
    private void handleNotHere(CommandSender sender, String[] args, NetworkCodex network) {
        String playerName = args[1];
        String action = args[2].toLowerCase();
        if (!action.equals("list") && !action.equals("unlock") && !action.equals("lock")) {
            sender.sendMessage(plugin.getConfigManager().getAdminUsageMessage());
            return;
        }
        String regionId = null;
        RegionConfig regionConfig = null;
        if (!action.equals("list")) {
            if (args.length < 4) {
                sender.sendMessage(plugin.getConfigManager().getAdminUsageMessage());
                return;
            }
            regionId = args[3];
            regionConfig = plugin.getConfigManager().getRegionConfig(regionId);
            if (regionConfig == null) {
                sender.sendMessage(plugin.getConfigManager().getAdminRegionNotFoundMessage().replace("{region_id}", regionId));
                return;
            }
        }
        String id = regionId;
        RegionConfig region = regionConfig;

        NetworkLink link = network.link();
        Optional<NetworkLink.Remote> remote = link.find(playerName).filter(r -> !r.server().equalsIgnoreCase(link.serverName()));
        if (remote.isPresent()) {
            NetworkLink.Remote there = remote.get();
            if (action.equals("list")) {
                showList(sender, network, there.uuid(), there.name());
                return;
            }
            link.runFor(there.name(), "codex admin " + there.name() + " " + action + " " + id);
            sender.sendMessage(plugin.getConfigManager().adminMessage("forwarded",
                "&7&l« &e&lSH &8&l» &e{player} &7est sur le serveur &e{server}&7 : la commande lui a été &etransmise&7.")
                .replace("{player}", there.name()).replace("{server}", there.server()));
            return;
        }

        network.resolveOffline(playerName, known -> {
            if (known == null) {
                sender.sendMessage(plugin.getConfigManager().getAdminPlayerNotFoundMessage().replace("{player}", playerName));
                return;
            }
            switch (action) {
                case "list":
                    showList(sender, network, known.uuid(), known.name());
                    break;
                case "unlock":
                    network.adminUnlock(known.uuid(), id, inserted -> {
                        if (inserted == null) {
                            sender.sendMessage(databaseError());
                        } else if (inserted) {
                            sender.sendMessage(plugin.getConfigManager().getAdminUnlockSuccessMessage()
                                .replace("{player}", known.name())
                                .replace("{region_name}", region.getDisplayName()));
                        } else {
                            sender.sendMessage(plugin.getConfigManager().getAdminAlreadyUnlockedMessage()
                                .replace("{player}", known.name())
                                .replace("{region_name}", region.getDisplayName()));
                        }
                    });
                    break;
                default:
                    network.adminLock(known.uuid(), id, deleted -> {
                        if (deleted == null) {
                            sender.sendMessage(databaseError());
                        } else if (deleted) {
                            sender.sendMessage(plugin.getConfigManager().getAdminLockSuccessMessage()
                                .replace("{player}", known.name())
                                .replace("{region_name}", region.getDisplayName()));
                        } else {
                            sender.sendMessage(plugin.getConfigManager().getAdminNotYetUnlockedMessage()
                                .replace("{player}", known.name())
                                .replace("{region_name}", region.getDisplayName()));
                        }
                    });
                    break;
            }
        });
    }

    private void showList(CommandSender sender, NetworkCodex network, UUID uuid, String name) {
        network.read(uuid, regions -> {
            if (regions == null) {
                sender.sendMessage(databaseError());
            } else {
                handleList(sender, name, new PlayerData(regions));
            }
        });
    }

    private String databaseError() {
        return plugin.getConfigManager().adminMessage("database-error",
            "&7&l« &c&lSH &8&l» &7La base du réseau ne répond pas.");
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
