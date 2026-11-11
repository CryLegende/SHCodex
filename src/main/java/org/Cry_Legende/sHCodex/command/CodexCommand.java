package org.Cry_Legende.sHCodex.command;

import com.google.common.collect.ImmutableList;
import org.Cry_Legende.sHCodex.SHCodex;
import org.Cry_Legende.sHCodex.config.RegionConfig;
import org.Cry_Legende.sHCodex.network.NetworkCodex;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
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

            case "import":
                if (sender.hasPermission("shcodex.admin")) {
                    importPlayerFiles(sender);
                    return true;
                }
                // sans la permission : comme toute sous-commande inconnue (le menu)

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

    /** /codex import : copie les découvertes de playerdata/ dans la base du réseau, sans rien écraser. */
    private void importPlayerFiles(CommandSender sender) {
        NetworkCodex network = plugin.getNetwork();
        if (network == null) {
            sender.sendMessage(plugin.getConfigManager().adminMessage("import-no-network",
                "&7&l« &c&lSH &8&l» &7Cette commande demande le &cmode réseau &7(SHNetwork installé et base joignable)."));
            return;
        }
        network.importFolder(new File(plugin.getDataFolder(), "playerdata"), result -> sender.sendMessage(result == null
            ? plugin.getConfigManager().adminMessage("import-failed",
                "&7&l« &c&lSH &8&l» &7L'importation a échoué, &cvérifiez la console&7.")
            : plugin.getConfigManager().adminMessage("import-done",
                    "&7&l« &a&lSH &8&l» &7Importation terminée : &a{imported} &7région(s) ajoutée(s) pour &a{players} &7joueur(s), &a{present} &7déjà présente(s).")
                .replace("{imported}", String.valueOf(result[0]))
                .replace("{present}", String.valueOf(result[1]))
                .replace("{players}", String.valueOf(result[2]))));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("shcodex.admin")) {
                completions.add("reload");
                completions.add("admin");
                if (plugin.getNetwork() != null) {
                    completions.add("import");
                }
            }
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        }

        if (args.length > 1 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("shcodex.admin")) {
            return adminCommand.onTabComplete(sender, command, alias, args);
        }

        return ImmutableList.of();
    }
}