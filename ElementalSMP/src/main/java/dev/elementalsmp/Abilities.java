package dev.elementalsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class Abilities {
    private final ElementalPlugin plugin;

    public Abilities(ElementalPlugin plugin) {
        this.plugin = plugin;
    }

    private record Hit(LivingEntity entity, Location point, double dist) {}

    /** Returns true if the ability was used (and the cooldown should start). */
    public boolean cast(Player p, Element e, int slot) {
        return switch (e) {
            case FIRE -> slot == 1 ? flameLance(p) : infernoBurst(p);
            case WATER -> slot == 1 ? tidalLance(p) : healingTide(p);
            case EARTH -> slot == 1 ? stoneSpike(p) : stoneSkin(p);
            case WIND -> slot == 1 ? gustDash(p) : cyclone(p);
            case LIGHTNING -> slot == 1 ? thunderStrike(p) : stormCall(p);
        };
    }

    // ---------- FIRE ----------

    private boolean flameLance(Player p) {
        Hit h = trace(p, 20);
        World w = p.getWorld();
        beam(p, h.dist(), l -> w.spawnParticle(Particle.FLAME, l, 2, 0.05, 0.05, 0.05, 0));
        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        if (h.entity() != null) {
            hurt(p, h.entity(), 6);
            h.entity().setFireTicks(100);
        }
        return true;
    }

    private boolean infernoBurst(Player p) {
        Location c = p.getLocation();
        World w = p.getWorld();
        for (int i = 1; i <= 4; i++) {
            double r = i * 1.5;
            ring(c, r, 0.2, l -> w.spawnParticle(Particle.FLAME, l, 1, 0, 0, 0, 0.02));
        }
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
        for (LivingEntity t : nearby(p, c, 6)) {
            hurt(p, t, 5);
            t.setFireTicks(100);
            pushAway(p, t, 1.0, 0.4);
        }
        return true;
    }

    // ---------- WATER ----------

    private boolean tidalLance(Player p) {
        Hit h = trace(p, 18);
        World w = p.getWorld();
        beam(p, h.dist(), l -> w.spawnParticle(Particle.SPLASH, l, 4, 0.1, 0.1, 0.1, 0));
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1f, 1f);
        p.setFireTicks(0);
        LivingEntity t = h.entity();
        if (t != null) {
            hurt(p, t, 4);
            Vector v = p.getEyeLocation().getDirection().multiply(1.2);
            v.setY(0.35);
            t.setVelocity(v);
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }
        return true;
    }

    private boolean healingTide(Player p) {
        Location c = p.getLocation();
        World w = p.getWorld();
        List<Player> targets = new ArrayList<>();
        targets.add(p);
        for (Player o : w.getPlayers()) {
            if (o != p && o.getGameMode() != GameMode.SPECTATOR
                    && o.getLocation().distanceSquared(c) <= 64) {
                targets.add(o);
            }
        }
        for (Player t : targets) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
            t.setFireTicks(0);
            w.spawnParticle(Particle.HEART, t.getLocation().add(0, 1.8, 0), 4, 0.3, 0.2, 0.3, 0);
        }
        for (int i = 1; i <= 4; i++) {
            double r = i * 2.0;
            ring(c, r, 0.2, l -> w.spawnParticle(Particle.SPLASH, l, 3, 0.1, 0.1, 0.1, 0));
        }
        w.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        return true;
    }

    // ---------- EARTH ----------

    private boolean stoneSpike(Player p) {
        Hit h = trace(p, 14);
        World w = p.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(120, 85, 50), 1.6f);
        beam(p, h.dist(), l -> w.spawnParticle(Particle.DUST, l, 3, 0.1, 0.1, 0.1, 0, dust));
        w.playSound(p.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
        if (h.entity() != null) {
            hurt(p, h.entity(), 5);
            h.entity().setVelocity(new Vector(0, 0.9, 0));
        }
        return true;
    }

    private boolean stoneSkin(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 0));
        World w = p.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(120, 85, 50), 1.6f);
        for (int i = 0; i < 4; i++) {
            double y = i * 0.5;
            ring(p.getLocation(), 0.8, y, l -> w.spawnParticle(Particle.DUST, l, 2, 0.05, 0.05, 0.05, 0, dust));
        }
        w.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
        return true;
    }

    // ---------- WIND ----------

    private boolean gustDash(Player p) {
        Vector v = p.getEyeLocation().getDirection().multiply(1.7);
        v.setY(Math.max(v.getY(), 0) + 0.25);
        p.setVelocity(v);
        p.setFallDistance(0);
        World w = p.getWorld();
        w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 25, 0.3, 0.3, 0.3, 0.05);
        w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.4f);
        return true;
    }

    private boolean cyclone(Player p) {
        Location c = p.getLocation();
        World w = p.getWorld();
        for (int i = 0; i < 4; i++) {
            double r = 1.5 + i;
            double y = i * 0.6;
            ring(c, r, y, l -> w.spawnParticle(Particle.CLOUD, l, 2, 0.05, 0.05, 0.05, 0.01));
        }
        w.playSound(c, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.7f);
        for (LivingEntity t : nearby(p, c, 7)) {
            hurt(p, t, 3);
            t.setVelocity(new Vector(0, 1.1, 0));
        }
        return true;
    }

    // ---------- LIGHTNING ----------

    private boolean thunderStrike(Player p) {
        Hit h = trace(p, 30);
        Location pt = h.point();
        pt.getWorld().strikeLightningEffect(pt);
        for (LivingEntity t : nearby(p, pt, 3)) {
            hurt(p, t, 8);
        }
        return true;
    }

    private boolean stormCall(Player p) {
        Location origin = p.getLocation();
        List<LivingEntity> targets = nearby(p, origin, 12);
        if (targets.isEmpty()) {
            p.sendActionBar(Component.text("No targets nearby", NamedTextColor.GRAY));
            return false;
        }
        targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(origin)));
        int count = Math.min(4, targets.size());
        for (int i = 0; i < count; i++) {
            LivingEntity t = targets.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || !t.isValid()) return;
                t.getWorld().strikeLightningEffect(t.getLocation());
                hurt(p, t, 6);
            }, i * 8L);
        }
        return true;
    }

    // ---------- helpers ----------

    private boolean valid(Player caster, Entity e) {
        if (e == caster || !(e instanceof LivingEntity) || e instanceof ArmorStand) return false;
        if (e instanceof Player pl) {
            return pl.getGameMode() != GameMode.SPECTATOR && pl.getGameMode() != GameMode.CREATIVE;
        }
        return true;
    }

    private Hit trace(Player p, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = p.getWorld().rayTrace(eye, dir, range, FluidCollisionMode.NEVER,
                true, 0.5, e -> valid(p, e));
        double dist = range;
        Location point = eye.clone().add(dir.clone().multiply(range));
        LivingEntity ent = null;
        if (r != null) {
            Vector hp = r.getHitPosition();
            dist = hp.distance(eye.toVector());
            point = hp.toLocation(p.getWorld());
            if (r.getHitEntity() instanceof LivingEntity le) ent = le;
        }
        return new Hit(ent, point, dist);
    }

    private void beam(Player p, double dist, Consumer<Location> draw) {
        Location start = p.getEyeLocation().subtract(0, 0.25, 0);
        Vector dir = start.getDirection();
        for (double i = 1.0; i <= dist; i += 0.5) {
            draw.accept(start.clone().add(dir.clone().multiply(i)));
        }
    }

    private void ring(Location c, double radius, double yOffset, Consumer<Location> draw) {
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI / 18;
            draw.accept(c.clone().add(Math.cos(a) * radius, yOffset, Math.sin(a) * radius));
        }
    }

    private List<LivingEntity> nearby(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (valid(p, e) && e.getLocation().distanceSquared(c) <= r * r) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    /** Damages the target, applying the element triangle if both are players. */
    private void hurt(Player p, LivingEntity target, double base) {
        double mult = 1.0;
        if (target instanceof Player tp) {
            Element te = plugin.element(tp);
            Element pe = plugin.element(p);
            if (te != null && pe != null) mult = pe.multiplierAgainst(te);
        }
        target.damage(base * mult, p);
    }

    private void pushAway(Player p, LivingEntity t, double strength, double lift) {
        Vector v = t.getLocation().toVector().subtract(p.getLocation().toVector());
        v.setY(0);
        if (v.lengthSquared() < 0.01) {
            v = p.getLocation().getDirection();
            v.setY(0);
        }
        if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
        v = v.normalize().multiply(strength);
        v.setY(lift);
        t.setVelocity(v);
    }
}
