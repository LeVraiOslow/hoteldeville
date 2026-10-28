package fr.hoteldeville.data;

import fr.hoteldeville.HotelDeVillePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistance des hôtels dans data.yml. */
public class DataManager {

    private final HotelDeVillePlugin plugin;
    private final File file;
    private final Map<UUID, HotelData> hotels = new HashMap<>();
    private boolean dirty = false;

    public DataManager(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public Map<UUID, HotelData> all() {
        return hotels;
    }

    public HotelData get(UUID island) {
        return hotels.get(island);
    }

    public void put(HotelData data) {
        hotels.put(data.islandId, data);
        dirty = true;
    }

    public void remove(UUID island) {
        hotels.remove(island);
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    public void load() {
        hotels.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("hotels");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            HotelData d = new HotelData(id);
            d.world = s.getString("world");
            d.pasteX = s.getInt("paste.x");
            d.pasteY = s.getInt("paste.y");
            d.pasteZ = s.getInt("paste.z");
            d.minX = s.getInt("min.x");
            d.minY = s.getInt("min.y");
            d.minZ = s.getInt("min.z");
            d.maxX = s.getInt("max.x");
            d.maxY = s.getInt("max.y");
            d.maxZ = s.getInt("max.z");
            d.level = Math.max(1, s.getInt("level", 1));
            d.active = s.getBoolean("active", false);
            d.upgradeEnd = s.getLong("upgrade.end", 0);
            d.upgradeStart = s.getLong("upgrade.start", 0);
            d.upgradeTarget = s.getInt("upgrade.target", 0);
            d.validated.addAll(s.getStringList("validated"));
            List<?> raw = s.getList("storage");
            if (raw != null) {
                for (Object o : raw) d.storage.add(o instanceof ItemStack is ? is : null);
            }
            hotels.put(id, d);
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (HotelData d : hotels.values()) {
            d.syncStorage();
            String p = "hotels." + d.islandId + ".";
            yaml.set(p + "world", d.world);
            yaml.set(p + "paste.x", d.pasteX);
            yaml.set(p + "paste.y", d.pasteY);
            yaml.set(p + "paste.z", d.pasteZ);
            yaml.set(p + "min.x", d.minX);
            yaml.set(p + "min.y", d.minY);
            yaml.set(p + "min.z", d.minZ);
            yaml.set(p + "max.x", d.maxX);
            yaml.set(p + "max.y", d.maxY);
            yaml.set(p + "max.z", d.maxZ);
            yaml.set(p + "level", d.level);
            yaml.set(p + "active", d.active);
            yaml.set(p + "upgrade.end", d.upgradeEnd);
            yaml.set(p + "upgrade.start", d.upgradeStart);
            yaml.set(p + "upgrade.target", d.upgradeTarget);
            yaml.set(p + "validated", new ArrayList<>(d.validated));
            yaml.set(p + "storage", d.storage);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder data.yml : " + e.getMessage());
        }
    }
}
