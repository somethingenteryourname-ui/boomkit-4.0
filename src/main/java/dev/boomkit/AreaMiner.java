package dev.boomkit;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Breaks the square/cube around a block, ripple by ripple, with the shockwave animation. */
public final class AreaMiner {

    private static final Color CYAN = Color.fromRGB(80, 240, 255);
    private static final Color PURPLE = Color.fromRGB(170, 70, 255);

    private final BoomKit plugin;
    private final Random random = new Random();

    /** True while we're firing our own BlockBreakEvents, so we don't start a new area break from them. */
    private boolean breakingExtra = false;

    public AreaMiner(BoomKit plugin) {
        this.plugin = plugin;
    }

    public boolean isBreakingExtra() {
        return breakingExtra;
    }

    /**
     * @param face the side of the block the player was mining (decides which way a "square" faces)
     */
    public void mine(Player player, Block origin, BlockFace face, ItemStack tool, BoomItems.PickSettings settings) {
        int r = settings.radius();
        boolean square = settings.shape() == BoomItems.Shape.SQUARE;

        // How far to go on each axis. A square is flat against the face you mined.
        int rx = r, ry = r, rz = r;
        if (square) {
            switch (face) {
                case UP, DOWN -> ry = 0;       // mining the floor/ceiling -> flat horizontal square
                case NORTH, SOUTH -> rz = 0;   // mining a wall -> upright square
                default -> rx = 0;             // EAST / WEST wall
            }
        }

        // Group blocks into "shells" by distance, so they break in an outward ripple.
        List<List<Block>> shells = new ArrayList<>();
        for (int i = 0; i <= r; i++) shells.add(new ArrayList<>());
        int total = 0;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dy = -ry; dy <= ry; dy++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    int d = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    if (d == 0) continue; // the block you actually broke
                    Block b = origin.getRelative(dx, dy, dz);
                    if (!canBreak(b)) continue;
                    shells.get(d).add(b);
                    total++;
                }
            }
        }
        if (total == 0) return;

        final int particlesPerBlock = total <= 30 ? 10 : total <= 150 ? 5 : 2;
        final boolean sparks = total <= 150;
        final boolean pickup = settings.autoPickup();
        final Location center = origin.getLocation().add(0.5, 0.5, 0.5);

        if (!settings.animation()) {
            for (List<Block> shell : shells) {
                for (Block b : shell) breakOne(player, b, tool, pickup, 1, false);
            }
            return;
        }

        // --- start of the animation: outline the whole area + charge-up sound ---
        final int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        final int frx = rx, fry = ry, frz = rz;
        outlineBox(origin.getWorld(), ox - rx, oy - ry, oz - rz, ox + rx + 1, oy + ry + 1, oz + rz + 1,
                new Particle.DustOptions(CYAN, 1.0f));
        sound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.7f);
        sound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
        burst(center, Particle.ELECTRIC_SPARK, 25, 0.35);

        new BukkitRunnable() {
            int d = 1;

            @Override
            public void run() {
                if (d > r || !player.isOnline()) {
                    if (player.isOnline()) finish(center);
                    cancel();
                    return;
                }

                Block firstBroken = null;
                for (Block b : shells.get(d)) {
                    Material before = b.getType();
                    if (breakOne(player, b, tool, pickup, particlesPerBlock, sparks) && firstBroken == null) {
                        firstBroken = b;
                        playBreakSound(b.getLocation().add(0.5, 0.5, 0.5), before, d);
                    }
                }

                // glowing ring showing the edge of this ripple
                int ex = Math.min(d, frx), ey = Math.min(d, fry), ez = Math.min(d, frz);
                Particle.DustTransition ring = new Particle.DustTransition(CYAN, PURPLE, 1.1f);
                outlineBox(center.getWorld(), ox - ex, oy - ey, oz - ez, ox + ex + 1, oy + ey + 1, oz + ez + 1, ring);

                d++;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** Anything solid you could normally mine. Never bedrock/barriers/portals, never chests or other containers. */
    private boolean canBreak(Block b) {
        Material type = b.getType();
        if (type.isAir() || b.isLiquid()) return false;
        if (type.getHardness() < 0) return false;
        return !(b.getState() instanceof Container); // setting a chest to air would delete its items
    }

    private boolean breakOne(Player player, Block b, ItemStack tool, boolean pickup, int particles, boolean sparks) {
        if (!player.isOnline() || !canBreak(b)) return false;

        // Let protection plugins (WorldGuard, claims, etc.) say no.
        BlockBreakEvent event = new BlockBreakEvent(b, player);
        breakingExtra = true;
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            breakingExtra = false;
        }
        if (event.isCancelled()) return false;

        BlockData data = b.getBlockData();
        Location spot = b.getLocation().add(0.5, 0.5, 0.5);
        boolean giveDrops = player.getGameMode() != GameMode.CREATIVE && event.isDropItems();
        Collection<ItemStack> drops = giveDrops ? b.getDrops(tool, player) : List.of(); // Fortune X applies here

        b.setType(Material.AIR);

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            if (pickup) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
                for (ItemStack extra : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            } else {
                b.getWorld().dropItemNaturally(spot, drop);
            }
        }

        particle(spot, Particle.BLOCK, particles, 0.3, 0.3, 0.3, 0, data);
        if (sparks && random.nextInt(3) == 0) particle(spot, Particle.ELECTRIC_SPARK, 2, 0.3, 0.3, 0.3, 0.05, null);
        return true;
    }

    private void playBreakSound(Location loc, Material type, int ripple) {
        try {
            Sound breakSound = type.createBlockData().getSoundGroup().getBreakSound();
            sound(loc, breakSound, 1f, 0.8f + ripple * 0.08f);
        } catch (Throwable ignored) {
        }
        sound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.0f + ripple * 0.15f);
    }

    private void finish(Location center) {
        sound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.4f);
        burst(center, Particle.FIREWORK, 30, 0.25);
        burst(center, Particle.END_ROD, 15, 0.15);
    }

    // ------------------------------------------------------------------
    //  particle helpers
    // ------------------------------------------------------------------

    /** Draws the 12 edges of a box with particles. */
    private void outlineBox(World world, double x1, double y1, double z1, double x2, double y2, double z2, Object dust) {
        if (world == null) return;
        Particle p = dust instanceof Particle.DustTransition ? Particle.DUST_COLOR_TRANSITION : Particle.DUST;
        double[][] corners = {
                {x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2},
                {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            double[] a = corners[e[0]], b = corners[e[1]];
            Vector from = new Vector(a[0], a[1], a[2]);
            Vector line = new Vector(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
            double length = line.length();
            if (length < 1e-6) continue;
            int steps = (int) Math.ceil(length / 0.5);
            for (int i = 0; i <= steps; i++) {
                Vector point = from.clone().add(line.clone().multiply(i / (double) steps));
                particle(point.toLocation(world), p, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private void burst(Location center, Particle particle, int amount, double speed) {
        for (int i = 0; i < amount; i++) {
            Vector dir = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSquared() < 1e-6) continue;
            dir.normalize();
            particle(center, particle, 0, dir.getX(), dir.getY(), dir.getZ(), speed, null);
        }
    }

    private void particle(Location loc, Particle particle, int count,
                          double ox, double oy, double oz, double extra, Object data) {
        World world = loc.getWorld();
        if (world == null) return;
        try {
            world.spawnParticle(particle, loc, count, ox, oy, oz, extra, data, true);
        } catch (Throwable ignored) {
        }
    }

    private void sound(Location loc, Sound sound, float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) return;
        try {
            world.playSound(loc, sound, volume, pitch);
        } catch (Throwable ignored) {
        }
    }
}
