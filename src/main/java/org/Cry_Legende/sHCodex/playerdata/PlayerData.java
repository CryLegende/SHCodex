package org.Cry_Legende.sHCodex.playerdata;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerData {
    // Key: regionId (String), Value: timestamp of discovery (Long)
    // Concurrente : modifiée sur le thread principal, copiée par la sauvegarde asynchrone et lue par PlaceholderAPI
    private final Map<String, Long> discoveredRegions;

    public PlayerData() {
        this.discoveredRegions = new ConcurrentHashMap<>();
    }

    public PlayerData(Map<String, Long> discoveredRegions) {
        this.discoveredRegions = new ConcurrentHashMap<>(discoveredRegions); // Copie pour éviter modif externe
    }

    public boolean hasDiscovered(String regionId) {
        return discoveredRegions.containsKey(regionId);
    }

    public void discoverRegion(String regionId) {
        discoveredRegions.putIfAbsent(regionId, System.currentTimeMillis());
    }

    public void undiscoverRegion(String regionId) { // Pour des besoins d'admin/debug
        discoveredRegions.remove(regionId);
    }

    public Long getDiscoveryTimestamp(String regionId) {
        return discoveredRegions.get(regionId);
    }

    public Set<String> getDiscoveredRegions() {
        return discoveredRegions.keySet(); // Retourne une vue des clés
    }

    public Map<String, Long> getDiscoveredRegionsWithTimestamps() {
        return new HashMap<>(discoveredRegions); // Retourne une copie de la map
    }

    public int getDiscoveredCount() {
        return discoveredRegions.size();
    }

    /** Toutes ces régions sont-elles déjà découvertes ? */
    public boolean hasDiscoveredAll(Collection<String> regionIds) {
        return discoveredRegions.keySet().containsAll(regionIds);
    }
}