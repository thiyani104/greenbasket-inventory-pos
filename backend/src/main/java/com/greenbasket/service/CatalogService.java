package com.greenbasket.service;

import com.greenbasket.domain.Category;
import com.greenbasket.domain.Supplier;
import com.greenbasket.repository.CategoryRepository;
import com.greenbasket.repository.ProductRepository;
import com.greenbasket.repository.SupplierRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categories and suppliers. */
@Service
public class CatalogService {

    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final ActivityService log;

    public CatalogService(CategoryRepository categories, SupplierRepository suppliers, ProductRepository products, ActivityService log) {
        this.categories = categories;
        this.suppliers = suppliers;
        this.products = products;
        this.log = log;
    }

    @Transactional(readOnly = true)
    public List<Category> categories() { return categories.findAllByOrderByNameKeyAsc(); }

    @Transactional
    public Category addCategory(String name, Actor actor) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw ApiException.badRequest("Category name is required.");
        if (categories.findByNameKey(Category.keyOf(n)).isPresent()) throw ApiException.conflict("Category '" + n + "' already exists.");
        Category c = categories.save(new Category(n));
        log.log(actor, "Add category", n);
        return c;
    }

    /** Finds a category by name (case-insensitive) or creates it. */
    @Transactional
    public Category findOrCreateCategory(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw ApiException.badRequest("Category is required.");
        return categories.findByNameKey(Category.keyOf(n)).orElseGet(() -> categories.save(new Category(n)));
    }

    @Transactional
    public Category renameCategory(Long id, String name, Actor actor) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found."));
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw ApiException.badRequest("Category name is required.");
        categories.findByNameKey(Category.keyOf(n)).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw ApiException.conflict("Category '" + n + "' already exists."); });
        String old = c.getName();
        c.setName(n);
        log.log(actor, "Rename category", old + " -> " + n);
        return c;
    }

    @Transactional
    public void deleteCategory(Long id, Actor actor) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found."));
        if (products.existsByCategoryId(id)) throw ApiException.conflict("Category '" + c.getName() + "' still has products.");
        categories.delete(c);
        log.log(actor, "Delete category", c.getName());
    }

    @Transactional(readOnly = true)
    public List<Supplier> suppliers() { return suppliers.findAllByOrderByNameKeyAsc(); }

    @Transactional
    public Supplier saveSupplier(Long id, String name, String phone, String email, String address, Actor actor) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw ApiException.badRequest("Supplier name is required.");
        suppliers.findByNameKey(Supplier.keyOf(n)).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw ApiException.conflict("Supplier '" + n + "' already exists."); });
        Supplier s = id == null ? new Supplier(n) : suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier not found."));
        s.setName(n);
        s.setPhone(blankToNull(phone));
        s.setEmail(blankToNull(email));
        s.setAddress(blankToNull(address));
        s = suppliers.save(s);
        log.log(actor, id == null ? "Add supplier" : "Edit supplier", n);
        return s;
    }

    @Transactional
    public Supplier findOrCreateSupplier(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return null;
        return suppliers.findByNameKey(Supplier.keyOf(n)).orElseGet(() -> suppliers.save(new Supplier(n)));
    }

    @Transactional
    public void deleteSupplier(Long id, Actor actor) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier not found."));
        if (products.existsBySupplierId(id)) throw ApiException.conflict("Supplier '" + s.getName() + "' still supplies products.");
        suppliers.delete(s);
        log.log(actor, "Delete supplier", s.getName());
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
