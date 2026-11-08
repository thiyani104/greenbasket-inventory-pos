package service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.*;
import util.FileManager;

/** Zip backups of the whole data folder, with a daily automatic copy and safe restore. */
public class BackupService {

    private static final String FOLDER = "backups";
    private static final int KEEP_AUTO = 7;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final FileManager fm;
    private final ActivityLog log;

    public BackupService(FileManager fm, ActivityLog log) {
        this.fm = fm;
        this.log = log;
    }

    private static boolean isDataFile(String name) {
        return name.matches("[A-Za-z0-9_.-]+\\.(csv|properties)");
    }

    /** Writes every data file into {@code target}. Returns the number of files saved. */
    public int backupTo(Path target, String user) {
        int n = 0;
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target));
                 Stream<Path> files = Files.list(fm.dir())) {
                for (Path p : files.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                    String name = p.getFileName().toString();
                    if (!isDataFile(name)) continue;
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(p, zip);
                    zip.closeEntry();
                    n++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Backup failed: " + e.getMessage(), e);
        }
        if (user != null) log.log(user, "BACKUP", target.getFileName() + " (" + n + " files)");
        return n;
    }

    /** Creates today's automatic backup if it does not exist yet, and prunes old ones. */
    public void autoBackup() {
        try {
            Path dir = fm.dir().resolve(FOLDER);
            Path today = dir.resolve("auto-" + LocalDate.now().toString().replace("-", "") + ".zip");
            if (!Files.exists(today)) backupTo(today, null);
            try (Stream<Path> s = Files.list(dir)) {
                List<Path> autos = s.filter(p -> p.getFileName().toString().startsWith("auto-"))
                        .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
                for (int i = KEEP_AUTO; i < autos.size(); i++) Files.deleteIfExists(autos.get(i));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Automatic backup skipped: " + e.getMessage());
        }
    }

    /**
     * Replaces the data files with the contents of a backup. A safety copy of the current data
     * is taken first. The caller must reload the application state afterwards.
     */
    public int restoreFrom(Path zipFile, String user) {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory() || !isDataFile(name)) continue; // ignore anything unexpected (zip-slip safe)
                contents.put(name, zin.readAllBytes());
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("That file is not a valid Green Basket backup.");
        }
        if (!contents.containsKey("users.csv") && !contents.containsKey("products.csv"))
            throw new IllegalArgumentException("That file is not a valid Green Basket backup.");
        backupTo(fm.dir().resolve(FOLDER).resolve("before-restore-" + LocalDateTime.now().format(STAMP) + ".zip"), null);
        try {
            for (Map.Entry<String, byte[]> en : contents.entrySet()) Files.write(fm.path(en.getKey()), en.getValue());
        } catch (IOException ex) {
            throw new UncheckedIOException("Restore failed: " + ex.getMessage(), ex);
        }
        if (user != null) log.log(user, "RESTORE", zipFile.getFileName().toString());
        return contents.size();
    }
}
