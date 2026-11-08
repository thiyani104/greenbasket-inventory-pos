import java.io.File;
import java.nio.file.*;
import java.time.LocalDate;
import model.*;
import service.*;
import util.*;

/**
 * Dependency-free self test for the business logic (no JUnit needed).
 * Run:  javac -d out -sourcepath src test/SelfTest.java && java -cp out SelfTest
 */
public class SelfTest {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) passed++; else { failed++; System.out.println("  FAIL: " + name); }
    }

    static void throwsIae(String name, Runnable r) {
        try { r.run(); check(name, false); } catch (IllegalArgumentException e) { check(name, true); }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("gb-test");
        SystemContext.init(dir);
        Inventory inv = SystemContext.inventory;
        AuthService auth = SystemContext.auth;
        SalesService sales = SystemContext.sales;

        // ---- seed + persistence
        check("seeded catalogue", inv.size() >= 15);
        int n = inv.size();
        Product p = inv.addProduct(new Product(inv.nextProductId(), "Test Mango", "Fruits", "Acme", 50, 100, 20, 5, LocalDate.now().plusDays(30)));
        check("add product", inv.size() == n + 1);
        throwsIae("duplicate name rejected", () -> SystemContext.inventory.addProduct(new Product(SystemContext.inventory.nextProductId(), "test mango", "Fruits", "", 1, 2, 3, 1, null)));
        throwsIae("negative price rejected", () -> SystemContext.inventory.addProduct(new Product(SystemContext.inventory.nextProductId(), "Bad", "X", "", 1, -2, 3, 1, null)));
        throwsIae("duplicate id rejected", () -> SystemContext.inventory.addProduct(new Product(p.getProductId(), "Other", "X", "", 1, 2, 3, 1, null)));

        SystemContext.init(dir); // reload from disk
        check("products survive restart", SystemContext.inventory.findById(p.getProductId()) != null);
        inv = SystemContext.inventory; auth = SystemContext.auth; sales = SystemContext.sales;
        Product mango = inv.findById(p.getProductId());

        // ---- stock logic
        mango.setQuantity(5);
        check("low stock at reorder level", mango.isLowStock());
        mango.setQuantity(0);
        check("out of stock", mango.isOutOfStock() && !mango.isLowStock());
        mango.setQuantity(20);
        final Inventory fi = inv; final int mid = mango.getProductId();
        throwsIae("cannot go below zero", () -> fi.adjustStock(mid, -21, "WRITE_OFF", "x"));
        inv.adjustStock(mid, 10, "RESTOCK", "delivery");
        check("restock", mango.getQuantity() == 30);
        check("ledger records movement", inv.getMovements().get(0).getChange() == 10);
        check("expired detection", new Product(99, "x", "y", "", 1, 2, 3, 1, LocalDate.now().minusDays(1)).isExpired());
        check("expiring soon", new Product(99, "x", "y", "", 1, 2, 3, 1, LocalDate.now().plusDays(3)).isExpiringSoon(7));

        // ---- cart + checkout
        Cart cart = new Cart(10); // 10% tax
        cart.add(mango, 2);      // 2 x 100 = 200
        cart.setDiscountPercent(10); // -20
        check("subtotal", cart.subtotal() == 200.0);
        check("discount", cart.discount() == 20.0);
        check("tax on discounted", cart.tax() == 18.0);
        check("total", cart.total() == 198.0);
        final Cart fc = cart;
        throwsIae("cannot oversell in cart", () -> fc.add(mango, 100));
        throwsIae("cash must cover total", () -> SystemContext.sales.checkout(fc, "admin", "", "Cash", 100));
        Sale s = sales.checkout(cart, "admin", "Walk-in", "Cash", 200);
        check("stock reduced after sale", mango.getQuantity() == 28);
        check("change", Math.abs(s.getChange() - 2.0) < 1e-9);
        check("sale persisted", sales.findById(s.getId()) != null && s.getItems().size() == 1);
        check("profit", Math.abs(s.profit() - (200 - 20 - 100)) < 1e-9);
        sales.voidSale(s, "admin");
        check("void restores stock", mango.getQuantity() == 30);
        throwsIae("double void rejected", () -> SystemContext.sales.voidSale(SystemContext.sales.findById(s.getId()), "admin"));
        check("voided excluded from revenue", SystemContext.reports.summary(LocalDate.now(), LocalDate.now()).orders == 0);

        SystemContext.init(dir);
        check("sales survive restart", SystemContext.sales.findById(s.getId()).isVoided());
        sales = SystemContext.sales;

        Cart c2 = new Cart(0);
        c2.add(SystemContext.inventory.findById(mid), 1);
        sales.checkout(c2, "admin", "", "Card", 0);
        ReportService.Summary sum = SystemContext.reports.summary(LocalDate.now(), LocalDate.now());
        check("report revenue", sum.orders == 1 && sum.revenue == 100.0);
        check("daily revenue zero-filled", SystemContext.reports.dailyRevenue(7).size() == 7);

        // ---- auth
        auth = SystemContext.auth;
        User admin = auth.findByUsername("admin");
        check("default admin uses default pw", auth.isUsingDefaultPassword(admin));
        try { auth.authenticate("admin", "1234"); check("login ok", true); } catch (Exception e) { check("login ok", false); }
        throwsIae("weak password rejected", () -> SystemContext.auth.createUser(Role.ASSISTANT, "sam", "Sam", "abc", "admin"));
        User sam = auth.createUser(Role.ASSISTANT, "sam", "Sam Silva", "fresh123", "admin");
        check("assistant is not manager", !sam.isManager());
        try { auth.authenticate("SAM", "fresh123"); check("assistant can log in (case-insens)", true); } catch (Exception e) { check("assistant can log in", false); }
        throwsIae("duplicate username rejected", () -> SystemContext.auth.createUser(Role.ASSISTANT, "Sam", "Other", "fresh123", "admin"));
        int fails = 0;
        for (int i = 0; i < 3; i++) { try { auth.authenticate("sam", "wrong" + i); } catch (AuthService.AuthException e) { fails++; } }
        check("3 failures", fails == 3);
        try { auth.authenticate("sam", "fresh123"); check("locked after 3 failures", false); } catch (AuthService.AuthException e) { check("locked after 3 failures", e.getMessage().contains("Locked") || e.getMessage().contains("Too many")); }
        throwsIae("cannot delete last manager", () -> SystemContext.auth.deleteUser(SystemContext.auth.findByUsername("admin"), "other"));
        throwsIae("cannot demote self", () -> SystemContext.auth.setActive(SystemContext.auth.findByUsername("admin"), false, "admin"));
        auth.changePassword(admin, "1234", "newpass9");
        check("password changed", !auth.isUsingDefaultPassword(admin) && admin.checkPassword("newpass9"));
        String usersFile = Files.readString(dir.resolve("users.csv"));
        check("no plaintext passwords on disk", !usersFile.contains("newpass9") && !usersFile.contains("fresh123"));

        // ---- csv round trip with tricky characters
        Product tricky = SystemContext.inventory.addProduct(new Product(SystemContext.inventory.nextProductId(),
                "Salt, \"fine\" 1kg", "Pantry", "A, B & Co", 10, 20, 5, 2, null));
        File csv = new File(dir.toFile(), "export.csv");
        SystemContext.inventory.exportCsv(csv);
        SystemContext.inventory.deleteProduct(tricky.getProductId());
        String res = SystemContext.inventory.importCsv(csv);
        Product back = SystemContext.inventory.findById(tricky.getProductId());
        check("csv round trip keeps commas/quotes", back != null && back.getProductName().equals("Salt, \"fine\" 1kg") && back.getSupplier().equals("A, B & Co"));
        check("import summary", res.contains("1 added"));

        // ---- legacy products.txt migration
        Path dir2 = Files.createTempDirectory("gb-legacy");
        Files.writeString(dir2.resolve("products.txt"), "1,Organic Apple,Fruits,120,5\n2,Organic Milk,Dairy,250,20\n");
        SystemContext.init(dir2);
        check("legacy file migrated", SystemContext.inventory.size() == 2 && SystemContext.inventory.findById(1).getPrice() == 120);

        System.out.println(failed == 0 ? "ALL " + passed + " CHECKS PASSED" : failed + " FAILED, " + passed + " passed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
