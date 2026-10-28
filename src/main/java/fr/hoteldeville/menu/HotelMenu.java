package fr.hoteldeville.menu;

import com.bgsoftware.superiorskyblock.api.island.Island;
import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.data.HotelData;
import fr.hoteldeville.manager.Configs;
import fr.hoteldeville.manager.HotelManager;
import fr.hoteldeville.model.LevelDef;
import fr.hoteldeville.model.Requirement;
import fr.hoteldeville.util.Effects;
import fr.hoteldeville.util.Items;
import fr.hoteldeville.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Menu /hotel. Tout est défini dans menus.yml : chaque entrée de "main.items" est un item libre
 * (slot ou slots, material, name, lore, glow, action, commands, click-sound, states).
 * Supprimer une entrée la retire du menu.
 */
public final class HotelMenu implements InventoryHolder {

    /** Une zone cliquable du menu. */
    private record Button(String action, List<String> commands, String sound) {
    }

    private final HotelDeVillePlugin plugin;
    private final UUID playerId;
    private final UUID islandId;
    private final int level;
    private Inventory inventory;

    private final Map<Integer, Requirement> requirementSlots = new HashMap<>();
    private final Map<Integer, Button> buttons = new HashMap<>();

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
        buttons.clear();
        int size = inventory.getSize();
        int max = cfg.maxLevel();

        // Remplissage (facultatif)
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
        String state = hotels.minionState(d);
        String statusText = m.getString("status." + state, state);
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
                "capacity", String.valueOf(storage.getSize()),
                "status", statusText,
                "minion_state", state
        };

        // Items libres définis dans menus.yml
        ConfigurationSection items = m.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                List<Integer> slots = new ArrayList<>();
                if (s.isList("slots")) slots.addAll(s.getIntegerList("slots"));
                if (s.contains("slot")) slots.add(s.getInt("slot"));
                if (slots.isEmpty()) continue;

                String action = s.getString("action", defaultAction(key)).toUpperCase(Locale.ROOT);
                ConfigurationSection override = s.getConfigurationSection("states." + stateFor(action, state));
                ItemStack item = Items.build(s, override, kv);
                Button button = new Button(action, s.getStringList("commands"), s.getString("click-sound", ""));
                for (int slot : slots) {
                    if (slot < 0 || slot >= size) continue;
                    inventory.setItem(slot, item.clone());
                    buttons.put(slot, button);
                }
            }
        }

        // Zone des ressources : amélioration en cours / niveau max / ressources à valider
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
                put(u, Items.build(u, ukv));
            }
        } else if (atMax) {
            ConfigurationSection mx = m.getConfigurationSection("max-level");
            if (mx != null) put(mx, Items.build(mx, kv));
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
                    buttons.remove(slot);
                    requirementSlots.put(slot, r);
                }
            }
        }
    }

    private static String defaultAction(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "storage" -> "STORAGE";
            case "close" -> "CLOSE";
            case "pickup", "move", "deplacer" -> "PICKUP";
            case "toggle", "minion", "start", "power" -> "TOGGLE_MINION";
            default -> "NONE";
        };
    }

    /** Clé "states.<x>" à appliquer pour une action donnée. */
    private static String stateFor(String action, String minionState) {
        return action.equals("TOGGLE_MINION") ? minionState : "none";
    }

    private void put(ConfigurationSection sec, ItemStack item) {
        List<Integer> slots = new ArrayList<>(sec.getIntegerList("slots"));
        if (sec.contains("slot")) slots.add(sec.getInt("slot"));
        if (slots.isEmpty()) slots.add(22);
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item.clone());
                buttons.remove(slot);
            }
        }
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
        Button button = buttons.get(slot);
        if (button == null) return;

        if (!button.sound().isBlank()) Effects.play(player, button.sound());

        switch (button.action()) {
            case "STORAGE" -> player.openInventory(plugin.hotels().storage(d));
            case "CLOSE" -> player.closeInventory();
            case "PICKUP" -> plugin.hotels().pickupHotel(player, d);
            case "TOGGLE_MINION" -> {
                plugin.hotels().toggleMinion(player, d);
                render();
            }
            default -> {
                // NONE : aucune action intégrée
            }
        }
        runCommands(player, button.commands());
    }

    /** Commandes de l'item : "[player] cmd", "[console] cmd", "[message] texte", "[close]". {player} disponible. */
    private void runCommands(Player player, List<String> commands) {
        for (String raw : commands) {
            String line = raw.trim().replace("{player}", player.getName());
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("[player]")) {
                Bukkit.dispatchCommand(player, line.substring(8).trim().replaceFirst("^/", ""));
            } else if (lower.startsWith("[console]")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.substring(9).trim().replaceFirst("^/", ""));
            } else if (lower.startsWith("[message]")) {
                player.sendMessage(Text.color(line.substring(9).trim()));
            } else if (lower.startsWith("[close]")) {
                player.closeInventory();
            }
        }
    }
}
