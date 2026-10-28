package fr.hoteldeville.manager;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

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

    /** Limites {minX,minY,minZ,maxX,maxY,maxZ} de la structure une fois collée en (x,y,z). */
    public int[] bounds(Clipboard clipboard, int x, int y, int z) {
        BlockVector3 offset = BlockVector3.at(x, y, z).subtract(clipboard.getOrigin());
        BlockVector3 min = clipboard.getMinimumPoint().add(offset);
        BlockVector3 max = clipboard.getMaximumPoint().add(offset);
        return new int[]{min.x(), min.y(), min.z(), max.x(), max.y(), max.z()};
    }

    public void paste(Clipboard clipboard, World world, int x, int y, int z, boolean ignoreAir) throws WorldEditException {
        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(ignoreAir)
                    .build();
            Operations.complete(op);
        }
    }
}
