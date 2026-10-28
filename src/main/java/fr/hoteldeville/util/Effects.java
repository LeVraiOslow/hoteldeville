package fr.hoteldeville.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Sons et particules à partir de noms configurables (BLOCK_STONE_BREAK ou block.stone.break). */
public final class Effects {

    private Effects() {}

    private static NamespacedKey key(String name, boolean dots) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (dots && !n.contains(".") && !n.contains(":")) n = n.replace('_', '.');
        return NamespacedKey.fromString(n);
    }

    public static Sound sound(String name) {
        if (name == null || name.isBlank()) return null;
        NamespacedKey k = key(name, true);
        return k == null ? null : Registry.SOUNDS.get(k);
    }

    public static Particle particle(String name) {
        if (name == null || name.isBlank()) return null;
        NamespacedKey k = key(name, false);
        return k == null ? null : Registry.PARTICLE_TYPE.get(k);
    }

    public static void play(Location at, String name, float volume) {
        Sound s = sound(name);
        if (s != null && at.getWorld() != null) at.getWorld().playSound(at, s, volume, 1f);
    }

    public static void play(Player p, String name) {
        Sound s = sound(name);
        if (s != null) p.playSound(p.getLocation(), s, 1f, 1f);
    }

    public static void spawn(Location at, String name, int count) {
        Particle p = particle(name);
        if (p != null && at.getWorld() != null) at.getWorld().spawnParticle(p, at, count, 0.3, 0.3, 0.3, 0.01);
    }
}
