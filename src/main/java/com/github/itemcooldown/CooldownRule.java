package com.github.itemcooldown;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * Represents a cooldown rule loaded from config.yml.
 */
public final class CooldownRule {
    private final Material material;
    private final NamespacedKey itemModel;
    private final int ticks;
    private final boolean useNative;
    private final String key;

    public CooldownRule(Material material, NamespacedKey itemModel, int ticks, boolean useNative) {
        this.material = material;
        this.itemModel = itemModel;
        this.ticks = ticks;
        this.useNative = useNative;

        // Unique key used to track the custom cooldown in player maps
        if (itemModel != null) {
            this.key = material.name() + ":" + itemModel.toString().toLowerCase();
        } else {
            this.key = material.name();
        }
    }

    public Material getMaterial() {
        return material;
    }

    public NamespacedKey getItemModel() {
        return itemModel;
    }

    public int getTicks() {
        return ticks;
    }

    public boolean isUseNative() {
        return useNative;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "CooldownRule{" +
                "material=" + material +
                ", itemModel=" + itemModel +
                ", ticks=" + ticks +
                ", useNative=" + useNative +
                ", key='" + key + '\'' +
                '}';
    }
}
