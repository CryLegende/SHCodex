package org.Cry_Legende.sHCodex.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import me.clip.placeholderapi.PlaceholderAPI;
import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.config.ConfigManager;
import org.Cry_Legende.sHCodex.config.RegionConfig;
import org.Cry_Legende.sHCodex.config.RewardAction;
import org.Cry_Legende.sHCodex.playerdata.PlayerData;
import org.Cry_Legende.sHCodex.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;

public class PlayerRegionListener implements Listener {

    private final SHCodex plugin;

    public PlayerRegionListener(SHCodex plugin) {
        this.plugin = plugin;
    }

    // MONITOR + ignoreCancelled : on lit la position d'arrivée, valable seulement si le déplacement a bien lieu
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) {
            return; // Le joueur n'a pas changé de bloc
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return; // Données pas encore chargées, on ignore
        }
        // Tout est déjà découvert : inutile d'interroger WorldGuard à chaque bloc
        Map<String, RegionConfig> regionConfigs = plugin.getConfigManager().getRegionConfigs();
        if (playerData.hasDiscoveredAll(regionConfigs.keySet())) {
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        // Position d'arrivée : pendant l'événement, player.getLocation() est encore le bloc de départ
        ApplicableRegionSet applicableRegions = query.getApplicableRegions(BukkitAdapter.adapt(to));
        String worldName = to.getWorld() != null ? to.getWorld().getName() : player.getWorld().getName();

        for (ProtectedRegion wgRegion : applicableRegions) {
            for (RegionConfig regionConfig : plugin.getConfigManager().getRegionsByWorldGuardId(wgRegion.getId())) {
                String codexRegionId = regionConfig.getId();

                if (worldName.equalsIgnoreCase(regionConfig.getWorldName())) {

                    if (!playerData.hasDiscovered(codexRegionId)) {
                        if (player.hasPermission("shcodex.discover." + codexRegionId) || player.hasPermission("shcodex.discover.*")) {
                            // Le joueur découvre la région
                            playerData.discoverRegion(codexRegionId);

                            // --- NOUVEAU ---
                            // Incrémenter le compteur global et sauvegarder
                            plugin.getStatsManager().incrementDiscoveryCount(codexRegionId);
                            // Il est préférable de sauvegarder périodiquement plutôt qu'à chaque fois,
                            // mais pour garantir la fraîcheur des données, nous sauvegardons ici.
                            plugin.getStatsManager().saveStats();
                            // Le fichier du joueur suit le compteur : sans cela, un arrêt brutal ferait perdre la
                            // découverte (récompensée et comptée une seconde fois à la reconnexion)
                            plugin.getPlayerDataManager().savePlayerDataAsync(player.getUniqueId());

                            plugin.getLogger().info(player.getName() + " discovered region: " + codexRegionId);
                            executeRewards(player, regionConfig);
                        }
                    }
                }
            }
        }
    }

    private void executeRewards(Player player, RegionConfig regionConfig) {
        for (RewardAction action : regionConfig.getRewards()) {
            String value = action.getValue();

            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                value = PlaceholderAPI.setPlaceholders(player, value);
            }

            value = value.replace("{player}", player.getName())
                .replace("{region_name}", regionConfig.getDisplayName())
                .replace("{region_id}", regionConfig.getId());

            switch (action.getType()) {
                case MESSAGE:
                    player.sendMessage(ChatUtil.colorize(value));
                    break;
                case COMMAND:
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                    break;
                case SOUND:
                    Sound sound = ConfigManager.sound(value);
                    if (sound != null) {
                        player.playSound(player.getLocation(), sound, action.getVolume(), action.getPitch());
                    } else {
                        plugin.getLogger().warning("Invalid sound name in rewards for region " + regionConfig.getId() + ": " + value);
                    }
                    break;
                case TITLE:
                case SUBTITLE:
                    String title = "";
                    String subtitle = "";
                    if (value.contains(";")) {
                        String[] parts = value.split(";", 2);
                        title = ChatUtil.colorize(parts[0]);
                        if (parts.length > 1) {
                            subtitle = ChatUtil.colorize(parts[1]);
                        }
                    } else {
                        if (action.getType() == RewardAction.ActionType.TITLE) title = ChatUtil.colorize(value);
                        else subtitle = ChatUtil.colorize(value);
                    }
                    player.sendTitle(title, subtitle, action.getFadeIn(), action.getStay(), action.getFadeOut());
                    break;
            }
        }
    }
}
