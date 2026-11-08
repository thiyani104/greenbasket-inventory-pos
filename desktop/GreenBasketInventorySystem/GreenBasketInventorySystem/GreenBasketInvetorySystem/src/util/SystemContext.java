package util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import model.User;
import service.*;

/**
 * The application's shared services, created once at start-up (and again after a backup restore).
 * Everything lives in one data folder, so the whole store can be backed up or moved by copying it.
 */
public final class SystemContext {

    public static FileManager files;
    public static Settings settings;
    public static ActivityLog log;
    public static AuthService auth;
    public static Inventory inventory;
    public static SalesService sales;
    public static PurchaseService purchases;
    public static ReportService reports;
    public static BackupService backup;

    /** The signed-in user, or null on the login screen. */
    public static volatile User currentUser;

    private SystemContext() { }

    /** Opens (or creates) the default data folder, "data" next to where the program runs. */
    public static void init() {
        init(Paths.get("data"));
    }

    public static void init(Path dir) {
        files = new FileManager(dir);
        settings = new Settings(files);
        log = new ActivityLog(files);
        auth = new AuthService(files, log);
        inventory = new Inventory(files, log);
        inventory.setActor(() -> currentUser == null ? "system" : currentUser.getUsername());
        loadCatalogue(dir);
        sales = new SalesService(files, inventory, log);
        purchases = new PurchaseService(files, inventory, log);
        reports = new ReportService(sales);
        backup = new BackupService(files, log);
    }

    /** First run: bring in the old products.txt if there is one, otherwise start with a sample catalogue. */
    private static void loadCatalogue(Path dir) {
        if (inventory.hasStoredCatalogue()) return;
        Path legacy = dir.resolve("products.txt");
        if (!Files.exists(legacy)) {
            Path cwd = Paths.get("products.txt");
            if (Files.exists(cwd) && dir.toAbsolutePath().equals(Paths.get("data").toAbsolutePath())) legacy = cwd;
        }
        int imported = Files.exists(legacy) ? SeedData.migrateLegacy(inventory, legacy, settings.getDefaultReorderLevel()) : 0;
        if (imported == 0) SeedData.seedCatalogue(inventory);
        else log.log("system", "MIGRATED", imported + " products imported from " + legacy.getFileName());
    }
}
