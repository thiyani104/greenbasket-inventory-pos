package com.greenbasket.repository;

import com.greenbasket.domain.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    @Query("select a from ActivityLog a where :q = '' or lower(a.username) like concat('%', :q, '%') "
            + "or lower(a.action) like concat('%', :q, '%') or lower(a.details) like concat('%', :q, '%') "
            + "order by a.createdAt desc, a.id desc")
    Page<ActivityLog> search(@Param("q") String q, Pageable pageable);
}
