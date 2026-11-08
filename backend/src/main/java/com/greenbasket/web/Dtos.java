package com.greenbasket.web;

import com.greenbasket.domain.ActivityLog;
import com.greenbasket.domain.MovementType;
import com.greenbasket.domain.PaymentMethod;
import com.greenbasket.domain.PoStatus;
import com.greenbasket.domain.Product;
import com.greenbasket.domain.PurchaseOrder;
import com.greenbasket.domain.PurchaseOrderItem;
import com.greenbasket.domain.Role;
import com.greenbasket.domain.Sale;
import com.greenbasket.domain.SaleItem;
import com.greenbasket.domain.StockMovement;
import com.greenbasket.domain.Supplier;
import com.greenbasket.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

/** Request and response shapes of the REST API. Entities are never serialised directly. */
public final class Dtos {

    private Dtos() { }

    // ============================================================ common
    public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <E, T> PageDto<T> of(Page<E> p, java.util.function.Function<E, T> map) {
            return new PageDto<>(p.getContent().stream().map(map).toList(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    // ============================================================ auth
    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
    public record RefreshRequest(@NotBlank String refreshToken) { }
    public record LogoutRequest(String refreshToken) { }
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) { }
    public record ForgotPasswordRequest(@NotBlank String usernameOrEmail) { }
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) { }

    // ============================================================ catalogue
    public record NameRequest(@NotBlank @Size(max = 60) String name) { }
    public record CategoryDto(Long id, String name) { }

    public record SupplierRequest(@NotBlank @Size(max = 100) String name, @Size(max = 40) String phone,
                                  @Email @Size(max = 150) String email, @Size(max = 255) String address) { }
    public record SupplierDto(Long id, String name, String phone, String email, String address) {
        public static SupplierDto of(Supplier s) { return new SupplierDto(s.getId(), s.getName(), s.getPhone(), s.getEmail(), s.getAddress()); }
    }

    // ============================================================ products
    public record ProductRequest(@NotBlank @Size(max = 80) String name,
                                 @NotBlank @Size(max = 60) String category,
                                 @Size(max = 100) String supplier,
                                 @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal price,
                                 @NotNull @DecimalMin("0.00") BigDecimal cost,
                                 @Min(0) Integer quantity,
                                 @Min(0) Integer reorderLevel,
                                 LocalDate expiryDate) { }

    public record ProductDto(Long id, String name, String category, String supplier, BigDecimal price, BigDecimal cost,
                             BigDecimal marginPercent, int quantity, int reorderLevel, LocalDate expiryDate,
                             Integer daysToExpiry, String status, boolean active) {
        public static ProductDto of(Product p, int warnDays) {
            return new ProductDto(p.getId(), p.getName(), p.getCategory().getName(),
                    p.getSupplier() == null ? null : p.getSupplier().getName(), p.getPrice(), p.getCost(), p.marginPercent(),
                    p.getQuantity(), p.getReorderLevel(), p.getExpiryDate(), p.daysToExpiry(), p.statusFor(warnDays).name(), p.isActive());
        }
    }

    public record StockAdjustRequest(@NotNull MovementType type, @Min(0) int quantity, @Size(max = 255) String note) { }

    public record MovementDto(Long id, Long productId, String productName, String type, int change, int balanceAfter,
                              String user, String note, LocalDateTime createdAt) {
        public static MovementDto of(StockMovement m) {
            return new MovementDto(m.getId(), m.getProduct().getId(), m.getProduct().getName(), m.getType().name(),
                    m.getQuantityChange(), m.getBalanceAfter(), m.getUserName(), m.getNote(), m.getCreatedAt());
        }
    }

    // ============================================================ sales
    public record SaleLineRequest(@NotNull Long productId, @Min(1) int quantity) { }

    public record CheckoutRequest(@NotEmpty @Valid List<SaleLineRequest> items, PaymentMethod paymentMethod,
                                  @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
                                  @DecimalMin("0") BigDecimal tendered) { }

    public record VoidRequest(@NotBlank @Size(max = 255) String reason) { }

    public record SaleItemDto(Long productId, String name, String category, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        static SaleItemDto of(SaleItem i) {
            return new SaleItemDto(i.getProductId(), i.getProductName(), i.getCategoryName(), i.getUnitPrice(), i.getQuantity(), i.lineTotal());
        }
    }

    public record SaleDto(Long id, String receiptNo, LocalDateTime createdAt, String cashier, PaymentMethod paymentMethod,
                          BigDecimal subtotal, BigDecimal discountPercent, BigDecimal discountAmount, BigDecimal taxRate,
                          BigDecimal taxAmount, BigDecimal total, BigDecimal tendered, BigDecimal change, int units,
                          boolean voided, String voidedBy, LocalDateTime voidedAt, String voidReason, List<SaleItemDto> items) {
        public static SaleDto of(Sale s) {
            return new SaleDto(s.getId(), s.getReceiptNo(), s.getCreatedAt(), s.getCashierName(), s.getPaymentMethod(),
                    s.subtotal(), s.getDiscountPercent(), s.discountAmount(), s.getTaxRate(), s.taxAmount(), s.total(),
                    s.getTendered(), s.change(), s.unitCount(), s.isVoided(), s.getVoidedBy(), s.getVoidedAt(), s.getVoidReason(),
                    s.getItems().stream().map(SaleItemDto::of).toList());
        }
    }

    // ============================================================ purchasing
    public record PoLineRequest(@NotNull Long productId, @Min(1) int quantity, @DecimalMin("0") BigDecimal unitCost) { }
    public record PoRequest(Long supplierId, @Size(max = 255) String notes, @NotEmpty @Valid List<PoLineRequest> items) { }
    /** Empty map = receive everything outstanding. */
    public record ReceiveRequest(java.util.Map<Long, Integer> quantities) { }

    public record PoItemDto(Long productId, String name, int quantityOrdered, int quantityReceived, BigDecimal unitCost, BigDecimal lineTotal) {
        static PoItemDto of(PurchaseOrderItem i) {
            return new PoItemDto(i.getProduct().getId(), i.getProduct().getName(), i.getQuantityOrdered(),
                    i.getQuantityReceived(), i.getUnitCost(), i.lineTotal());
        }
    }

    public record PoDto(Long id, String poNumber, Long supplierId, String supplier, PoStatus status, String notes, String createdBy,
                        LocalDateTime createdAt, LocalDateTime orderedAt, LocalDateTime receivedAt, BigDecimal total, List<PoItemDto> items) {
        public static PoDto of(PurchaseOrder o) {
            return new PoDto(o.getId(), o.getPoNumber(), o.getSupplier() == null ? null : o.getSupplier().getId(),
                    o.getSupplier() == null ? null : o.getSupplier().getName(), o.getStatus(), o.getNotes(), o.getCreatedBy(),
                    o.getCreatedAt(), o.getOrderedAt(), o.getReceivedAt(), o.total(), o.getItems().stream().map(PoItemDto::of).toList());
        }
    }

    // ============================================================ users
    public record CreateUserRequest(@NotBlank String username, @NotBlank @Size(max = 100) String fullName,
                                    @Email String email, @NotNull Role role, @NotBlank String password) { }
    public record UpdateUserRequest(@Size(max = 100) String fullName, @Email String email, Role role, Boolean active) { }
    public record TempPasswordRequest(@NotBlank String temporaryPassword) { }

    public record UserDto(Long id, String username, String fullName, String email, Role role, boolean active,
                          boolean locked, boolean mustChangePassword, LocalDateTime lastLogin, LocalDateTime createdAt) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole(), u.isActive(),
                    u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now()),
                    u.isMustChangePassword(), u.getLastLogin(), u.getCreatedAt());
        }
    }

    // ============================================================ activity
    public record ActivityDto(Long id, String username, String action, String details, LocalDateTime createdAt) {
        public static ActivityDto of(ActivityLog a) {
            return new ActivityDto(a.getId(), a.getUsername(), a.getAction(), a.getDetails(), a.getCreatedAt());
        }
    }
}
