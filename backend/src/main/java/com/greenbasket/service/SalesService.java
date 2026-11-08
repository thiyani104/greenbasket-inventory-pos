package com.greenbasket.service;

import com.greenbasket.domain.MovementType;
import com.greenbasket.domain.PaymentMethod;
import com.greenbasket.domain.Product;
import com.greenbasket.domain.Role;
import com.greenbasket.domain.Sale;
import com.greenbasket.domain.SaleItem;
import com.greenbasket.domain.User;
import com.greenbasket.repository.ProductRepository;
import com.greenbasket.repository.SaleRepository;
import com.greenbasket.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesService {

    public record Line(Long productId, int quantity) { }

    public record CheckoutRequest(List<Line> items, PaymentMethod paymentMethod, BigDecimal discountPercent, BigDecimal tendered) { }

    private static final BigDecimal ASSISTANT_MAX_DISCOUNT = BigDecimal.TEN;

    private final SaleRepository sales;
    private final ProductRepository products;
    private final UserRepository users;
    private final ProductService productService;
    private final SettingsService settings;
    private final ActivityService log;

    public SalesService(SaleRepository sales, ProductRepository products, UserRepository users,
                        ProductService productService, SettingsService settings, ActivityService log) {
        this.sales = sales;
        this.products = products;
        this.users = users;
        this.productService = productService;
        this.settings = settings;
        this.log = log;
    }

    /**
     * Completes a sale atomically: locks every product row (in id order), re-checks stock and expiry against the
     * locked rows, deducts stock, writes ledger rows and the receipt. Any failure rolls the whole thing back.
     */
    @Transactional
    public Sale checkout(CheckoutRequest req, Actor actor, Role role) {
        if (req.items() == null || req.items().isEmpty()) throw ApiException.badRequest("The cart is empty.");
        PaymentMethod method = req.paymentMethod() == null ? PaymentMethod.CASH : req.paymentMethod();
        BigDecimal discount = req.discountPercent() == null ? BigDecimal.ZERO : req.discountPercent();
        if (discount.signum() < 0 || discount.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("Discount must be between 0 and 100%.");
        }
        if (role != Role.MANAGER && discount.compareTo(ASSISTANT_MAX_DISCOUNT) > 0) {
            throw ApiException.forbidden("Your account can give at most " + ASSISTANT_MAX_DISCOUNT.intValue()
                    + "% discount. Ask the store manager to approve more.");
        }

        // merge duplicate lines
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (Line l : req.items()) {
            if (l.productId() == null) throw ApiException.badRequest("Every line needs a productId.");
            if (l.quantity() <= 0) throw ApiException.badRequest("Quantity must be at least 1.");
            wanted.merge(l.productId(), l.quantity(), Integer::sum);
        }
        Map<Long, Product> locked = products.findAllByIdForUpdate(wanted.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        User cashier = actor.userId() == null ? null : users.getReferenceById(actor.userId());
        Sale sale = new Sale(cashier, actor.username(), method, discount.setScale(2, java.math.RoundingMode.HALF_UP),
                settings.taxRate().setScale(2, java.math.RoundingMode.HALF_UP), BigDecimal.ZERO);

        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product p = locked.get(e.getKey());
            if (p == null || !p.isActive()) throw ApiException.notFound("Product " + e.getKey() + " is not available.");
            if (p.isExpired()) throw ApiException.conflict(p.getName() + " has expired and cannot be sold.");
            if (e.getValue() > p.getQuantity()) {
                throw ApiException.conflict("Only " + p.getQuantity() + " of " + p.getName() + " left in stock.");
            }
            sale.addItem(new SaleItem(p, e.getValue()));
        }

        BigDecimal total = sale.total();
        BigDecimal paid = method == PaymentMethod.CASH ? (req.tendered() == null ? BigDecimal.ZERO : req.tendered()) : total;
        if (method == PaymentMethod.CASH && paid.compareTo(total) < 0) {
            throw ApiException.badRequest("Cash tendered is less than the total (" + total.toPlainString() + ").");
        }
        sale.setTendered(paid.setScale(2, java.math.RoundingMode.HALF_UP));
        sale = sales.saveAndFlush(sale);              // need the generated id for the receipt number
        sale.setReceiptNo(String.format("GB-%06d", sale.getId()));

        for (SaleItem item : sale.getItems()) {
            Product p = locked.get(item.getProduct().getId());
            p.setQuantity(p.getQuantity() - item.getQuantity());
            p.touch();
            productService.record(p, MovementType.SALE, -item.getQuantity(), actor, sale.getReceiptNo());
        }
        log.log(actor, "Sale", sale.getReceiptNo() + " · " + sale.unitCount() + " items · " + sale.total().toPlainString());
        return sale;
    }

    /** Voids a sale and puts the stock back. Manager-only (enforced at the web layer as well). */
    @Transactional
    public Sale voidSale(Long id, String reason, Actor actor, Role role) {
        if (role != Role.MANAGER) throw ApiException.forbidden("Only a store manager can void a sale.");
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Give a reason for the void.");
        Sale s = sales.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Sale not found."));
        if (s.isVoided()) throw ApiException.conflict(s.getReceiptNo() + " is already voided.");

        List<Long> ids = s.getItems().stream().map(i -> i.getProduct().getId()).distinct().sorted().toList();
        Map<Long, Product> locked = products.findAllByIdForUpdate(ids).stream().collect(Collectors.toMap(Product::getId, p -> p));
        for (SaleItem i : s.getItems()) {
            Product p = locked.get(i.getProduct().getId());
            p.setQuantity(p.getQuantity() + i.getQuantity());
            p.touch();
            productService.record(p, MovementType.VOID_RETURN, i.getQuantity(), actor, s.getReceiptNo() + " voided: " + reason);
        }
        s.markVoided(actor.username(), reason.trim());
        log.log(actor, "Void sale", s.getReceiptNo() + " · " + reason.trim());
        return s;
    }

    @Transactional(readOnly = true)
    public Sale get(Long id) {
        return sales.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Sale not found."));
    }

    @Transactional(readOnly = true)
    public Sale getByReceipt(String receiptNo) {
        return sales.findWithItemsByReceiptNo(receiptNo).orElseThrow(() -> ApiException.notFound("Receipt not found."));
    }

    /** Date range is inclusive on both ends; defaults to today. Newest first. */
    @Transactional(readOnly = true)
    public Page<Sale> list(LocalDate from, LocalDate to, int page, int size) {
        LocalDate f = from == null ? LocalDate.now() : from;
        LocalDate t = to == null ? f : to;
        if (t.isBefore(f)) throw ApiException.badRequest("'to' cannot be before 'from'.");
        Page<Sale> result = sales.findBetween(f.atStartOfDay(), t.plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
        result.getContent().forEach(s -> s.getItems().size());   // initialise lazily inside the transaction
        return result;
    }
}
