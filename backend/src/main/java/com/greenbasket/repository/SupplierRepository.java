package com.greenbasket.repository;

import com.greenbasket.domain.Supplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByNameKey(String nameKey);
    List<Supplier> findAllByOrderByNameKeyAsc();
}
