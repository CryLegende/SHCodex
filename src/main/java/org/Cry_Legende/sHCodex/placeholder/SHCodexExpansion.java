package org.Cry_Legende.sHCodex.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.playerdata.PlayerData;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class SHCodexExpansion extends PlaceholderExpansion {

    private final SHCodex plugin;

    public SHCodexExpansion(SHCodex plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shcodex";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Permet de ne pas devoir recharger PAPI pour que les placeholders soient actifs
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            return null;
        }
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(offlinePlayer.getUniqueId());
        if (playerData == null) {
            return "PlayerData N/A";
        }

        if (params.equalsIgnoreCase("discovered_count")) {
            return String.valueOf(playerData.getDiscoveredRegions().size());
        }

        if (params.equalsIgnoreCase("total_count")) {
            return String.valueOf(plugin.getConfigManager().getRegionConfigs().size());
        }

        if (params.startsWith("is_discovered_")) {
            String regionId = params.substring("is_discovered_".length());
            if (plugin.getConfigManager().getRegionConfig(regionId) == null) {
                return "Invalid Region"; // La région n'existe pas dans la config
            }
            return playerData.hasDiscovered(regionId) ? "true" : "false";
        }

        if (params.startsWith("name_")) { // %shcodex_name_<region_id>%
            String regionId = params.substring("name_".length());
            if (plugin.getConfigManager().getRegionConfig(regionId) != null) {
                return plugin.getConfigManager().getRegionConfig(regionId).getDisplayName();
            }
            return "Unknown Region";
        }

        return null; // Placeholder non reconnu
    }
}