package com.greenbasket.repository;

import com.greenbasket.domain.PoStatus;
import com.greenbasket.domain.PurchaseOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseOrder> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<PurchaseOrder> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = "items")
    List<PurchaseOrder> findByStatusOrderByCreatedAtDescIdDesc(PoStatus status);
}
