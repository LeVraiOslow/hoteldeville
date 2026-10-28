package fr.hoteldeville.listener;

import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.data.HotelData;
import fr.hoteldeville.menu.HotelMenu;
import fr.hoteldeville.menu.StorageHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Clics dans le menu /hotel et sauvegarde du stockage à la fermeture. */
public class MenuListener implements Listener {

    private final HotelDeVillePlugin plugin;

    public MenuListener(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof StorageHolder) {
            handleStorageClick(event, top);
            return;
        }
        if (!(top.getHolder() instanceof HotelMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != top) return;
        if (event.getClick() == ClickType.DOUBLE_CLICK) return;
        menu.click(player, event.getSlot());
    }

    /** Stockage du minion : on peut récupérer les ressources, mais jamais en déposer. */
    private void handleStorageClick(InventoryClickEvent event, Inventory top) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return; // clic en dehors de la fenêtre (jeter le curseur)
        InventoryAction a = event.getAction();

        if (clicked != top) {
            // Inventaire du joueur : interdit d'envoyer des items vers le stockage (shift-clic)
            if (a == InventoryAction.MOVE_TO_OTHER_INVENTORY) event.setCancelled(true);
            return;
        }
        switch (a) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_SOME, PICKUP_ONE,
                 MOVE_TO_OTHER_INVENTORY, DROP_ALL_SLOT, DROP_ONE_SLOT, NOTHING -> {
                // retrait autorisé
            }
            case HOTBAR_SWAP -> {
                // Autorisé uniquement si la case de la hotbar / main secondaire est vide (simple retrait)
                ItemStack other = event.getClick() == ClickType.SWAP_OFFHAND
                        ? event.getWhoClicked().getInventory().getItemInOffHand()
                        : (event.getHotbarButton() >= 0 ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null);
                if (other != null && !other.getType().isAir()) event.setCancelled(true);
            }
            case COLLECT_TO_CURSOR -> {
                // Double-clic : ne doit jamais toucher au stockage avec un curseur rempli par un item déposé
                // (récupérer depuis le stockage reste un retrait, mais on évite les cas ambigus)
                event.setCancelled(true);
            }
            default -> event.setCancelled(true); // PLACE_*, SWAP_WITH_CURSOR, HOTBAR_MOVE_AND_READD, CLONE_STACK...
        }
    }

    @EventHandler
    public void onStorageDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof StorageHolder)) return;
        int size = top.getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < size) {
                event.setCancelled(true);
                return;
            }
        }
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
