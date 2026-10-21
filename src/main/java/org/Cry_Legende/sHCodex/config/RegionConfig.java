package org.Cry_Legende.sHCodex.config;

import java.util.List;

public class RegionConfig {
    private final String id;
    private final String worldGuardRegionId;
    private final String worldName;
    private final String displayName;
    private final MenuItemConfig undiscoveredItem;
    private final MenuItemConfig discoveredItem;
    private final List<RewardAction> rewards;

    public RegionConfig(String id, String worldGuardRegionId, String worldName, String displayName,
                        MenuItemConfig undiscoveredItem, MenuItemConfig discoveredItem, List<RewardAction> rewards) {
        this.id = id;
        this.worldGuardRegionId = worldGuardRegionId;
        this.worldName = worldName;
        this.displayName = displayName;
        this.undiscoveredItem = undiscoveredItem;
        this.discoveredItem = discoveredItem;
        this.rewards = rewards;
    }

    public String getId() {
        return id;
    }

    public String getWorldGuardRegionId() {
        return worldGuardRegionId;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MenuItemConfig getUndiscoveredItem() {
        return undiscoveredItem;
    }

    public MenuItemConfig getDiscoveredItem() {
        return discoveredItem;
    }

    public List<RewardAction> getRewards() {
        return rewards;
    }
}
