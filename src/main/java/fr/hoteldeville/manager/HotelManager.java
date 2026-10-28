package fr.hoteldeville.manager;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.data.DataManager;
import fr.hoteldeville.data.HotelData;
import fr.hoteldeville.menu.StorageHolder;
import fr.hoteldeville.model.LevelDef;
import fr.hoteldeville.model.Requirement;
import fr.hoteldeville.util.Effects;
import fr.hoteldeville.util.Items;
import fr.hoteldeville.util.Text;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Logique principale : placement, améliorations, stockage et minions. */
public class HotelManager {

    private final HotelDeVillePlugin plugin;
    private final Configs cfg;
    private final DataManager data;

    public final NamespacedKey shulkerKey;
    public final NamespacedKey minionKey;

    private SchematicManager schematics;
    private Statistic playStat;

    public HotelManager(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.configs();
        this.data = plugin.data();
        this.shulkerKey = new NamespacedKey(plugin, "hotel_shulker");
        this.minionKey = new NamespacedKey(plugin, "hotel_minion");
        for (String n : new String[]{"PLAY_ONE_MINUTE", "PLAY_TIME"}) {
            try {
                playStat = Statistic.valueOf(n);
                break;
            } catch (IllegalArgumentException ignored) {
                // essai suivant
            }
        }
    }

    // ───────────────────────── Îles ─────────────────────────

    public Island islandOf(Player p) {
        SuperiorPlayer sp = SuperiorSkyblockAPI.getPlayer(p.getUniqueId());
        return sp == null ? null : sp.getIsland();
    }

    public Island islandById(UUID id) {
        return SuperiorSkyblockAPI.getGrid().getIslandByUUID(id);
    }

    public HotelData hotelOf(Island island) {
        return island == null ? null : data.get(island.getUniqueId());
    }

    public void forEachOnlineMember(Island island, Consumer<Player> action) {
        if (island == null) return;
        for (SuperiorPlayer m : island.getIslandMembers(true)) {
            Player p = m.asPlayer();
            if (p != null && p.isOnline()) action.accept(p);
        }
    }

    public void broadcast(Island island, String path, String... kv) {
        forEachOnlineMember(island, p -> cfg.send(p, path, kv));
    }

    private Location oneBlockLocation(Island island) {
        World.Environment env;
        try {
            env = World.Environment.valueOf(cfg.config().getString("oneblock.environment", "NORMAL").toUpperCase());
        } catch (IllegalArgumentException e) {
            env = World.Environment.NORMAL;
        }
        Location center;
        try {
            center = island.getCenter(env);
        } catch (Exception e) {
            return null;
        }
        if (center == null || center.getWorld() == null) return null;
        ConfigurationSection o = cfg.config().getConfigurationSection("oneblock.offset");
        int ox = o == null ? 0 : o.getInt("x"), oy = o == null ? 0 : o.getInt("y"), oz = o == null ? 0 : o.getInt("z");
        return new Location(center.getWorld(), center.getBlockX() + ox, center.getBlockY() + oy, center.getBlockZ() + oz);
    }

    // ───────────────────────── Shulker ─────────────────────────

