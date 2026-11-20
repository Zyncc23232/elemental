package dev.elementalsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ElementalPlugin extends JavaPlugin implements Listener, TabExecutor {

    private NamespacedKey key;
    private Abilities abilities;
    /** index 1 = first ability, index 2 = second ability; values are expiry times in ms. */
    private final Map<UUID, long[]> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        key = new NamespacedKey(this, "element");
        abilities = new Abilities(this);
        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand cmd = getCommand("element");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        Bukkit.getScheduler().runTaskTimer(this, this::applyPassives, 20L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::updateActionBars, 20L, 10L);
        getLogger().info("ElementalSMP enabled.");
    }

    // ---------- element storage ----------

    public Element element(Player p) {
        String s = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return Element.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void setElement(Player p, Element e) {
        p.getPersistentDataContainer().set(key, PersistentDataType.STRING, e.name());
        p.playerListName(Component.text(p.getName(), e.color));
    }

    // ---------- join / quit ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        Element e = element(p);
        if (e == null) {
            e = Element.values()[ThreadLocalRandom.current().nextInt(Element.values().length)];
            setElement(p, e);
            p.showTitle(Title.title(
                    Component.text(e.display, e.color).decorate(TextDecoration.BOLD),
                    Component.text("Your element has been chosen. Use /element to see your powers.", NamedTextColor.GRAY)));
        } else {
            setElement(p, e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        cooldowns.remove(ev.getPlayer().getUniqueId());
    }

    // ---------- ability triggers (F and Shift+F) ----------

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent ev) {
        Player p = ev.getPlayer();
        Element e = element(p);
        if (e == null || p.getGameMode() == GameMode.SPECTATOR) return;
        ev.setCancelled(true);

        int slot = p.isSneaking() ? 2 : 1;
        long now = System.currentTimeMillis();
        long[] cd = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new long[3]);
        if (cd[slot] > now) {
            long left = (cd[slot] - now + 999) / 1000;
            p.sendActionBar(Component.text("On cooldown: " + left + "s", NamedTextColor.GRAY));
            return;
        }
        if (abilities.cast(p, e, slot)) {
            int seconds = slot == 1 ? e.cooldown1 : e.cooldown2;
            cd[slot] = now + seconds * 1000L;
        }
    }

    // ---------- passives that need events ----------

    @EventHandler
    public void onDamage(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) return;
        Element e = element(p);
        if (e == null) return;
        DamageCause c = ev.getCause();
        boolean cancel = switch (e) {
            case WIND -> c == DamageCause.FALL;
            case LIGHTNING -> c == DamageCause.LIGHTNING;
            case FIRE -> c == DamageCause.FIRE || c == DamageCause.FIRE_TICK
                    || c == DamageCause.LAVA || c == DamageCause.HOT_FLOOR;
            case WATER -> c == DamageCause.DROWNING;
            default -> false;
        };
        if (cancel) ev.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent ev) {
        if (ev.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(ev.getDamager() instanceof Player p) || element(p) != Element.LIGHTNING) return;
        if (ThreadLocalRandom.current().nextDouble() < 0.12) {
            ev.getEntity().getWorld().strikeLightningEffect(ev.getEntity().getLocation());
            ev.setDamage(ev.getDamage() + 3);
        }
    }

    // ---------- repeating tasks ----------

    private void applyPassives() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Element e = element(p);
            if (e == null || p.getGameMode() == GameMode.SPECTATOR) continue;
            switch (e) {
                case FIRE -> {
                    give(p, PotionEffectType.FIRE_RESISTANCE, 0);
                    if (p.isInWater()) give(p, PotionEffectType.SLOWNESS, 0);
                }
                case WATER -> {
                    give(p, PotionEffectType.WATER_BREATHING, 0);
                    if (p.isInWater()) {
                        give(p, PotionEffectType.DOLPHINS_GRACE, 0);
                    } else if (p.getWorld().hasStorm() && p.getLocation().getBlock().getLightFromSky() >= 15) {
                        give(p, PotionEffectType.REGENERATION, 0);
                    }
                }
                case EARTH -> {
                    give(p, PotionEffectType.HASTE, 0);
                    if (p.getHealth() < 10) give(p, PotionEffectType.RESISTANCE, 0);
                }
                case WIND -> give(p, PotionEffectType.SPEED, 0);
                case LIGHTNING -> {
                    if (p.getWorld().isThundering()) {
                        give(p, PotionEffectType.STRENGTH, 0);
                        give(p, PotionEffectType.SPEED, 0);
                    }
                }
            }
        }
    }

    /** Applies a short, invisible effect without overriding a stronger one. */
    private void give(Player p, PotionEffectType type, int amp) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && (cur.getAmplifier() > amp
                || (cur.getAmplifier() == amp && cur.getDuration() > 100))) {
            return;
        }
        p.addPotionEffect(new PotionEffect(type, 100, amp, true, false, false));
    }

    private void updateActionBars() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Element e = element(p);
            if (e == null) continue;
            long[] cd = cooldowns.get(p.getUniqueId());
            String a1 = status(cd == null ? 0 : cd[1], now);
            String a2 = status(cd == null ? 0 : cd[2], now);
            p.sendActionBar(Component.text(
                    e.display + "  |  F: " + e.ability1 + " " + a1
                            + "  |  Shift+F: " + e.ability2 + " " + a2, e.color));
        }
    }

    private String status(long expiry, long now) {
        if (expiry <= now) return "ready";
        return ((expiry - now + 999) / 1000) + "s";
    }

    // ---------- /element command ----------

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Only players have an element.");
                return true;
            }
            Element mine = element(p);
            if (mine == null) {
                s.sendMessage(Component.text("You have no element yet.", NamedTextColor.GRAY));
            } else {
                info(s, mine);
            }
            return true;
        }

        switch (a[0].toLowerCase()) {
            case "list" -> {
                for (Element e : Element.values()) info(s, e);
            }
            case "info" -> {
                Element e = a.length > 1 ? parse(a[1]) : null;
                if (e == null) {
                    s.sendMessage(Component.text("Usage: /element info <fire|water|earth|wind|lightning>", NamedTextColor.RED));
                } else {
                    info(s, e);
                }
            }
            case "set" -> {
                if (!s.hasPermission("elemental.admin")) {
                    s.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                Element e = a.length > 2 ? parse(a[2]) : null;
                if (target == null || e == null) {
                    s.sendMessage(Component.text("Usage: /element set <player> <element>", NamedTextColor.RED));
                } else {
                    setElement(target, e);
                    s.sendMessage(Component.text(target.getName() + " is now " + e.display + ".", e.color));
                    target.sendMessage(Component.text("Your element is now " + e.display + "!", e.color));
                }
            }
            default -> s.sendMessage(Component.text("Usage: /element [list | info <element> | set <player> <element>]", NamedTextColor.RED));
        }
        return true;
    }

    private Element parse(String s) {
        try {
            return Element.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void info(CommandSender s, Element e) {
        s.sendMessage(Component.text("● " + e.display, e.color).decorate(TextDecoration.BOLD));
        s.sendMessage(Component.text("  Passive: " + e.passive, NamedTextColor.GRAY));
        s.sendMessage(Component.text("  F - " + e.ability1 + " (" + e.cooldown1 + "s): " + e.desc1, NamedTextColor.GRAY));
        s.sendMessage(Component.text("  Shift+F - " + e.ability2 + " (" + e.cooldown2 + "s): " + e.desc2, NamedTextColor.GRAY));
        s.sendMessage(Component.text("  Strong against " + e.beats().display + ".", NamedTextColor.DARK_GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("list");
            out.add("info");
            if (s.hasPermission("elemental.admin")) out.add("set");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("info")) {
            addElements(out);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("set")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("set")) {
            addElements(out);
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }

    private void addElements(List<String> out) {
        for (Element e : Element.values()) out.add(e.name().toLowerCase());
    }
}
