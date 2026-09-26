package dev.boomkit;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BoomKit extends JavaPlugin {

    public static final float VANILLA_CRYSTAL_POWER = 6f;
    public static final float VANILLA_ANCHOR_POWER = 5f;
    public static final float MIN_POWER = 1f;
    public static final float MAX_POWER = 25f;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        BoomItems items = new BoomItems(this);
        Effects effects = new Effects(this);

        getServer().getPluginManager().registerEvents(new BoomListener(this, items, effects), this);

        BoomCommand give = new BoomCommand(items);
        for (String name : new String[]{"boomcrystal", "boomanchor", "boomkit", "boomarmor"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) cmd.setExecutor(give);
        }

        RadiusCommand radius = new RadiusCommand(this);
        PluginCommand radiusCmd = getCommand("boomradius");
        if (radiusCmd != null) {
            radiusCmd.setExecutor(radius);
            radiusCmd.setTabCompleter(radius);
        }

        AreaMiner miner = new AreaMiner(this);
        PickaxeListener pickaxeListener = new PickaxeListener(this, items, miner);
        getServer().getPluginManager().registerEvents(pickaxeListener, this);
        PickaxeCommand pickaxeCommand = new PickaxeCommand(this, items);
        PluginCommand pickCmd = getCommand("boompick");
        if (pickCmd != null) {
            pickCmd.setExecutor(pickaxeCommand);
            pickCmd.setTabCompleter(pickaxeCommand);
        }

        getLogger().info("BoomKit enabled - crystal power " + crystalPower() + ", anchor power " + anchorPower());
    }

    public float crystalPower() {
        return clampPower((float) getConfig().getDouble("crystal-power", 10.0));
    }

    public float anchorPower() {
        return clampPower((float) getConfig().getDouble("anchor-power", 10.0));
    }

    public void setCrystalPower(float power) {
        getConfig().set("crystal-power", (double) clampPower(power));
        saveConfig();
    }

    public void setAnchorPower(float power) {
        getConfig().set("anchor-power", (double) clampPower(power));
        saveConfig();
    }

    /** Biggest radius the Shockwave Pickaxe is allowed to use (bigger = more lag). */
    public int pickaxeMaxRadius() {
        return Math.max(1, Math.min(getConfig().getInt("pickaxe-max-radius", 5), 10));
    }

    public static float clampPower(float power) {
        return Math.max(MIN_POWER, Math.min(power, MAX_POWER));
    }
}
