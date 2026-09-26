package dev.boomkit;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PickaxeListener implements Listener {

    private final BoomKit plugin;
    private final BoomItems items;
    private final AreaMiner miner;

    /** Which side of a block each player last hit (so a "square" faces the right way). */
    private final Map<UUID, BlockFace> lastFace = new HashMap<>();

    public PickaxeListener(BoomKit plugin, BoomItems items, AreaMiner miner) {
        this.plugin = plugin;
        this.items = items;
        this.miner = miner;
    }

    // ------------------------------------------------------------------
    //  Mining
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeftClickBlock(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            lastFace.put(event.getPlayer().getUniqueId(), event.getBlockFace());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (miner.isBreakingExtra()) return; // one of our own extra blocks
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!items.isPickaxe(tool)) return;

        BlockFace face = lastFace.get(player.getUniqueId());
        if (face == null || !(face.isCartesian())) face = guessFace(player);

        miner.mine(player, event.getBlock(), face, tool.clone(), items.readPickSettings(tool));
    }

    /** Auto-pickup for the block you actually broke (the extra blocks are handled in AreaMiner). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!items.isPickaxe(tool) || !items.readPickSettings(tool).autoPickup()) return;

        for (Item drop : event.getItems()) {
            for (ItemStack extra : player.getInventory().addItem(drop.getItemStack()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
        event.getItems().clear();
    }

    private BlockFace guessFace(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch > 45) return BlockFace.UP;
        if (pitch < -45) return BlockFace.DOWN;
        return player.getFacing();
    }

    // ------------------------------------------------------------------
    //  Settings menu
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onShiftRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !items.isPickaxe(event.getItem())) return;

        event.setCancelled(true);
        openMenu(player);
    }

    public void openMenu(Player player) {
        if (!player.hasPermission("boomkit.pickaxe")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to change pickaxe settings.");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!items.isPickaxe(held)) {
            player.sendMessage(ChatColor.RED + "Hold your Shockwave Pickaxe in your main hand first.");
            return;
        }
        PickaxeMenu menu = new PickaxeMenu(player.getInventory().getHeldItemSlot(),
                plugin.pickaxeMaxRadius(), items.readPickSettings(held));
        player.openInventory(menu.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.4f);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PickaxeMenu menu)) return;
        event.setCancelled(true); // nothing can be taken out of or put into the menu
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) return;

        ItemStack pick = player.getInventory().getItem(menu.pickaxeSlot());
        if (!items.isPickaxe(pick)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Your Shockwave Pickaxe isn't in that slot anymore.");
            return;
        }

        BoomItems.PickSettings s = items.readPickSettings(pick);
        BoomItems.PickSettings updated = switch (event.getSlot()) {
            case PickaxeMenu.SHAPE -> new BoomItems.PickSettings(
                    s.shape() == BoomItems.Shape.CUBE ? BoomItems.Shape.SQUARE : BoomItems.Shape.CUBE,
                    s.radius(), s.autoPickup(), s.animation());
            case PickaxeMenu.RADIUS_DOWN -> new BoomItems.PickSettings(s.shape(), s.radius() - 1, s.autoPickup(), s.animation());
            case PickaxeMenu.RADIUS_UP -> new BoomItems.PickSettings(s.shape(), s.radius() + 1, s.autoPickup(), s.animation());
            case PickaxeMenu.PICKUP -> new BoomItems.PickSettings(s.shape(), s.radius(), !s.autoPickup(), s.animation());
            case PickaxeMenu.ANIMATION -> new BoomItems.PickSettings(s.shape(), s.radius(), s.autoPickup(), !s.animation());
            default -> null;
        };
        if (updated == null) return;

        items.writePickSettings(pick, updated);
        player.getInventory().setItem(menu.pickaxeSlot(), pick);
        menu.render(items.readPickSettings(pick));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PickaxeMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFace.remove(event.getPlayer().getUniqueId());
    }
}
