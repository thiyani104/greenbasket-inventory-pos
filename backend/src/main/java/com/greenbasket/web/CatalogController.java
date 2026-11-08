package com.greenbasket.web;

import com.greenbasket.security.CurrentUser;
import com.greenbasket.service.CatalogService;
import com.greenbasket.web.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Categories & suppliers")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/categories")
    public List<CategoryDto> categories() { return catalog.categories().stream().map(c -> new CategoryDto(c.getId(), c.getName())).toList(); }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public CategoryDto addCategory(@Valid @RequestBody NameRequest r) {
        var c = catalog.addCategory(r.name(), CurrentUser.get().actor());
        return new CategoryDto(c.getId(), c.getName());
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public CategoryDto renameCategory(@PathVariable Long id, @Valid @RequestBody NameRequest r) {
        var c = catalog.renameCategory(id, r.name(), CurrentUser.get().actor());
        return new CategoryDto(c.getId(), c.getName());
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public void deleteCategory(@PathVariable Long id) { catalog.deleteCategory(id, CurrentUser.get().actor()); }

    @GetMapping("/suppliers")
    public List<SupplierDto> suppliers() { return catalog.suppliers().stream().map(SupplierDto::of).toList(); }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public SupplierDto addSupplier(@Valid @RequestBody SupplierRequest r) {
        return SupplierDto.of(catalog.saveSupplier(null, r.name(), r.phone(), r.email(), r.address(), CurrentUser.get().actor()));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public SupplierDto editSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest r) {
        return SupplierDto.of(catalog.saveSupplier(id, r.name(), r.phone(), r.email(), r.address(), CurrentUser.get().actor()));
    }

    @DeleteMapping("/suppliers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public void deleteSupplier(@PathVariable Long id) { catalog.deleteSupplier(id, CurrentUser.get().actor()); }
}
