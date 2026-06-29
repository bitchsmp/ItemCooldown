package com.github.itemcooldown;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public final class ItemCooldownPlugin extends JavaPlugin {

    private final List<CooldownRule> rules = new ArrayList<>();
    private final Map<UUID, Map<String, Long>> activeCooldowns = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Save default config if not already present
        saveDefaultConfig();
        loadCooldownConfig();

        // Register listener for events
        getServer().getPluginManager().registerEvents(new CooldownListener(this), this);

        // Register commands
        ItemCooldownCommand commandExecutor = new ItemCooldownCommand(this);
        getCommand("itemcooldown").setExecutor(commandExecutor);
        getCommand("itemcooldown").setTabCompleter(commandExecutor);

        getLogger().info("ItemCooldown v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        activeCooldowns.clear();
        rules.clear();
        getLogger().info("ItemCooldown has been disabled!");
    }

    /**
     * Loads/reloads the configuration rules.
     */
    @SuppressWarnings("deprecation")
    public void loadCooldownConfig() {
        reloadConfig();
        rules.clear();

        List<?> rawList = getConfig().getList("cooldowns");
        if (rawList == null) {
            getLogger().warning("No 'cooldowns' list found in config.yml!");
            return;
        }

        int loadedCount = 0;
        for (Object obj : rawList) {
            if (!(obj instanceof Map)) {
                getLogger().warning("Invalid rule entry format (must be a key-value mapping): " + obj);
                continue;
            }

            Map<?, ?> map = (Map<?, ?>) obj;

            // 1. Get Material
            String matStr = getStringValue(map.get("material"));
            if (matStr == null) {
                getLogger().warning("Skipping rule: missing required 'material' field in entry: " + obj);
                continue;
            }

            Material material = Material.getMaterial(matStr.toUpperCase());
            if (material == null) {
                getLogger().warning("Skipping rule: invalid material type '" + matStr + "'");
                continue;
            }

            // 2. Get Item Model
            String modelStr = getStringValue(map.get("item_model"));
            NamespacedKey itemModel = null;
            if (modelStr != null && !modelStr.trim().isEmpty()) {
                itemModel = NamespacedKey.fromString(modelStr.toLowerCase());
                if (itemModel == null) {
                    getLogger().warning("Skipping rule for " + matStr + ": invalid 'item_model' format '" + modelStr + "'. Expected format: namespace:key");
                    continue;
                }
            }

            // 3. Get Ticks
            Object ticksObj = map.get("ticks");
            int ticks;
            if (ticksObj instanceof Number) {
                ticks = ((Number) ticksObj).intValue();
            } else {
                getLogger().warning("Skipping rule for " + matStr + ": missing or invalid 'ticks' number");
                continue;
            }

            if (ticks < 0) {
                getLogger().warning("Skipping rule for " + matStr + ": 'ticks' cannot be negative (" + ticks + ")");
                continue;
            }

            // 4. Get Use Native
            boolean useNative;
            if (map.containsKey("use_native")) {
                useNative = Boolean.parseBoolean(getStringValue(map.get("use_native")));
            } else {
                // Default: true if item_model is null, false if item_model is specified
                useNative = (itemModel == null);
            }

            // Validation: Cannot use native cooldown on custom models since Minecraft applies cooldown to entire Material
            if (useNative && itemModel != null) {
                getLogger().warning("Rule for " + material.name() + " with custom model '" + itemModel + "' cannot use native cooldown. Automatically setting use_native: false.");
                useNative = false;
            }

            rules.add(new CooldownRule(material, itemModel, ticks, useNative));
            loadedCount++;
        }

        getLogger().info("Successfully loaded " + loadedCount + " cooldown rule(s).");
    }

    /**
     * Finds the matching CooldownRule for a given ItemStack.
     * Evaluates specific item_model rules first, then generic fallback material rules.
     */
    public CooldownRule findMatchingRule(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;

        // 1. Specific item_model rules first
        for (CooldownRule rule : rules) {
            if (rule.getItemModel() == null) {
                continue;
            }
            if (rule.getMaterial() == item.getType() && meta != null && meta.hasItemModel()) {
                if (rule.getItemModel().equals(meta.getItemModel())) {
                    return rule;
                }
            }
        }

        // 2. Generic material-only rules second
        for (CooldownRule rule : rules) {
            if (rule.getItemModel() != null) {
                continue;
            }
            if (rule.getMaterial() == item.getType()) {
                return rule;
            }
        }

        return null;
    }

    /**
     * Gets the remaining cooldown time in milliseconds for a player for a specific rule key.
     * Cleans up expired cooldowns automatically.
     */
    public long getRemainingCooldownMillis(UUID uuid, String key) {
        Map<String, Long> playerCooldowns = activeCooldowns.get(uuid);
        if (playerCooldowns == null) {
            return 0;
        }
        Long expireTime = playerCooldowns.get(key);
        if (expireTime == null) {
            return 0;
        }
        long remaining = expireTime - System.currentTimeMillis();
        if (remaining <= 0) {
            playerCooldowns.remove(key);
            if (playerCooldowns.isEmpty()) {
                activeCooldowns.remove(uuid);
            }
            return 0;
        }
        return remaining;
    }

    /**
     * Sets a custom cooldown for a player.
     */
    public void setCustomCooldown(UUID uuid, String key, int ticks) {
        if (ticks <= 0) {
            return;
        }
        long expireTime = System.currentTimeMillis() + (ticks * 50L);
        activeCooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(key, expireTime);
    }

    /**
     * Clears all custom cooldown storage for a disconnecting player.
     */
    public void clearCooldowns(UUID uuid) {
        activeCooldowns.remove(uuid);
    }

    /**
     * Sends the formatted remaining time message to the player's action bar.
     */
    public void sendCooldownMessage(Player player, long remainingMillis) {
        String format = getConfig().getString("action-bar-message", "&cThis item is on cooldown! &e%time%s &cleft.");
        double secondsLeft = remainingMillis / 1000.0;
        String timeStr = String.format("%.1f", secondsLeft);
        String finalMessage = format.replace("%time%", timeStr);

        // Send action bar via Kyori Adventure API (built-in in Paper)
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(finalMessage));
    }

    public List<CooldownRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    private String getStringValue(Object obj) {
        return obj == null ? null : obj.toString();
    }
}
