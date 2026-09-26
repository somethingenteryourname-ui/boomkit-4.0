package dev.boomkit;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

/**
 * /boomradius                      -> shows the current blast sizes
 * /boomradius crystal <power>      -> sets the crystal blast size
 * /boomradius anchor <power>       -> sets the anchor blast size
 * /boomradius reset                -> back to BoomKit defaults (10 / 10)
 */
public final class RadiusCommand implements CommandExecutor, TabCompleter {

    private final BoomKit plugin;

    public RadiusCommand(BoomKit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            show(sender);
            return true;
        }

        String which = args[0].toLowerCase();

        if (which.equals("reset")) {
            plugin.setCrystalPower(10f);
            plugin.setAnchorPower(10f);
            sender.sendMessage(ChatColor.GREEN + "Blast sizes reset to the BoomKit defaults.");
            show(sender);
            return true;
        }

        if (!which.equals("crystal") && !which.equals("anchor")) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " [crystal|anchor] [power]  or  /" + label + " reset");
            return true;
        }

        if (args.length == 1) {
            float power = which.equals("crystal") ? plugin.crystalPower() : plugin.anchorPower();
            sender.sendMessage(line(which.equals("crystal") ? "Crystal" : "Anchor", power,
                    which.equals("crystal") ? BoomKit.VANILLA_CRYSTAL_POWER : BoomKit.VANILLA_ANCHOR_POWER));
            return true;
        }

        float power;
        try {
            power = Float.parseFloat(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "'" + args[1] + "' isn't a number.");
            return true;
        }
        if (power < BoomKit.MIN_POWER || power > BoomKit.MAX_POWER) {
            sender.sendMessage(ChatColor.RED + "Power must be between " + (int) BoomKit.MIN_POWER
                    + " and " + (int) BoomKit.MAX_POWER + " (big numbers can lag the server).");
            return true;
        }

        if (which.equals("crystal")) {
            plugin.setCrystalPower(power);
        } else {
            plugin.setAnchorPower(power);
        }
        sender.sendMessage(ChatColor.GREEN + "Updated!");
        show(sender);
        return true;
    }

    private void show(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "BoomKit blast sizes");
        sender.sendMessage(line("Crystal", plugin.crystalPower(), BoomKit.VANILLA_CRYSTAL_POWER));
        sender.sendMessage(line("Anchor", plugin.anchorPower(), BoomKit.VANILLA_ANCHOR_POWER));
        sender.sendMessage(ChatColor.GRAY + "Change with /boomradius <crystal|anchor> <power>");
    }

    private String line(String name, float power, float vanilla) {
        return ChatColor.YELLOW + name + ChatColor.WHITE + ": power " + fmt(power)
                + ChatColor.GRAY + " (hits up to ~" + fmt(power * 2) + " blocks away, vanilla is " + fmt(vanilla) + ")";
    }

    private String fmt(float f) {
        return f == Math.floor(f) ? String.valueOf((int) f) : String.format("%.1f", f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"crystal", "anchor", "reset"}) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reset")) {
            out.add("6");
            out.add("10");
            out.add("15");
        }
        return out;
    }
}
