package dev.boomkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The chest-style settings menu for the Shockwave Pickaxe. */
public final class PickaxeMenu implements InventoryHolder {

    public static final int SHAPE = 10;
    public static final int RADIUS_DOWN = 12;
    public static final int RADIUS = 13;
    public static final int RADIUS_UP = 14;
    public static final int PICKUP = 15;
    public static final int ANIMATION = 16;

    private final Inventory inventory;
    private final int pickaxeSlot;
    private final int maxRadius;

    public PickaxeMenu(int pickaxeSlot, int maxRadius, BoomItems.PickSettings settings) {
        this.pickaxeSlot = pickaxeSlot;
        this.maxRadius = maxRadius;
        this.inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Shockwave Pickaxe Settings");
        render(settings);
    }

    /** Inventory slot the pickaxe was in when the menu opened. */
    public int pickaxeSlot() {
        return pickaxeSlot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void render(BoomItems.PickSettings s) {
        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        boolean cube = s.shape() == BoomItems.Shape.CUBE;
        inventory.setItem(SHAPE, button(cube ? Material.STONE : Material.SMOOTH_STONE_SLAB,
                ChatColor.AQUA + "Shape: " + ChatColor.WHITE + (cube ? "Cube" : "Square"),
                List.of(ChatColor.GRAY + (cube ? "Breaks a full 3D box." : "Breaks a flat square facing you."),
                        ChatColor.YELLOW + "Click to switch")));

        inventory.setItem(RADIUS_DOWN, button(Material.RED_CONCRETE, ChatColor.RED + "Radius -1",
                List.of(ChatColor.GRAY + "Smaller area")));

        ItemStack radius = button(Material.AMETHYST_SHARD,
                ChatColor.LIGHT_PURPLE + "Radius: " + ChatColor.WHITE + s.radius(),
                List.of(ChatColor.GRAY + "Area: " + ChatColor.WHITE + s.sizeText(),
                        ChatColor.DARK_GRAY + "Max radius: " + maxRadius));
        radius.setAmount(Math.max(1, s.radius()));
        inventory.setItem(RADIUS, radius);

        inventory.setItem(RADIUS_UP, button(Material.LIME_CONCRETE, ChatColor.GREEN + "Radius +1",
                List.of(ChatColor.GRAY + "Bigger area")));

        inventory.setItem(PICKUP, button(s.autoPickup() ? Material.HOPPER : Material.BARRIER,
                ChatColor.GOLD + "Auto-pickup: " + onOff(s.autoPickup()),
                List.of(ChatColor.GRAY + "Send drops straight to your inventory.",
                        ChatColor.YELLOW + "Click to toggle")));

        inventory.setItem(ANIMATION, button(s.animation() ? Material.FIREWORK_ROCKET : Material.GRAY_DYE,
                ChatColor.GOLD + "Animation: " + onOff(s.animation()),
                List.of(ChatColor.GRAY + "Shockwave ripple effect when mining.",
                        ChatColor.GRAY + "Off = instant break, less lag.",
                        ChatColor.YELLOW + "Click to toggle")));
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static String onOff(boolean on) {
        return on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
    }
}
