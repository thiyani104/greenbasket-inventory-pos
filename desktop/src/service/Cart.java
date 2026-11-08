package service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.PaymentMethod;
import model.Product;
import model.Sale;
import model.SaleItem;

/** The sale being rung up at the till. Totals come from a preview Sale so there is one set of maths. */
public class Cart {

    public static class Line {
        private final Product product;
        private int quantity;

        Line(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        public Product getProduct() { return product; }
        public int getQuantity() { return quantity; }
        public double getLineTotal() { return util.Formats.round2(product.getPrice() * quantity); }
    }

    private final List<Line> lines = new ArrayList<>();

    public List<Line> getLines() { return Collections.unmodifiableList(lines); }

    public boolean isEmpty() { return lines.isEmpty(); }

    public int getItemCount() {
        int n = 0;
        for (Line l : lines) n += l.quantity;
        return n;
    }

    private Line find(Product p) {
        for (Line l : lines) if (l.product.getProductId() == p.getProductId()) return l;
        return null;
    }

    public void add(Product p, int qty) {
        if (qty <= 0) throw new ServiceException("Quantity must be at least 1.");
        if (p.isExpired()) throw new ServiceException(p.getProductName() + " is past its expiry date and cannot be sold.");
        Line existing = find(p);
        int wanted = (existing == null ? 0 : existing.quantity) + qty;
        if (wanted > p.getQuantity()) {
            throw new ServiceException("Only " + p.getQuantity() + " of " + p.getProductName() + " in stock.");
        }
        if (existing == null) lines.add(new Line(p, qty));
        else existing.quantity = wanted;
    }

    public void setQuantity(int index, int qty) {
        Line l = lines.get(index);
        if (qty <= 0) {
            lines.remove(index);
            return;
        }
        if (qty > l.product.getQuantity()) {
            throw new ServiceException("Only " + l.product.getQuantity() + " of " + l.product.getProductName() + " in stock.");
        }
        l.quantity = qty;
    }

    public void remove(int index) { lines.remove(index); }

    public void clear() { lines.clear(); }

    public List<SaleItem> toItems() {
        List<SaleItem> items = new ArrayList<>();
        for (Line l : lines) {
            Product p = l.product;
            items.add(new SaleItem(p.getProductId(), p.getProductName(), p.getCategory(), p.getPrice(), p.getCost(), l.quantity));
        }
        return items;
    }

    /** A non-persisted Sale used only to compute subtotal / discount / tax / total. */
    public Sale preview(double discountPercent, double taxRate) {
        return new Sale(0, LocalDateTime.now(), "", PaymentMethod.CASH, toItems(), discountPercent, taxRate, 0);
    }
}
