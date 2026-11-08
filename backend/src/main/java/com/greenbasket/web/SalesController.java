package com.greenbasket.web;

import com.greenbasket.security.AppPrincipal;
import com.greenbasket.security.CurrentUser;
import com.greenbasket.service.SalesService;
import com.greenbasket.web.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
@Tag(name = "Sales (POS)")
public class SalesController {

    private final SalesService sales;

    public SalesController(SalesService sales) { this.sales = sales; }

    @PostMapping
    public ResponseEntity<SaleDto> checkout(@Valid @RequestBody CheckoutRequest r) {
        AppPrincipal me = CurrentUser.get();
        var req = new SalesService.CheckoutRequest(
                r.items().stream().map(l -> new SalesService.Line(l.productId(), l.quantity())).toList(),
                r.paymentMethod(), r.discountPercent(), r.tendered());
        return ResponseEntity.status(HttpStatus.CREATED).body(SaleDto.of(sales.checkout(req, me.actor(), me.role())));
    }

    @GetMapping
    public PageDto<SaleDto> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return PageDto.of(sales.list(from, to, page, size), SaleDto::of);
    }

    @GetMapping("/{id}")
    public SaleDto get(@PathVariable Long id) { return SaleDto.of(sales.get(id)); }

    @GetMapping("/receipt/{receiptNo}")
    public SaleDto byReceipt(@PathVariable String receiptNo) { return SaleDto.of(sales.getByReceipt(receiptNo)); }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('MANAGER')")
    public SaleDto voidSale(@PathVariable Long id, @Valid @RequestBody VoidRequest r) {
        AppPrincipal me = CurrentUser.get();
        return SaleDto.of(sales.voidSale(id, r.reason(), me.actor(), me.role()));
    }
}
