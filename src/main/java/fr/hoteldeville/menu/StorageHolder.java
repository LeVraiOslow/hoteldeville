package fr.hoteldeville.menu;

import fr.hoteldeville.HotelDeVillePlugin;
import fr.hoteldeville.data.HotelData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Holder de l'inventaire de stockage du minion (partagé par tous les membres d'une île). */
public final class StorageHolder implements InventoryHolder {

    private final UUID islandId;

    public StorageHolder(UUID islandId) {
        this.islandId = islandId;
    }

    public UUID islandId() {
        return islandId;
    }

    @Override
    public Inventory getInventory() {
        HotelData d = HotelDeVillePlugin.get().data().get(islandId);
        return d == null ? null : d.inventory;
    }
}
