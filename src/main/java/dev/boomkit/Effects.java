package dev.boomkit;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * All the explosion animations. These are purely visual/sound - the real
 * explosion (damage, knockback, block breaking) is still the vanilla one.
 */
public final class Effects {

    private static final Color MAGENTA = Color.fromRGB(225, 60, 255);
    private static final Color CYAN = Color.fromRGB(80, 240, 255);
    private static final Color VOID_PURPLE = Color.fromRGB(110, 25, 190);
    private static final Color GLOWSTONE = Color.fromRGB(255, 215, 80);
    private static final Color FIRE_ORANGE = Color.fromRGB(255, 120, 20);
    private static final Color EMBER_RED = Color.fromRGB(200, 25, 10);
    private static final Color SMOKE_GREY = Color.fromRGB(70, 60, 60);

    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private final BoomKit plugin;
    private final Random random = new Random();

    public Effects(BoomKit plugin) {
        this.plugin = plugin;
    }

    // ==================================================================
    //  CRYSTAL: "Void Burst"
    // ==================================================================
    public void voidBurst(Location center, double sizeScale) {
        if (center.getWorld() == null) return;
        final double scale = clamp(sizeScale);
        final Location ground = center.clone().add(0, 0.1, 0);
        final Location mid = center.clone().add(0, 1.0, 0);

        final Particle.DustTransition shockwave = new Particle.DustTransition(MAGENTA, CYAN, 1.9f);
        final Particle.DustOptions purple = new Particle.DustOptions(MAGENTA, 1.4f);
        final Particle.DustOptions cyan = new Particle.DustOptions(CYAN, 1.3f);
        final Particle.DustOptions voidDust = new Particle.DustOptions(VOID_PURPLE, 1.6f);

        // --- the initial bang ---
        sound(mid, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 1.3f);
        sound(mid, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1.8f);
        sound(mid, Sound.BLOCK_BEACON_DEACTIVATE, 3f, 0.6f);
        sound(mid, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 3f, 0.5f);

        particle(mid, Particle.EXPLOSION_EMITTER, 2, 0.3, 0.3, 0.3, 0, null);
        particle(mid, Particle.SONIC_BOOM, 1, 0, 0, 0, 0, null);
        particle(mid, Particle.REVERSE_PORTAL, 160, 0.6, 0.6, 0.6, 1.2, null);
        burst(mid, Particle.END_ROD, 70, 0.55);
        burst(mid, Particle.ELECTRIC_SPARK, 55, 0.9);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t > 36) {
                    cancel();
                    return;
                }

                // 1) expanding ground shockwave, magenta fading to cyan
                if (t <= 14) {
                    double r = (0.6 + t * 0.6) * scale;
                    ring(ground, r, (int) (r * 14), Particle.DUST_COLOR_TRANSITION, shockwave);
                    if (t % 2 == 0) {
                        ring(ground.clone().add(0, 0.35, 0), r * 0.8, (int) (r * 6), Particle.END_ROD, null);
                    }
                }

                // 2) sky beam shooting upward
                if (t <= 12) {
                    double top = Math.min(t * 1.6, 18);
                    for (double y = 0; y <= top; y += 0.5) {
                        particle(mid.clone().add(0, y, 0), Particle.END_ROD, 1, 0.04, 0, 0.04, 0, null);
                    }
                    particle(mid.clone().add(0, top, 0), Particle.DUST, 6, 0.3, 0.3, 0.3, 0, cyan);
                }

                // 3) twin spiraling helix rising around the blast
                if (t <= 26) {
                    for (int strand = 0; strand < 2; strand++) {
                        for (int k = 0; k < 3; k++) {
                            double step = t + k / 3.0;
                            double angle = step * 0.55 + strand * Math.PI;
                            double radius = (1.4 + step * 0.06) * Math.sqrt(scale);
                            Location p = ground.clone().add(Math.cos(angle) * radius, step * 0.35, Math.sin(angle) * radius);
                            particle(p, Particle.DUST, 1, 0, 0, 0, 0, strand == 0 ? purple : cyan);
                            if (k == 0) particle(p, Particle.SOUL_FIRE_FLAME, 1, 0, 0, 0, 0.01, null);
                        }
                    }
                }

