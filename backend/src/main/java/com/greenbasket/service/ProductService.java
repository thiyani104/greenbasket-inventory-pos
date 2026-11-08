package com.greenbasket.service;

import com.greenbasket.domain.Category;
import com.greenbasket.domain.MovementType;
import com.greenbasket.domain.Product;
import com.greenbasket.domain.StockMovement;
import com.greenbasket.domain.Supplier;
import com.greenbasket.domain.User;
import com.greenbasket.repository.ProductRepository;
import com.greenbasket.repository.StockMovementRepository;
import com.greenbasket.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    /** Input for create/update. Stock quantity is only used on create; later changes go through the ledger. */
    public record ProductInput(String name, String category, String supplier, BigDecimal price, BigDecimal cost,
                               Integer quantity, Integer reorderLevel, LocalDate expiryDate) { }

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final UserRepository users;
    private final CatalogService catalog;
    private final SettingsService settings;
    private final ActivityService log;

    public ProductService(ProductRepository products, StockMovementRepository movements, UserRepository users,
                          CatalogService catalog, SettingsService settings, ActivityService log) {
        this.products = products;
        this.movements = movements;
        this.users = users;
        this.catalog = catalog;
        this.settings = settings;
        this.log = log;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<Product> search(String q, String category, String status, boolean includeInactive, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), sortFor(sort));
        return products.findAll(spec(q, category, status, includeInactive), pageable);
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product " + id + " not found."));
    }

    private static Sort sortFor(String sort) {
        if (sort == null || sort.isBlank()) return Sort.by("nameKey");
        String[] p = sort.split(",");
        String field = switch (p[0]) {
            case "price" -> "price";
            case "quantity" -> "quantity";
            case "expiry" -> "expiryDate";
            case "category" -> "category.nameKey";
            default -> "nameKey";
        };
        return p.length > 1 && p[1].equalsIgnoreCase("desc") ? Sort.by(field).descending() : Sort.by(field).ascending();
    }

    private Specification<Product> spec(String q, String category, String status, boolean includeInactive) {
        final int warn = settings.expiryWarnDays();
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!includeInactive) ps.add(cb.isTrue(root.get("active")));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(root.get("nameKey"), like),
                        cb.like(root.get("category").get("nameKey"), like),
                        cb.like(cb.lower(root.join("supplier", jakarta.persistence.criteria.JoinType.LEFT).get("name")), like)));
            }
            if (category != null && !category.isBlank()) {
                ps.add(cb.equal(root.get("category").get("nameKey"), Category.keyOf(category)));
            }
            if (status != null && !status.isBlank()) {
                LocalDate today = LocalDate.now();
                Predicate expired = cb.and(cb.isNotNull(root.get("expiryDate")), cb.lessThan(root.<LocalDate>get("expiryDate"), today));
                Predicate notExpired = cb.not(expired);
                Predicate out = cb.lessThanOrEqualTo(root.<Integer>get("quantity"), 0);
                Predicate low = cb.and(cb.greaterThan(root.<Integer>get("quantity"), 0),
                        cb.le(root.<Integer>get("quantity"), root.<Integer>get("reorderLevel")));
                Predicate expiring = cb.and(cb.isNotNull(root.get("expiryDate")),
                        cb.greaterThanOrEqualTo(root.<LocalDate>get("expiryDate"), today),
                        cb.lessThanOrEqualTo(root.<LocalDate>get("expiryDate"), today.plusDays(warn)));
                switch (status.toUpperCase()) {
                    case "EXPIRED" -> ps.add(expired);
                    case "OUT" -> ps.add(cb.and(notExpired, out));
                    case "LOW" -> ps.add(cb.and(notExpired, low));
                    case "EXPIRING" -> ps.add(cb.and(notExpired, cb.not(out), cb.not(low), expiring));
                    case "OK" -> ps.add(cb.and(notExpired, cb.not(out), cb.not(low), cb.not(expiring)));
                    default -> throw ApiException.badRequest("Unknown status filter: " + status);
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public Product create(ProductInput in, Actor actor) {
        validate(in, true);
        if (products.findByNameKey(Product.keyOf(in.name())).isPresent()) {
            throw ApiException.conflict("A product named '" + in.name().trim() + "' already exists.");
        }
        Category cat = catalog.findOrCreateCategory(in.category());
        Supplier sup = catalog.findOrCreateSupplier(in.supplier());
        int qty = in.quantity() == null ? 0 : in.quantity();
        int reorder = in.reorderLevel() == null ? settings.defaultReorder() : in.reorderLevel();
        Product p = products.save(new Product(in.name(), cat, sup, in.price().setScale(2), in.cost().setScale(2), qty, reorder, in.expiryDate()));
        if (qty > 0) record(p, MovementType.INITIAL, qty, actor, "Opening stock");
        log.log(actor, "Add product", p.getName());
        return p;
    }

    @Transactional
    public Product update(Long id, ProductInput in, Actor actor) {
        validate(in, false);
        Product p = get(id);
        products.findByNameKey(Product.keyOf(in.name())).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw ApiException.conflict("A product named '" + in.name().trim() + "' already exists."); });
        p.setName(in.name());
        p.setCategory(catalog.findOrCreateCategory(in.category()));
        p.setSupplier(catalog.findOrCreateSupplier(in.supplier()));
        p.setPrice(in.price().setScale(2));
        p.setCost(in.cost().setScale(2));
        if (in.reorderLevel() != null) p.setReorderLevel(in.reorderLevel());
        p.setExpiryDate(in.expiryDate());
        p.touch();
        log.log(actor, "Edit product", p.getName());
        return p;
    }

    /** Soft delete: products referenced by sales history can never be physically removed. */
    @Transactional
    public Product setActive(Long id, boolean active, Actor actor) {
        Product p = get(id);
        p.setActive(active);
        p.touch();
        log.log(actor, active ? "Restore product" : "Archive product", p.getName());
        return p;
    }

    /**
     * RECEIVE adds, WASTAGE removes, CORRECTION sets the counted level. Runs under a row lock so it
     * cannot interleave with a checkout of the same product.
     */
    @Transactional
    public Product adjustStock(Long id, MovementType type, int quantity, String note, Actor actor) {
        Product p = products.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Product " + id + " not found."));
        int change;
        switch (type) {
            case RECEIVE -> {
                if (quantity <= 0) throw ApiException.badRequest("Quantity received must be at least 1.");
                change = quantity;
            }
            case WASTAGE -> {
                if (quantity <= 0) throw ApiException.badRequest("Quantity written off must be at least 1.");
                if (quantity > p.getQuantity()) throw ApiException.conflict("Only " + p.getQuantity() + " in stock; cannot write off " + quantity + ".");
                if (note == null || note.isBlank()) throw ApiException.badRequest("Give a reason for the write-off.");
                change = -quantity;
            }
            case CORRECTION -> {
                if (quantity < 0) throw ApiException.badRequest("Counted quantity cannot be negative.");
                change = quantity - p.getQuantity();
                if (change == 0) throw ApiException.badRequest("Counted quantity equals the current stock; nothing to correct.");
            }
            default -> throw ApiException.badRequest("Use RECEIVE, WASTAGE or CORRECTION for manual stock changes.");
        }
        p.setQuantity(p.getQuantity() + change);
        p.touch();
        record(p, type, change, actor, note);
        log.log(actor, "Stock " + type.name().toLowerCase(), p.getName() + " " + (change > 0 ? "+" : "") + change);
        return p;
    }

    /** Writes one ledger row for the product's already-updated quantity. Must run inside a transaction. */
    public StockMovement record(Product p, MovementType type, int change, Actor actor, String note) {
        User u = actor.userId() == null ? null : users.getReferenceById(actor.userId());
        return movements.save(new StockMovement(p, type, change, p.getQuantity(), u, actor.username(), note));
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> movements(Long productId, int page, int size) {
        Pageable pg = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        return productId == null ? movements.findRecent(pg) : movements.findByProduct(productId, pg);
    }

    private static void validate(ProductInput in, boolean creating) {
        if (in.name() == null || in.name().isBlank()) throw ApiException.badRequest("Product name is required.");
        if (in.name().trim().length() > 80) throw ApiException.badRequest("Product name is too long (max 80).");
        if (in.category() == null || in.category().isBlank()) throw ApiException.badRequest("Category is required.");
        if (in.price() == null || in.price().signum() <= 0) throw ApiException.badRequest("Selling price must be greater than zero.");
        if (in.cost() == null || in.cost().signum() < 0) throw ApiException.badRequest("Cost price cannot be negative.");
        if (in.cost().compareTo(in.price()) > 0) throw ApiException.badRequest("Cost price cannot be higher than the selling price.");
        if (in.price().scale() > 2 || in.cost().scale() > 2) throw ApiException.badRequest("Prices can have at most 2 decimal places.");
        if (in.reorderLevel() != null && in.reorderLevel() < 0) throw ApiException.badRequest("Reorder level cannot be negative.");
        if (creating && in.quantity() != null && in.quantity() < 0) throw ApiException.badRequest("Opening stock cannot be negative.");
    }
}
