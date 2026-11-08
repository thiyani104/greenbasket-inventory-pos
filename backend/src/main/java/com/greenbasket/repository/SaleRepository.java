package com.greenbasket.repository;

import com.greenbasket.domain.Sale;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Sale> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<Sale> findWithItemsByReceiptNo(String receiptNo);

    @Query("select s from Sale s where s.createdAt >= :from and s.createdAt < :to order by s.createdAt desc, s.id desc")
    Page<Sale> findBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    /** Items are fetched in the same query for report maths. */
    @EntityGraph(attributePaths = "items")
    @Query("select distinct s from Sale s where s.createdAt >= :from and s.createdAt < :to order by s.createdAt asc")
    List<Sale> findAllWithItemsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(s) from Sale s where s.createdAt >= :from and s.createdAt < :to")
    long countBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
