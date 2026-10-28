package fr.hoteldeville.manager;

import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.model.LevelDef;
import fr.hoteldeville.model.Requirement;
import fr.hoteldeville.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Chargement de config.yml, levels.yml, menus.yml et messages.yml. */
public class Configs {

    private final HotelDeVillePlugin plugin;
    private FileConfiguration config;
    private FileConfiguration menus;
    private FileConfiguration messages;
    private FileConfiguration levelsFile;
    private final Map<Integer, LevelDef> levels = new HashMap<>();
    private int maxLevel = 12;

    public Configs(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        // config.yml et messages.yml : les clés absentes reprennent la valeur par défaut du plugin.
        // levels.yml et menus.yml : AUCUNE valeur par défaut, tout ce que vous supprimez reste supprimé.
        config = loadFile("config.yml", true);
        levelsFile = loadFile("levels.yml", false);
        menus = loadFile("menus.yml", false);
        messages = loadFile("messages.yml", true);

        maxLevel = Math.max(1, config.getInt("settings.max-level", 12));
        Text.setUnits(
                config.getString("settings.time-units.day", "j"),
                config.getString("settings.time-units.hour", "h"),
                config.getString("settings.time-units.minute", "min"),
                config.getString("settings.time-units.second", "s"));
        Text.setNumberFormat(config.getString("settings.number-format", "#,##0.##"));
        loadLevels();
    }

    private FileConfiguration loadFile(String name, boolean withDefaults) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (withDefaults) {
            try (InputStreamReader reader = new InputStreamReader(plugin.getResource(name), StandardCharsets.UTF_8)) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (Exception ignored) {
                // pas de valeurs par défaut disponibles
            }
        }
        return yaml;
    }

    private void loadLevels() {
        levels.clear();
        ConfigurationSection root = levelsFile.getConfigurationSection("levels");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            int lvl;
            try {
                lvl = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            long time = Text.parseSeconds(sec.getString("time", "0"));
            List<Requirement> reqs = new ArrayList<>();
            ConfigurationSection rs = sec.getConfigurationSection("requirements");
            if (rs != null) {
                for (String rid : rs.getKeys(false)) {
                    ConfigurationSection r = rs.getConfigurationSection(rid);
                    if (r == null) continue;
                    Requirement.Type type;
                    try {
                        type = Requirement.Type.valueOf(r.getString("type", "ITEM").toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Type de ressource invalide pour niveau " + lvl + " / " + rid);
                        continue;
                    }
                    boolean xpLevels = !"POINTS".equalsIgnoreCase(r.getString("mode", "LEVELS"));
                    reqs.add(new Requirement(rid, type, r.getString("item", ""),
                            r.getDouble("amount", 1), r.getInt("slot", -1), xpLevels, r));
                }
            }
            levels.put(lvl, new LevelDef(lvl, time, reqs));
        }
    }

    // ───────────── accès ─────────────

    public FileConfiguration config() { return config; }
    public FileConfiguration menus() { return menus; }
    public int maxLevel() { return maxLevel; }

    /** Définition pour ATTEINDRE ce niveau (null si inexistante). */
    public LevelDef level(int level) { return levels.get(level); }

    // ───────────── messages ─────────────

    public String raw(String path, String... kv) {
        String s = messages.getString(path);
        if (s == null) return "&c[message manquant: " + path + "]";
        return Text.rep(s, kv);
    }

    public Component message(String path, String... kv) {
        return Text.color(messages.getString("prefix", "") + raw(path, kv));
    }

    public void send(CommandSender to, String path, String... kv) {
        if (to != null) to.sendMessage(message(path, kv));
    }

    public List<String> rawList(String path) {
        return messages.getStringList(path);
    }

    // ───────────── facteurs du minion ─────────────

    public double factor(String factor, int level) {
        ConfigurationSection levelsSec = config.getConfigurationSection("minion.factors." + factor + ".levels");
        if (levelsSec == null) return 0;
        for (int l = level; l >= 1; l--) {
            if (levelsSec.contains(String.valueOf(l))) return levelsSec.getDouble(String.valueOf(l));
        }
        return 0;
    }

    public String factorFormatted(String factor, int level) {
        String format = config.getString("minion.factors." + factor + ".format", "{value}");
        return Text.rep(format, "value", Text.number(factor(factor, level)));
    }

    public String factorName(String factor) {
        return config.getString("minion.factors." + factor + ".display-name", factor);
    }

    /** Nombre de slots du stockage : arrondi au multiple de 9, entre 9 et 54. */
    public int storageSlots(int level) {
        int v = (int) Math.round(factor("storage", level));
        v = ((Math.max(v, 1) + 8) / 9) * 9;
        return Math.max(9, Math.min(54, v));
    }
}
