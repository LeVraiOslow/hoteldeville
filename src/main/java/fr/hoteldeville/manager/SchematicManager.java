package fr.hoteldeville.manager;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.world.block.BaseBlock;
import fr.hoteldeville.data.HotelData;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

    /** Position (dans le monde) d'un bloc du clipboard une fois collé en (x,y,z) avec la rotation donnée. */
    private BlockVector3 map(Clipboard clipboard, AffineTransform t, BlockVector3 pos, int x, int y, int z) {
        Vector3 v = t.apply(pos.subtract(clipboard.getOrigin()).toVector3());
        return BlockVector3.at(x + (int) Math.round(v.x()), y + (int) Math.round(v.y()), z + (int) Math.round(v.z()));
    }

    /** Angle (0/90/180/270) à appliquer pour que l'avant de la schématique (face {@code from}) regarde vers {@code to}. */
    public int angleBetween(BlockFace from, BlockFace to) {
        for (int k = 0; k < 360; k += 90) {
            Vector3 v = new AffineTransform().rotateY(k).apply(Vector3.at(from.getModX(), 0, from.getModZ()));
            if ((int) Math.round(v.x()) == to.getModX() && (int) Math.round(v.z()) == to.getModZ()) return k;
        }
        return 0;
    }

    /** Limites {minX,minY,minZ,maxX,maxY,maxZ} de la structure une fois collée en (x,y,z) avec la rotation donnée. */
    public int[] bounds(Clipboard clipboard, int x, int y, int z, int rotation) {
        AffineTransform t = new AffineTransform().rotateY(rotation);
        BlockVector3 lo = clipboard.getMinimumPoint(), hi = clipboard.getMaximumPoint();
        int[] r = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int cx : new int[]{lo.x(), hi.x()}) for (int cy : new int[]{lo.y(), hi.y()}) for (int cz : new int[]{lo.z(), hi.z()}) {
            BlockVector3 m = map(clipboard, t, BlockVector3.at(cx, cy, cz), x, y, z);
            r[0] = Math.min(r[0], m.x()); r[1] = Math.min(r[1], m.y()); r[2] = Math.min(r[2], m.z());
            r[3] = Math.max(r[3], m.x()); r[4] = Math.max(r[4], m.y()); r[5] = Math.max(r[5], m.z());
        }
        return r;
    }

    /**
     * Colle la schématique en (x,y,z) avec la rotation donnée. L'air de la schématique n'efface jamais rien ;
     * selon {@code keep}, les blocs déjà présents sont conservés (la schématique s'adapte autour).
     *
     * @return les positions (packées, voir {@link HotelData#pack}) où un bloc existant a été conservé
     */
    public Set<Long> paste(Clipboard clipboard, World world, int x, int y, int z, int rotation, KeepMode keep) throws WorldEditException {
        AffineTransform t = new AffineTransform().rotateY(rotation);
        BlockTransformExtent source = new BlockTransformExtent(clipboard, t); // oriente aussi les escaliers, portes, etc.
        Set<Long> kept = new HashSet<>();
        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            for (BlockVector3 pos : clipboard.getRegion()) {
                BaseBlock block = source.getFullBlock(pos);
                if (block.getBlockType().getMaterial().isAir()) continue;
                BlockVector3 target = map(clipboard, t, pos, x, y, z);
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
     * Retire uniquement les blocs appartenant à la schématique (collée en x,y,z avec la rotation donnée).
     * Un bloc n'est retiré que s'il est toujours du type prévu par la schématique et n'a pas été conservé
     * à la pose : tout ce que les joueurs ont posé dans le bâtiment (coffres, fours, etc.) reste en place.
     *
     * @return le nombre de blocs retirés
     */
    public int clear(Clipboard clipboard, World world, int x, int y, int z, int rotation, Set<Long> kept) {
        AffineTransform t = new AffineTransform().rotateY(rotation);
        int removed = 0;
        for (BlockVector3 pos : clipboard.getRegion()) {
            var state = clipboard.getBlock(pos);
            if (state.getBlockType().getMaterial().isAir()) continue;
            BlockVector3 target = map(clipboard, t, pos, x, y, z);
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
