package dev.elementalsmp;

import net.kyori.adventure.text.format.NamedTextColor;

public enum Element {
    FIRE("Fire", NamedTextColor.RED,
            "Immune to fire and lava. Slowed while in water.",
            "Flame Lance", "A ranged beam that burns the target.", 6,
            "Inferno Burst", "A fiery shockwave that burns and knocks back nearby enemies.", 20),
    WATER("Water", NamedTextColor.AQUA,
            "Water breathing, faster swimming, and regeneration in the rain.",
            "Tidal Lance", "A jet of water that shoves and slows the target.", 6,
            "Healing Tide", "Heals you and nearby players and puts out fires.", 25),
    EARTH("Earth", NamedTextColor.GREEN,
            "Haste, plus damage resistance when below 5 hearts.",
            "Stone Spike", "A spike of rock that damages and launches the target upward.", 7,
            "Stone Skin", "Big resistance and absorption for 8s, but you are slowed.", 30),
    WIND("Wind", NamedTextColor.WHITE,
            "Permanent speed and no fall damage.",
            "Gust Dash", "Launch yourself forward.", 4,
            "Cyclone", "Throws nearby enemies high into the air.", 22),
    LIGHTNING("Lightning", NamedTextColor.YELLOW,
            "Immune to lightning. Melee hits sometimes shock. Stronger and faster in thunderstorms.",
            "Thunder Strike", "Call lightning down where you are looking.", 8,
            "Storm Call", "Strike the 4 closest enemies with lightning.", 35);

    public final String display;
    public final NamedTextColor color;
    public final String passive;
    public final String ability1, desc1;
    public final int cooldown1;
    public final String ability2, desc2;
    public final int cooldown2;

    Element(String display, NamedTextColor color, String passive,
            String ability1, String desc1, int cooldown1,
            String ability2, String desc2, int cooldown2) {
        this.display = display;
        this.color = color;
        this.passive = passive;
        this.ability1 = ability1;
        this.desc1 = desc1;
        this.cooldown1 = cooldown1;
        this.ability2 = ability2;
        this.desc2 = desc2;
        this.cooldown2 = cooldown2;
    }

    /** The element this one is strong against. */
    public Element beats() {
        return switch (this) {
            case WATER -> FIRE;
            case FIRE -> WIND;
            case WIND -> EARTH;
            case EARTH -> LIGHTNING;
            case LIGHTNING -> WATER;
        };
    }

    /** Damage multiplier when this element attacks the given element. */
    public double multiplierAgainst(Element defender) {
        if (this.beats() == defender) return 1.25;
        if (defender.beats() == this) return 0.8;
        return 1.0;
    }
}