                // 4) ring of sonic booms
                if (t == 5) {
                    sound(mid, Sound.ENTITY_BREEZE_WIND_BURST, 3f, 0.6f);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        particle(mid.clone().add(Math.cos(a) * 3.5 * scale, 0, Math.sin(a) * 3.5 * scale), Particle.SONIC_BOOM, 1, 0, 0, 0, 0, null);
                    }
                }

                // 5) energy dome that collapses inward...
                if (t >= 10 && t <= 18) {
                    double r = (3.4 - (t - 10) * 0.35) * scale;
                    sphere(mid, r, (int) (110 * scale), Particle.DUST, (t % 2 == 0) ? voidDust : cyan, t * 0.3);
                }

                // 6) ...and pops
                if (t == 19) {
                    sound(mid, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 3f, 0.8f);
                    sound(mid, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 3f, 0.6f);
                    particle(mid, Particle.EXPLOSION, 6, 0.6, 0.6, 0.6, 0, null);
                    burst(mid, Particle.FIREWORK, 100, 0.45);
                    burst(mid, Particle.END_ROD, 40, 0.3);
                }

                // 7) purple stardust raining down
                if (t >= 20) {
                    Location sky = ground.clone().add(0, 5, 0);
                    particle(sky, Particle.FALLING_OBSIDIAN_TEAR, (int) (8 * scale * scale), 3.5 * scale, 1.2, 3.5 * scale, 0, null);
                    particle(sky, Particle.END_ROD, (int) (3 * scale), 3.5 * scale, 1.5, 3.5 * scale, 0.01, null);
                }

                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ==================================================================
    //  ANCHOR: "Inferno Blast" (mushroom cloud)
    // ==================================================================
    public void infernoBlast(Location center, double sizeScale) {
        if (center.getWorld() == null) return;
        final double scale = clamp(sizeScale);
        final Location ground = center.clone().add(0, -0.4, 0);
        final Location mid = center.clone();

        final Particle.DustTransition shockwave = new Particle.DustTransition(GLOWSTONE, EMBER_RED, 2.0f);
        final Particle.DustOptions fire = new Particle.DustOptions(FIRE_ORANGE, 2.0f);
        final Particle.DustOptions ember = new Particle.DustOptions(EMBER_RED, 1.8f);
        final Particle.DustOptions glow = new Particle.DustOptions(GLOWSTONE, 1.5f);
        final Particle.DustOptions smoke = new Particle.DustOptions(SMOKE_GREY, 2.2f);

        // --- the initial bang ---
        sound(mid, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 3f, 0.5f);
        sound(mid, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        sound(mid, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 3f, 0.6f);
        sound(mid, Sound.ENTITY_BLAZE_SHOOT, 3f, 0.5f);
        sound(mid, Sound.ENTITY_GHAST_SHOOT, 3f, 0.5f);

        particle(mid, Particle.EXPLOSION_EMITTER, 2, 0.4, 0.4, 0.4, 0, null);
        particle(mid, Particle.LAVA, 60, 1.5, 1.0, 1.5, 0, null);
        particle(mid, Particle.DUST, 90, 1.3, 1.3, 1.3, 0, glow);
        burst(mid, Particle.FLAME, 100, 0.65);

        final double stemMax = 7.0 * Math.sqrt(scale);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t > 38) {
                    cancel();
                    return;
                }

                // 1) fiery ground shockwave, glowstone-yellow fading to red
                if (t <= 14) {
                    double r = (0.8 + t * 0.65) * scale;
                    ring(ground, r, (int) (r * 14), Particle.DUST_COLOR_TRANSITION, shockwave);
                    if (t % 2 == 0) ring(ground, Math.max(0.3, r - 0.7), (int) (r * 4), Particle.LARGE_SMOKE, null);
                    if (t % 3 == 0) ring(ground.clone().add(0, 0.2, 0), r, (int) (r * 5), Particle.FLAME, null);
                }

                // 2) rising fire stem
                double stemTop = Math.min(t * 0.55 * Math.sqrt(scale), stemMax);
                if (t <= 20) {
                    for (int i = 0; i < 7; i++) {
                        Location p = ground.clone().add(0, random.nextDouble() * stemTop, 0);
                        particle(p, Particle.FLAME, 2, 0.25, 0.1, 0.25, 0.01, null);
                        particle(p, Particle.LARGE_SMOKE, 1, 0.35, 0.1, 0.35, 0.01, null);
                        particle(p, Particle.DUST, 1, 0.3, 0.1, 0.3, 0, (i % 2 == 0) ? fire : ember);
                    }
                    if (t % 3 == 0) particle(ground, Particle.CAMPFIRE_COSY_SMOKE, 3, 0.6, 0.1, 0.6, 0.02, null);
                }

                // 3) rolling mushroom-cloud cap
                if (t >= 8 && t <= 30) {
                    Location cap = ground.clone().add(0, stemTop, 0);
                    double major = (1.6 + (t - 8) * 0.08) * Math.sqrt(scale);
                    double minor = Math.sqrt(scale);
                    double roll = t * 0.35;
                    for (int i = 0; i < 20; i++) {
                        double theta = i * (Math.PI * 2 / 20) + t * 0.05;
                        for (int j = 0; j < 6; j++) {
                            double phi = j * (Math.PI * 2 / 6) + roll;
                            double dist = major + minor * Math.cos(phi);
                            Location p = cap.clone().add(Math.cos(theta) * dist, minor * Math.sin(phi), Math.sin(theta) * dist);
                            Particle.DustOptions c = (j % 3 == 0) ? smoke : (j % 2 == 0 ? fire : ember);
                            particle(p, Particle.DUST, 1, 0, 0, 0, 0, c);
                        }
                    }
                }

                if (t == 8) {
                    sound(mid, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.3f); // deep rumble
                    particle(ground.clone().add(0, stemTop, 0), Particle.LAVA, 30, 1.5, 0.5, 1.5, 0, null);
                }
                if (t == 14) sound(mid, Sound.BLOCK_FIRE_AMBIENT, 3f, 0.6f);

                // 4) embers and ash raining down
                if (t >= 14) {
                    Location sky = ground.clone().add(0, stemMax, 0);
                    particle(sky, Particle.FALLING_LAVA, (int) (5 * scale * scale), 3.5 * scale, 0.6, 3.5 * scale, 0, null);
                    particle(sky, Particle.ASH, (int) (15 * scale * scale), 4.0 * scale, 1.5, 4.0 * scale, 0, null);
                }

                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ==================================================================
    //  small "charged!" effect when an Infinite Anchor is placed
    // ==================================================================
    public void anchorCharged(Location center) {
        sound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f);
        particle(center, Particle.DUST, 25, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(GLOWSTONE, 1.2f));
        particle(center, Particle.GLOW, 10, 0.5, 0.5, 0.5, 0, null);
    }

    // ==================================================================
    //  helpers
    // ==================================================================

    /** Keeps the animation size sane even with huge blast settings. */
    private static double clamp(double scale) {
        return Math.max(0.6, Math.min(scale, 3.0));
    }

    private void particle(Location loc, Particle particle, int count,
                          double ox, double oy, double oz, double extra, Object data) {
        World world = loc.getWorld();
        if (world == null) return;
        try {
            // force = true -> visible from far away, even with "minimal" particles
            world.spawnParticle(particle, loc, count, ox, oy, oz, extra, data, true);
        } catch (Throwable ignored) {
            // never let a single particle break the whole animation
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

    /** Shoots particles outward in every direction. */
    private void burst(Location center, Particle particle, int amount, double speed) {
        for (int i = 0; i < amount; i++) {
            Vector dir = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSquared() < 1e-6) continue;
            dir.normalize();
            // count = 0 makes the offsets act as a direction and "extra" as speed
            particle(center, particle, 0, dir.getX(), dir.getY(), dir.getZ(), speed, null);
        }
    }

    private void ring(Location center, double radius, int points, Particle particle, Object data) {
        points = Math.max(points, 8);
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2 / points);
            particle(center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), particle, 1, 0, 0, 0, 0, data);
        }
    }

    private void sphere(Location center, double radius, int points, Particle particle, Object data, double spin) {
        for (int i = 0; i < points; i++) {
            double y = 1 - 2 * (i + 0.5) / points;
            double r = Math.sqrt(1 - y * y);
            double theta = i * GOLDEN_ANGLE + spin;
            particle(center.clone().add(Math.cos(theta) * r * radius, y * radius, Math.sin(theta) * r * radius),
                    particle, 1, 0, 0, 0, 0, data);
        }
    }
}
