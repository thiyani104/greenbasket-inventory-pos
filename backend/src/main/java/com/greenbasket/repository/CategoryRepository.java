package com.greenbasket.repository;

import com.greenbasket.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByNameKey(String nameKey);
    List<Category> findAllByOrderByNameKeyAsc();
}
