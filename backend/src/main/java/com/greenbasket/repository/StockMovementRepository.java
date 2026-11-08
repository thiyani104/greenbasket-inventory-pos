package com.greenbasket.repository;

import com.greenbasket.domain.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("select m from StockMovement m where m.product.id = :productId order by m.createdAt desc, m.id desc")
    Page<StockMovement> findByProduct(@Param("productId") Long productId, Pageable pageable);

    @Query("select m from StockMovement m order by m.createdAt desc, m.id desc")
    Page<StockMovement> findRecent(Pageable pageable);
}
