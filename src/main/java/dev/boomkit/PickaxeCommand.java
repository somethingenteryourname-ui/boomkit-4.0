package dev.boomkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * /boompick [player]              give the Shockwave Pickaxe (ops)
 * /boompick settings              open the settings menu
 * /boompick shape <square|cube>
 * /boompick radius <number>
 * /boompick pickup <on|off>
 * /boompick animation <on|off>
 * /boompick info
 */
public final class PickaxeCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> SUBCOMMANDS = Set.of("settings", "shape", "radius", "pickup", "animation", "info");

    private final BoomKit plugin;
    private final BoomItems items;

    public PickaxeCommand(BoomKit plugin, BoomItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && SUBCOMMANDS.contains(args[0].toLowerCase())) {
            return settings(sender, label, args);
        }

        // --- give ---
        if (!sender.hasPermission("boomkit.give")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to get the Shockwave Pickaxe.");
            return true;
        }
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' isn't online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "From the console you need to name a player: /" + label + " <player>");
            return true;
        }

        for (ItemStack extra : target.getInventory().addItem(items.pickaxe()).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), extra);
        }
        target.sendMessage(ChatColor.AQUA + "You got the " + ChatColor.BOLD + "Shockwave Pickaxe" + ChatColor.AQUA
                + "! Shift + right-click it to change the settings.");
        if (sender != target) sender.sendMessage(ChatColor.GREEN + "Gave a Shockwave Pickaxe to " + target.getName() + ".");
        return true;
    }

    private boolean settings(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can change pickaxe settings.");
            return true;
        }
        if (!player.hasPermission("boomkit.pickaxe")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to change pickaxe settings.");
            return true;
        }
        ItemStack pick = player.getInventory().getItemInMainHand();
        if (!items.isPickaxe(pick)) {
            player.sendMessage(ChatColor.RED + "Hold your Shockwave Pickaxe in your main hand first.");
            return true;
        }

        String sub = args[0].toLowerCase();
        BoomItems.PickSettings s = items.readPickSettings(pick);

        if (sub.equals("settings")) {
            PickaxeMenu menu = new PickaxeMenu(player.getInventory().getHeldItemSlot(), plugin.pickaxeMaxRadius(), s);
            player.openInventory(menu.getInventory());
            return true;
        }
        if (sub.equals("info")) {
            info(player, s);
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /" + label + " " + sub + " <value>");
            return true;
        }

        String value = args[1].toLowerCase();
        BoomItems.PickSettings updated;
        switch (sub) {
            case "shape" -> {
                if (!value.equals("square") && !value.equals("cube")) {
                    player.sendMessage(ChatColor.RED + "Shape must be square or cube.");
                    return true;
                }
                updated = new BoomItems.PickSettings(value.equals("cube") ? BoomItems.Shape.CUBE : BoomItems.Shape.SQUARE,
                        s.radius(), s.autoPickup(), s.animation());
            }
            case "radius" -> {
                int r;
                try {
                    r = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "'" + args[1] + "' isn't a whole number.");
                    return true;
                }
                int max = plugin.pickaxeMaxRadius();
                if (r < 1 || r > max) {
                    player.sendMessage(ChatColor.RED + "Radius must be between 1 and " + max + ".");
                    return true;
                }
                updated = new BoomItems.PickSettings(s.shape(), r, s.autoPickup(), s.animation());
            }
            case "pickup" -> {
                Boolean on = parseOnOff(value);
                if (on == null) {
                    player.sendMessage(ChatColor.RED + "Use on or off.");
                    return true;
                }
                updated = new BoomItems.PickSettings(s.shape(), s.radius(), on, s.animation());
            }
            default -> { // animation
                Boolean on = parseOnOff(value);
                if (on == null) {
                    player.sendMessage(ChatColor.RED + "Use on or off.");
                    return true;
                }
                updated = new BoomItems.PickSettings(s.shape(), s.radius(), s.autoPickup(), on);
            }
        }

        items.writePickSettings(pick, updated);
        player.getInventory().setItemInMainHand(pick);
        player.sendMessage(ChatColor.GREEN + "Pickaxe updated!");
        info(player, items.readPickSettings(pick));
        return true;
    }

    private void info(Player player, BoomItems.PickSettings s) {
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Shockwave Pickaxe");
        player.sendMessage(ChatColor.GRAY + "Shape: " + ChatColor.WHITE + (s.shape() == BoomItems.Shape.CUBE ? "Cube" : "Square"));
        player.sendMessage(ChatColor.GRAY + "Radius: " + ChatColor.WHITE + s.radius() + ChatColor.GRAY + " (" + s.sizeText() + ")");
        player.sendMessage(ChatColor.GRAY + "Auto-pickup: " + (s.autoPickup() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        player.sendMessage(ChatColor.GRAY + "Animation: " + (s.animation() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
    }

    private static Boolean parseOnOff(String value) {
        return switch (value) {
            case "on", "true", "yes" -> true;
            case "off", "false", "no" -> false;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
            if (sender.hasPermission("boomkit.give")) {
                for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "shape" -> options.addAll(List.of("square", "cube"));
                case "pickup", "animation" -> options.addAll(List.of("on", "off"));
                case "radius" -> {
                    for (int i = 1; i <= plugin.pickaxeMaxRadius(); i++) options.add(String.valueOf(i));
                }
                default -> {
                }
            }
        }
        String typed = args[args.length - 1].toLowerCase();
        options.removeIf(o -> !o.toLowerCase().startsWith(typed));
        return options;
    }
}
