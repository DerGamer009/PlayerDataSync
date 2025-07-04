package com.example.playerdatasync;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class SyncCommand implements CommandExecutor {
    private final PlayerDataSync plugin;

    public SyncCommand(PlayerDataSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Current sync settings:");
            sender.sendMessage("coordinates: " + plugin.isSyncCoordinates());
            sender.sendMessage("xp: " + plugin.isSyncXp());
            sender.sendMessage("gamemode: " + plugin.isSyncGamemode());
            sender.sendMessage("enderchest: " + plugin.isSyncEnderchest());
            sender.sendMessage("inventory: " + plugin.isSyncInventory());
            sender.sendMessage("health: " + plugin.isSyncHealth());
            sender.sendMessage("hunger: " + plugin.isSyncHunger());
            sender.sendMessage("position: " + plugin.isSyncPosition());
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!hasPerm(sender, "reload")) return true;
            plugin.reloadPlugin();
            sender.sendMessage(plugin.getMessageManager().get("prefix") + " " + plugin.getMessageManager().get("reloaded"));
            return true;
        }

        if (args.length == 2) {
            String option = args[0].toLowerCase();
            String val = args[1].toLowerCase();
            if (!val.equals("true") && !val.equals("false")) {
                sender.sendMessage("Usage: /sync <option> <true|false>");
                return true;
            }
            boolean enabled = Boolean.parseBoolean(val);
            if (option.equals("coordinates")) {
                if (!hasPerm(sender, "coordinates")) return true;
                plugin.setSyncCoordinates(enabled);
            } else if (option.equals("xp")) {
                if (!hasPerm(sender, "xp")) return true;
                plugin.setSyncXp(enabled);
            } else if (option.equals("gamemode")) {
                if (!hasPerm(sender, "gamemode")) return true;
                plugin.setSyncGamemode(enabled);
            } else if (option.equals("enderchest")) {
                if (!hasPerm(sender, "enderchest")) return true;
                plugin.setSyncEnderchest(enabled);
            } else if (option.equals("inventory")) {
                if (!hasPerm(sender, "inventory")) return true;
                plugin.setSyncInventory(enabled);
            } else if (option.equals("health")) {
                if (!hasPerm(sender, "health")) return true;
                plugin.setSyncHealth(enabled);
            } else if (option.equals("hunger")) {
                if (!hasPerm(sender, "hunger")) return true;
                plugin.setSyncHunger(enabled);
            } else if (option.equals("position")) {
                if (!hasPerm(sender, "position")) return true;
                plugin.setSyncPosition(enabled);
            } else {
                sender.sendMessage("Unknown option: " + option);
                return true;
            }
            sender.sendMessage("Set " + option + " sync to " + enabled);
            return true;
        }

        sender.sendMessage("Usage: /sync [<option> <true|false>]");
        return true;
    }

    private boolean hasPerm(CommandSender sender, String option) {
        if (sender.hasPermission("playerdatasync.admin.*") || sender.hasPermission("playerdatasync.admin." + option)) {
            return true;
        }
        sender.sendMessage("You do not have permission to change " + option);
        return false;
    }
}
