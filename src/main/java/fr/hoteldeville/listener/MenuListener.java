package fr.hoteldeville.listener;

import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.data.HotelData;
import fr.hoteldeville.menu.HotelMenu;
import fr.hoteldeville.menu.StorageHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Clics dans le menu /hotel et sauvegarde du stockage à la fermeture. */
public class MenuListener implements Listener {

    private final HotelDeVillePlugin plugin;

    public MenuListener(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof HotelMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != top) return;
        if (event.getClick() == ClickType.DOUBLE_CLICK) return;
        menu.click(player, event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HotelMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof StorageHolder holder) {
            HotelData d = plugin.data().get(holder.islandId());
            if (d != null) {
                d.syncStorage();
                plugin.data().markDirty();
            }
        }
    }
}
