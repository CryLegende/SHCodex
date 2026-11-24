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
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerRegionListener implements Listener {

    private final SHCodex plugin;

    public PlayerRegionListener(SHCodex plugin) {
        this.plugin = plugin;
    }

    /**
     * Téléportation qui arrive sur un autre joueur (/tpa, /tpahere : la position d'arrivée est celle d'un joueur, à
     * « teleportation.rayon » blocs près) dans une zone du codex que le voyageur n'a pas encore découverte : refusée,
     * il doit y aller à pied. Les warps, le spawn, l'île et la téléportation du /codex ne visent pas un joueur, et leurs
     * causes (COMMAND pour /warp) restent hors de « teleportation.causes ».
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getRawConfig();
        List<String> causes = config.isList("teleportation.causes")
                ? config.getStringList("teleportation.causes") : List.of("PLUGIN");
        if (!config.getBoolean("teleportation.bloquer-vers-joueur", true) || !causes.contains(event.getCause().name())) {
            return;
        }
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || player.hasPermission("shcodex.teleportation.libre")) {
            return;
        }
        double radius = config.getDouble("teleportation.rayon", 0.5);
        double r2 = radius * radius;
        List<Player> hosts = new ArrayList<>();
        for (Player other : to.getWorld().getPlayers()) {
            if (!other.equals(player) && other.getLocation().distanceSquared(to) <= r2) {
                hosts.add(other);
            }
        }
        if (hosts.isEmpty()) {
            return;
        }
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return;
        }
        for (RegionConfig zone : zonesAt(to)) {
            if (!playerData.hasDiscovered(zone.getId()) && canDiscover(player, zone)) {
                event.setCancelled(true);
                ConfigManager cfg = plugin.getConfigManager();
                player.sendMessage(cfg.message("teleportation-voyageur",
                        "&7&l« &c&lSH &8&l» &7Ce lieu du &c/codex&7 n'est pas encore découvert, il faut le rejoindre &cà pied&7."));
                String host = cfg.message("teleportation-hote",
                        "&7&l« &c&lSH &8&l» &c{player} &7n'a pas encore découvert ce lieu du &c/codex&7, emmenez-le &cà pied&7.")
                        .replace("{player}", player.getName());
                hosts.forEach(h -> h.sendMessage(host));
                return;
            }
        }
    }

    /** Zones du codex (regions.yml) qui contiennent ce point. */
    private List<RegionConfig> zonesAt(Location loc) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        ApplicableRegionSet applicable = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        String worldName = loc.getWorld().getName();
        List<RegionConfig> out = new ArrayList<>();
        for (ProtectedRegion wgRegion : applicable) {
            for (RegionConfig regionConfig : plugin.getConfigManager().getRegionsByWorldGuardId(wgRegion.getId())) {
                if (worldName.equalsIgnoreCase(regionConfig.getWorldName())) {
                    out.add(regionConfig);
                }
            }
        }
        return out;
    }

    private static boolean canDiscover(Player player, RegionConfig zone) {
        return player.hasPermission("shcodex.discover." + zone.getId()) || player.hasPermission("shcodex.discover.*");
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
                            // (en mode réseau : écrit en base, ligne par ligne, et les autres serveurs sont prévenus)
                            plugin.getPlayerDataManager().regionDiscovered(player.getUniqueId(), codexRegionId);

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
