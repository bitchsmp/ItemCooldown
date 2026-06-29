package com.github.itemcooldown;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class CooldownListener implements Listener {

    private final ItemCooldownPlugin plugin;

    public CooldownListener(ItemCooldownPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Prevent players from initiating usage (e.g. starting to eat/drink)
     * if the item is currently on a custom cooldown.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        CooldownRule rule = plugin.findMatchingRule(item);
        if (rule == null || rule.isUseNative()) {
            return;
        }

        Player player = event.getPlayer();

        // Creative mode players bypass cooldowns
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Check if player has permission bypass
        if (hasBypassPermission(player, rule)) {
            return;
        }

        long remaining = plugin.getRemainingCooldownMillis(player.getUniqueId(), rule.getKey());
        if (remaining > 0) {
            // Allow opening interactable blocks (chests, doors, etc.) unless sneaking
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                if (event.getClickedBlock().getType().isInteractable() && !player.isSneaking()) {
                    return;
                }
            }

            // Cancel the use of the item and send the action bar warning
            event.setCancelled(true);
            plugin.sendCooldownMessage(player, remaining);
        }
    }

    /**
     * Handles the consumption of the item (when a player finishes eating/drinking).
     * Applies native or custom cooldowns if matched.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        CooldownRule rule = plugin.findMatchingRule(item);
        if (rule == null) {
            return;
        }

        Player player = event.getPlayer();

        // Creative mode players bypass cooldowns
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Check if player has permission bypass
        if (hasBypassPermission(player, rule)) {
            return;
        }

        if (rule.isUseNative()) {
            // Apply Minecraft's native material cooldown
            player.setCooldown(rule.getMaterial(), rule.getTicks());
        } else {
            // Custom cooldown check and set
            long remaining = plugin.getRemainingCooldownMillis(player.getUniqueId(), rule.getKey());
            if (remaining > 0) {
                event.setCancelled(true);
                plugin.sendCooldownMessage(player, remaining);
            } else {
                plugin.setCustomCooldown(player.getUniqueId(), rule.getKey(), rule.getTicks());
            }
        }
    }

    /**
     * Cleans up custom cooldown maps when a player leaves to prevent memory leaks.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.clearCooldowns(event.getPlayer().getUniqueId());
    }

    /**
     * Helper to verify if the player bypasses the cooldown.
     */
    private boolean hasBypassPermission(Player player, CooldownRule rule) {
        // 1. General bypass
        if (player.hasPermission("itemcooldown.bypass")) {
            return true;
        }

        // 2. Material bypass (e.g. itemcooldown.bypass.enchanted_golden_apple)
        String matPermission = "itemcooldown.bypass." + rule.getMaterial().name().toLowerCase();
        if (player.hasPermission(matPermission)) {
            return true;
        }

        // 3. Custom item model key bypass (e.g. itemcooldown.bypass.half_golden_apple)
        if (rule.getItemModel() != null) {
            String modelPermission = "itemcooldown.bypass." + rule.getItemModel().getKey().toLowerCase();
            if (player.hasPermission(modelPermission)) {
                return true;
            }
        }

        return false;
    }
}