    public ItemStack createShulker() {
        ConfigurationSection s = cfg.config().getConfigurationSection("shulker");
        ItemStack item;
        if (s == null) {
            item = new ItemStack(Material.PURPLE_SHULKER_BOX);
        } else {
            item = Items.build(s);
            if (item.getType() == Material.BARRIER) item = new ItemStack(Material.PURPLE_SHULKER_BOX);
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(shulkerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHotelShulker(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(shulkerKey, PersistentDataType.BYTE);
    }

    public void giveShulker(Player p) {
        ItemStack shulker = createShulker();
        var leftover = p.getInventory().addItem(shulker);
        for (ItemStack extra : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), extra);
    }

    // ───────────────────────── Placement ─────────────────────────

    /** Appelé quand un joueur pose la shulker. Gère tout (messages inclus). */
    public void placeHotel(Player player, Block block, EquipmentSlot hand) {
        Island island = islandOf(player);
        if (island == null) {
            cfg.send(player, "general.no-island");
            return;
        }
        Location loc = block.getLocation();
        Island at = SuperiorSkyblockAPI.getGrid().getIslandAt(loc);
        if (at == null) {
            cfg.send(player, "shulker.not-in-island");
            return;
        }
        if (!at.getUniqueId().equals(island.getUniqueId())) {
            cfg.send(player, "shulker.not-your-island");
            return;
        }
        if (data.get(island.getUniqueId()) != null) {
            cfg.send(player, "shulker.already-built");
            return;
        }
        if (!worldEditAvailable()) {
            cfg.send(player, "shulker.worldedit-missing");
            return;
        }
        String fileName = cfg.config().getString("hotel.schematic", "hotel.schem");
        File file = new File(plugin.getDataFolder(), "schematics" + File.separator + fileName);
        if (!file.exists()) {
            cfg.send(player, "shulker.schematic-missing", "file", fileName);
            return;
        }

        ConfigurationSection po = cfg.config().getConfigurationSection("hotel.paste-offset");
        int px = loc.getBlockX() + (po == null ? 0 : po.getInt("x"));
        int py = loc.getBlockY() + (po == null ? 0 : po.getInt("y"));
        int pz = loc.getBlockZ() + (po == null ? 0 : po.getInt("z"));

        try {
            if (schematics == null) schematics = new SchematicManager();
            Clipboard clipboard = schematics.load(file);
            int[] b = schematics.bounds(clipboard, px, py, pz);

            if (cfg.config().getBoolean("hotel.check-inside-island", true)) {
                World w = loc.getWorld();
                int[] xs = {b[0], b[3]}, ys = {b[1], b[4]}, zs = {b[2], b[5]};
                for (int x : xs) for (int y : ys) for (int z : zs) {
                    if (!island.isInside(new Location(w, x, y, z))) {
                        cfg.send(player, "shulker.out-of-bounds");
                        return;
                    }
                }
            }

            schematics.paste(clipboard, loc.getWorld(), px, py, pz, cfg.config().getBoolean("hotel.ignore-air", false));

            HotelData d = new HotelData(island.getUniqueId());
            d.world = loc.getWorld().getName();
            d.pasteX = px;
            d.pasteY = py;
            d.pasteZ = pz;
            d.minX = b[0];
            d.minY = b[1];
            d.minZ = b[2];
            d.maxX = b[3];
            d.maxY = b[4];
            d.maxZ = b[5];
            ConfigurationSection so = cfg.config().getConfigurationSection("minion.spawn-offset");
            d.minionX = loc.getBlockX() + (so == null ? 0.5 : so.getDouble("x", 0.5));
            d.minionY = loc.getBlockY() + (so == null ? 1.0 : so.getDouble("y", 1.0));
            d.minionZ = loc.getBlockZ() + (so == null ? 0.5 : so.getDouble("z", 0.5));
            d.level = 1;
            data.put(d);
            data.save();

            // Consomme la shulker seulement en cas de succès
            ItemStack inHand = player.getInventory().getItem(hand);
            if (inHand != null) {
                inHand.setAmount(inHand.getAmount() - 1);
                player.getInventory().setItem(hand, inHand.getAmount() <= 0 ? null : inHand);
            }

            ensureMinion(d);
            Effects.play(loc, cfg.config().getString("hotel.sound-build", ""), 1f);
            cfg.send(player, "shulker.built");
            forEachOnlineMember(island, m -> {
                if (!m.getUniqueId().equals(player.getUniqueId())) {
                    cfg.send(m, "shulker.built-members", "player", player.getName());
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().severe("Échec du collage de l'hôtel de ville : " + t);
            t.printStackTrace();
            cfg.send(player, "shulker.paste-failed");
        }
    }

    public boolean worldEditAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    // ───────────────────────── Réinitialisation ─────────────────────────

    /** Supprime l'hôtel (structure, minion, stockage, données) d'une île. */
    public void resetHotel(UUID islandId) {
        HotelData d = data.get(islandId);
        if (d == null) return;
        if (d.inventory != null) {
            for (HumanEntity v : new ArrayList<>(d.inventory.getViewers())) v.closeInventory();
        }
        removeMinion(d);
        World w = d.world == null ? null : Bukkit.getWorld(d.world);
        if (w != null) {
            for (int x = d.minX; x <= d.maxX; x++) {
                for (int y = d.minY; y <= d.maxY; y++) {
                    for (int z = d.minZ; z <= d.maxZ; z++) {
                        Block b = w.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
                }
            }
        }
        data.remove(islandId);
        data.save();
    }

    /** Supprime les hôtels dont l'île n'existe plus (à appeler après le chargement de SuperiorSkyblock2). */
    public void cleanOrphans() {
        for (UUID id : new ArrayList<>(data.all().keySet())) {
            if (islandById(id) == null) {
                plugin.getLogger().info("Île " + id + " introuvable : hôtel de ville réinitialisé.");
                resetHotel(id);
            }
        }
    }

    // ───────────────────────── Ressources / améliorations ─────────────────────────

    public int playMinutes(Player p) {
        if (playStat == null) return 0;
        return (int) (p.getStatistic(playStat) / 1200L);
    }

    private int countItems(Player p, String spec) {
        int c = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (Items.matches(s, spec)) c += s.getAmount();
        }
        return c;
    }

    private void removeItems(Player p, String spec, int amount) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (!Items.matches(s, spec)) continue;
            int take = Math.min(left, s.getAmount());
            s.setAmount(s.getAmount() - take);
            if (s.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        p.getInventory().setStorageContents(contents);
    }

    /** Quantité que possède le joueur pour cette ressource. */
    public double have(Player p, Requirement r) {
        switch (r.type) {
            case ITEM:
                return countItems(p, r.item);
            case MONEY: {
                Economy eco = plugin.economy();
                return eco == null ? 0 : eco.getBalance(p);
            }
            case XP:
                return r.xpLevels ? p.getLevel() : p.calculateTotalExperiencePoints();
            case PLAYTIME:
                return playMinutes(p);
            default:
                return 0;
        }
    }

    public String requirementDisplay(Requirement r) {
        ConfigurationSection names = cfg.menus().getConfigurationSection("main.requirement.display-names");
        String key = r.type.name();
        if (r.type == Requirement.Type.XP && !r.xpLevels) key = "XP_POINTS";
        String template = names == null ? "{amount}" : names.getString(key, "{amount}");
        return Text.rep(template, "amount", Text.number(r.amount), "item", Items.displayName(r.item));
    }

    /** Clic sur une ressource dans le menu. */
    public void clickRequirement(Player p, HotelData d, Requirement r) {
        Island island = islandById(d.islandId);
        if (d.isUpgrading()) {
            cfg.send(p, "hotel.upgrading-wait");
            return;
        }
        if (d.level >= cfg.maxLevel()) {
            cfg.send(p, "hotel.max-level");
            return;
        }
        if (d.validated.contains(r.id)) {
            cfg.send(p, "hotel.requirement-already");
            return;
        }

        double have = have(p, r);
        double missing = r.amount - have;
        switch (r.type) {
            case ITEM -> {
                if (have < r.amount) {
                    cfg.send(p, "hotel.missing-item", "missing", Text.number(missing), "item", Items.displayName(r.item));
                    return;
                }
                removeItems(p, r.item, (int) r.amount);
            }
            case MONEY -> {
                Economy eco = plugin.economy();
                if (eco == null) {
                    cfg.send(p, "hotel.economy-error");
                    return;
                }
                if (have < r.amount) {
                    cfg.send(p, "hotel.missing-money", "missing", Text.number(missing));
                    return;
                }
                EconomyResponse resp = eco.withdrawPlayer(p, r.amount);
                if (!resp.transactionSuccess()) {
                    cfg.send(p, "hotel.economy-error");
                    return;
                }
            }
            case XP -> {
                if (have < r.amount) {
                    cfg.send(p, r.xpLevels ? "hotel.missing-xp" : "hotel.missing-xp-points", "missing", Text.number(missing));
                    return;
                }
                if (r.xpLevels) p.giveExpLevels(-(int) r.amount);
                else p.setExperienceLevelAndProgress(p.calculateTotalExperiencePoints() - (int) r.amount);
            }
            case PLAYTIME -> {
                if (have < r.amount) {
                    cfg.send(p, "hotel.missing-playtime", "missing", Text.number(missing));
                    return;
                }
            }
        }

        d.validated.add(r.id);
        data.markDirty();
        cfg.send(p, "hotel.requirement-validated", "display", requirementDisplay(r));
        tryStartUpgrade(d, p, island);
    }

    /** Lance l'amélioration si toutes les ressources du niveau suivant sont validées. */
    public boolean tryStartUpgrade(HotelData d, Player starter, Island island) {
        if (d.isUpgrading() || d.level >= cfg.maxLevel()) return false;
        int next = d.level + 1;
        LevelDef def = cfg.level(next);
        if (def == null) return false;
        for (Requirement r : def.requirements()) {
            if (!d.validated.contains(r.id)) return false;
        }
        d.validated.clear();
        data.markDirty();
        if (def.timeSeconds() <= 0) {
            d.upgradeTarget = next;
            finishUpgrade(d);
            return true;
        }
        long now = System.currentTimeMillis();
        d.upgradeStart = now;
        d.upgradeEnd = now + def.timeSeconds() * 1000L;
        d.upgradeTarget = next;
        data.save();
        if (starter != null) {
            cfg.send(starter, "hotel.upgrade-started", "level", String.valueOf(next), "time", Text.formatDuration(def.timeSeconds()));
        }
        if (island != null) {
            forEachOnlineMember(island, m -> {
                if (starter == null || !m.getUniqueId().equals(starter.getUniqueId())) {
                    cfg.send(m, "hotel.upgrade-started-members",
                            "player", starter == null ? "?" : starter.getName(), "level", String.valueOf(next));
                }
            });
        }
        return true;
    }

    public void tickUpgrades() {
        long now = System.currentTimeMillis();
        for (HotelData d : new ArrayList<>(data.all().values())) {
            if (d.isUpgrading() && now >= d.upgradeEnd) finishUpgrade(d);
        }
    }

    public void finishUpgrade(HotelData d) {
        if (d.upgradeTarget > 0) d.level = Math.min(d.upgradeTarget, cfg.maxLevel());
        d.upgradeEnd = 0;
        d.upgradeStart = 0;
        d.upgradeTarget = 0;
        d.validated.clear();
        applyLevelChange(d);
        data.save();
        Island island = islandById(d.islandId);
        forEachOnlineMember(island, m -> {
            cfg.send(m, "hotel.upgrade-finished", "level", String.valueOf(d.level));
            Effects.play(m, cfg.config().getString("hotel.sound-upgrade", ""));
        });
    }

    /** Réapplique les effets d'un niveau : stockage redimensionné, nom du minion. */
    public void applyLevelChange(HotelData d) {
        storage(d);
        Entity e = d.minionUuid == null ? null : Bukkit.getEntity(d.minionUuid);
        if (e instanceof ArmorStand a) updateMinionName(d, a);
        data.markDirty();
    }

    public void setLevel(HotelData d, int level) {
        d.level = Math.max(1, Math.min(level, cfg.maxLevel()));
        d.upgradeEnd = 0;
        d.upgradeStart = 0;
        d.upgradeTarget = 0;
        d.validated.clear();
        applyLevelChange(d);
        data.save();
    }

    // ───────────────────────── Stockage ─────────────────────────

    public Inventory storage(HotelData d) {
        int capacity = cfg.storageSlots(d.level);
        if (d.inventory == null || d.inventory.getSize() != capacity) {
            ItemStack[] contents;
            if (d.inventory != null) {
                for (HumanEntity v : new ArrayList<>(d.inventory.getViewers())) v.closeInventory();
                contents = d.inventory.getContents();
            } else {
                contents = d.storage.toArray(new ItemStack[0]);
            }
            String title = cfg.menus().getString("storage.title", "&8Stockage du minion");
            title = Text.rep(title, "level", String.valueOf(d.level), "capacity", String.valueOf(capacity));
            Inventory inv = Bukkit.createInventory(new StorageHolder(d.islandId), capacity, Text.color(title));
            for (int i = 0; i < contents.length; i++) {
                ItemStack it = contents[i];
                if (it == null || it.getType().isAir()) continue;
                if (i < capacity && inv.getItem(i) == null) {
                    inv.setItem(i, it);
                } else {
                    inv.addItem(it);
                }
            }
            d.inventory = inv;
        }
        return d.inventory;
    }

    public int usedSlots(Inventory inv) {
        int used = 0;
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir()) used++;
        }
        return used;
    }

    private boolean hasRoom(Inventory inv) {
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir() || it.getAmount() < it.getMaxStackSize()) return true;
        }
        return false;
    }

    // ───────────────────────── Minion : entité ─────────────────────────

    private ArmorStand findMinion(HotelData d, World w, int cx, int cz) {
        ArmorStand found = null;
        if (d.minionUuid != null) {
            Entity e = Bukkit.getEntity(d.minionUuid);
            if (e instanceof ArmorStand a && a.isValid()) found = a;
        }
        if (found == null) {
            String id = d.islandId.toString();
            for (Entity e : w.getChunkAt(cx, cz).getEntities()) {
                if (e instanceof ArmorStand a
                        && id.equals(a.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING))) {
                    if (found == null) found = a;
                    else a.remove();
                }
            }
        }
        return found;
    }

