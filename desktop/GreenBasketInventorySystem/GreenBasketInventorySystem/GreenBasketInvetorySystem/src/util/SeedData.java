package util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import model.*;
import service.*;

/** First-run catalogue and optional demo sales history. */
public final class SeedData {

    private SeedData() { }

    private static Product p(int id, String name, String cat, String sup, double cost, double price,
                             int qty, int reorder, Integer expiryDays, String barcode) {
        Product x = new Product(id, name, cat, sup, cost, price, qty, reorder,
                expiryDays == null ? null : LocalDate.now().plusDays(expiryDays));
        x.setBarcode(barcode);
        return x;
    }

    public static List<Product> catalogue() {
        return Arrays.asList(
                p(1, "Organic Apple", "Fruits", "Sunrise Orchards", 85, 120, 120, 20, 14, "4790001000011"),
                p(2, "Organic Milk 1L", "Dairy", "Meadow Dairy", 190, 250, 60, 15, 6, "4790001000028"),
                p(3, "Organic Oranges", "Fruits", "Sunrise Orchards", 140, 200, 80, 15, 12, "4790001000035"),
                p(4, "Bananas (1kg)", "Fruits", "Sunrise Orchards", 90, 140, 8, 15, 4, "4790001000042"),
                p(5, "Fresh Tomatoes (500g)", "Vegetables", "Green Valley Farm", 70, 110, 45, 12, 7, "4790001000059"),
                p(6, "Carrots (500g)", "Vegetables", "Green Valley Farm", 60, 95, 55, 12, 10, "4790001000066"),
                p(7, "Baby Spinach", "Vegetables", "Green Valley Farm", 80, 130, 6, 10, 3, "4790001000073"),
                p(8, "Red Onions (1kg)", "Vegetables", "Green Valley Farm", 150, 220, 70, 15, 30, "4790001000080"),
                p(9, "Greek Yoghurt", "Dairy", "Meadow Dairy", 120, 180, 40, 10, 9, "4790001000097"),
                p(10, "Cheddar Cheese 200g", "Dairy", "Meadow Dairy", 420, 580, 25, 8, 40, "4790001000103"),
                p(11, "Free-range Eggs (10)", "Dairy", "Hillside Poultry", 360, 480, 0, 12, 18, "4790001000110"),
                p(12, "Wholewheat Bread", "Bakery", "Stone Oven Bakery", 130, 190, 18, 10, 3, "4790001000127"),
                p(13, "Butter Croissant", "Bakery", "Stone Oven Bakery", 90, 150, 22, 8, 2, "4790001000134"),
                p(14, "Sourdough Loaf", "Bakery", "Stone Oven Bakery", 260, 380, 12, 6, -1, "4790001000141"),
                p(15, "Cold-pressed Orange Juice", "Beverages", "PureSip", 280, 390, 36, 10, 21, "4790001000158"),
                p(16, "Green Tea (25 bags)", "Beverages", "Highland Tea Co.", 310, 440, 50, 10, 365, "4790001000165"),
                p(17, "Sparkling Water 500ml", "Beverages", "PureSip", 60, 100, 90, 24, 300, "4790001000172"),
                p(18, "Brown Rice 1kg", "Pantry", "Harvest Grains", 270, 360, 65, 15, 240, "4790001000189"),
                p(19, "Rolled Oats 500g", "Pantry", "Harvest Grains", 210, 295, 48, 12, 200, "4790001000196"),
                p(20, "Extra Virgin Olive Oil", "Pantry", "Harvest Grains", 1450, 1890, 14, 5, 400, "4790001000202"),
                p(21, "Wild Honey 350g", "Pantry", "Highland Tea Co.", 640, 850, 9, 6, 500, "4790001000219"),
                p(22, "Mixed Nuts 200g", "Snacks", "Harvest Grains", 520, 720, 30, 8, 120, "4790001000226"),
                p(23, "Dark Chocolate 70%", "Snacks", "Highland Tea Co.", 290, 420, 5, 10, 150, "4790001000233"),
                p(24, "Bamboo Toothbrush", "Household", "EcoHome Ltd", 110, 190, 40, 10, null, "4790001000240"),
                p(25, "Eco Dish Soap 500ml", "Household", "EcoHome Ltd", 260, 380, 28, 8, null, "4790001000257")
        );
    }

