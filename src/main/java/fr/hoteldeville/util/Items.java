package fr.hoteldeville.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/** Création d'items depuis la config : MATERIAL vanilla ou nexo:id (via réflexion). */
public final class Items {

    private static Method nexoItemFromId;
    private static Method nexoIdFromItem;
    private static boolean nexoChecked = false;

    private Items() {}

    private static void initNexo() {
        if (nexoChecked) return;
        nexoChecked = true;
        try {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
            nexoItemFromId = api.getMethod("itemFromId", String.class);
            nexoIdFromItem = api.getMethod("idFromItem", ItemStack.class);
        } catch (Throwable ignored) {
            nexoItemFromId = null;
            nexoIdFromItem = null;
        }
    }

    public static boolean isNexoSpec(String spec) {
        return spec != null && spec.toLowerCase(Locale.ROOT).startsWith("nexo:");
    }

    /** Id Nexo d'un item, ou null si ce n'est pas un item Nexo. */
    public static String nexoId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        initNexo();
        if (nexoIdFromItem == null) return null;
        try {
            Object id = nexoIdFromItem.invoke(null, stack);
            return id == null ? null : id.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Parse "STONE" ou "nexo:id". Retourne null si invalide. */
    public static ItemStack parse(String spec, int amount) {
        if (spec == null || spec.isBlank()) return null;
        spec = spec.trim();
        ItemStack result = null;
        if (isNexoSpec(spec)) {
            initNexo();
            if (nexoItemFromId == null) return null;
            try {
                String id = spec.substring(5);
                Object builder = nexoItemFromId.invoke(null, id);
                if (builder == null) return null;
                Method build = builder.getClass().getMethod("build");
                result = (ItemStack) build.invoke(builder);
            } catch (Throwable t) {
                return null;
            }
        } else {
            Material mat = Material.matchMaterial(spec);
            if (mat == null || mat.isAir() || !mat.isItem()) return null;
            result = new ItemStack(mat);
        }
        if (result == null) return null;
        result = result.clone();
        result.setAmount(Math.max(1, Math.min(amount, Math.max(1, result.getMaxStackSize()))));
        return result;
    }

    /** Vérifie qu'un item correspond à la spécification (vanilla strict ou nexo). */
    public static boolean matches(ItemStack stack, String spec) {
        if (stack == null || stack.getType().isAir() || spec == null) return false;
        String nexo = nexoId(stack);
        if (isNexoSpec(spec)) {
            return nexo != null && nexo.equalsIgnoreCase(spec.substring(5).trim());
        }
        if (nexo != null) return false; // un item Nexo ne compte pas pour une ressource vanilla
        Material mat = Material.matchMaterial(spec.trim());
        return mat != null && stack.getType() == mat;
    }

    /** Nom lisible d'une spécification. */
    public static String displayName(String spec) {
        if (spec == null) return "";
        if (isNexoSpec(spec)) return Text.prettify(spec.substring(5));
        return Text.prettify(spec);
    }

    /**
     * Construit un item depuis une section :
     * material, amount, name, lore, glow, custom-model-data.
     */
    public static ItemStack build(ConfigurationSection sec, String... kv) {
        if (sec == null) return new ItemStack(Material.STONE);
        return buildAs(sec, sec.getString("material", "STONE"), kv);
    }

    /** Comme build(), mais les clés présentes dans "override" remplacent celles de "base" (états du menu). */
    public static ItemStack build(ConfigurationSection base, ConfigurationSection override, String... kv) {
        if (override == null) return build(base, kv);
        String material = override.getString("material", base.getString("material", "STONE"));
        int amount = Math.max(1, override.getInt("amount", base.getInt("amount", 1)));
        ItemStack stack = parse(material, amount);
        if (stack == null) stack = new ItemStack(Material.BARRIER);
        String name = override.contains("name") ? override.getString("name") : base.getString("name", null);
        ConfigurationSection loreSrc = override.isList("lore") ? override : base;
        List<String> lore = loreSrc.isList("lore") ? loreSrc.getStringList("lore") : null;
        boolean glow = override.contains("glow") ? override.getBoolean("glow") : base.getBoolean("glow", false);
        apply(stack, name, lore, glow, kv);
        return stack;
    }

    public static ItemStack buildAs(ConfigurationSection sec, String materialSpec, String... kv) {
        ItemStack stack = parse(materialSpec, Math.max(1, sec.getInt("amount", 1)));
        if (stack == null) stack = new ItemStack(Material.BARRIER);
        apply(stack, sec.getString("name", null), sec.isList("lore") ? sec.getStringList("lore") : null,
                sec.getBoolean("glow", false), kv);
        return stack;
    }

    /** Applique nom/lore/glow sur un item existant (name/lore null = inchangé). */
    public static void apply(ItemStack stack, String name, List<String> lore, boolean glow, String... kv) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        if (name != null) meta.displayName(Text.item(Text.rep(name, kv)));
        if (lore != null) meta.lore(Text.itemList(Text.rep(lore, kv)));
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
    }
}
