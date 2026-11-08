package ui.kit;

import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/** File dialogs. */
public final class Pick {

    private Pick() { }

    private static File lastDir;

    /** Asks where to save; returns null if cancelled. Asks before overwriting. */
    public static Path save(Component parent, String suggestedName, String description, String ext) {
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setDialogTitle("Save");
        fc.setSelectedFile(new File(lastDir == null ? new File(".") : lastDir, suggestedName));
        fc.setFileFilter(new FileNameExtensionFilter(description, ext));
        if (fc.showSaveDialog(parent instanceof java.awt.Window ? parent : SwingUtilities.getWindowAncestor(parent)) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith("." + ext)) f = new File(f.getParentFile(), f.getName() + "." + ext);
        lastDir = f.getParentFile();
        if (f.exists() && !Dialogs.confirm(parent, "Replace file?", "\"" + f.getName() + "\" already exists. Replace it?", "Replace", true)) return null;
        return f.toPath();
    }

    /** Asks for an existing file; returns null if cancelled. */
    public static File open(Component parent, String description, String... ext) {
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setDialogTitle("Open");
        fc.setFileFilter(new FileNameExtensionFilter(description, ext));
        if (fc.showOpenDialog(parent instanceof java.awt.Window ? parent : SwingUtilities.getWindowAncestor(parent)) != JFileChooser.APPROVE_OPTION) return null;
        lastDir = fc.getSelectedFile().getParentFile();
        return fc.getSelectedFile();
    }
}
