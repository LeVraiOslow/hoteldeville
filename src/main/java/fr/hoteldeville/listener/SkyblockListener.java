package fr.hoteldeville.listener;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandKickEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandQuitEvent;
import com.bgsoftware.superiorskyblock.api.events.PostIslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreate(PostIslandCreateEvent event) {
        if (!plugin.configs().config().getBoolean("shulker.give-on-island-create", true)) return;
        SuperiorPlayer owner = event.getPlayer();
        if (owner == null) return;
        UUID playerId = owner.getUniqueId();

        // Petit délai : le joueur est téléporté sur son île juste après la création.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p == null || !p.isOnline()) return;
            Island island = plugin.hotels().islandOf(p);
            if (island == null) return;
            if (plugin.hotels().hotelOf(island) != null) return;
            if (plugin.data().getCarried(island.getUniqueId()) != null) return;
            for (ItemStack it : p.getInventory().getContents()) {
                if (plugin.hotels().isHotelShulker(it)) return; // déjà en possession
            }
            plugin.hotels().giveShulker(p);
            plugin.configs().send(p, "shulker.received");
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDisband(IslandDisbandEvent event) {
        Island island = event.getIsland();
        if (island == null) return;
        UUID id = island.getUniqueId();
        HotelData d = plugin.data().get(id);
        boolean carried = plugin.data().getCarried(id) != null;
        if (d == null && !carried) return;
        if (d != null) plugin.hotels().broadcast(island, "hotel.reset");
        plugin.hotels().resetHotel(id); // supprime aussi l'hôtel récupéré (shulker devenue invalide)
    }

    /** Le joueur qui a récupéré l'hôtel quitte son île : l'hôtel est supprimé. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(IslandQuitEvent event) {
        if (event.getIsland() == null || event.getPlayer() == null) return;
        plugin.hotels().onMemberLeft(event.getIsland().getUniqueId(), event.getPlayer().getUniqueId());
    }

    /** Idem si le porteur est expulsé de l'île. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(IslandKickEvent event) {
        if (event.getIsland() == null || event.getTarget() == null) return;
        plugin.hotels().onMemberLeft(event.getIsland().getUniqueId(), event.getTarget().getUniqueId());
    }
}
