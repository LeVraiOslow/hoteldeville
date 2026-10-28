package fr.hoteldeville.listener;

import fr.hoteldeville.HotelDeVillePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/** Capture les drops du bloc OneBlock vers le stockage du minion (mode EVENT uniquement). */
public class CaptureListener implements Listener {

    private final HotelDeVillePlugin plugin;

    public CaptureListener(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawn(EntitySpawnEvent event) {
        plugin.hotels().captureSpawn(event.getEntity(), event);
    }
}
