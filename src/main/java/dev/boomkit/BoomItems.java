package dev.boomkit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates and recognises all the special BoomKit items. */
public final class BoomItems {

    private static final String CRYSTAL = "infinite_crystal";
    private static final String ANCHOR = "infinite_anchor";
    private static final String ARMOR = "blastproof_armor";
    private static final String PICKAXE = "shockwave_pickaxe";

    public enum Shape { SQUARE, CUBE }

    /** The settings each Shockwave Pickaxe carries around with it. */
    public record PickSettings(Shape shape, int radius, boolean autoPickup, boolean animation) {
        public String sizeText() {
            int d = radius * 2 + 1;
            return shape == Shape.CUBE ? d + "x" + d + "x" + d : d + "x" + d;
        }
    }

    private final BoomKit plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey pickShapeKey;
    private final NamespacedKey pickRadiusKey;
    private final NamespacedKey pickPickupKey;
    private final NamespacedKey pickAnimationKey;

    public BoomItems(BoomKit plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "boom_item");
        this.pickShapeKey = new NamespacedKey(plugin, "pick_shape");
        this.pickRadiusKey = new NamespacedKey(plugin, "pick_radius");
        this.pickPickupKey = new NamespacedKey(plugin, "pick_autopickup");
        this.pickAnimationKey = new NamespacedKey(plugin, "pick_animation");
    }

    public ItemStack crystal() {
        ItemStack item = new ItemStack(Material.END_CRYSTAL, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Infinite Crystal");
        meta.setLore(List.of(
                ChatColor.GRAY + "Never runs out.",
                ChatColor.GRAY + "Place it on " + ChatColor.WHITE + "any block" + ChatColor.GRAY + ".",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "Explodes in a Void Burst"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, CRYSTAL);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack anchor(int amount) {
        ItemStack item = new ItemStack(Material.RESPAWN_ANCHOR, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        // Store "charges = 4" on the item itself so it is placed already full of glowstone.
        if (meta instanceof BlockDataMeta blockDataMeta) {
            RespawnAnchor data = (RespawnAnchor) Material.RESPAWN_ANCHOR.createBlockData();
            data.setCharges(data.getMaximumCharges());
            blockDataMeta.setBlockData(data);
        }

        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Infinite Anchor");
        meta.setLore(List.of(
                ChatColor.GRAY + "Never runs out.",
                ChatColor.GRAY + "Places " + ChatColor.YELLOW + "fully charged" + ChatColor.GRAY + " with glowstone.",
                ChatColor.RED + "" + ChatColor.ITALIC + "Explodes in an Inferno Blast"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, ANCHOR);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------
    //  Blastproof netherite armor
    // ------------------------------------------------------------------

    /** Helmet, chestplate, leggings, boots. */
    public ItemStack[] armorSet() {
        Map<Enchantment, Integer> helmet = new LinkedHashMap<>();
        helmet.put(Enchantment.PROTECTION, 4);
        helmet.put(Enchantment.UNBREAKING, 3);
        helmet.put(Enchantment.MENDING, 1);
        helmet.put(Enchantment.RESPIRATION, 3);
        helmet.put(Enchantment.AQUA_AFFINITY, 1);
        helmet.put(Enchantment.THORNS, 3);

        Map<Enchantment, Integer> chest = new LinkedHashMap<>();
        chest.put(Enchantment.PROTECTION, 4);
        chest.put(Enchantment.UNBREAKING, 3);
        chest.put(Enchantment.MENDING, 1);
        chest.put(Enchantment.THORNS, 3);

        Map<Enchantment, Integer> legs = new LinkedHashMap<>();
        legs.put(Enchantment.PROTECTION, 4);
        legs.put(Enchantment.UNBREAKING, 3);
        legs.put(Enchantment.MENDING, 1);
        legs.put(Enchantment.SWIFT_SNEAK, 3);
        legs.put(Enchantment.THORNS, 3);

        Map<Enchantment, Integer> boots = new LinkedHashMap<>();
        boots.put(Enchantment.PROTECTION, 4);
        boots.put(Enchantment.UNBREAKING, 3);
        boots.put(Enchantment.MENDING, 1);
        boots.put(Enchantment.FEATHER_FALLING, 4);
        boots.put(Enchantment.DEPTH_STRIDER, 3);
        boots.put(Enchantment.SOUL_SPEED, 3);
        boots.put(Enchantment.THORNS, 3);

        return new ItemStack[]{
                armorPiece(Material.NETHERITE_HELMET, "Blastproof Helmet", helmet),
                armorPiece(Material.NETHERITE_CHESTPLATE, "Blastproof Chestplate", chest),
                armorPiece(Material.NETHERITE_LEGGINGS, "Blastproof Leggings", legs),
                armorPiece(Material.NETHERITE_BOOTS, "Blastproof Boots", boots)
        };
    }

    private ItemStack armorPiece(Material material, String name, Map<Enchantment, Integer> enchants) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + name);
        meta.setLore(List.of(
                ChatColor.GOLD + "Full set bonus:",
                ChatColor.GRAY + "No damage or knockback from",
                ChatColor.GRAY + "end crystals and respawn anchors."));
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, ARMOR);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBlastproof(ItemStack item) {
        return ARMOR.equals(tagOf(item));
    }

    /** True only when all four Blastproof pieces are being worn. */
    public boolean wearingFullSet(Player player) {
        return isBlastproof(player.getInventory().getHelmet())
                && isBlastproof(player.getInventory().getChestplate())
                && isBlastproof(player.getInventory().getLeggings())
                && isBlastproof(player.getInventory().getBoots());
    }

    // ------------------------------------------------------------------
    //  Shockwave Pickaxe
    // ------------------------------------------------------------------

    public ItemStack pickaxe() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Shockwave Pickaxe");
        meta.addEnchant(Enchantment.EFFICIENCY, 50, true);
        meta.addEnchant(Enchantment.FORTUNE, 10, true);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, PICKAXE);
        item.setItemMeta(meta);
        writePickSettings(item, new PickSettings(Shape.SQUARE, 1, false, true));
        return item;
    }

    public boolean isPickaxe(ItemStack item) {
        return PICKAXE.equals(tagOf(item));
    }

    public PickSettings readPickSettings(ItemStack item) {
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Shape shape;
        try {
            shape = Shape.valueOf(pdc.getOrDefault(pickShapeKey, PersistentDataType.STRING, "SQUARE"));
        } catch (IllegalArgumentException e) {
            shape = Shape.SQUARE;
        }
        int radius = clampRadius(pdc.getOrDefault(pickRadiusKey, PersistentDataType.INTEGER, 1));
        boolean pickup = pdc.getOrDefault(pickPickupKey, PersistentDataType.BYTE, (byte) 0) == 1;
        boolean animation = pdc.getOrDefault(pickAnimationKey, PersistentDataType.BYTE, (byte) 1) == 1;
        return new PickSettings(shape, radius, pickup, animation);
    }

    /** Saves the settings onto the pickaxe and refreshes its description. */
    public void writePickSettings(ItemStack item, PickSettings s) {
        PickSettings settings = new PickSettings(s.shape(), clampRadius(s.radius()), s.autoPickup(), s.animation());
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(pickShapeKey, PersistentDataType.STRING, settings.shape().name());
        pdc.set(pickRadiusKey, PersistentDataType.INTEGER, settings.radius());
        pdc.set(pickPickupKey, PersistentDataType.BYTE, (byte) (settings.autoPickup() ? 1 : 0));
        pdc.set(pickAnimationKey, PersistentDataType.BYTE, (byte) (settings.animation() ? 1 : 0));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Shape: " + ChatColor.WHITE + (settings.shape() == Shape.CUBE ? "Cube" : "Square"));
        lore.add(ChatColor.GRAY + "Radius: " + ChatColor.WHITE + settings.radius()
                + ChatColor.GRAY + " (" + settings.sizeText() + ")");
        lore.add(ChatColor.GRAY + "Auto-pickup: " + onOff(settings.autoPickup()));
        lore.add(ChatColor.GRAY + "Animation: " + onOff(settings.animation()));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Shift + right-click for settings");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public int clampRadius(int radius) {
        return Math.max(1, Math.min(radius, plugin.pickaxeMaxRadius()));
    }

    private static String onOff(boolean on) {
        return on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
    }

    public boolean isCrystal(ItemStack item) {
        return CRYSTAL.equals(tagOf(item));
    }

    public boolean isAnchor(ItemStack item) {
        return ANCHOR.equals(tagOf(item));
    }

    private String tagOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }
}