    public static void seedCatalogue(Inventory inv) {
        inv.batch(() -> {
            for (Product p : catalogue()) inv.addProduct(p);
        });
    }

    /** Reads the old single-file format (id,name,category,price,quantity). Returns how many were imported. */
    public static int migrateLegacy(Inventory inv, java.nio.file.Path file, int defaultReorder) {
        int[] n = {0};
        try {
            List<String> lines = java.nio.file.Files.readAllLines(file);
            inv.batch(() -> {
                for (String line : lines) {
                    String[] f = line.split(",");
                    try {
                        int id = Integer.parseInt(f[0].trim());
                        double price = Double.parseDouble(f[3].trim());
                        inv.addProduct(new Product(id, f[1], f[2], "", Math.round(price * 0.75), price,
                                Integer.parseInt(f[4].trim()), defaultReorder, null));
                        n[0]++;
                    } catch (RuntimeException ignored) { }
                }
            });
        } catch (java.io.IOException e) {
            return 0;
        }
        return n[0];
    }

    /**
     * Generates {@code days} days of realistic sales (busier at weekends and lunchtime) so the
     * dashboards and reports have something to show. Stock levels are not affected.
     * Returns the number of sales created.
     */
    public static int generateSales(SalesService sales, Inventory inv, AuthService auth, Settings settings, int days) {
        List<Product> all = new ArrayList<>(inv.getAllProducts());
        if (all.isEmpty()) return 0;
        List<String> cashiers = new ArrayList<>();
        for (User u : auth.getUsers()) if (u.isActive()) cashiers.add(u.getUsername());
        if (cashiers.isEmpty()) cashiers.add("admin");
        String[] customers = {"", "", "", "Walk-in", "Walk-in", "Amara Perera", "Kasun Silva", "Nadeesha Fernando",
                "Ruwan Jayasuriya", "Tharushi Weerasinghe", "Imran Hussain", "Dilini Rathnayake"};
        String[] pay = SalesService.PAYMENT_METHODS;
        Random rnd = new Random(42);
        List<Sale> out = new ArrayList<>();
        int id = sales.nextSaleId();
        double taxRate = settings.getTaxRate();
        LocalDate today = LocalDate.now();
        for (int d = days; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            int count = (weekend ? 16 : 9) + rnd.nextInt(8);
            List<LocalDateTime> times = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int hour = rnd.nextInt(100) < 40 ? 12 + rnd.nextInt(2) : 8 + rnd.nextInt(12);
                times.add(day.atTime(hour, rnd.nextInt(60), rnd.nextInt(60)));
            }
            Collections.sort(times);
            for (LocalDateTime t : times) {
                if (t.isAfter(LocalDateTime.now())) continue;
                int lines = 1 + rnd.nextInt(5);
                Cart cart = new Cart(taxRate);
                Set<Integer> used = new HashSet<>();
                for (int l = 0; l < lines; l++) {
                    Product p = all.get((int) Math.min(all.size() - 1, Math.abs(rnd.nextGaussian()) * all.size() / 2.2));
                    if (!used.add(p.getProductId())) continue;
                    cart.getLines().add(new SaleItem(p.getProductId(), p.getProductName(), p.getCategory(),
                            1 + rnd.nextInt(3), p.getPrice(), p.getCostPrice()));
                }
                if (cart.isEmpty()) continue;
                if (rnd.nextInt(100) < 12) cart.setDiscountPercent(5);
                String method = pay[rnd.nextInt(100) < 55 ? 0 : rnd.nextInt(100) < 60 ? 1 : 2];
                double tendered = "Cash".equals(method) ? Math.ceil(cart.total() / 100.0) * 100.0 : cart.total();
                Sale s = new Sale(id++, t, cashiers.get(rnd.nextInt(cashiers.size())),
                        customers[rnd.nextInt(customers.length)], method, cart.subtotal(), cart.discount(),
                        cart.tax(), cart.total(), tendered, rnd.nextInt(100) < 2 ? Sale.VOIDED : Sale.COMPLETED);
                for (SaleItem i : cart.getLines()) s.getItems().add(i);
                out.add(s);
            }
        }
        sales.importHistorical(out);
        return out.size();
    }
}
