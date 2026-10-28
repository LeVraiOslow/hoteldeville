package fr.hoteldeville.model;

import org.bukkit.configuration.ConfigurationSection;

/** Une ressource à fournir pour atteindre un niveau (= un slot dans le menu). */
public final class Requirement {

    public enum Type { ITEM, MONEY, XP, PLAYTIME }

    public final String id;
    public final Type type;
    public final String item;        // ITEM : MATERIAL ou nexo:id
    public final double amount;
    public final int slot;           // -1 = automatique
    public final boolean xpLevels;   // XP : true = niveaux, false = points
    public final ConfigurationSection section;

    public Requirement(String id, Type type, String item, double amount, int slot, boolean xpLevels,
                       ConfigurationSection section) {
        this.id = id;
        this.type = type;
        this.item = item;
        this.amount = amount;
        this.slot = slot;
        this.xpLevels = xpLevels;
        this.section = section;
    }
}
