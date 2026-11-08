import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import model.*;
import service.*;
import util.*;

/** Dependency-free tests. Run with ./test.sh (exit code 1 on any failure). */
public class TestRunner {

    private static int passed, failed;

    public static void main(String[] args) throws Exception {
        run("CSV round trip with commas and quotes", TestRunner::csv);
        run("Password hashing", TestRunner::passwords);
        run("Product status rules", TestRunner::productStatus);
        run("Inventory add/update/delete + validation", TestRunner::inventoryCrud);
        run("Stock ledger", TestRunner::stockLedger);
        run("Cart rules", TestRunner::cart);
        run("Checkout maths, stock deduction, change", TestRunner::checkout);
        run("Checkout rejections", TestRunner::checkoutRejections);
        run("Void sale restores stock", TestRunner::voidSale);
        run("User accounts, lockout, last-manager guard", TestRunner::users);
        run("Reports ignore voided sales", TestRunner::reports);
        run("CSV import / export", TestRunner::csvImport);
        run("Persistence survives restart", TestRunner::persistence);
        run("Demo data loads", TestRunner::demo);
        run("Receipt formatting", TestRunner::receipt);
        run("Reorder planner", TestRunner::reorder);
        run("Dead stock, hourly and weekday reports", TestRunner::insights);
        run("End-of-day close report", TestRunner::dayClose);
        run("Backup and restore", TestRunner::backup);
        run("Legacy products.txt migration", TestRunner::legacy);
        run("Atomic save leaves no temp files", TestRunner::atomic);
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ harness
    interface Case { void run() throws Exception; }

    static void run(String name, Case c) {
        try {
            c.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  " + name + "  ->  " + t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(4, st.length); i++) System.out.println("          at " + st[i]);
        }
    }

    static void check(boolean cond, String msg) { if (!cond) throw new AssertionError(msg); }

    static void eq(Object expected, Object actual, String msg) {
        if (!expected.equals(actual)) throw new AssertionError(msg + " expected <" + expected + "> but was <" + actual + ">");
    }

    static void near(double expected, double actual, String msg) {
        if (Math.abs(expected - actual) > 0.001) throw new AssertionError(msg + " expected <" + expected + "> but was <" + actual + ">");
    }

    static void fails(String contains, Runnable r) {
        try {
            r.run();
        } catch (ServiceException e) {
            check(e.getMessage().toLowerCase().contains(contains.toLowerCase()),
                    "wrong message: \"" + e.getMessage() + "\" (wanted to contain \"" + contains + "\")");
            return;
        }
        throw new AssertionError("expected ServiceException containing \"" + contains + "\"");
    }

    /** A fresh isolated environment in a temp folder. */
    static class Env {
        final Path dir;
        FileManager fm;
        AppSettings settings;
        ActivityLog log;
        Inventory inv;
        UserService users;
        SalesService sales;
        User admin;

        Env() throws Exception { this(Files.createTempDirectory("gbtest")); }

        Env(Path dir) {
            this.dir = dir;
            fm = new FileManager(dir);
            settings = fm.loadSettings();
            log = new ActivityLog(fm);
            log.load();
            inv = new Inventory(fm, settings);
            inv.load();
            users = new UserService(fm, log);
            users.load();
            sales = new SalesService(fm, inv, settings, log);
            sales.load();
            admin = users.findByUsername("admin");
        }

        Product add(String name, String cat, double price, double cost, int qty, int reorder) {
            return inv.addProduct(new Product(0, name, cat, "Sup", price, cost, qty, reorder, null), "admin");
        }
    }

    // ------------------------------------------------------------------ tests
    static void csv() {
        String line = Csv.row("a,b", "say \"hi\"", "plain", null, "multi\nline");
        String[] back = Csv.parseLine(line);
        eq(5, back.length, "column count");
        eq("a,b", back[0], "comma field");
        eq("say \"hi\"", back[1], "quote field");
        eq("", back[3], "null field");
        eq("multi line", back[4], "newline flattened");
    }

    static void passwords() {
        String h = PasswordUtil.hash("secret1");
        check(PasswordUtil.verify("secret1", h), "correct password verifies");
        check(!PasswordUtil.verify("secret2", h), "wrong password rejected");
        check(!h.equals(PasswordUtil.hash("secret1")), "salted: same password gives different hashes");
        check(!PasswordUtil.verify("x", "garbage"), "garbage hash rejected");
    }

    static void productStatus() {
        LocalDate today = LocalDate.now();
        Product ok = new Product(1, "A", "C", "", 10, 5, 50, 10, null);
        eq(StockStatus.OK, ok.getStatus(7), "ok");
        ok.setQuantity(10);
        eq(StockStatus.LOW, ok.getStatus(7), "at reorder level is low");
        ok.setQuantity(0);
        eq(StockStatus.OUT, ok.getStatus(7), "zero is out");
        Product soon = new Product(2, "B", "C", "", 10, 5, 50, 10, today.plusDays(3));
        eq(StockStatus.EXPIRING, soon.getStatus(7), "expiring");
        Product gone = new Product(3, "C", "C", "", 10, 5, 5, 10, today.minusDays(1));
        eq(StockStatus.EXPIRED, gone.getStatus(7), "expired beats low");
        near(50.0, new Product(4, "D", "C", "", 10, 5, 1, 1, null).marginPercent(), "margin");
    }

    static void inventoryCrud() throws Exception {
        Env e = new Env();
        Product p = e.add("Milk", "Dairy", 250, 190, 20, 5);
        eq(1, p.getProductId(), "first id");
        eq(2, e.add("Eggs", "Dairy", 480, 380, 10, 5).getProductId(), "second id");
        fails("already exists", () -> e.add("milk", "Dairy", 1, 0.5, 1, 1));
        fails("name is required", () -> e.add(" ", "Dairy", 1, 0.5, 1, 1));
        fails("category is required", () -> e.add("X", "", 1, 0.5, 1, 1));
        fails("greater than zero", () -> e.add("X", "Y", 0, 0, 1, 1));
        fails("cost price is higher", () -> e.add("X", "Y", 5, 9, 1, 1));
        fails("negative", () -> e.add("X", "Y", 5, 1, -3, 1));
        Product edit = p.copy();
        edit.setPrice(260);
        edit.setProductName("Organic Milk");
        e.inv.updateProduct(edit);
        near(260, e.inv.findById(1).getPrice(), "price updated");
        eq("Organic Milk", e.inv.findById(1).getProductName(), "name updated");
        eq(20, e.inv.findById(1).getQuantity(), "quantity untouched by edit");
        Product dup = e.inv.findById(2).copy();
        dup.setProductName("organic milk");
        fails("already exists", () -> e.inv.updateProduct(dup));
        check(e.inv.deleteProduct(2), "delete ok");
        check(!e.inv.deleteProduct(2), "second delete false");
        eq(1, e.inv.size(), "size");
        eq(1, e.inv.search("MILK", null, Inventory.StatusFilter.ALL).size(), "search is case-insensitive");
        eq(0, e.inv.search("zzz", null, Inventory.StatusFilter.ALL).size(), "search no match");
        eq(1, e.inv.getAllCategories().size(), "categories");
    }

    static void stockLedger() throws Exception {
        Env e = new Env();
        Product p = e.add("Rice", "Pantry", 520, 410, 10, 5);
        eq(1, e.inv.getMovements().size(), "opening movement");
        e.inv.receiveStock(p.getProductId(), 15, 400.0, "delivery", "admin");
        eq(25, p.getQuantity(), "received");
        near(400, p.getCost(), "cost updated on receipt");
        e.inv.writeOff(p.getProductId(), 5, "damaged", "admin");
        eq(20, p.getQuantity(), "written off");
        e.inv.setCount(p.getProductId(), 18, "stock take", "admin");
        eq(18, p.getQuantity(), "counted");
        fails("same as", () -> e.inv.setCount(p.getProductId(), 18, "", "admin"));
        fails("not enough", () -> e.inv.writeOff(p.getProductId(), 99, "", "admin"));
        fails("greater than zero", () -> e.inv.receiveStock(p.getProductId(), 0, null, "", "admin"));
        List<StockMovement> m = e.inv.getMovements();
        eq(4, m.size(), "ledger size");
        eq(MovementType.CORRECTION, m.get(0).getType(), "newest first");
        eq(18, m.get(0).getBalanceAfter(), "balance recorded");
        eq(-2, m.get(0).getChange(), "delta recorded");
    }

    static void cart() throws Exception {
        Env e = new Env();
        Product p = e.add("Tea", "Pantry", 100, 60, 5, 2);
        Cart c = new Cart();
        c.add(p, 2);
        c.add(p, 1);
        eq(1, c.getLines().size(), "same product merges");
        eq(3, c.getItemCount(), "item count");
        fails("only 5", () -> c.add(p, 3));
        fails("at least 1", () -> c.add(p, 0));
        c.setQuantity(0, 5);
        eq(5, c.getItemCount(), "set quantity");
        fails("only 5", () -> c.setQuantity(0, 6));
        c.setQuantity(0, 0);
        check(c.isEmpty(), "quantity 0 removes the line");
        Product old = e.inv.addProduct(new Product(0, "Old", "X", "", 10, 5, 5, 1, LocalDate.now().minusDays(1)), "admin");
        fails("expiry", () -> c.add(old, 1));
    }

    static Sale sell(Env e, Cart c, double disc, PaymentMethod pm, double tendered) {
        return e.sales.checkout(c, e.admin, pm, disc, tendered);
    }

    static void checkout() throws Exception {
        Env e = new Env();
        e.settings.setTaxRate(10);
        Product a = e.add("A", "Cat", 100.50, 60, 10, 2);
        Product b = e.add("B", "Cat", 20, 10, 10, 2);
        Cart c = new Cart();
        c.add(a, 2);   // 201.00
        c.add(b, 3);   // 60.00
        Sale prev = c.preview(10, 10);
        near(261.00, prev.getSubtotal(), "subtotal");
        near(26.10, prev.getDiscountAmount(), "discount");
        near(234.90, prev.getNetSales(), "net");
        near(23.49, prev.getTaxAmount(), "tax");
        near(258.39, prev.getTotal(), "total");
        Sale s = sell(e, c, 10, PaymentMethod.CASH, 300);
        near(41.61, s.getChange(), "change");
        eq(8, a.getQuantity(), "stock A deducted");
        eq(7, b.getQuantity(), "stock B deducted");
        eq(1, s.getId(), "sale id");
        eq("GB-000001", s.getReceiptNumber(), "receipt number");
        // profit = net(234.90) - cost(2*60 + 3*10 = 150)
        near(84.90, s.getProfit(), "profit");
        eq(1, e.sales.getAll().size(), "stored");
        eq(MovementType.SALE, e.inv.getMovements().get(0).getType(), "ledger records sale");
        Cart c2 = new Cart();
        c2.add(b, 1);
        Sale s2 = sell(e, c2, 0, PaymentMethod.CARD, 0);
        near(22.0, s2.getTotal(), "card total with tax");
        near(22.0, s2.getTendered(), "card tendered equals total");
        eq(2, s2.getId(), "next id");
    }

    static void checkoutRejections() throws Exception {
        Env e = new Env();
        Product a = e.add("A", "Cat", 100, 60, 3, 1);
        User asst = e.users.addUser("sam", "Sam Fernando", Role.ASSISTANT, "secret1", e.admin);
        fails("empty", () -> sell(e, new Cart(), 0, PaymentMethod.CASH, 0));
        Cart c = new Cart();
        c.add(a, 2);
        fails("less than the total", () -> sell(e, c, 0, PaymentMethod.CASH, 150));
        fails("at most 10%", () -> e.sales.checkout(c, asst, PaymentMethod.CASH, 15, 500));
        fails("between 0 and 100", () -> sell(e, c, 120, PaymentMethod.CASH, 500));
        // stock disappears between adding to cart and paying
        e.inv.writeOff(a.getProductId(), 2, "spill", "admin");
        fails("left in stock", () -> sell(e, c, 0, PaymentMethod.CASH, 500));
        eq(1, a.getQuantity(), "nothing deducted on failure");
        eq(0, e.sales.getAll().size(), "no sale stored on failure");
        e.inv.deleteProduct(a.getProductId());
        fails("no longer", () -> sell(e, c, 0, PaymentMethod.CASH, 500));
    }

    static void voidSale() throws Exception {
        Env e = new Env();
        Product a = e.add("A", "Cat", 100, 60, 10, 1);
        User asst = e.users.addUser("sam", "Sam Fernando", Role.ASSISTANT, "secret1", e.admin);
        Cart c = new Cart();
        c.add(a, 4);
        Sale s = e.sales.checkout(c, asst, PaymentMethod.CARD, 0, 0);
        eq(6, a.getQuantity(), "after sale");
        fails("only a store manager", () -> e.sales.voidSale(s.getId(), asst, "x"));
        e.sales.voidSale(s.getId(), e.admin, "customer returned");
        eq(10, a.getQuantity(), "stock restored");
        check(s.isVoided(), "marked voided");
        eq("admin", s.getVoidedBy(), "voided by");
        fails("already voided", () -> e.sales.voidSale(s.getId(), e.admin, ""));
        eq(MovementType.VOID_RETURN, e.inv.getMovements().get(0).getType(), "ledger records return");
    }

    static void users() throws Exception {
        Env e = new Env();
        check(e.admin != null && e.admin.getRole() == Role.MANAGER, "default admin exists");
        check(e.users.isUsingDefaultPassword(e.admin), "default password detected");
        User u = e.users.addUser("kasun.p", "Kasun Perera", Role.ASSISTANT, "secret1", e.admin);
        eq(Role.ASSISTANT, u.getRole(), "role");
        check(!u.canEditProducts() && !u.canViewReports() && !u.canVoidSales(), "assistant permissions");
        check(e.admin.canEditProducts() && e.admin.canViewReports(), "manager permissions");
        fails("taken", () -> e.users.addUser("KASUN.P", "X Y", Role.ASSISTANT, "secret1", e.admin));
        fails("username must be", () -> e.users.addUser("a b", "X Y", Role.ASSISTANT, "secret1", e.admin));
        fails("at least 6", () -> e.users.addUser("zed", "Z", Role.ASSISTANT, "123", e.admin));
        fails("only a store manager", () -> e.users.addUser("zed", "Z", Role.ASSISTANT, "secret1", u));
        eq("kasun.p", e.users.authenticate("Kasun.P", "secret1").getUsername(), "login is case-insensitive on username");
        fails("invalid", () -> e.users.authenticate("kasun.p", "nope"));
        e.users.updateUser(u.getUserId(), "Kasun P.", Role.ASSISTANT, false, e.admin);
        fails("deactivated", () -> e.users.authenticate("kasun.p", "secret1"));
        fails("at least one active", () -> e.users.updateUser(e.admin.getUserId(), "Admin", Role.ASSISTANT, true, e.admin));
        fails("own account", () -> e.users.updateUser(e.admin.getUserId(), "Admin", Role.MANAGER, false, e.admin));
        // promote and keep permissions in sync
        User promoted = e.users.updateUser(u.getUserId(), "Kasun P.", Role.MANAGER, true, e.admin);
        check(promoted.canEditProducts(), "promoted user gets manager permissions");
        e.users.resetPassword(u.getUserId(), "newpass1", e.admin);
        e.users.authenticate("kasun.p", "newpass1");
        fails("incorrect", () -> e.users.changeOwnPassword(e.admin, "wrong", "abcdef"));
        e.users.changeOwnPassword(e.admin, "1234", "abcdef");
        check(!e.users.isUsingDefaultPassword(e.admin), "default password gone after change");
        // lockout: 5 bad attempts, then even the right password is refused
        for (int i = 0; i < 5; i++) {
            try { e.users.authenticate("admin", "bad"); } catch (ServiceException ignored) { }
        }
        fails("too many", () -> e.users.authenticate("admin", "abcdef"));
    }

    static void reports() throws Exception {
        Env e = new Env();
        Product a = e.add("A", "Fruit", 100, 60, 100, 1);
        Product b = e.add("B", "Dairy", 50, 20, 100, 1);
        Cart c = new Cart();
        c.add(a, 2);
        c.add(b, 2);
        Sale s1 = sell(e, c, 0, PaymentMethod.CARD, 0);     // 300 net, cost 160, profit 140
        Cart c2 = new Cart();
        c2.add(a, 1);
        Sale s2 = sell(e, c2, 10, PaymentMethod.CASH, 100); // 90 net, cost 60, profit 30
        Cart c3 = new Cart();
        c3.add(b, 5);
        Sale s3 = sell(e, c3, 0, PaymentMethod.CASH, 250);
        e.sales.voidSale(s3.getId(), e.admin, "mistake");
        ReportService.Summary sum = ReportService.summarize(e.sales.getAll());
        eq(2, sum.transactions(), "voided sale excluded");
        eq(5, sum.items(), "items");
        near(390, sum.netSales(), "net sales");
        near(10, sum.discounts(), "discounts");
        near(170, sum.profit(), "profit");
        near(195, sum.averageBasket(), "avg basket");
        var top = ReportService.topProducts(e.sales.getAll(), 5);
        eq("A", top.get(0).name(), "top product by revenue");
        eq(3, top.get(0).quantity(), "top product units");
        var days = ReportService.netSalesByDay(e.sales.getAll(), LocalDate.now().minusDays(6), LocalDate.now());
        eq(7, days.size(), "zero-filled days");
        near(390, days.get(LocalDate.now()), "today's sales");
        eq("Fruit", ReportService.byCategory(e.sales.getAll()).get(0).label(), "category ranking");
        eq(2, ReportService.byPayment(e.sales.getAll()).size(), "payment split");
        check(ReportService.toCsv(e.sales.getAll(), LocalDate.now().minusDays(6), LocalDate.now()).size() > 15, "csv rows");
    }

    static void csvImport() throws Exception {
        Env e = new Env();
        e.add("Milk", "Dairy", 250, 190, 5, 5);
        Path f = Files.createTempFile("imp", ".csv");
        Files.writeString(f, String.join("\n",
                "Name,Category,Supplier,Price,Cost,Quantity,Reorder Level,Expiry",
                "Milk,Dairy,GVD,260,200,12,6,2030-01-01",
                "\"Jam, Strawberry\",Pantry,Bee,400,,8,,",
                "Broken,Pantry,,abc,,,,",
                "Cheese,Dairy,GVD,500,9999,1,1,",
                ""));
        Inventory.ImportResult r = e.inv.importCsv(f, "admin");
        eq(1, r.added(), "added");
        eq(1, r.updated(), "updated");
        eq(2, r.errors().size(), "two bad rows reported");
        Product milk = e.inv.search("milk", null, Inventory.StatusFilter.ALL).get(0);
        near(260, milk.getPrice(), "existing product updated");
        eq(12, milk.getQuantity(), "stock reconciled via ledger");
        Product jam = e.inv.search("jam", null, Inventory.StatusFilter.ALL).get(0);
        eq("Jam, Strawberry", jam.getProductName(), "quoted comma name");
        eq(e.settings.getDefaultReorderLevel(), jam.getReorderLevel(), "default reorder level");
        eq(3, e.inv.exportRows().size(), "export rows = header + 2");
        Path bad = Files.createTempFile("bad", ".csv");
        Files.writeString(bad, "foo,bar\n1,2\n");
        fails("missing required", () -> e.inv.importCsv(bad, "admin"));
    }

    static void persistence() throws Exception {
        Env e = new Env();
        e.settings.setStoreName("Test Store, Ltd");
        e.settings.setCurrency("$");
        e.settings.setTaxRate(8.5);
        e.fm.saveSettings(e.settings);
        Product a = e.add("Cashews, roasted", "Snacks", 780, 560, 20, 5);
        a.setExpiryDate(LocalDate.of(2031, 5, 17));
        e.inv.updateProduct(a);
        e.users.addUser("sam", "Sam \"Sammy\" Fernando", Role.ASSISTANT, "secret1", e.admin);
        Cart c = new Cart();
        c.add(a, 3);
        Sale s = e.sales.checkout(c, e.admin, PaymentMethod.CASH, 5, 3000);
        e.sales.voidSale(s.getId(), e.admin, "test, with comma");

        Env r = new Env(e.dir); // "restart"
        eq("Test Store, Ltd", r.settings.getStoreName(), "settings name");
        eq("$", r.settings.getCurrency(), "settings currency");
        near(8.5, r.settings.getTaxRate(), "settings tax");
        Product a2 = r.inv.findById(a.getProductId());
        eq("Cashews, roasted", a2.getProductName(), "product name");
        eq(20, a2.getQuantity(), "quantity after sale+void");
        eq(LocalDate.of(2031, 5, 17), a2.getExpiryDate(), "expiry");
        eq("Sam \"Sammy\" Fernando", r.users.findByUsername("sam").getFullName(), "user name with quotes");
        Sale s2 = r.sales.find(s.getId());
        check(s2.isVoided(), "void flag persisted");
        eq("admin", s2.getVoidedBy(), "voidedBy persisted");
        eq(1, s2.getItems().size(), "items persisted");
        near(s.getTotal(), s2.getTotal(), "total recomputed identically");
        check(r.users.authenticate("sam", "secret1") != null, "password hash persisted");
        check(r.inv.getMovements().size() >= 3, "ledger persisted");
        check(r.log.getAll().size() >= 3, "audit log persisted");
        // a corrupted line must not stop startup
        Files.writeString(e.dir.resolve("products.csv"),
                Files.readString(e.dir.resolve("products.csv")) + "garbage line\n,,,\n");
        Env r2 = new Env(e.dir);
        eq(1, r2.inv.size(), "corrupt lines skipped");
    }

    static void demo() throws Exception {
        Env e = new Env();
        String msg = DemoData.load(e.inv, e.sales, e.users, e.log, e.admin);
        check(e.inv.size() >= 30, "catalogue loaded: " + msg);
        check(e.sales.getAll().size() > 300, "sales generated");
        check(e.inv.checkLowStockProducts().size() >= 5, "some low stock to look at");
        check(!e.inv.getExpiringSoon().isEmpty(), "some expiring items");
        check(!e.inv.getExpired().isEmpty(), "an expired item");
        int before = e.inv.size();
        DemoData.load(e.inv, e.sales, e.users, e.log, e.admin);
        eq(before, e.inv.size(), "loading twice does not duplicate products");
        Env r = new Env(e.dir);
        eq(e.sales.getAll().size(), r.sales.getAll().size(), "demo sales persisted");
    }

    static void receipt() throws Exception {
        Env e = new Env();
        e.settings.setStoreName("Green Basket");
        e.settings.setTaxRate(5);
        Product a = e.add("Organic Apple with a very long name that must be shortened", "Fruit", 120, 80, 10, 1);
        Cart c = new Cart();
        c.add(a, 2);
        Sale s = sell(e, c, 10, PaymentMethod.CASH, 500);
        String txt = ReceiptFormatter.format(s, e.settings);
        check(txt.contains("GREEN BASKET"), "store name");
        check(txt.contains("GB-000001"), "receipt number");
        check(txt.contains("Discount (10%)"), "discount line");
        check(txt.contains("Tax (5%)"), "tax line");
        check(txt.contains("Change"), "change line");
        for (String line : txt.split("\n")) check(line.length() <= ReceiptFormatter.WIDTH, "line too wide: " + line);
    }
    static void reorder() throws Exception {
        Env e = new Env();
        e.add("Plenty", "Cat", 10, 5, 100, 10);
        Product low = e.inv.addProduct(new Product(0, "Low", "Cat", "Zeta", 10, 5, 4, 10, null), "admin");
        Product out = e.inv.addProduct(new Product(0, "Out", "Cat", "Alpha", 10, 5, 0, 5, null), "admin");
        e.inv.addProduct(new Product(0, "Stale", "Cat", "Alpha", 10, 5, 0, 5, LocalDate.now().minusDays(2)), "admin");
        List<Inventory.ReorderLine> plan = e.inv.suggestReorder();
        eq(2, plan.size(), "expired stock is not reordered, healthy stock is not listed");
        eq("Out", plan.get(0).product().getProductName(), "grouped by supplier (Alpha first)");
        eq(10, plan.get(0).suggestedQty(), "tops up to twice the reorder level");
        near(50, plan.get(0).estimatedCost(), "estimated cost");
        eq(16, plan.get(1).suggestedQty(), "20 target minus 4 on hand");
        String po = ReportService.purchaseOrders(plan, "Green Basket", LocalDate.of(2026, 1, 5));
        check(po.contains("Supplier: Alpha") && po.contains("Supplier: Zeta"), "one block per supplier");
        check(po.contains("PO-20260105-01") && po.contains("PO-20260105-02"), "numbered orders");
        for (String line : po.split("\n")) check(line.length() <= 64, "PO line too wide: " + line);
        eq("Nothing to order.\n", ReportService.purchaseOrders(List.of(), "X", LocalDate.now()), "empty plan");
    }

    static void insights() throws Exception {
        Env e = new Env();
        Product a = e.add("Seller", "Cat", 100, 60, 50, 1);
        e.add("Dust", "Cat", 100, 60, 50, 1);
        Cart c = new Cart();
        c.add(a, 2);
        sell(e, c, 0, PaymentMethod.CARD, 0);
        var dead = ReportService.deadStock(e.inv.getAllProducts(), e.sales.getAll(), LocalDate.now().minusDays(30));
        eq(1, dead.size(), "one dead product");
        eq("Dust", dead.get(0).getProductName(), "the unsold one");
        double[] hours = ReportService.netSalesByHour(e.sales.getAll());
        near(200, hours[java.time.LocalDateTime.now().getHour()], "hourly bucket");
        double[] wd = ReportService.netSalesByWeekday(e.sales.getAll());
        near(200, wd[LocalDate.now().getDayOfWeek().getValue() - 1], "weekday bucket");
        check(ReportService.change(120, 100) > 19.99 && ReportService.change(120, 100) < 20.01, "change %");
        check(ReportService.change(5, 0) == null, "no baseline gives null");
    }

    static void dayClose() throws Exception {
        Env e = new Env();
        Product a = e.add("A", "Cat", 100, 60, 50, 1);
        Cart c = new Cart();
        c.add(a, 3);
        sell(e, c, 0, PaymentMethod.CASH, 300);
        Cart c2 = new Cart();
        c2.add(a, 1);
        sell(e, c2, 0, PaymentMethod.CARD, 0);
        Cart c3 = new Cart();
        c3.add(a, 2);
        Sale v = sell(e, c3, 0, PaymentMethod.CASH, 200);
        e.sales.voidSale(v.getId(), e.admin, "oops");
        String txt = ReportService.dayClose(e.sales.getToday(), LocalDate.now(), "Green Basket", "admin");
        check(txt.contains("GREEN BASKET"), "store name");
        check(txt.contains("Cash expected in drawer") && txt.contains("300.00"), "only the kept cash sale is expected in the drawer");
        check(txt.contains("Voided sales") && txt.contains("1 (200.00)"), "void listed");
        for (String line : txt.split("\n")) check(line.length() <= 42, "line too wide: " + line);
    }

    static void backup() throws Exception {
        Env e = new Env();
        e.add("Milk", "Dairy", 250, 190, 20, 5);
        Path zip = Files.createTempFile("gbbackup", ".zip");
        check(e.fm.backupTo(zip) >= 3, "files stored");
        e.add("Eggs", "Dairy", 480, 380, 10, 5);
        eq(2, new Env(e.dir).inv.size(), "two products before restore");
        e.fm.restoreFrom(zip);
        eq(1, new Env(e.dir).inv.size(), "restored to the backed-up state");
        Path junk = Files.createTempFile("junk", ".zip");
        Files.writeString(junk, "not a zip");
        try { e.fm.restoreFrom(junk); throw new AssertionError("should fail"); } catch (ServiceException ok) { /* expected */ }
        e.fm.autoBackup(3);
        check(Files.exists(e.dir.resolve("backups")), "auto backup folder");
    }

    static void legacy() throws Exception {
        Env e = new Env();
        Path f = Files.createTempFile("products", ".txt");
        Files.writeString(f, "1,Organic Apple,Fruits,120,5\n2,Organic Milk,Dairy,250,20\nbad line\n");
        eq(2, e.inv.importLegacy(f, "system"), "two legacy products");
        Product apple = e.inv.findById(1);
        eq("Organic Apple", apple.getProductName(), "name kept");
        near(120, apple.getPrice(), "price kept");
        eq(5, apple.getQuantity(), "quantity kept");
        eq(2, e.inv.getMovements().size(), "opening stock recorded");
    }

    static void atomic() throws Exception {
        Env e = new Env();
        e.add("Milk", "Dairy", 250, 190, 20, 5);
        try (var s = Files.list(e.dir)) {
            check(s.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "no leftover .tmp files");
        }
    }
}
