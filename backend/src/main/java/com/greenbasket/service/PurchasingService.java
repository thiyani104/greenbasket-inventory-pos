package com.greenbasket.service;

import com.greenbasket.domain.MovementType;
import com.greenbasket.domain.PoStatus;
import com.greenbasket.domain.Product;
import com.greenbasket.domain.PurchaseOrder;
import com.greenbasket.domain.PurchaseOrderItem;
import com.greenbasket.domain.Supplier;
import com.greenbasket.repository.ProductRepository;
import com.greenbasket.repository.PurchaseOrderRepository;
import com.greenbasket.repository.SupplierRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Purchase orders: DRAFT -> ORDERED -> RECEIVED (or CANCELLED). Receiving posts RECEIVE rows to the stock ledger. */
@Service
public class PurchasingService {

    public record PoLine(Long productId, int quantity, BigDecimal unitCost) { }

    private final PurchaseOrderRepository orders;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final ProductService productService;
    private final ActivityService log;

    public PurchasingService(PurchaseOrderRepository orders, ProductRepository products, SupplierRepository suppliers,
                             ProductService productService, ActivityService log) {
        this.orders = orders;
        this.products = products;
        this.suppliers = suppliers;
        this.productService = productService;
        this.log = log;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> list(PoStatus status) {
        return status == null ? orders.findAllByOrderByCreatedAtDescIdDesc() : orders.findByStatusOrderByCreatedAtDescIdDesc(status);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(Long id) {
        return orders.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Purchase order not found."));
    }

    @Transactional
    public PurchaseOrder create(Long supplierId, String notes, List<PoLine> lines, Actor actor) {
        if (lines == null || lines.isEmpty()) throw ApiException.badRequest("Add at least one product to the order.");
        Supplier supplier = supplierId == null ? null
                : suppliers.findById(supplierId).orElseThrow(() -> ApiException.notFound("Supplier not found."));
        PurchaseOrder po = new PurchaseOrder("PENDING", supplier, notes == null || notes.isBlank() ? null : notes.trim(), actor.username());
        Set<Long> seen = new HashSet<>();
        for (PoLine l : lines) {
            if (l.productId() == null || !seen.add(l.productId())) throw ApiException.badRequest("Each product can appear once per order.");
            if (l.quantity() <= 0) throw ApiException.badRequest("Order quantity must be at least 1.");
            Product p = products.findById(l.productId()).orElseThrow(() -> ApiException.notFound("Product " + l.productId() + " not found."));
            BigDecimal cost = l.unitCost() == null ? p.getCost() : l.unitCost();
            if (cost.signum() < 0) throw ApiException.badRequest("Unit cost cannot be negative.");
            po.addItem(new PurchaseOrderItem(p, l.quantity(), cost.setScale(2, java.math.RoundingMode.HALF_UP)));
        }
        po = orders.saveAndFlush(po);
        po.setPoNumber(String.format("PO-%d-%04d", LocalDate.now().getYear(), po.getId()));
        log.log(actor, "Create PO", po.getPoNumber() + " · " + po.getItems().size() + " lines");
        return po;
    }

    @Transactional
    public PurchaseOrder markOrdered(Long id, Actor actor) {
        PurchaseOrder po = get(id);
        if (po.getStatus() != PoStatus.DRAFT) throw ApiException.conflict(po.getPoNumber() + " is " + po.getStatus() + ", not DRAFT.");
        po.setStatus(PoStatus.ORDERED);
        po.setOrderedAt(LocalDateTime.now().withNano(0));
        log.log(actor, "Order PO", po.getPoNumber());
        return po;
    }

    @Transactional
    public PurchaseOrder cancel(Long id, Actor actor) {
        PurchaseOrder po = get(id);
        if (po.getStatus() == PoStatus.RECEIVED) throw ApiException.conflict("A received order cannot be cancelled.");
        if (po.getStatus() == PoStatus.CANCELLED) throw ApiException.conflict(po.getPoNumber() + " is already cancelled.");
        po.setStatus(PoStatus.CANCELLED);
        log.log(actor, "Cancel PO", po.getPoNumber());
        return po;
    }

    /**
     * Receives the outstanding quantity of every line (or the given per-product quantities, which may be partial
     * deliveries). The order becomes RECEIVED once every line is fully delivered; until then it stays ORDERED.
     */
    @Transactional
    public PurchaseOrder receive(Long id, Map<Long, Integer> quantities, Actor actor) {
        PurchaseOrder po = get(id);
        if (po.getStatus() != PoStatus.ORDERED) {
            throw ApiException.conflict(po.getPoNumber() + " is " + po.getStatus() + "; only ORDERED orders can be received.");
        }
        List<Long> ids = po.getItems().stream().map(i -> i.getProduct().getId()).sorted().toList();
        Map<Long, Product> locked = products.findAllByIdForUpdate(ids).stream().collect(Collectors.toMap(Product::getId, p -> p));
        int units = 0;
        for (PurchaseOrderItem i : po.getItems()) {
            int outstanding = i.getQuantityOrdered() - i.getQuantityReceived();
            int now = quantities == null || quantities.isEmpty() ? outstanding : quantities.getOrDefault(i.getProduct().getId(), 0);
            if (now < 0) throw ApiException.badRequest("Received quantity cannot be negative.");
            if (now > outstanding) {
                throw ApiException.badRequest("Only " + outstanding + " of " + i.getProduct().getName() + " are outstanding.");
            }
            if (now == 0) continue;
            Product p = locked.get(i.getProduct().getId());
            p.setQuantity(p.getQuantity() + now);
            p.touch();
            if (i.getUnitCost().compareTo(p.getPrice()) <= 0) p.setCost(i.getUnitCost());   // keep margin rule valid
            i.setQuantityReceived(i.getQuantityReceived() + now);
            productService.record(p, MovementType.RECEIVE, now, actor, po.getPoNumber());
            units += now;
        }
        if (units == 0) throw ApiException.badRequest("Nothing to receive.");
        boolean complete = po.getItems().stream().allMatch(i -> i.getQuantityReceived() >= i.getQuantityOrdered());
        if (complete) {
            po.setStatus(PoStatus.RECEIVED);
            po.setReceivedAt(LocalDateTime.now().withNano(0));
        }
        log.log(actor, complete ? "Receive PO" : "Partial receive PO", po.getPoNumber() + " · " + units + " units");
        return po;
    }
}
