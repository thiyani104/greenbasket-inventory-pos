package com.greenbasket.web;

import com.greenbasket.security.CurrentUser;
import com.greenbasket.service.ProductService;
import com.greenbasket.service.SettingsService;
import com.greenbasket.web.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products & stock")
public class ProductController {

    private final ProductService products;
    private final SettingsService settings;

    public ProductController(ProductService products, SettingsService settings) {
        this.products = products;
        this.settings = settings;
    }

    private ProductDto dto(com.greenbasket.domain.Product p) { return ProductDto.of(p, settings.expiryWarnDays()); }

    @GetMapping
    public PageDto<ProductDto> search(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "false") boolean includeInactive,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size,
                                      @RequestParam(required = false) String sort) {
        boolean inactive = includeInactive && CurrentUser.get().role() == com.greenbasket.domain.Role.MANAGER;
        return PageDto.of(products.search(q, category, status, inactive, page, size, sort), this::dto);
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable Long id) { return dto(products.get(id)); }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductRequest r) {
        var p = products.create(toInput(r), CurrentUser.get().actor());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto(p));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest r) {
        return dto(products.update(id, toInput(r), CurrentUser.get().actor()));
    }

    /** Soft delete: the product disappears from selling and lists but stays in history. */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ProductDto archive(@PathVariable Long id) { return dto(products.setActive(id, false, CurrentUser.get().actor())); }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('MANAGER')")
    public ProductDto restore(@PathVariable Long id) { return dto(products.setActive(id, true, CurrentUser.get().actor())); }

    @PostMapping("/{id}/stock")
    @PreAuthorize("hasRole('MANAGER')")
    public ProductDto adjust(@PathVariable Long id, @Valid @RequestBody StockAdjustRequest r) {
        return dto(products.adjustStock(id, r.type(), r.quantity(), r.note(), CurrentUser.get().actor()));
    }

    @GetMapping("/{id}/movements")
    public PageDto<MovementDto> movementsOf(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return PageDto.of(products.movements(id, page, size), MovementDto::of);
    }

    @GetMapping("/movements")
    @PreAuthorize("hasRole('MANAGER')")
    public PageDto<MovementDto> allMovements(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return PageDto.of(products.movements(null, page, size), MovementDto::of);
    }

    private static ProductService.ProductInput toInput(ProductRequest r) {
        return new ProductService.ProductInput(r.name(), r.category(), r.supplier(), r.price(), r.cost(),
                r.quantity(), r.reorderLevel(), r.expiryDate());
    }
}
