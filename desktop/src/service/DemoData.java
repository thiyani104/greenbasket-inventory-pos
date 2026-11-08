package service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import model.PaymentMethod;
import model.Product;
import model.Role;
import model.Sale;
import model.SaleItem;
import model.User;

/** Optional sample catalogue + 45 days of sales so every screen has something to show. */
public final class DemoData {

    // name, category, supplier, price, cost, qty, reorder, expiry days from today (-1 = none; negative below -1 = already expired)
    private static final Object[][] CATALOGUE = {
        {"Organic Apple", "Fruits", "Hill Country Farms", 120.0, 85.0, 64, 15, 9},
        {"Organic Oranges", "Fruits", "Hill Country Farms", 200.0, 140.0, 8, 12, 5},
        {"Bananas (1kg)", "Fruits", "Ceylon Fresh", 150.0, 100.0, 42, 15, 4},
        {"Papaya", "Fruits", "Ceylon Fresh", 180.0, 120.0, 0, 8, 3},
        {"Fresh Pineapple", "Fruits", "Ceylon Fresh", 260.0, 170.0, 22, 8, 6},
        {"Carrots (500g)", "Vegetables", "Nuwara Eliya Growers", 110.0, 70.0, 55, 15, 8},
        {"Tomatoes (500g)", "Vegetables", "Nuwara Eliya Growers", 140.0, 95.0, 36, 15, 5},
        {"Spinach Bunch", "Vegetables", "Nuwara Eliya Growers", 70.0, 40.0, 6, 10, 2},
        {"Red Onions (1kg)", "Vegetables", "Dambulla Wholesale", 230.0, 170.0, 70, 20, 30},
        {"Potatoes (1kg)", "Vegetables", "Dambulla Wholesale", 210.0, 150.0, 80, 20, 25},
        {"Organic Milk", "Dairy", "Green Valley Dairy", 250.0, 190.0, 30, 20, 6},
        {"Greek Yoghurt", "Dairy", "Green Valley Dairy", 180.0, 125.0, 18, 12, 10},
        {"Cheddar Cheese 200g", "Dairy", "Green Valley Dairy", 640.0, 480.0, 14, 8, 45},
        {"Farm Butter 250g", "Dairy", "Green Valley Dairy", 720.0, 560.0, 9, 8, 60},
        {"Free-range Eggs (10)", "Dairy", "Happy Hens Co.", 480.0, 380.0, 48, 20, 14},
        {"Whole Wheat Bread", "Bakery", "Daily Oven", 160.0, 105.0, 20, 15, 3},
        {"Butter Croissant", "Bakery", "Daily Oven", 120.0, 70.0, 4, 10, 1},
        {"Fruit Buns (4)", "Bakery", "Daily Oven", 200.0, 130.0, 16, 10, -2},
        {"Basmati Rice 1kg", "Pantry", "Lanka Grains", 520.0, 410.0, 90, 25, 300},
        {"Red Lentils 500g", "Pantry", "Lanka Grains", 330.0, 250.0, 60, 20, 280},
        {"Coconut Oil 750ml", "Pantry", "Palm Valley", 1150.0, 920.0, 26, 10, 220},
        {"Black Tea 200g", "Pantry", "Highland Estates", 590.0, 430.0, 34, 12, 400},
        {"Organic Honey 350g", "Pantry", "Bee Better", 1350.0, 980.0, 12, 8, 500},
        {"Sparkling Water 1L", "Beverages", "Aqua Lanka", 150.0, 90.0, 120, 30, 200},
        {"Cold-pressed Orange Juice", "Beverages", "Juice Lab", 420.0, 290.0, 15, 12, 5},
        {"Ginger Beer 330ml", "Beverages", "Aqua Lanka", 180.0, 115.0, 3, 15, 120},
        {"Roasted Cashews 150g", "Snacks", "Nutty Co.", 780.0, 560.0, 40, 12, 150},
        {"Dark Chocolate 80g", "Snacks", "Cocoa Works", 520.0, 360.0, 28, 12, 240},
        {"Vegetable Crisps", "Snacks", "Nutty Co.", 260.0, 170.0, 5, 12, 90},
        {"Bamboo Toothbrush", "Household", "EcoHome", 220.0, 130.0, 45, 10, -1},
        {"Eco Dish Soap 500ml", "Household", "EcoHome", 480.0, 320.0, 24, 10, -1},
        {"Reusable Shopping Bag", "Household", "EcoHome", 350.0, 200.0, 0, 10, -1}
    };

