package com.github.itemcooldown;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ItemCooldownCommand implements CommandExecutor, TabCompleter {

    private final ItemCooldownPlugin plugin;

    public ItemCooldownCommand(ItemCooldownPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("itemcooldown.admin")) {
            sender.sendRichMessage("<red>You do not have permission to execute this command.</red>");
            return true;
        }

        if (args.length > 0) {
            String subcommand = args[0].toLowerCase();
            if (subcommand.equals("reload")) {
                plugin.loadCooldownConfig();
                sender.sendRichMessage("<green>[ItemCooldown] Configuration reloaded successfully!</green>");
                return true;
            } else if (subcommand.equals("list")) {
                sender.sendRichMessage("<gold>=== ItemCooldown Active Rules ===</gold>");
                List<CooldownRule> rules = plugin.getRules();
                if (rules.isEmpty()) {
                    sender.sendRichMessage("<gray>No cooldown rules configured.</gray>");
                    return true;
                }
                for (CooldownRule rule : rules) {
                    String modelInfo = rule.getItemModel() != null ? " [Model: <white>" + rule.getItemModel() + "</white>]" : "";
                    String typeInfo = rule.isUseNative() ? " <gray>(Native)</gray>" : " <gray>(Custom)</gray>";
                    sender.sendRichMessage("<yellow>- " + rule.getMaterial().name() + "</yellow>" + modelInfo + ": <white>" +
                            rule.getTicks() + "</white> ticks (" + String.format("%.1f", rule.getTicks() / 20.0) + "s)" + typeInfo);
                }
                return true;
            }
        }

        sender.sendRichMessage("<gold>=== ItemCooldown Commands ===</gold>");
        sender.sendRichMessage("<yellow>/itemcooldown reload</yellow> <gray>- Reloads the configuration file.</gray>");
        sender.sendRichMessage("<yellow>/itemcooldown list</yellow>   <gray>- Lists all loaded cooldown rules.</gray>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("itemcooldown.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("reload");
            subcommands.add("list");
            return StringUtil.copyPartialMatches(args[0], subcommands, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
