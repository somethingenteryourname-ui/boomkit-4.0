package dev.boomkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class BoomCommand implements CommandExecutor {

    private final BoomItems items;

    public BoomCommand(BoomItems items) {
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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

        String name = command.getName().toLowerCase();
        boolean giveCrystal = name.equals("boomcrystal") || name.equals("boomkit");
        boolean giveAnchor = name.equals("boomanchor") || name.equals("boomkit");

        if (giveCrystal) {
            give(target, items.crystal());
            target.sendMessage(ChatColor.LIGHT_PURPLE + "You got an " + ChatColor.BOLD + "Infinite Crystal" + ChatColor.LIGHT_PURPLE + "!");
        }
        if (giveAnchor) {
            give(target, items.anchor(1));
            target.sendMessage(ChatColor.GOLD + "You got an " + ChatColor.BOLD + "Infinite Anchor" + ChatColor.GOLD + "!");
        }
        if (name.equals("boomarmor")) {
            for (ItemStack piece : items.armorSet()) {
                give(target, piece);
            }
            target.sendMessage(ChatColor.DARK_RED + "You got the " + ChatColor.BOLD + "Blastproof Armor" + ChatColor.DARK_RED
                    + " set! Wear all 4 pieces to be immune to crystals and anchors.");
        }
        if (sender != target) {
            sender.sendMessage(ChatColor.GREEN + "Gave BoomKit item(s) to " + target.getName() + ".");
        }
        return true;
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }
}
