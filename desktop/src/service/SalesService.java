package service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.MovementType;
import model.PaymentMethod;
import model.Product;
import model.Sale;
import model.SaleItem;
import model.User;
import util.AppSettings;
import util.FileManager;

public class SalesService {

    private final FileManager files;
    private final Inventory inventory;
    private final AppSettings settings;
    private final ActivityLog log;
    private final List<Sale> sales = new ArrayList<>();

    public SalesService(FileManager files, Inventory inventory, AppSettings settings, ActivityLog log) {
        this.files = files;
        this.inventory = inventory;
        this.settings = settings;
        this.log = log;
    }

    public void load() {
        sales.clear();
        sales.addAll(files.loadSales());
    }

    private int nextId() {
        int max = 0;
        for (Sale s : sales) max = Math.max(max, s.getId());
        return max + 1;
    }

    /** Completes a sale: validates, deducts stock, stores the receipt. */
    public Sale checkout(Cart cart, User cashier, PaymentMethod payment, double discountPercent, double tendered) {
        if (cart.isEmpty()) throw new ServiceException("The cart is empty.");
        if (discountPercent < 0 || discountPercent > 100) throw new ServiceException("Discount must be between 0 and 100%.");
        if (discountPercent > cashier.getMaxDiscountPercent()) {
            throw new ServiceException("Your account can give at most " + (int) cashier.getMaxDiscountPercent()
                    + "% discount. Ask the store manager to approve more.");
        }
        // re-check every line against live stock before touching anything
        for (Cart.Line l : cart.getLines()) {
            Product p = inventory.findById(l.getProduct().getProductId());
            if (p == null) throw new ServiceException(l.getProduct().getProductName() + " is no longer in the catalogue.");
            if (p.isExpired()) throw new ServiceException(p.getProductName() + " has expired and cannot be sold.");
            if (l.getQuantity() > p.getQuantity()) {
                throw new ServiceException("Only " + p.getQuantity() + " of " + p.getProductName() + " left in stock.");
            }
        }
        Sale preview = cart.preview(discountPercent, settings.getTaxRate());
        double paid = payment == PaymentMethod.CASH ? tendered : preview.getTotal();
        if (payment == PaymentMethod.CASH && paid + 0.005 < preview.getTotal()) {
            throw new ServiceException("Cash tendered is less than the total.");
        }
        Sale sale = new Sale(nextId(), LocalDateTime.now().withNano(0), cashier.getUsername(), payment,
                cart.toItems(), discountPercent, settings.getTaxRate(), paid);
        for (SaleItem i : sale.getItems()) {
            inventory.changeStock(i.getProductId(), -i.getQuantity(), MovementType.SALE,
                    sale.getReceiptNumber(), cashier.getUsername());
        }
        sales.add(sale);
        files.saveSales(sales);
        log.add(cashier.getUsername(), "Sale", sale.getReceiptNumber() + " · " + sale.getItemCount()
                + " items · " + util.Formats.money(sale.getTotal()) + " · " + payment.getLabel());
        return sale;
    }

    public void voidSale(int saleId, User by, String reason) {
        if (!by.canVoidSales()) throw new ServiceException("Only a store manager can void a sale.");
        Sale s = find(saleId);
        if (s == null) throw new ServiceException("Sale not found.");
        if (s.isVoided()) throw new ServiceException(s.getReceiptNumber() + " is already voided.");
        for (SaleItem i : s.getItems()) {
            if (inventory.findById(i.getProductId()) != null) {
                inventory.changeStock(i.getProductId(), i.getQuantity(), MovementType.VOID_RETURN,
                        s.getReceiptNumber() + " voided" + (reason.isBlank() ? "" : ": " + reason), by.getUsername());
            }
        }
        s.markVoided(by.getUsername(), LocalDateTime.now().withNano(0));
        files.saveSales(sales);
        log.add(by.getUsername(), "Void sale", s.getReceiptNumber() + (reason.isBlank() ? "" : " · " + reason));
    }

    /** Adds an already-completed sale without touching stock (sample data, imports). */
    public void addHistoric(Sale s) {
        sales.add(s);
    }

    public void saveAll() {
        files.saveSales(sales);
    }

    public int nextSaleId() { return nextId(); }

    public Sale find(int id) {
        for (Sale s : sales) if (s.getId() == id) return s;
        return null;
    }

    /** Newest first. */
    public List<Sale> getAll() {
        List<Sale> copy = new ArrayList<>(sales);
        copy.sort((a, b) -> b.getDateTime().compareTo(a.getDateTime()));
        return copy;
    }

    public List<Sale> between(LocalDate from, LocalDate to) {
        List<Sale> out = new ArrayList<>();
        for (Sale s : getAll()) {
            LocalDate d = s.getDateTime().toLocalDate();
            if ((from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to))) out.add(s);
        }
        return out;
    }

    public List<Sale> forCashier(String username, LocalDate from, LocalDate to) {
        List<Sale> out = new ArrayList<>();
        for (Sale s : between(from, to)) if (s.getCashier().equalsIgnoreCase(username)) out.add(s);
        return out;
    }

    public List<Sale> getToday() {
        LocalDate t = LocalDate.now();
        return Collections.unmodifiableList(between(t, t));
    }
}