    public void ensureMinion(HotelData d) {
        if (!cfg.config().getBoolean("minion.enabled", true) || d.world == null) return;
        World w = Bukkit.getWorld(d.world);
        if (w == null) return;
        int cx = ((int) Math.floor(d.minionX)) >> 4;
        int cz = ((int) Math.floor(d.minionZ)) >> 4;
        if (!w.isChunkLoaded(cx, cz)) return;

        ArmorStand stand = findMinion(d, w, cx, cz);
        if (stand == null) {
            ConfigurationSection mc = cfg.config().getConfigurationSection("minion");
            Location l = new Location(w, d.minionX, d.minionY, d.minionZ);
            stand = w.spawn(l, ArmorStand.class, as -> {
                as.setInvulnerable(true);
                as.setGravity(false);
                as.setPersistent(true);
                as.setRemoveWhenFarAway(false);
                as.setCanPickupItems(false);
                as.setArms(mc == null || mc.getBoolean("arms", true));
                as.setSmall(mc != null && mc.getBoolean("small", false));
                as.setBasePlate(mc != null && mc.getBoolean("base-plate", false));
                as.getPersistentDataContainer().set(minionKey, PersistentDataType.STRING, d.islandId.toString());
                if (mc != null) {
                    ConfigurationSection eq = mc.getConfigurationSection("equipment");
                    if (eq != null) {
                        setSlot(as, EquipmentSlot.HEAD, eq.getString("helmet", ""));
                        setSlot(as, EquipmentSlot.CHEST, eq.getString("chestplate", ""));
                        setSlot(as, EquipmentSlot.LEGS, eq.getString("leggings", ""));
                        setSlot(as, EquipmentSlot.FEET, eq.getString("boots", ""));
                        setSlot(as, EquipmentSlot.HAND, eq.getString("hand", ""));
                    }
                }
            });
        }
        d.minionUuid = stand.getUniqueId();
        updateMinionName(d, stand);
    }

