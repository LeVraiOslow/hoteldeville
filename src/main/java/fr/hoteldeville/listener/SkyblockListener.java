package fr.hoteldeville.listener;

import com.bgsoftware.superiorskyblock.api.events.IslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.data.HotelData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Réagit à la création / suppression d'île SuperiorSkyblock2. */
public class SkyblockListener implements Listener {

    private final HotelDeVillePlugin plugin;

    public SkyblockListener(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreate(IslandCreateEvent event) {
        if (!plugin.configs().config().getBoolean("shulker.give-on-island-create", true)) return;
        UUID playerId = event.getPlayer().getUniqueId();

        // L'île est réellement créée quelques instants après l'événement : on donne l'item avec un petit délai.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p == null || !p.isOnline()) return;
            Island island = plugin.hotels().islandOf(p);
            if (island == null) return;
            if (plugin.hotels().hotelOf(island) != null) return;
            for (ItemStack it : p.getInventory().getContents()) {
                if (plugin.hotels().isHotelShulker(it)) return; // déjà en possession
            }
            plugin.hotels().giveShulker(p);
            plugin.configs().send(p, "shulker.received");
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDisband(IslandDisbandEvent event) {
        Island island = event.getIsland();
        if (island == null) return;
        UUID id = island.getUniqueId();
        HotelData d = plugin.data().get(id);
        if (d == null) return;
        plugin.hotels().broadcast(island, "hotel.reset");
        plugin.hotels().resetHotel(id);
    }
}
