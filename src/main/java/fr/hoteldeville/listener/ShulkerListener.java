package fr.hoteldeville.listener;

import fr.hoteldeville.HotelDeVillePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Pose de la shulker box de l'hôtel de ville. */
public class ShulkerListener implements Listener {

    private final HotelDeVillePlugin plugin;

    public ShulkerListener(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.hotels().isHotelShulker(event.getItemInHand())) return;
        // La shulker n'est jamais posée telle quelle : on construit l'hôtel à la place.
        event.setCancelled(true);

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        EquipmentSlot hand = event.getHand();

        // Exécuté au tick suivant : évite que l'annulation de l'événement ne restaure un bloc par-dessus la structure.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (!plugin.hotels().isHotelShulker(player.getInventory().getItem(hand))) return;
            plugin.hotels().placeHotel(player, block, hand);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (plugin.hotels().isHotelShulker(event.getItem())) event.setCancelled(true);
    }
}
