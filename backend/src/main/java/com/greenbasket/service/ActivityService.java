package com.greenbasket.service;

import com.greenbasket.domain.ActivityLog;
import com.greenbasket.repository.ActivityLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private final ActivityLogRepository repo;

    public ActivityService(ActivityLogRepository repo) { this.repo = repo; }

    /** Joins the caller's transaction so the audit row commits or rolls back together with the change. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(Actor actor, String action, String details) {
        repo.save(new ActivityLog(actor.username(), action, details));
    }

    /** Independent transaction: used for events that must be kept even if the caller rolls back (failed logins). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAlways(String username, String action, String details) {
        repo.save(new ActivityLog(username, action, details));
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> search(String q, int page, int size) {
        String term = q == null ? "" : q.trim().toLowerCase();
        return repo.search(term, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
    }
}
