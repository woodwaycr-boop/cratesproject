package dev.cratecore;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/** Sounds, particles and fireworks. Sound ids come from config.yml so admins can change them. */
final class Effects {

    private static final Color[] COLORS = {
            Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.BLUE, Color.FUCHSIA, Color.WHITE
    };
    private static final FireworkEffect.Type[] TYPES = {
            FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.STAR, FireworkEffect.Type.BURST, FireworkEffect.Type.BALL
    };
    /** Rising bell notes played at the start of a great win. */
    private static final float[] ARPEGGIO = {0.7f, 0.9f, 1.1f, 1.4f, 1.8f};

    private final CrateCore plugin;
    private final NamespacedKey fireworkTag;

    Effects(CrateCore plugin) {
        this.plugin = plugin;
        this.fireworkTag = new NamespacedKey(plugin, "celebration_firework");
    }

    /** Marks fireworks we launch, so CrateListener can cancel the explosion damage they would deal. */
    NamespacedKey fireworkTag() {
        return fireworkTag;
    }

    /** Plays a configured sound (animation.sounds.<name>) to one player. Blank or missing means silence. */
    void play(Player player, String name, float volume, float pitch) {
        String id = plugin.getConfig().getString("animation.sounds." + name);
        if (id == null || id.isBlank()) {
            return;
        }
        player.playSound(player.getLocation(), id.trim(), volume, pitch);
    }

    /** A thin ring of sparks that climbs around the crate while it spins. */
    void spinParticles(Location center, int step) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double angle = step * 0.55;
        double rise = (step % 12) * 0.1;
        for (int i = 0; i < 2; i++) {
            double a = angle + i * Math.PI;
            world.spawnParticle(Particle.END_ROD,
                    center.getX() + Math.cos(a) * 0.9,
                    center.getY() + rise,
                    center.getZ() + Math.sin(a) * 0.9,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Called once when the reel stops. */
    void win(Player player, Location center, boolean great) {
        play(player, "win", 1.0f, 1.0f);

        World world = center.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.END_ROD, center.getX(), center.getY() + 0.5, center.getZ(),
                    great ? 80 : 30, 0.5, 0.6, 0.5, 0.08);
        }

        if (great) {
            greatWin(player, center);
        }
    }

    private void greatWin(Player player, Location center) {
        play(player, "great-win", 1.0f, 1.0f);

        World world = center.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.FLAME, center.getX(), center.getY() + 0.5, center.getZ(),
                    120, 0.6, 0.8, 0.6, 0.12);
        }

        final int bursts = Math.max(1, plugin.getConfig().getInt("animation.great-win-fireworks", 8));
        new BukkitRunnable() {
            private int fired = 0;

            @Override
            public void run() {
                if (!player.isOnline() || fired >= bursts) {
                    cancel();
                    return;
                }
                if (fired < ARPEGGIO.length) {
                    play(player, "great-win-note", 1.0f, ARPEGGIO[fired]);
                }
                launchFirework(player);
                fired++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void launchFirework(Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = player.getLocation().add(
                random.nextDouble(-3.0, 3.0),
                1.0 + random.nextDouble(0.0, 2.0),
                random.nextDouble(-3.0, 3.0));
        World world = at.getWorld();
        if (world == null) {
            return;
        }

        FireworkEffect effect = FireworkEffect.builder()
                .with(TYPES[random.nextInt(TYPES.length)])
                .withColor(COLORS[random.nextInt(COLORS.length)], COLORS[random.nextInt(COLORS.length)])
                .withFade(COLORS[random.nextInt(COLORS.length)])
                .flicker(true)
                .trail(true)
                .build();

        Firework firework = world.spawn(at, Firework.class, fw -> {
            fw.getPersistentDataContainer().set(fireworkTag, PersistentDataType.BYTE, (byte) 1);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(effect);
            fw.setFireworkMeta(meta);
        });
        firework.detonate();
    }
}
