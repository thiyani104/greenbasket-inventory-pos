package com.greenbasket.web;

import com.greenbasket.domain.PoStatus;
import com.greenbasket.security.CurrentUser;
import com.greenbasket.service.PurchasingService;
import com.greenbasket.web.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/purchase-orders")
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Purchasing")
public class PurchaseController {

    private final PurchasingService purchasing;

    public PurchaseController(PurchasingService purchasing) { this.purchasing = purchasing; }

    @GetMapping
    public List<PoDto> list(@RequestParam(required = false) PoStatus status) {
        return purchasing.list(status).stream().map(PoDto::of).toList();
    }

    @GetMapping("/{id}")
    public PoDto get(@PathVariable Long id) { return PoDto.of(purchasing.get(id)); }

    @PostMapping
    public ResponseEntity<PoDto> create(@Valid @RequestBody PoRequest r) {
        var lines = r.items().stream().map(l -> new PurchasingService.PoLine(l.productId(), l.quantity(), l.unitCost())).toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PoDto.of(purchasing.create(r.supplierId(), r.notes(), lines, CurrentUser.get().actor())));
    }

    @PostMapping("/{id}/order")
    public PoDto order(@PathVariable Long id) { return PoDto.of(purchasing.markOrdered(id, CurrentUser.get().actor())); }

    @PostMapping("/{id}/receive")
    public PoDto receive(@PathVariable Long id, @RequestBody(required = false) ReceiveRequest r) {
        return PoDto.of(purchasing.receive(id, r == null ? null : r.quantities(), CurrentUser.get().actor()));
    }

    @PostMapping("/{id}/cancel")
    public PoDto cancel(@PathVariable Long id) { return PoDto.of(purchasing.cancel(id, CurrentUser.get().actor())); }
}
