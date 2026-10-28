package fr.hoteldeville.listener;

import fr.hoteldeville.HotelDeVillePlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Les blocs de la structure de l'hôtel de ville ne peuvent être cassés que via le menu /hotel. */
public class ProtectionListener implements Listener {

    private final HotelDeVillePlugin plugin;

    public ProtectionListener(HotelDeVillePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean protectedBlock(Block b) {
        return plugin.hotels().isHotelBlock(b.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!protectedBlock(event.getBlock())) return;
        Player p = event.getPlayer();
        if (p != null && p.hasPermission("hotel.bypass")) return;
        event.setCancelled(true);
        if (p != null) plugin.configs().send(p, "hotel.protected");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (protectedBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (protectedBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (protectedBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (protectedBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
