package fr.hoteldeville.manager;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import fr.hoteldeville.data.HotelData;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Collage de la schématique via WorldEdit / FAWE. Cette classe ne doit être chargée que si WorldEdit est présent. */
public final class SchematicManager {

    public Clipboard load(File file) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IOException("Format de schématique inconnu : " + file.getName());
        try (FileInputStream in = new FileInputStream(file);
             ClipboardReader reader = format.getReader(in)) {
            return reader.read();
        }
    }

    /** Que faire d'un bloc déjà présent à l'endroit où la schématique veut poser un bloc ? */
    public enum KeepMode {
        NONE,        // la schématique remplace tout
        CONTAINERS,  // coffres, fours, barils, etc. sont conservés (rien n'est détruit ni drop)
        ALL;         // tout bloc déjà présent est conservé : la schématique ne remplit que les vides

        public static KeepMode parse(String s) {
            try {
                return valueOf(s == null ? "CONTAINERS" : s.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return CONTAINERS;
            }
        }
    }

    /** Position (dans le monde) d'un bloc du clipboard une fois collé en (x,y,z). */
    private BlockVector3 map(Clipboard clipboard, BlockVector3 pos, int x, int y, int z) {
        return pos.subtract(clipboard.getOrigin()).add(x, y, z);
    }

    /** Limites {minX,minY,minZ,maxX,maxY,maxZ} de la structure une fois collée en (x,y,z). */
    public int[] bounds(Clipboard clipboard, int x, int y, int z) {
        BlockVector3 min = map(clipboard, clipboard.getMinimumPoint(), x, y, z);
        BlockVector3 max = map(clipboard, clipboard.getMaximumPoint(), x, y, z);
        return new int[]{min.x(), min.y(), min.z(), max.x(), max.y(), max.z()};
    }

    /**
     * Colle la schématique en (x,y,z). L'air de la schématique n'efface jamais rien ;
     * selon {@code keep}, les blocs déjà présents sont conservés (la schématique s'adapte autour).
     *
     * @return les positions (packées, voir {@link HotelData#pack}) où un bloc existant a été conservé
     */
    public Set<Long> paste(Clipboard clipboard, World world, int x, int y, int z, KeepMode keep) throws WorldEditException {
        Set<Long> kept = new HashSet<>();
        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            for (BlockVector3 pos : clipboard.getRegion()) {
                BaseBlock block = clipboard.getFullBlock(pos);
                if (block.getBlockType().getMaterial().isAir()) continue;
                BlockVector3 target = map(clipboard, pos, x, y, z);
                if (keep != KeepMode.NONE) {
                    Block existing = world.getBlockAt(target.x(), target.y(), target.z());
                    boolean conserve = keep == KeepMode.ALL
                            ? !existing.getType().isAir()
                            : existing.getState(false) instanceof Container;
                    if (conserve) {
                        kept.add(HotelData.pack(target.x(), target.y(), target.z()));
                        continue;
                    }
                }
                session.setBlock(target, block);
            }
        }
        return kept;
    }

    /**
     * Retire uniquement les blocs appartenant à la schématique (collée en x,y,z).
     * Un bloc n'est retiré que s'il est toujours du type prévu par la schématique et n'a pas été conservé
     * à la pose : tout ce que les joueurs ont posé dans le bâtiment (coffres, fours, etc.) reste en place.
     *
     * @return le nombre de blocs retirés
     */
    public int clear(Clipboard clipboard, World world, int x, int y, int z, Set<Long> kept) {
        int removed = 0;
        for (BlockVector3 pos : clipboard.getRegion()) {
            var state = clipboard.getBlock(pos);
            if (state.getBlockType().getMaterial().isAir()) continue;
            BlockVector3 target = map(clipboard, pos, x, y, z);
            if (kept != null && kept.contains(HotelData.pack(target.x(), target.y(), target.z()))) continue;
            Material expected = BukkitAdapter.adapt(state.getBlockType());
            Block b = world.getBlockAt(target.x(), target.y(), target.z());
            if (b.getType() != expected) continue; // bloc posé par un joueur (ou modifié) : on le garde
            b.setType(Material.AIR, false);
            removed++;
        }
        return removed;
    }
}
