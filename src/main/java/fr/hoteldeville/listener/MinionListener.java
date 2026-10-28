package fr.hoteldeville.listener;

import com.bgsoftware.superiorskyblock.api.island.Island;
import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.data.HotelData;
import fr.hoteldeville.menu.HotelMenu;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** Protection et interactions avec l'entité minion. */
public class MinionListener implements Listener {

    private final HotelDeVillePlugin plugin;

    public MinionListener(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
    }

    private UUID minionIsland(Entity e) {
        if (!(e instanceof ArmorStand)) return null;
        String raw = e.getPersistentDataContainer().get(plugin.hotels().minionKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        UUID islandId = minionIsland(event.getRightClicked());
        if (islandId == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("hotel.use")) {
            plugin.configs().send(player, "general.no-permission");
            return;
        }
        Island island = plugin.hotels().islandOf(player);
        if (island == null || !island.getUniqueId().equals(islandId)) {
            plugin.configs().send(player, "minion.not-yours");
            return;
        }
        HotelData d = plugin.data().get(islandId);
        if (d == null) return;

        String action = plugin.configs().config().getString("minion.click-action", "STORAGE").toUpperCase();
        switch (action) {
            case "STORAGE" -> player.openInventory(plugin.hotels().storage(d));
            case "MENU" -> HotelMenu.open(plugin, player);
            default -> {
                // aucune action
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (minionIsland(event.getRightClicked()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (minionIsland(event.getEntity()) != null) event.setCancelled(true);
    }
}
