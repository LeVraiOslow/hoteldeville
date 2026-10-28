package fr.hoteldeville.menu;

import com.bgsoftware.superiorskyblock.api.island.Island;
import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.data.HotelData;
import fr.hoteldeville.manager.Configs;
import fr.hoteldeville.manager.HotelManager;
import fr.hoteldeville.model.LevelDef;
import fr.hoteldeville.model.Requirement;
import fr.hoteldeville.util.Items;
import fr.hoteldeville.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Menu /hotel : informations, ressources du prochain niveau, accès au stockage. */
public final class HotelMenu implements InventoryHolder {

    private final HotelDeVillePlugin plugin;
    private final UUID playerId;
    private final UUID islandId;
    private final int level;
    private Inventory inventory;

    private final Map<Integer, Requirement> requirementSlots = new HashMap<>();
    private final Map<Integer, String> actions = new HashMap<>();

    private HotelMenu(HotelDeVillePlugin plugin, UUID playerId, UUID islandId, int level) {
        this.plugin = plugin;
        this.playerId = playerId;
        this.islandId = islandId;
        this.level = level;
    }

    /** Ouvre le menu pour un joueur (vérifie île + hôtel). */
    public static void open(HotelDeVillePlugin plugin, Player player) {
        HotelManager hotels = plugin.hotels();
        Configs cfg = plugin.configs();
        Island island = hotels.islandOf(player);
        if (island == null) {
            cfg.send(player, "general.no-island");
            return;
        }
        HotelData d = hotels.hotelOf(island);
        if (d == null) {
            cfg.send(player, "general.no-hotel");
            return;
        }
        // Niveau sans ressource demandée : démarre directement l'amélioration
        hotels.tryStartUpgrade(d, player, island);

        ConfigurationSection m = cfg.menus().getConfigurationSection("main");
        int size = 54;
        String title = "&8Hôtel de Ville";
        if (m != null) {
            size = Math.max(9, Math.min(54, (m.getInt("size", 54) / 9) * 9));
            title = m.getString("title", title);
        }
        title = Text.rep(title, "level", String.valueOf(d.level), "max", String.valueOf(cfg.maxLevel()));

        HotelMenu menu = new HotelMenu(plugin, player.getUniqueId(), island.getUniqueId(), d.level);
        menu.inventory = Bukkit.createInventory(menu, size, Text.color(title));
        menu.render();
        player.openInventory(menu.inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int level() {
        return level;
    }

    public UUID islandId() {
        return islandId;
    }

    // ───────────────────────── Affichage ─────────────────────────

    public void render() {
        Configs cfg = plugin.configs();
        HotelManager hotels = plugin.hotels();
        HotelData d = plugin.data().get(islandId);
        Player viewer = Bukkit.getPlayer(playerId);
        ConfigurationSection m = cfg.menus().getConfigurationSection("main");
        if (d == null || viewer == null || m == null) return;

        inventory.clear();
        requirementSlots.clear();
        actions.clear();
        int size = inventory.getSize();
        int max = cfg.maxLevel();

        // Remplissage
        ConfigurationSection filler = m.getConfigurationSection("filler");
        if (filler != null && filler.getBoolean("enabled", true)) {
            ItemStack f = Items.build(filler);
            for (int i = 0; i < size; i++) inventory.setItem(i, f.clone());
        }

        // Placeholders communs
        int next = Math.min(d.level + 1, max);
        boolean atMax = d.level >= max;
        String maxText = cfg.raw("max");
        Inventory storage = hotels.storage(d);
        String[] kv = new String[]{
                "level", String.valueOf(d.level),
                "max", String.valueOf(max),
                "next_level", atMax ? maxText : String.valueOf(next),
                "speed", cfg.factorFormatted("speed", d.level),
                "speed_next", atMax ? maxText : cfg.factorFormatted("speed", next),
                "storage", cfg.factorFormatted("storage", d.level),
                "storage_next", atMax ? maxText : cfg.factorFormatted("storage", next),
                "money", cfg.factorFormatted("money", d.level),
                "money_next", atMax ? maxText : cfg.factorFormatted("money", next),
                "xp", cfg.factorFormatted("xp", d.level),
                "xp_next", atMax ? maxText : cfg.factorFormatted("xp", next),
                "used", String.valueOf(hotels.usedSlots(storage)),
                "capacity", String.valueOf(storage.getSize())
        };

        // Items fixes (info, stats, storage, close, ...)
        ConfigurationSection items = m.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= size) continue;
                inventory.setItem(slot, Items.build(s, kv));
                String def = key.equalsIgnoreCase("storage") ? "STORAGE" : key.equalsIgnoreCase("close") ? "CLOSE" : "NONE";
                actions.put(slot, s.getString("action", def).toUpperCase());
            }
        }

