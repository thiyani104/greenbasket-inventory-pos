package util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import model.User;
import service.ActivityLog;
import service.Inventory;
import service.SalesService;
import service.UserService;

/** Application-wide services and the signed-in user. Call {@link #init} once at start-up. */
public final class SystemContext {

    public static FileManager files;
    public static AppSettings settings;
    public static ActivityLog log;
    public static Inventory inventory;
    public static UserService users;
    public static SalesService sales;
    public static User currentUser;

    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private SystemContext() {}

    /** Loads (or creates) all data under the given folder. Returns a note when legacy data was migrated, else null. */
    public static String init(Path dataDir) {
        files = new FileManager(dataDir);
        files.autoBackup(7);
        settings = files.loadSettings();
        log = new ActivityLog(files);
        log.load();
        inventory = new Inventory(files, settings);
        inventory.load();
        users = new UserService(files, log);
        users.load();
        sales = new SalesService(files, inventory, settings, log);
        sales.load();
        currentUser = null;

        // migrate the original single-file catalogue once
        Path legacy = Path.of("products.txt");
        if (!files.exists("products.csv") && Files.exists(legacy)) {
            int n = inventory.importLegacy(legacy, "system");
            if (n > 0) {
                log.add("system", "Migrated data", n + " products imported from products.txt (costs need updating)");
                return n + " products were imported from your old products.txt. Their cost prices are unknown: please update them.";
            }
        }
        return null;
    }

    /** Re-reads everything from disk (after a restore). */
    public static void reload() {
        Path dir = files.getDir();
        User keep = currentUser;
        init(dir);
        if (keep != null) currentUser = users.findByUsername(keep.getUsername());
        fireChanged();
    }

    /** Username used in ledger and audit entries. */
    public static String actor() { return currentUser == null ? "system" : currentUser.getUsername(); }

    public static void addListener(Runnable r) { LISTENERS.add(r); }

    public static void clearListeners() { LISTENERS.clear(); }

    /** Tell open screens that data changed (sale made, stock adjusted...). */
    public static void fireChanged() {
        for (Runnable r : new ArrayList<>(LISTENERS)) r.run();
    }
}
