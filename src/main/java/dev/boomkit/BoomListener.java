package dev.boomkit;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public final class BoomListener implements Listener {

    private static final int OFFHAND_SLOT = 40;

    private final BoomKit plugin;
    private final BoomItems items;
    private final Effects effects;
    private final NamespacedKey crystalKey;
    private final NamespacedKey noKnockbackKey;

    /** True only while one of our anchors is exploding (damage happens during that call). */
    private boolean anchorExploding = false;

    public BoomListener(BoomKit plugin, BoomItems items, Effects effects) {
        this.plugin = plugin;
        this.items = items;
        this.effects = effects;
        this.crystalKey = new NamespacedKey(plugin, "infinite_crystal");
        this.noKnockbackKey = new NamespacedKey(plugin, "blastproof_no_knockback");
    }

    // ------------------------------------------------------------------
    //  Right-click handling: detonate our anchors, place our crystals
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (event.getHand() == EquipmentSlot.HAND && wouldDetonate(event.getPlayer(), clicked)) {
            if (event.useInteractedBlock() == Event.Result.DENY) return; // protected area
            if (isMarkedAnchor(clicked)) {
                event.setCancelled(true);
                detonateAnchor(clicked);
            } else {
                // A normal anchor is about to blow up the vanilla way.
                shieldNearby(clicked.getLocation().add(0.5, 0.5, 0.5), BoomKit.VANILLA_ANCHOR_POWER);
            }
            return;
        }

        if (items.isCrystal(event.getItem())) {
            placeCrystal(event, clicked);
        }
    }

    /** Same rules vanilla uses for when right-clicking a charged anchor blows it up. */
    private boolean wouldDetonate(Player player, Block block) {
        if (!(block.getBlockData() instanceof RespawnAnchor anchor)) return false;
        if (anchor.getCharges() <= 0) return false;
        if (block.getWorld().isRespawnAnchorWorks()) return false; // in the Nether it just sets your spawn
        boolean handsFull = !isEmpty(player.getInventory().getItemInMainHand())
                || !isEmpty(player.getInventory().getItemInOffHand());
        return !(player.isSneaking() && handsFull); // sneaking with an item = place/use item instead
    }

    private void detonateAnchor(Block block) {
        markAnchor(block, false);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        float power = plugin.anchorPower();

        block.setType(Material.AIR, false);
        shieldNearby(center, power);
        anchorExploding = true;
        try {
            // power, set fire (like vanilla anchors), break blocks
            block.getWorld().createExplosion(center, power, true, true);
        } finally {
            anchorExploding = false;
        }
        effects.infernoBlast(center, power / BoomKit.VANILLA_ANCHOR_POWER);
    }

    private void placeCrystal(PlayerInteractEvent event, Block clicked) {
        // Respect protection plugins that already blocked this click.
        boolean blocked = event.useItemInHand() == Event.Result.DENY;

        // Always stop vanilla (it only allows obsidian/bedrock and uses the item up).
        event.setCancelled(true);
        if (blocked) return;

        BlockFace face = event.getBlockFace();
        Block base = clicked.getRelative(face);
        if (!base.getType().isAir() && !base.isPassable()) return;

        World world = base.getWorld();
        BoundingBox space = new BoundingBox(
                base.getX(), base.getY(), base.getZ(),
                base.getX() + 1, base.getY() + 2, base.getZ() + 1);
        if (!world.getNearbyEntities(space).isEmpty()) return; // vanilla rule: nothing in the way

        EnderCrystal crystal = world.spawn(base.getLocation().add(0.5, 0, 0.5), EnderCrystal.class);
        crystal.setShowingBottom(false);
        crystal.getPersistentDataContainer().set(crystalKey, PersistentDataType.BYTE, (byte) 1);

        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            player.swingOffHand();
        } else {
            player.swingMainHand();
        }
        // The item is NOT removed from the hand -> infinite.
    }

    // ------------------------------------------------------------------
    //  Crystal blast size + animation
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal)) return;
        if (isOurCrystal(event.getEntity())) {
            event.setRadius(plugin.crystalPower());
        }
        // Any crystal (ours or normal): make armor wearers un-pushable for this blast.
        shieldNearby(event.getEntity().getLocation(), event.getRadius());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalExplode(EntityExplodeEvent event) {
        if (!isOurCrystal(event.getEntity())) return;
        effects.voidBurst(event.getLocation(), plugin.crystalPower() / BoomKit.VANILLA_CRYSTAL_POWER);
    }

    private boolean isOurCrystal(Entity entity) {
        return entity instanceof EnderCrystal
                && entity.getPersistentDataContainer().has(crystalKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------
    //  Blastproof armor: no damage (and so no knockback) from crystals/anchors
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlastDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!items.wearingFullSet(player)) return;
        if (isCrystalOrAnchorDamage(event)) {
            // Cancelling explosion damage also cancels the explosion knockback.
            event.setCancelled(true);
        }
    }

    private boolean isCrystalOrAnchorDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean explosion = cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;

        // Our anchors (custom explosion)
        if (anchorExploding && explosion) return true;

        // Any end crystal
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof EnderCrystal) {
            return true;
        }

        // Normal respawn anchors / anything else crystal-caused
        try {
            DamageSource source = event.getDamageSource();
            if (source.getDamageType() == DamageType.BAD_RESPAWN_POINT) return true;
            if (source.getDirectEntity() instanceof EnderCrystal) return true;
            if (source.getCausingEntity() instanceof EnderCrystal) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Gives full-set wearers near the blast 100% explosion knockback resistance
     * (a real vanilla attribute, so it works in survival and creative and the
     * client gets zero push), then takes it away again next tick.
     */
    private void shieldNearby(Location center, float power) {
        World world = center.getWorld();
        if (world == null) return;
        double range = power * 2.0 + 3.0;

        List<Player> shielded = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, range, range, range)) {
            if (entity instanceof Player player && items.wearingFullSet(player)) {
                if (addNoKnockback(player)) shielded.add(player);
            }
        }
        if (shielded.isEmpty()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : shielded) removeNoKnockback(player);
        }, 2L);
    }

    private boolean addNoKnockback(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
        if (attr == null) return false;
        if (hasNoKnockback(attr)) return false; // already shielded by another blast this tick
        attr.addModifier(new AttributeModifier(noKnockbackKey, 1.0, AttributeModifier.Operation.ADD_NUMBER));
        return true;
    }

    private void removeNoKnockback(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
        if (attr == null) return;
        for (AttributeModifier modifier : new ArrayList<>(attr.getModifiers())) {
            if (noKnockbackKey.equals(modifier.getKey())) attr.removeModifier(modifier);
        }
    }

    private boolean hasNoKnockback(AttributeInstance attr) {
        for (AttributeModifier modifier : attr.getModifiers()) {
            if (noKnockbackKey.equals(modifier.getKey())) return true;
        }
        return false;
    }

    /** Safety: if the server stopped mid-blast, don't leave anyone permanently un-pushable. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        removeNoKnockback(event.getPlayer());
    }

    // ------------------------------------------------------------------
    //  Infinite anchor: places fully charged, never consumed
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceAnchor(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (!items.isAnchor(inHand)) return;

        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        int slot = event.getHand() == EquipmentSlot.OFF_HAND ? OFFHAND_SLOT : inventory.getHeldItemSlot();
        int amount = inHand.getAmount();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        fillWithGlowstone(block);
        markAnchor(block, true);
        effects.anchorCharged(block.getLocation().add(0.5, 0.5, 0.5));

        // Next tick: make sure it's still full, and give the anchor back so it never runs out.
        Bukkit.getScheduler().runTask(plugin, () -> {
            fillWithGlowstone(block);
            if (creative || !player.isOnline()) return;

            ItemStack now = inventory.getItem(slot);
            if (now == null || now.getType().isAir()) {
                inventory.setItem(slot, items.anchor(amount));
            } else if (items.isAnchor(now) && now.getAmount() < amount) {
                now.setAmount(amount);
            }
        });
    }

    /** Backup: if one of our anchors blows up some other way, still show the animation. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnchorExplode(BlockExplodeEvent event) {
        Block block = event.getBlock();
        if (!isMarkedAnchor(block)) return;
        markAnchor(block, false);
        effects.infernoBlast(block.getLocation().add(0.5, 0.5, 0.5), 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnchorBreak(BlockBreakEvent event) {
        if (isMarkedAnchor(event.getBlock())) {
            markAnchor(event.getBlock(), false);
        }
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private void fillWithGlowstone(Block block) {
        if (block.getBlockData() instanceof RespawnAnchor anchor
                && anchor.getCharges() < anchor.getMaximumCharges()) {
            anchor.setCharges(anchor.getMaximumCharges());
            block.setBlockData(anchor, false);
        }
    }

    /** Remember which anchors are ours (stored in the chunk, so it survives restarts). */
    private void markAnchor(Block block, boolean marked) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = anchorKey(block);
        if (marked) {
            pdc.set(key, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(key);
        }
    }

    private boolean isMarkedAnchor(Block block) {
        return block.getChunk().getPersistentDataContainer().has(anchorKey(block), PersistentDataType.BYTE);
    }

    private NamespacedKey anchorKey(Block block) {
        return new NamespacedKey(plugin, "anchor_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }
}