    private DemoData() {}

    public static String load(Inventory inventory, SalesService sales, UserService users, ActivityLog log, User actor) {
        int added = 0;
        List<Product> created = new ArrayList<>();
        for (Object[] row : CATALOGUE) {
            boolean exists = false;
            for (Product p : inventory.getAllProducts()) if (p.getProductName().equalsIgnoreCase((String) row[0])) exists = true;
            if (exists) continue;
            int expiryDays = (Integer) row[7];
            LocalDate expiry = expiryDays == -1 ? null : LocalDate.now().plusDays(expiryDays);
            Product p = new Product(0, (String) row[0], (String) row[1], (String) row[2], (Double) row[3], (Double) row[4],
                    (Integer) row[5], (Integer) row[6], expiry);
            inventory.addProduct(p, actor.getUsername());
            created.add(p);
            added++;
        }
        // a little ledger history
        int n = 0;
        for (Product p : created) {
            if (p.getQuantity() > 0 && n % 4 == 0) inventory.receiveStock(p.getProductId(), 12, null, "Weekly delivery", actor.getUsername());
            if (p.getProductName().startsWith("Fruit Buns")) inventory.writeOff(p.getProductId(), 2, "Past best-before", actor.getUsername());
            n++;
        }

        int assistantsAdded = 0;
        String[][] staff = {{"kasun", "Kasun Perera"}, {"nadee", "Nadeesha Silva"}};
        for (String[] s : staff) {
            if (users.findByUsername(s[0]) == null) {
                users.addUser(s[0], s[1], Role.ASSISTANT, "cashier123", actor);
                assistantsAdded++;
            }
        }

        int salesAdded = 0;
        if (sales.getAll().isEmpty() && !inventory.getAllProducts().isEmpty()) {
            salesAdded = generateSales(inventory, sales);
        }
        log.add(actor.getUsername(), "Sample data", added + " products, " + salesAdded + " sales, " + assistantsAdded + " staff accounts");
        return added + " products, " + salesAdded + " sales and " + assistantsAdded + " sample staff accounts added.";
    }

    private static int generateSales(Inventory inventory, SalesService sales) {
        Random rnd = new Random(42);
        List<Product> all = new ArrayList<>(inventory.getAllProducts());
        String[] cashiers = {"kasun", "nadee", "admin"};
        PaymentMethod[] pays = {PaymentMethod.CASH, PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.CARD, PaymentMethod.MOBILE};
        int id = sales.nextSaleId();
        int count = 0;
        for (int back = 45; back >= 0; back--) {
            LocalDate day = LocalDate.now().minusDays(back);
            int dow = day.getDayOfWeek().getValue();
            int perDay = 9 + rnd.nextInt(8) + (dow >= 5 ? 7 : 0);
            if (back == 0) perDay = 6;
            for (int t = 0; t < perDay; t++) {
                int lines = 1 + rnd.nextInt(5);
                List<SaleItem> items = new ArrayList<>();
                List<Integer> used = new ArrayList<>();
                for (int l = 0; l < lines; l++) {
                    Product p = all.get(rnd.nextInt(all.size()));
                    if (used.contains(p.getProductId())) continue;
                    used.add(p.getProductId());
                    items.add(new SaleItem(p.getProductId(), p.getProductName(), p.getCategory(), p.getPrice(), p.getCost(), 1 + rnd.nextInt(3)));
                }
                if (items.isEmpty()) continue;
                LocalDateTime when = LocalDateTime.of(day, LocalTime.of(8 + rnd.nextInt(12), rnd.nextInt(60), rnd.nextInt(60)));
                if (when.isAfter(LocalDateTime.now())) when = LocalDateTime.now().minusMinutes(5 + rnd.nextInt(60));
                double discount = rnd.nextInt(10) == 0 ? 5 : 0;
                Sale probe = new Sale(id, when, cashiers[rnd.nextInt(cashiers.length)], pays[rnd.nextInt(pays.length)],
                        items, discount, 0, 0);
                double tendered = probe.getPayment() == PaymentMethod.CASH ? Math.ceil(probe.getTotal() / 100.0) * 100.0 : probe.getTotal();
                Sale s = new Sale(id++, when, probe.getCashier(), probe.getPayment(), items, discount, 0, tendered);
                sales.addHistoric(s);
                count++;
            }
        }
        sales.saveAll();
        return count;
    }
}
