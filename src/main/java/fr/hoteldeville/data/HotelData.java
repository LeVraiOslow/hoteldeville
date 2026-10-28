package fr.hoteldeville.data;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Données d'un hôtel de ville (un par île). */
public final class HotelData {

    public final UUID islandId;

    public String world;
    public int pasteX, pasteY, pasteZ;
    public int minX, minY, minZ, maxX, maxY, maxZ;

    public int level = 1;

    /** Le minion mine-t-il en ce moment ? (bouton démarrer / arrêter du menu) */
    public boolean active = false;

    /** Amélioration en cours : fin (epoch ms), début (epoch ms), niveau visé. 0 = aucune. */
    public long upgradeEnd = 0;
    public long upgradeStart = 0;
    public int upgradeTarget = 0;

    /** Identifiants des ressources déjà validées pour le prochain niveau. */
    public final Set<String> validated = new LinkedHashSet<>();

    /** Contenu sauvegardé du stockage (peut contenir des null). */
    public List<ItemStack> storage = new ArrayList<>();

    // ── runtime (non sauvegardé) ──
    public transient Inventory inventory;
    public transient long nextBreak = 0;

    public HotelData(UUID islandId) {
        this.islandId = islandId;
    }

    public boolean isUpgrading() {
        return upgradeEnd > 0;
    }

    /** Copie le contenu de l'inventaire vers la liste sauvegardable. */
    public void syncStorage() {
        if (inventory == null) return;
        storage = new ArrayList<>();
        for (ItemStack it : inventory.getContents()) storage.add(it);
    }
}