    private void setSlot(ArmorStand as, EquipmentSlot slot, String spec) {
        ItemStack item = Items.parse(spec, 1);
        if (item != null) as.getEquipment().setItem(slot, item);
    }

    private void updateMinionName(HotelData d, ArmorStand stand) {
        boolean show = cfg.config().getBoolean("minion.show-name", true);
        String name = cfg.config().getString("minion.name", "&6Minion");
        stand.customName(Text.color(Text.rep(name, "level", String.valueOf(d.level))));
        stand.setCustomNameVisible(show);
    }

    public void removeMinion(HotelData d) {
        if (d.world == null) return;
        World w = Bukkit.getWorld(d.world);
        if (w == null) return;
        int cx = ((int) Math.floor(d.minionX)) >> 4;
        int cz = ((int) Math.floor(d.minionZ)) >> 4;
        if (!w.isChunkLoaded(cx, cz)) w.getChunkAt(cx, cz);
        String id = d.islandId.toString();
        for (Entity e : w.getChunkAt(cx, cz).getEntities()) {
            if (e instanceof ArmorStand a
                    && id.equals(a.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING))) {
                a.remove();
            }
        }
        if (d.minionUuid != null) {
            Entity e = Bukkit.getEntity(d.minionUuid);
            if (e != null) e.remove();
        }
        d.minionUuid = null;
    }

    public void ensureAllMinions() {
        for (HotelData d : data.all().values()) ensureMinion(d);
    }

    // ───────────────────────── Minion : minage ─────────────────────────

    public void tickMinions() {
        if (!cfg.config().getBoolean("minion.enabled", true)) return;
        long now = System.currentTimeMillis();
        for (HotelData d : data.all().values()) {
            if (now < d.nextBreak) continue;
            try {
                mine(d, now);
            } catch (Throwable t) {
                plugin.getLogger().warning("Erreur minion (île " + d.islandId + ") : " + t);
                d.nextBreak = now + 5000;
            }
        }
    }

    private void mine(HotelData d, long now) {
        Island island = islandById(d.islandId);
        if (island == null) return;
        Location loc = oneBlockLocation(island);
        if (loc == null) return;
        World w = loc.getWorld();
        if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        Block block = loc.getBlock();
        List<String> ignored = cfg.config().getStringList("minion.ignored-blocks");
        if (ignored.contains(block.getType().name())) return;

        Player actor = firstOnlineMember(island);
        if (actor == null && cfg.config().getBoolean("minion.only-when-members-online", false)) return;

        Inventory inv = storage(d);
        boolean pause = "PAUSE".equalsIgnoreCase(cfg.config().getString("minion.on-storage-full", "PAUSE"));
        if (pause && !hasRoom(inv)) {
            if (!d.fullWarned) {
                d.fullWarned = true;
                broadcast(island, "minion.storage-full");
            }
            return;
        }
        d.fullWarned = false;

        double seconds = Math.max(0.25, cfg.factor("speed", d.level));
        d.nextBreak = now + (long) (seconds * 1000);

        ItemStack tool = Items.parse(cfg.config().getString("minion.tool", "NETHERITE_PICKAXE"), 1);
        Material before = block.getType();
        boolean dropItems = true;

        if (cfg.config().getBoolean("minion.fire-break-event", true) && actor != null) {
            BlockBreakEvent ev = new BlockBreakEvent(block, actor);
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return;
            dropItems = ev.isDropItems();
        }

        Collection<ItemStack> drops = List.of();
        if (dropItems) {
            drops = tool == null ? block.getDrops() : (actor != null ? block.getDrops(tool, actor) : block.getDrops(tool));
        }
        if (block.getType() == before) block.setType(Material.AIR);

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            inv.addItem(drop); // surplus perdu si plein (mode VOID, ou PAUSE sans place restante)
        }

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Effects.play(center, cfg.config().getString("minion.effects.sound", ""),
                (float) cfg.config().getDouble("minion.effects.sound-volume", 0.4));
        Effects.spawn(center, cfg.config().getString("minion.effects.particle", ""),
                cfg.config().getInt("minion.effects.particle-count", 4));

        rewards(d, island);
        data.markDirty();
    }

    private Player firstOnlineMember(Island island) {
        for (SuperiorPlayer m : island.getIslandMembers(true)) {
            Player p = m.asPlayer();
            if (p != null && p.isOnline()) return p;
        }
        return null;
    }

    private void rewards(HotelData d, Island island) {
        double money = cfg.factor("money", d.level);
        int xp = (int) Math.round(cfg.factor("xp", d.level));
        if (money <= 0 && xp <= 0) return;

        List<SuperiorPlayer> targets = new ArrayList<>();
        if ("ALL_MEMBERS".equalsIgnoreCase(cfg.config().getString("minion.reward-target", "LEADER"))) {
            targets.addAll(island.getIslandMembers(true));
        } else if (island.getOwner() != null) {
            targets.add(island.getOwner());
        }
        Economy eco = plugin.economy();
        for (SuperiorPlayer sp : targets) {
            if (money > 0 && eco != null) {
                OfflinePlayer off = sp.asOfflinePlayer();
                if (off != null) eco.depositPlayer(off, money);
            }
            Player online = sp.asPlayer();
            if (xp > 0 && online != null) online.giveExp(xp);
        }
    }
}
