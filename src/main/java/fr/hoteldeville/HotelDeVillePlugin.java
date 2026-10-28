package fr.hoteldeville;

import fr.hoteldeville.command.HotelCommand;
import fr.hoteldeville.data.DataManager;
import fr.hoteldeville.data.HotelData;
import fr.hoteldeville.listener.MenuListener;
import fr.hoteldeville.listener.ProtectionListener;
import fr.hoteldeville.listener.CaptureListener;
import fr.hoteldeville.listener.ShulkerListener;
import fr.hoteldeville.listener.SkyblockListener;
import fr.hoteldeville.manager.Configs;
import fr.hoteldeville.manager.HotelManager;
import fr.hoteldeville.menu.HotelMenu;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class HotelDeVillePlugin extends JavaPlugin {

    private static HotelDeVillePlugin instance;

    private Configs configs;
    private DataManager data;
    private HotelManager hotels;
    private Economy economy;

    public static HotelDeVillePlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();
        new File(getDataFolder(), "schematics").mkdirs();

        configs = new Configs(this);
        configs.load();
        data = new DataManager(this);
        data.load();
        hotels = new HotelManager(this);

        if (economy() == null) {
            getLogger().warning("Aucune économie Vault trouvée (Essentials ?). Les ressources MONEY et la monnaie du minion sont désactivées.");
        }
        if (!hotels.worldEditAvailable()) {
            getLogger().warning("WorldEdit/FAWE introuvable : l'hôtel de ville ne pourra pas être construit.");
        }
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            getLogger().info("Nexo non détecté : les items 'nexo:...' ne seront pas disponibles.");
        }

        Bukkit.getPluginManager().registerEvents(new SkyblockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ShulkerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CaptureListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);

        HotelCommand command = new HotelCommand(this);
        var cmd = getCommand("hotel");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        // Nettoyage (optionnel) des hôtels dont l'île n'existe plus, une fois SuperiorSkyblock2 chargé
        if (configs.config().getBoolean("settings.clean-orphans", false)) {
            Bukkit.getScheduler().runTaskLater(this, () -> hotels.cleanOrphans(), 20L * 30);
        }

        // Minage des minions (toutes les 5 ticks)
        Bukkit.getScheduler().runTaskTimer(this, () -> hotels.tickMinions(), 40L, 5L);

        // Améliorations + rafraîchissement des menus (chaque seconde)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            hotels.tickUpgrades();
            refreshMenus();
        }, 20L, 20L);

        // Sauvegarde automatique
        long autosave = Math.max(10, configs.config().getInt("settings.autosave-seconds", 60)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (HotelData d : data.all().values()) d.syncStorage();
            data.markDirty();
            data.saveIfDirty();
        }, autosave, autosave);

        getLogger().info("HotelDeVille activé.");
    }

    @Override
    public void onDisable() {
        if (data != null) data.save();
    }

    private void refreshMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof HotelMenu menu) {
                HotelData d = data.get(menu.islandId());
                if (d == null) {
                    p.closeInventory();
                } else if (d.level != menu.level()) {
                    HotelMenu.open(this, p);
                } else {
                    menu.render();
                }
            }
        }
    }

    public void reload() {
        configs.load();
    }

    public Configs configs() {
        return configs;
    }

    public DataManager data() {
        return data;
    }

    public HotelManager hotels() {
        return hotels;
    }

    /** Économie Vault (Essentials ou autre), résolue à la demande. */
    public Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }
}
