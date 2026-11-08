package com.greenbasket.repository;

import com.greenbasket.domain.Product;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByNameKey(String nameKey);

    boolean existsByCategoryId(Long categoryId);

    boolean existsBySupplierId(Long supplierId);

    /** Row lock so two cashiers cannot oversell the last unit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /** Locks several rows in a stable (id) order to avoid deadlocks between concurrent checkouts. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    List<Product> findByActiveTrueOrderByNameKeyAsc();

    @Query("select p from Product p where p.active = true and p.quantity <= p.reorderLevel order by p.quantity asc, p.nameKey asc")
    List<Product> findNeedingReorder();

    @Query("select p from Product p where p.active = true and p.expiryDate is not null and p.expiryDate <= :limit order by p.expiryDate asc")
    List<Product> findExpiringBy(@Param("limit") LocalDate limit);

    @Query("select sum(p.quantity * p.cost) from Product p where p.active = true")
    java.math.BigDecimal stockValueAtCost();

    @Query("select sum(p.quantity * p.price) from Product p where p.active = true")
    java.math.BigDecimal stockValueAtRetail();
}
