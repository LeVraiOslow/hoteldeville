package fr.hoteldeville.manager;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.BlockOffset;
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
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.RegisteredListener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** Logique principale : placement, améliorations, stockage et minion (invisible). */
public class HotelManager {

    private final HotelDeVillePlugin plugin;
    private final Configs cfg;
    private final DataManager data;

    public final NamespacedKey shulkerKey;

    private SchematicManager schematics;
    private Statistic playStat;

    // Liaison avec le module SSBOneBlock (par réflexion : aucune dépendance de compilation)
    private boolean moduleChecked = false;
    private Object phasesHandler;
    private Method runNextAction;

    // Capture des drops pendant un BlockBreakEvent simulé (mode EVENT)
    private HotelData capturing;
    private Location captureAt;
    private boolean capturedSomething;

    public HotelManager(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.configs();
        this.data = plugin.data();
        this.shulkerKey = new NamespacedKey(plugin, "hotel_shulker");
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
        SuperiorPlayer sp = SuperiorSkyblockAPI.getPlayer(p);
        return sp == null ? null : sp.getIsland();
    }

    public Island islandById(UUID id) {
        return SuperiorSkyblockAPI.getIslandByUUID(id);
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

    private Player firstOnlineMember(Island island) {
        for (SuperiorPlayer m : island.getIslandMembers(true)) {
            Player p = m.asPlayer();
            if (p != null && p.isOnline()) return p;
        }
        return null;
    }

    // ───────────────────────── Bloc générateur OneBlock ─────────────────────────

    /** Décalage du bloc : lu dans modules/OneBlock/config.yml (offset-source: MODULE) ou dans config.yml. */
    private int[] oneBlockOffset() {
        String source = cfg.config().getString("oneblock.offset-source", "MODULE");
        if ("MODULE".equalsIgnoreCase(source)) {
            Plugin ssb = Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2");
            if (ssb != null) {
                File f = new File(ssb.getDataFolder(), "modules" + File.separator + "OneBlock" + File.separator + "config.yml");
                if (f.exists()) {
                    String raw = YamlConfiguration.loadConfiguration(f).getString("block-offset", null);
                    if (raw != null) {
                        String[] parts = raw.split(",");
                        if (parts.length == 3) {
                            try {
                                // Le module convertit les décimales en entiers par troncature (-0.5 -> 0).
                                return new int[]{
                                        (int) Double.parseDouble(parts[0].trim()),
                                        (int) Double.parseDouble(parts[1].trim()),
                                        (int) Double.parseDouble(parts[2].trim())};
                            } catch (NumberFormatException ignored) {
                                // on retombe sur la config du plugin
                            }
                        }
                    }
                }
            }
        }
        return new int[]{
                cfg.config().getInt("oneblock.offset.x", 0),
                cfg.config().getInt("oneblock.offset.y", -1),
                cfg.config().getInt("oneblock.offset.z", 0)};
    }

    /** Emplacement exact du bloc générateur OneBlock de l'île (même formule que SSBOneBlock). */
    public Location oneBlockLocation(Island island) {
        try {
            SuperiorSkyblock ssb = SuperiorSkyblockAPI.getSuperiorSkyblock();
            Location center = island.getCenter(ssb.getSettings().getWorlds().getDefaultWorldDimension());
            if (center == null || center.getWorld() == null) return null;
            int[] o = oneBlockOffset();
            BlockOffset offset = SuperiorSkyblockAPI.getFactory().createBlockOffset(o[0], o[1], o[2]);
            Location l = offset.applyToLocation(center.clone().subtract(0.5, 0, 0.5));
            return l.getBlock().getLocation();
        } catch (Throwable t) {
            plugin.getLogger().warning("Position du OneBlock introuvable : " + t);
            return null;
        }
    }

    /** Récupère le gestionnaire de phases du module SSBOneBlock (via son listener enregistré). */
    private boolean initModule() {
        if (phasesHandler != null && runNextAction != null) return true;
        if (moduleChecked) return false;
        moduleChecked = true;
        try {
            for (RegisteredListener rl : BlockBreakEvent.getHandlerList().getRegisteredListeners()) {
                Object listener = rl.getListener();
                if (!listener.getClass().getName().equals("com.bgsoftware.ssboneblock.listeners.BlocksListener")) continue;
                Field f = listener.getClass().getDeclaredField("module");
                f.setAccessible(true);
                Object module = f.get(listener);
                phasesHandler = module.getClass().getMethod("getPhasesHandler").invoke(module);
                for (Method m : phasesHandler.getClass().getMethods()) {
                    if (m.getName().equals("runNextAction") && m.getParameterCount() == 2) {
                        runNextAction = m;
                        break;
                    }
                }
                return phasesHandler != null && runNextAction != null;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Liaison avec SSBOneBlock impossible : " + t);
        }
        plugin.getLogger().warning("Module SSBOneBlock introuvable : le mode DIRECT du minion est indisponible.");
        return false;
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
            d.level = 1;
            d.active = cfg.config().getBoolean("minion.active-on-build", false);
            data.put(d);
            data.save();

            // Consomme la shulker seulement en cas de succès
            ItemStack inHand = player.getInventory().getItem(hand);
            if (inHand != null) {
                inHand.setAmount(inHand.getAmount() - 1);
                player.getInventory().setItem(hand, inHand.getAmount() <= 0 ? null : inHand);
            }

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

    /** Supprime l'hôtel (structure, stockage, données) d'une île. */
    public void resetHotel(UUID islandId) {
        HotelData d = data.get(islandId);
        if (d == null) return;
        d.active = false;
        if (d.inventory != null) {
            for (HumanEntity v : new ArrayList<>(d.inventory.getViewers())) v.closeInventory();
        }
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

    /** Supprime les hôtels dont l'île n'existe plus. */
    public void cleanOrphans() {
        if (SuperiorSkyblockAPI.getGrid().getIslands().isEmpty()) return; // îles pas encore chargées
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
        storage(d);
        data.save();
        Island island = islandById(d.islandId);
        forEachOnlineMember(island, m -> {
            cfg.send(m, "hotel.upgrade-finished", "level", String.valueOf(d.level));
            Effects.play(m, cfg.config().getString("hotel.sound-upgrade", ""));
        });
    }

    public void setLevel(HotelData d, int level) {
        d.level = Math.max(1, Math.min(level, cfg.maxLevel()));
        d.upgradeEnd = 0;
        d.upgradeStart = 0;
        d.upgradeTarget = 0;
        d.validated.clear();
        storage(d);
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

    /** Reste-t-il de la place (slot vide) ? Un stockage "plein" = plus aucun slot libre. */
    public boolean hasRoom(Inventory inv) {
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir()) return true;
        }
        return false;
    }

    // ───────────────────────── Minion : démarrer / arrêter ─────────────────────────

    public boolean minionEnabled() {
        return cfg.config().getBoolean("minion.enabled", true);
    }

    /** "active", "inactive" ou "full" (arrêté car le stockage est plein). */
    public String minionState(HotelData d) {
        if (d.active) return "active";
        return hasRoom(storage(d)) ? "inactive" : "full";
    }

    /** Bouton démarrer / arrêter du menu. */
    public void toggleMinion(Player player, HotelData d) {
        if (!minionEnabled()) {
            cfg.send(player, "minion.disabled");
            return;
        }
        if (d.active) {
            d.active = false;
            data.markDirty();
            cfg.send(player, "minion.stopped");
            return;
        }
        if (!hasRoom(storage(d))) {
            cfg.send(player, "minion.cannot-start-full");
            return;
        }
        d.active = true;
        d.nextBreak = 0;
        data.markDirty();
        cfg.send(player, "minion.started");
    }

    private void stopBecauseFull(HotelData d, Island island) {
        d.active = false;
        data.markDirty();
        broadcast(island, "minion.stopped-full");
    }

    // ───────────────────────── Minion : minage ─────────────────────────

    public void tickMinions() {
        if (!minionEnabled()) return;
        long now = System.currentTimeMillis();
        for (HotelData d : data.all().values()) {
            if (!d.active || now < d.nextBreak) continue;
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

        Player actor = firstOnlineMember(island);
        if (actor == null && cfg.config().getBoolean("minion.only-when-members-online", false)) return;

        Block block = loc.getBlock();
        if (cfg.config().getStringList("minion.ignored-blocks").contains(block.getType().name())) {
            d.nextBreak = now + 1000;
            return;
        }

        Inventory inv = storage(d);
        if (!hasRoom(inv)) {
            stopBecauseFull(d, island);
            return;
        }

        double seconds = Math.max(0.25, cfg.factor("speed", d.level));
        d.nextBreak = now + (long) (seconds * 1000);

        boolean eventMode = "EVENT".equalsIgnoreCase(cfg.config().getString("minion.break-mode", "DIRECT"));
        boolean broke = (eventMode && actor != null) ? breakViaEvent(d, block, actor) : breakDirect(d, island, block, inv);
        if (!broke) return;

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Effects.play(center, cfg.config().getString("minion.effects.sound", ""),
                (float) cfg.config().getDouble("minion.effects.sound-volume", 0.4));
        Effects.spawn(center, cfg.config().getString("minion.effects.particle", ""),
                cfg.config().getInt("minion.effects.particle-count", 4));

        rewards(d, island);
        data.markDirty();

        if (!hasRoom(inv)) stopBecauseFull(d, island);
    }

    /**
     * Mode EVENT : simule la casse par un membre connecté. SSBOneBlock gère tout (drops, phases)
     * et les autres plugins voient un vrai BlockBreakEvent. Les drops sont capturés vers le stockage.
     * Les drops dépendent alors de l'outil tenu par le membre.
     */
    private boolean breakViaEvent(HotelData d, Block block, Player actor) {
        Material before = block.getType();
        capturing = d;
        captureAt = block.getLocation();
        capturedSomething = false;
        try {
            Bukkit.getPluginManager().callEvent(new BlockBreakEvent(block, actor));
        } finally {
            capturing = null;
            captureAt = null;
        }
        return capturedSomething || block.getType() != before;
    }

    /** Appelé par le listener d'entités pendant breakViaEvent. */
    public void captureSpawn(Entity entity, Cancellable event) {
        HotelData d = capturing;
        if (d == null || captureAt == null) return;
        Location l = entity.getLocation();
        if (l.getWorld() != captureAt.getWorld() || l.distanceSquared(captureAt.clone().add(0.5, 1, 0.5)) > 9) return;

        if (entity instanceof ExperienceOrb) {
            event.setCancelled(true); // l'xp du minion est donnée par le facteur "xp"
            capturedSomething = true;
        } else if (entity instanceof Item item) {
            ItemStack stack = item.getItemStack();
            var left = storage(d).addItem(stack);
            capturedSomething = true;
            if (left.isEmpty()) {
                event.setCancelled(true);
            } else {
                item.setItemStack(left.values().iterator().next());
            }
        }
    }

    /**
     * Mode DIRECT (par défaut) : le minion casse le bloc avec son propre outil (config minion.tool),
     * range les drops et demande à SSBOneBlock de générer le bloc suivant. Fonctionne même sans joueur connecté.
     */
    private boolean breakDirect(HotelData d, Island island, Block block, Inventory inv) {
        if (!initModule()) return false;

        ItemStack tool = Items.parse(cfg.config().getString("minion.tool", "NETHERITE_PICKAXE"), 1);
        List<ItemStack> drops = new ArrayList<>(tool == null ? block.getDrops() : block.getDrops(tool));

        BlockState state = block.getState();
        if (state instanceof InventoryHolder holder && !(state instanceof ShulkerBox)) {
            for (ItemStack it : holder.getInventory().getContents()) {
                if (it != null && !it.getType().isAir()) drops.add(it);
            }
            holder.getInventory().clear();
        }

        block.setType(Material.AIR);
        try {
            runNextAction.invoke(phasesHandler, island, null);
        } catch (Throwable t) {
            plugin.getLogger().warning("SSBOneBlock n'a pas pu générer le bloc suivant : " + t);
        }

        Location above = block.getLocation().add(0.5, 1, 0.5);
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            for (ItemStack rest : inv.addItem(drop).values()) {
                block.getWorld().dropItemNaturally(above, rest); // plein : rien n'est perdu
            }
        }
        return true;
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
