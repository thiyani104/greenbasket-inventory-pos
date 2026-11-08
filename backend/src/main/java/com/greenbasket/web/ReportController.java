package com.greenbasket.web;

import com.greenbasket.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) { this.reports = reports; }

    @GetMapping("/dashboard")
    public ReportService.Dashboard dashboard() { return reports.dashboard(); }

    @GetMapping("/summary")
    public ReportService.Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.summary(from, to);
    }

    @GetMapping("/reorder")
    public List<ReportService.ReorderSuggestion> reorder() { return reports.reorderSuggestions(); }

    @GetMapping("/dead-stock")
    public List<ReportService.DeadStock> deadStock(@RequestParam(defaultValue = "30") int days) { return reports.deadStock(days); }
}
