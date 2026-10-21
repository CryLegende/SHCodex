package org.Cry_Legende.sHCodex.command;

import com.google.common.collect.ImmutableList;
import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.config.RegionConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CodexCommand implements CommandExecutor, TabCompleter {

    private final SHCodex plugin;
    private final CodexAdminCommand adminCommand;

    public CodexCommand(SHCodex plugin) {
        this.plugin = plugin;
        this.adminCommand = new CodexAdminCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getConfigManager().getPlayerOnlyMessage());
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("shcodex.use")) {
                player.sendMessage(plugin.getConfigManager().getNoPermissionMessage());
                return true;
            }
            if (plugin.getConfigManager().getBlacklistedWorlds().contains(player.getWorld().getName())) {
                player.sendMessage(plugin.getConfigManager().getWorldBlacklistedMessage());
                return true;
            }
            plugin.getMenuManager().openCodexMenu(player, 0);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("shcodex.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getNoPermissionMessage());
                    return true;
                }
                plugin.getConfigManager().reloadConfigs();
                plugin.getStatsManager().loadStats(); // Recharger aussi les stats
                sender.sendMessage(plugin.getConfigManager().getReloadMessage());
                return true;

            case "admin":
                return adminCommand.onCommand(sender, command, label, args);

            default:
                if (sender instanceof Player) {
                    // Si la sous-commande n'est pas reconnue, on ouvre le menu
                    Player player = (Player) sender;
                    plugin.getMenuManager().openCodexMenu(player, 0);
                } else {
                    sender.sendMessage(plugin.getConfigManager().getPlayerOnlyMessage());
                }
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("shcodex.admin")) {
                completions.add("reload");
                completions.add("admin");
            }
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        }

        if (args.length > 1 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("shcodex.admin")) {
            return adminCommand.onTabComplete(sender, command, alias, args);
        }

        return ImmutableList.of();
    }
}