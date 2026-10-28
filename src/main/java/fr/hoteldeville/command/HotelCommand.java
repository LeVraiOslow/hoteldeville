package fr.hoteldeville.command;

import com.bgsoftware.superiorskyblock.api.island.Island;
import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.data.HotelData;
import fr.hoteldeville.manager.Configs;
import fr.hoteldeville.manager.HotelManager;
import fr.hoteldeville.menu.HotelMenu;
import fr.hoteldeville.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /hotel, /hotel storage, /hotel admin ... */
public class HotelCommand implements CommandExecutor, TabCompleter {

    private final HotelDeVillePlugin plugin;

    public HotelCommand(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Configs cfg = plugin.configs();
        HotelManager hotels = plugin.hotels();

        if (args.length == 0) {
            Player p = playerOnly(sender);
            if (p == null) return true;
            if (!p.hasPermission("hotel.use")) {
                cfg.send(p, "general.no-permission");
                return true;
            }
            HotelMenu.open(plugin, p);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "storage", "stockage" -> {
                Player p = playerOnly(sender);
                if (p == null) return true;
                if (!p.hasPermission("hotel.use")) {
                    cfg.send(p, "general.no-permission");
                    return true;
                }
                Island island = hotels.islandOf(p);
                if (island == null) {
                    cfg.send(p, "general.no-island");
                    return true;
                }
                HotelData d = hotels.hotelOf(island);
                if (d == null) {
                    cfg.send(p, "general.no-hotel");
                    return true;
                }
                p.openInventory(hotels.storage(d));
            }
            case "help" -> {
                for (String line : cfg.rawList("help")) sender.sendMessage(Text.color(line));
            }
            case "admin" -> admin(sender, args);
            default -> cfg.send(sender, "general.unknown-command");
        }
        return true;
    }

    private Player playerOnly(CommandSender sender) {
        if (sender instanceof Player p) return p;
        plugin.configs().send(sender, "general.player-only");
        return null;
    }

    private void admin(CommandSender sender, String[] args) {
        Configs cfg = plugin.configs();
        HotelManager hotels = plugin.hotels();

        if (!sender.hasPermission("hotel.admin")) {
            cfg.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            cfg.send(sender, "general.unknown-command");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);

        if (action.equals("reload")) {
            plugin.reload();
            cfg.send(sender, "general.reloaded");
            return;
        }
        if (args.length < 3) {
            cfg.send(sender, "general.unknown-command");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            cfg.send(sender, "general.player-not-found", "player", args[2]);
            return;
        }

        if (action.equals("give")) {
            hotels.giveShulker(target);
            cfg.send(sender, "shulker.given", "player", target.getName());
            return;
        }

        Island island = hotels.islandOf(target);
        if (island == null) {
            cfg.send(sender, "general.player-no-island", "player", target.getName());
            return;
        }
        HotelData d = hotels.hotelOf(island);
        if (d == null) {
            cfg.send(sender, "general.no-hotel");
            return;
        }

        switch (action) {
            case "setlevel" -> {
                if (args.length < 4) {
                    cfg.send(sender, "general.unknown-command");
                    return;
                }
                int level;
                try {
                    level = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    cfg.send(sender, "general.invalid-number", "value", args[3]);
                    return;
                }
                hotels.setLevel(d, level);
                cfg.send(sender, "admin.level-set", "player", target.getName(), "level", String.valueOf(d.level));
            }
            case "finish" -> {
                if (!d.isUpgrading()) {
                    cfg.send(sender, "admin.no-upgrade");
                    return;
                }
                hotels.finishUpgrade(d);
                cfg.send(sender, "admin.upgrade-finished", "player", target.getName());
            }
            case "reset" -> {
                hotels.resetHotel(d.islandId);
                cfg.send(sender, "admin.hotel-reset", "player", target.getName());
            }
            default -> cfg.send(sender, "general.unknown-command");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("storage", "help"));
            if (sender.hasPermission("hotel.admin")) out.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("hotel.admin")) {
            out.addAll(List.of("give", "setlevel", "finish", "reset", "reload"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("hotel.admin")
                && !args[1].equalsIgnoreCase("reload")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