        // Zone centrale : amélioration en cours / niveau max / ressources
        if (d.isUpgrading()) {
            ConfigurationSection u = m.getConfigurationSection("upgrading");
            if (u != null) {
                long total = Math.max(1, d.upgradeEnd - d.upgradeStart);
                long left = Math.max(0, d.upgradeEnd - System.currentTimeMillis());
                double ratio = 1.0 - ((double) left / total);
                int percent = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 100);
                String[] ukv = append(kv,
                        "target_level", String.valueOf(d.upgradeTarget),
                        "time_left", Text.formatDuration((left + 999) / 1000),
                        "progress", bar(m, ratio),
                        "percent", String.valueOf(percent));
                put(u.getInt("slot", 22), Items.build(u, ukv));
            }
        } else if (atMax) {
            ConfigurationSection mx = m.getConfigurationSection("max-level");
            if (mx != null) put(mx.getInt("slot", 22), Items.build(mx, kv));
        } else {
            LevelDef def = cfg.level(d.level + 1);
            if (def != null) {
                List<Integer> auto = m.getIntegerList("requirement-slots");
                int autoIdx = 0;
                for (Requirement r : def.requirements()) {
                    int slot;
                    if (r.slot >= 0) {
                        slot = r.slot;
                    } else if (autoIdx < auto.size()) {
                        slot = auto.get(autoIdx++);
                    } else {
                        continue;
                    }
                    if (slot < 0 || slot >= size) continue;
                    inventory.setItem(slot, requirementItem(viewer, d, r));
                    requirementSlots.put(slot, r);
                }
            }
        }
    }

    private void put(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    private static String[] append(String[] base, String... extra) {
        String[] out = new String[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }

    private String bar(ConfigurationSection m, double ratio) {
        int length = Math.max(1, m.getInt("progress-bar.length", 20));
        String full = m.getString("progress-bar.full", "&a|");
        String empty = m.getString("progress-bar.empty", "&7|");
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(i < filled ? full : empty);
        return sb.toString();
    }

    private ItemStack requirementItem(Player viewer, HotelData d, Requirement r) {
        ConfigurationSection rc = plugin.configs().menus().getConfigurationSection("main.requirement");
        boolean done = d.validated.contains(r.id);

        String iconSpec = r.section.getString("material", null);
        if (iconSpec == null) {
            iconSpec = r.type == Requirement.Type.ITEM
                    ? r.item
                    : (rc == null ? "PAPER" : rc.getString("icons." + r.type.name(), "PAPER"));
        }
        int amount = r.type == Requirement.Type.ITEM ? (int) Math.max(1, Math.min(64, r.amount)) : 1;
        ItemStack item = Items.parse(iconSpec, amount);
        if (item == null) item = new ItemStack(Material.PAPER);

        double have = plugin.hotels().have(viewer, r);
        String[] kv = new String[]{
                "display", plugin.hotels().requirementDisplay(r),
                "amount", Text.number(r.amount),
                "have", Text.number(have),
                "have_color", have >= r.amount ? "&a" : "&c",
                "item", Items.displayName(r.item)
        };

        ConfigurationSection pending = rc == null ? null : rc.getConfigurationSection("pending");
        ConfigurationSection validated = rc == null ? null : rc.getConfigurationSection("validated");

        if (done) {
            String mat = validated == null ? "" : validated.getString("material", "");
            if (!mat.isBlank()) {
                ItemStack alt = Items.parse(mat, 1);
                if (alt != null) item = alt;
            }
            String name = r.section.getString("validated-name", validated == null ? "&a✔ {display}" : validated.getString("name", "&a✔ {display}"));
            List<String> lore = validated == null ? List.of("&aRessource validée !") : validated.getStringList("lore");
            Items.apply(item, name, lore, validated == null || validated.getBoolean("glow", true), kv);
        } else {
            String name = r.section.getString("name", pending == null ? "&c✘ {display}" : pending.getString("name", "&c✘ {display}"));
            List<String> lore = r.section.isList("lore")
                    ? r.section.getStringList("lore")
                    : (pending == null ? List.of("&eClic pour valider") : pending.getStringList("lore"));
            Items.apply(item, name, lore, false, kv);
        }
        return item;
    }

    // ───────────────────────── Clics ─────────────────────────

    public void click(Player player, int slot) {
        HotelData d = plugin.data().get(islandId);
        if (d == null) {
            player.closeInventory();
            return;
        }
        Requirement r = requirementSlots.get(slot);
        if (r != null) {
            plugin.hotels().clickRequirement(player, d, r);
            render();
            return;
        }
        String action = actions.get(slot);
        if (action == null) return;
        switch (action) {
            case "STORAGE" -> player.openInventory(plugin.hotels().storage(d));
            case "CLOSE" -> player.closeInventory();
            default -> {
                // aucune action
            }
        }
    }
}
